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

import static org.hyperledger.besu.ethereum.core.Transaction.toHashList;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetPooledTransactionsFromPeerTask;
import org.hyperledger.besu.ethereum.eth.transactions.PeerTransactionTracker;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BufferedGetPooledTransactionsFromPeerFetcher {
  private static final Logger LOG =
      LoggerFactory.getLogger(BufferedGetPooledTransactionsFromPeerFetcher.class);
  private static final AtomicInteger TASK_ID_GENERATOR = new AtomicInteger(0);
  @VisibleForTesting static final int MAX_HASHES = 256;

  private final TransactionPool transactionPool;
  private final PeerTransactionTracker transactionTracker;
  private final EthContext ethContext;
  private final EthPeer peer;

  public BufferedGetPooledTransactionsFromPeerFetcher(
      final EthContext ethContext,
      final EthPeer peer,
      final TransactionPool transactionPool,
      final PeerTransactionTracker transactionTracker) {
    this.ethContext = ethContext;
    this.peer = peer;
    this.transactionPool = transactionPool;
    this.transactionTracker = transactionTracker;
  }

  public void requestTransactions() {
    final int taskId = TASK_ID_GENERATOR.incrementAndGet();
    int iteration = 0;
    List<Hash> txHashesToRequest;
    while (!(txHashesToRequest =
            transactionTracker.claimTransactionAnnouncementsToRequestFromPeer(peer, MAX_HASHES))
        .isEmpty()) {

      try {
        // retry until this batch is complete, in a best effort way,
        // since loop can be interrupted by a failure or an empty response.
        while (!txHashesToRequest.isEmpty()) {
          ++iteration;
          LOG.atTrace()
              .setMessage(
                  "[{}:{}] Transaction hashes to request from peer={}, requesting hashes={}")
              .addArgument(taskId)
              .addArgument(iteration)
              .addArgument(peer::getLoggableId)
              .addArgument(txHashesToRequest)
              .log();

          final GetPooledTransactionsFromPeerTask task =
              new GetPooledTransactionsFromPeerTask(txHashesToRequest);

          final PeerTaskExecutorResult<List<Transaction>> taskResult =
              ethContext.getPeerTaskExecutor().executeAgainstPeer(task, peer);

          // if failure or no results then stop iterating
          if (taskResult.responseCode() != PeerTaskExecutorResponseCode.SUCCESS
              || taskResult.result().map(List::isEmpty).orElse(true)) {

            LOG.atTrace()
                .setMessage(
                    "[{}:{}] Aborting task, failed to retrieve transactions by hash from peer={}, requested hashes={}, result={}")
                .addArgument(taskId)
                .addArgument(iteration)
                .addArgument(peer::getLoggableId)
                .addArgument(txHashesToRequest)
                .addArgument(
                    () ->
                        taskResult.responseCode() == PeerTaskExecutorResponseCode.SUCCESS
                            ? "empty response"
                            : taskResult.responseCode())
                .log();

            // in case of failure or no progress, stop iterating for this batch
            // so other peers can try to download
            break;

          } else {
            final List<Transaction> retrievedTransactions = taskResult.result().get();
            final List<Hash> retrievedHashes = toHashList(retrievedTransactions);
            transactionTracker.markTransactionsAsSeen(peer, retrievedHashes);
            transactionPool.addRemoteTransactions(retrievedTransactions);

            txHashesToRequest.removeIf(retrievedHashes::contains);

            LOG.atTrace()
                .setMessage(
                    "[{}:{}] Got {} transactions requested (missing {}) from peer={}, "
                        + "retrieved hashes={}, missed hashes={}")
                .addArgument(taskId)
                .addArgument(iteration)
                .addArgument(retrievedHashes::size)
                .addArgument(txHashesToRequest::size)
                .addArgument(peer::getLoggableId)
                .addArgument(retrievedHashes)
                .addArgument(txHashesToRequest)
                .log();
          }
        }
      } catch (final Throwable t) {
        LOG.atTrace()
            .setMessage(
                "[{}:{}] Failed to retrieve transactions by hash from peer={}, requested hashes={}")
            .addArgument(taskId)
            .addArgument(iteration)
            .addArgument(peer)
            .addArgument(txHashesToRequest)
            .setCause(t)
            .log();
      } finally {
        transactionTracker.consumedTransactionAnnouncements(txHashesToRequest);
      }
    }
  }
}
