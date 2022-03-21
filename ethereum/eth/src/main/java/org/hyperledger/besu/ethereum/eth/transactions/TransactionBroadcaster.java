/*
 * Copyright contributors to Hyperledger Besu
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

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.messages.EthPV65;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool.TransactionBatchAddedListener;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter.TransactionInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TransactionBroadcaster implements TransactionBatchAddedListener {

  private final AbstractPendingTransactionsSorter pendingTransactions;
  private final PeerTransactionTracker transactionTracker;
  private final TransactionsMessageSender transactionsMessageSender;
  private final NewPooledTransactionHashesMessageSender newPooledTransactionHashesMessageSender;
  private final EthContext ethContext;
  private final int numPeersToSendFullTransactions;

  public TransactionBroadcaster(
      final EthContext ethContext,
      final AbstractPendingTransactionsSorter pendingTransactions,
      final PeerTransactionTracker transactionTracker,
      final TransactionsMessageSender transactionsMessageSender,
      final NewPooledTransactionHashesMessageSender newPooledTransactionHashesMessageSender) {
    this.pendingTransactions = pendingTransactions;
    this.transactionTracker = transactionTracker;
    this.transactionsMessageSender = transactionsMessageSender;
    this.newPooledTransactionHashesMessageSender = newPooledTransactionHashesMessageSender;
    this.ethContext = ethContext;
    this.numPeersToSendFullTransactions =
        (int) Math.ceil(Math.sqrt(ethContext.getEthPeers().getMaxPeers()));
  }

  public void handlePeerConnection(final EthPeer peer) {
    Set<TransactionInfo> pendingTransactionInfo = pendingTransactions.getTransactionInfo();
    if (!pendingTransactionInfo.isEmpty()) {
      if (peer.hasSupportForMessage(EthPV65.NEW_POOLED_TRANSACTION_HASHES)) {
        sendTransactionHashes(toTransactionList(pendingTransactionInfo), List.of(peer));
      } else {
        sendFullTransactions(toTransactionList(pendingTransactionInfo), List.of(peer));
      }
    }
  }

  @Override
  public void onTransactionsAdded(final Iterable<Transaction> transactions) {
    final int currPeerCount = ethContext.getEthPeers().peerCount();
    if (currPeerCount == 0) {
      return;
    }

    List<EthPeer> peersWithOnlyTransactionSupport = new ArrayList<>(currPeerCount);
    List<EthPeer> peersWithTransactionHashesSupport = new ArrayList<>(currPeerCount);

    ethContext
        .getEthPeers()
        .streamAvailablePeers()
        .forEach(
            peer -> {
              if (peer.hasSupportForMessage(EthPV65.NEW_POOLED_TRANSACTION_HASHES)) {
                peersWithTransactionHashesSupport.add(peer);
              } else {
                peersWithOnlyTransactionSupport.add(peer);
              }
            });

    if (peersWithOnlyTransactionSupport.size() < numPeersToSendFullTransactions) {
      final int delta =
          Math.min(
              numPeersToSendFullTransactions - peersWithOnlyTransactionSupport.size(),
              peersWithTransactionHashesSupport.size());
      // add peer from the other list to reach the required size
      Collections.shuffle(peersWithTransactionHashesSupport);
      IntStream.range(0, delta)
          .forEach(
              _unused ->
                  peersWithOnlyTransactionSupport.add(
                      peersWithTransactionHashesSupport.remove(
                          peersWithTransactionHashesSupport.size() - 1)));
    }

    sendFullTransactions(transactions, peersWithOnlyTransactionSupport);

    sendTransactionHashes(transactions, peersWithTransactionHashesSupport);
  }

  private void sendFullTransactions(
      final Iterable<Transaction> transactions, final List<EthPeer> fullTransactionPeers) {
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
      final Iterable<Transaction> transactions, final List<EthPeer> transactionHashPeers) {
    transactionHashPeers.stream()
        .forEach(
            peer -> {
              transactions.forEach(
                  transaction -> transactionTracker.addToPeerSendQueue(peer, transaction));
              ethContext
                  .getScheduler()
                  .scheduleSyncWorkerTask(
                      () ->
                          newPooledTransactionHashesMessageSender.sendTransactionHashesToPeer(
                              peer));
            });
  }

  private Collection<Transaction> toTransactionList(
      final Collection<TransactionInfo> transactionsInfo) {
    return transactionsInfo.stream()
        .map(TransactionInfo::getTransaction)
        .collect(Collectors.toUnmodifiableList());
  }
}
