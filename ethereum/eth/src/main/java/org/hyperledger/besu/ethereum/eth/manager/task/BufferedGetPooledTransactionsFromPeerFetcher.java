/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.eth.manager.task;

import static org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration.DEFAULT_MAX_PENDING_TRANSACTIONS;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.transactions.PeerTransactionTracker;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ScheduledFuture;

import com.google.common.collect.EvictingQueue;
import com.google.common.collect.Queues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("UnstableApiUsage")
public class BufferedGetPooledTransactionsFromPeerFetcher {
  private static final Logger LOG =
      LoggerFactory.getLogger(BufferedGetPooledTransactionsFromPeerFetcher.class);
  private static final int MAX_HASHES = 256;

  private final TransactionPool transactionPool;
  private final PeerTransactionTracker transactionTracker;
  private final EthContext ethContext;
  private final TransactionPoolMetrics metrics;
  private final ScheduledFuture<?> scheduledFuture;
  private final EthPeer peer;
  private final Queue<Hash> txAnnounces;

  public BufferedGetPooledTransactionsFromPeerFetcher(
      final EthContext ethContext,
      final ScheduledFuture<?> scheduledFuture,
      final EthPeer peer,
      final TransactionPool transactionPool,
      final PeerTransactionTracker transactionTracker,
      final TransactionPoolMetrics metrics) {
    this.ethContext = ethContext;
    this.scheduledFuture = scheduledFuture;
    this.peer = peer;
    this.transactionPool = transactionPool;
    this.transactionTracker = transactionTracker;
    this.metrics = metrics;
    this.txAnnounces =
        Queues.synchronizedQueue(EvictingQueue.create(DEFAULT_MAX_PENDING_TRANSACTIONS));
  }

  public ScheduledFuture<?> getScheduledFuture() {
    return scheduledFuture;
  }

  public void requestTransactions() {
    List<Hash> txHashesAnnounced;
    while (!(txHashesAnnounced = getTxHashesAnnounced()).isEmpty()) {
      final GetPooledTransactionsFromPeerTask task =
          GetPooledTransactionsFromPeerTask.forHashes(
              ethContext, txHashesAnnounced, metrics.getMetricsSystem());
      task.assignPeer(peer);
      ethContext
          .getScheduler()
          .scheduleSyncWorkerTask(task)
          .thenAccept(
              result -> {
                List<Transaction> retrievedTransactions = result.getResult();
                transactionTracker.markTransactionsAsSeen(peer, retrievedTransactions);

                traceLambda(
                    LOG,
                    "Got {} transactions of {} hashes requested from peer {}",
                    retrievedTransactions::size,
                    task.getTransactionHashes()::size,
                    peer::toString);

                transactionPool.addRemoteTransactions(retrievedTransactions);
              });
    }
  }

  public void addHashes(final Collection<Hash> hashes) {
    txAnnounces.addAll(hashes);
  }

  private List<Hash> getTxHashesAnnounced() {
    final List<Hash> toRetrieve = new ArrayList<>(MAX_HASHES);
    int discarded = 0;
    while (toRetrieve.size() < MAX_HASHES && !txAnnounces.isEmpty()) {
      final Hash txHashAnnounced = txAnnounces.poll();
      if (!transactionTracker.hasSeenTransaction(txHashAnnounced)) {
        toRetrieve.add(txHashAnnounced);
      } else {
        discarded++;
      }
    }

    final int alreadySeenCount = discarded;
    metrics.incrementAlreadySeenTransactions("new_pooled_transaction_hashes", alreadySeenCount);
    traceLambda(
        LOG,
        "Transaction hashes to request from peer {}, fresh count {}, already seen count {}",
        peer::toString,
        toRetrieve::size,
        () -> alreadySeenCount);

    return toRetrieve;
  }
}
