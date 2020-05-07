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
package org.hyperledger.besu.ethereum.eth.transactions;

import static java.time.Instant.now;
import static org.apache.logging.log4j.LogManager.getLogger;

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.task.GetPooledTransactionsFromPeerTask;
import org.hyperledger.besu.ethereum.eth.messages.NewPooledTransactionHashesMessage;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.metrics.RunnableCounter;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Logger;

class PendingTransactionsMessageProcessor {

  private static final int MAX_HASHES = 256;
  private static final int SKIPPED_MESSAGES_LOGGING_THRESHOLD = 1000;
  private static final long SYNC_TOLERANCE = 100L;

  private static final Logger LOG = getLogger();
  private final PeerPendingTransactionTracker transactionTracker;
  private final Counter totalSkippedTransactionsMessageCounter;
  private final TransactionPool transactionPool;
  private final EthContext ethContext;
  private final MetricsSystem metricsSystem;
  private final SyncState syncState;

  PendingTransactionsMessageProcessor(
      final PeerPendingTransactionTracker transactionTracker,
      final TransactionPool transactionPool,
      final Counter metricsCounter,
      final EthContext ethContext,
      final MetricsSystem metricsSystem,
      final SyncState syncState) {
    this.transactionTracker = transactionTracker;
    this.transactionPool = transactionPool;
    this.ethContext = ethContext;
    this.metricsSystem = metricsSystem;
    this.syncState = syncState;
    this.totalSkippedTransactionsMessageCounter =
        new RunnableCounter(
            metricsCounter,
            () ->
                LOG.warn(
                    "{} expired transaction messages have been skipped.",
                    SKIPPED_MESSAGES_LOGGING_THRESHOLD),
            SKIPPED_MESSAGES_LOGGING_THRESHOLD);
  }

  void processNewPooledTransactionHashesMessage(
      final EthPeer peer,
      final NewPooledTransactionHashesMessage transactionsMessage,
      final Instant startedAt,
      final Duration keepAlive) {
    // Check if message not expired.
    if (startedAt.plus(keepAlive).isAfter(now())) {
      this.processNewPooledTransactionHashesMessage(peer, transactionsMessage);
    } else {
      totalSkippedTransactionsMessageCounter.inc();
    }
  }

  private void processNewPooledTransactionHashesMessage(
      final EthPeer peer, final NewPooledTransactionHashesMessage transactionsMessage) {
    try {
      LOG.trace("Received pooled transaction hashes message from {}", peer);

      final List<Hash> pendingHashes = transactionsMessage.pendingTransactions();
      transactionTracker.markTransactionsHashesAsSeen(peer, pendingHashes);
      if (syncState.isInSync(SYNC_TOLERANCE)) {
        final List<Hash> toRequest = new ArrayList<>();
        for (final Hash hash : pendingHashes) {
          if (transactionPool.addTransactionHash(hash)) {
            toRequest.add(hash);
          }
        }
        while (!toRequest.isEmpty()) {
          final List<Hash> messageHashes =
              toRequest.subList(0, Math.min(toRequest.size(), MAX_HASHES));
          final GetPooledTransactionsFromPeerTask task =
              GetPooledTransactionsFromPeerTask.forHashes(ethContext, messageHashes, metricsSystem);
          task.assignPeer(peer);
          ethContext
              .getScheduler()
              .scheduleSyncWorkerTask(task)
              .thenAccept(
                  result -> {
                    final List<Transaction> txs = result.getResult();
                    transactionPool.addRemoteTransactions(txs);
                  });

          toRequest.removeAll(messageHashes);
        }
      }
    } catch (final RLPException ex) {
      if (peer != null) {
        LOG.debug(
            "Malformed pooled transaction hashes message received, disconnecting: {}", peer, ex);
        peer.disconnect(DisconnectReason.BREACH_OF_PROTOCOL);
      }
    }
  }
}
