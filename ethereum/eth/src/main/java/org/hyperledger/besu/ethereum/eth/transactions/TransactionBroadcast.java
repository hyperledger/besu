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

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool.TransactionBatchAddedListener;

class TransactionBroadcast implements TransactionBatchAddedListener {

  private final PeerTransactionTracker transactionTracker;
  private final TransactionsMessageSender transactionsMessageSender;
  private final PendingTransactionsMessageSender pendingTransactionsMessageSender;
  private final EthContext ethContext;
  private final int numPeersToSendFullTransactions;

  public TransactionBroadcast(
      final PeerTransactionTracker transactionTracker,
      final TransactionsMessageSender transactionsMessageSender,
      final PendingTransactionsMessageSender pendingTransactionsMessageSender,
      final EthContext ethContext) {
    this.transactionTracker = transactionTracker;
    this.transactionsMessageSender = transactionsMessageSender;
    this.pendingTransactionsMessageSender = pendingTransactionsMessageSender;
    this.ethContext = ethContext;
    this.numPeersToSendFullTransactions =
        (int) Math.ceil(Math.sqrt(ethContext.getEthPeers().getMaxPeers()));
  }

  @Override
  public void onTransactionsAdded(final Iterable<Transaction> transactions) {
    List<EthPeer> availablePeers =
        ethContext.getEthPeers().streamAvailablePeers().collect(Collectors.toList());

    Collections.shuffle(availablePeers);

    sendFullTransactions(transactions, availablePeers.subList(0, numPeersToSendFullTransactions));

    sendTransactionHashes(
        transactions,
        availablePeers.subList(numPeersToSendFullTransactions, availablePeers.size() - 1));
  }

  private void sendFullTransactions(
      Iterable<Transaction> transactions, List<EthPeer> fullTransactionPeers) {
    fullTransactionPeers.forEach(
        peer -> {
          transactions.forEach(
              transaction -> transactionTracker.addToPeerSendQueue(peer, transaction));
          ethContext
              .getScheduler()
              .scheduleSyncWorkerTask(() -> transactionsMessageSender.sendTransactionsToPeer(peer));
        });
  }

  private void sendTransactionHashes(
      Iterable<Transaction> transactions, List<EthPeer> transactionHashPeers) {
    transactionHashPeers.stream()
        .filter(transactionTracker::peerHasPooledTransactionHashSupport)
        .forEach(
            peer -> {
              transactions.forEach(
                  transaction -> transactionTracker.addToPeerSendQueue(peer, transaction));
              ethContext
                  .getScheduler()
                  .scheduleSyncWorkerTask(
                      () -> pendingTransactionsMessageSender.sendTransactionHashesToPeer(peer));
            });
  }
}
