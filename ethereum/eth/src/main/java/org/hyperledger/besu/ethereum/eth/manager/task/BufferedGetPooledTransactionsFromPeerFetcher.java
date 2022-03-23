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
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.transactions.NewPooledTransactionHashesMessageProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import com.google.common.collect.EvictingQueue;
import com.google.common.collect.Queues;

@SuppressWarnings("UnstableApiUsage")
public class BufferedGetPooledTransactionsFromPeerFetcher {

  private static final int MAX_HASHES = 256;

  private final EthPeer peer;
  private final NewPooledTransactionHashesMessageProcessor processor;
  private final Queue<Hash> txAnnounces;

  public BufferedGetPooledTransactionsFromPeerFetcher(
      final EthPeer peer, final NewPooledTransactionHashesMessageProcessor processor) {
    this.peer = peer;
    this.processor = processor;
    this.txAnnounces = Queues.synchronizedQueue(EvictingQueue.create(MAX_PENDING_TRANSACTIONS));
  }

  public void requestTransactions() {
    for (List<Hash> txAnnounces = getTxAnnounces();
        !txAnnounces.isEmpty();
        txAnnounces = getTxAnnounces()) {
      final GetPooledTransactionsFromPeerTask task =
          GetPooledTransactionsFromPeerTask.forHashes(
              processor.getEthContext(), txAnnounces, processor.getMetricsSystem());
      task.assignPeer(peer);
      processor
          .getEthContext()
          .getScheduler()
          .scheduleSyncWorkerTask(task)
          .thenAccept(
              result -> processor.getTransactionPool().addRemoteTransactions(result.getResult()));
    }
  }

  public void addHash(final Hash hash) {
    txAnnounces.add(hash);
  }

  private List<Hash> getTxAnnounces() {
    List<Hash> retrieved = new ArrayList<>();
    while (retrieved.size() < MAX_HASHES && !txAnnounces.isEmpty()) {
      final Hash txAnnounce = txAnnounces.poll();
      if (processor.getTransactionPool().getTransactionByHash(txAnnounce).isEmpty()) {
        retrieved.add(txAnnounce);
      }
    }
    return retrieved;
  }
}
