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

import static org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration.MAX_PENDING_TRANSACTIONS;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.transactions.PeerTransactionTracker;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import com.google.common.collect.EvictingQueue;
import com.google.common.collect.Queues;

@SuppressWarnings("UnstableApiUsage")
public class BufferedGetPooledTransactionsFromPeerFetcher {

  private static final int MAX_HASHES = 256;

  private final TransactionPool transactionPool;
  private final PeerTransactionTracker transactionTracker;
  private final EthContext ethContext;
  private final MetricsSystem metricsSystem;
  private final EthPeer peer;
  private final Queue<Hash> txAnnounces;

  public BufferedGetPooledTransactionsFromPeerFetcher(
      final EthContext ethContext,
      final TransactionPool transactionPool,
      final PeerTransactionTracker transactionTracker,
      final MetricsSystem metricsSystem,
      final EthPeer peer) {
    this.ethContext = ethContext;
    this.transactionPool = transactionPool;
    this.transactionTracker = transactionTracker;
    this.metricsSystem = metricsSystem;
    this.peer = peer;
    this.txAnnounces = Queues.synchronizedQueue(EvictingQueue.create(MAX_PENDING_TRANSACTIONS));
  }

  public void requestTransactions() {
    for (List<Hash> txHashesAnnounced = getTxHashesAnnounced();
        !txHashesAnnounced.isEmpty();
        txHashesAnnounced = getTxHashesAnnounced()) {

      final GetPooledTransactionsFromPeerTask task =
          GetPooledTransactionsFromPeerTask.forHashes(ethContext, txHashesAnnounced, metricsSystem);
      task.assignPeer(peer);
      ethContext
          .getScheduler()
          .scheduleSyncWorkerTask(task)
          .thenAccept(result -> transactionPool.addRemoteTransactions(result.getResult()));
    }
  }

  public void addHash(final Hash hash) {
    txAnnounces.add(hash);
  }

  private List<Hash> getTxHashesAnnounced() {
    List<Hash> retrieved = new ArrayList<>(MAX_HASHES);
    while (retrieved.size() < MAX_HASHES && !txAnnounces.isEmpty()) {
      final Hash txHashAnnounced = txAnnounces.poll();
      if (notSeen(txHashAnnounced)) {
        retrieved.add(txHashAnnounced);
      }
    }
    return retrieved;
  }

  private boolean notSeen(final Hash txHash) {
    return transactionPool.getTransactionByHash(txHash).isEmpty()
        && !transactionTracker.hasSeenTransaction(txHash);
  }
}
