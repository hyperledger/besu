/*
 * Copyright contributors to Hyperledger Besu.
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

import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.toTransactionList;

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool.TransactionBatchAddedListener;

import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionBroadcaster
    implements TransactionBatchAddedListener, PendingTransactionDroppedListener {
  private static final Logger LOG = LoggerFactory.getLogger(TransactionBroadcaster.class);

  private final PeerTransactionTracker transactionTracker;
  private final NewPooledTransactionHashesMessageSender newPooledTransactionHashesMessageSender;
  private final EthContext ethContext;

  public TransactionBroadcaster(
      final EthContext ethContext,
      final PeerTransactionTracker transactionTracker,
      final NewPooledTransactionHashesMessageSender newPooledTransactionHashesMessageSender) {
    this.transactionTracker = transactionTracker;
    this.newPooledTransactionHashesMessageSender = newPooledTransactionHashesMessageSender;
    this.ethContext = ethContext;
  }

  public void relayTransactionPoolTo(
      final EthPeer peer, final Collection<PendingTransaction> pendingTransactions) {
    if (!pendingTransactions.isEmpty()) {
      sendTransactionHashes(toTransactionList(pendingTransactions), List.of(peer));
    }
  }

  @Override
  public void onTransactionsAdded(final Collection<Transaction> transactions) {
    final int currPeerCount = ethContext.getEthPeers().peerCount();
    if (currPeerCount == 0) {
      return;
    }
    final List<EthPeer> ethPeers = ethContext.getEthPeers().streamAvailablePeers().toList();
    LOG.atTrace().setMessage("Sending transaction hashes to {} peers.").addArgument(ethPeers).log();

    sendTransactionHashes(transactions, ethPeers);
  }

  private void sendTransactionHashes(
      final Collection<Transaction> transactions, final List<EthPeer> transactionHashPeers) {
    if (!transactions.isEmpty()) {
      transactionHashPeers.stream()
          .forEach(
              peer -> {
                transactions.forEach(
                    transaction -> transactionTracker.addToPeerHashSendQueue(peer, transaction));
                ethContext
                    .getScheduler()
                    .scheduleSyncWorkerTask(
                        () ->
                            newPooledTransactionHashesMessageSender.sendTransactionHashesToPeer(
                                peer));
              });
    }
  }

  @Override
  public void onTransactionDropped(final Transaction transaction, final RemovalReason reason) {
    transactionTracker.onTransactionDropped(transaction, reason);
  }
}
