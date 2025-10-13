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

import static org.hyperledger.besu.datatypes.TransactionType.BLOB;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.toTransactionList;

import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.messages.EthPV65;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool.TransactionBatchAddedListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionBroadcaster
    implements TransactionBatchAddedListener, PendingTransactionDroppedListener {
  private static final Logger LOG = LoggerFactory.getLogger(TransactionBroadcaster.class);

  private static final EnumSet<TransactionType> ANNOUNCE_HASH_ONLY_TX_TYPES = EnumSet.of(BLOB);

  private static final Boolean HASH_ONLY_BROADCAST = Boolean.TRUE;
  private static final Boolean FULL_BROADCAST = Boolean.FALSE;

  private final PeerTransactionTracker transactionTracker;
  private final TransactionsMessageSender transactionsMessageSender;
  private final NewPooledTransactionHashesMessageSender newPooledTransactionHashesMessageSender;
  private final EthContext ethContext;
  private final Random random;

  public TransactionBroadcaster(
      final EthContext ethContext,
      final PeerTransactionTracker transactionTracker,
      final TransactionsMessageSender transactionsMessageSender,
      final NewPooledTransactionHashesMessageSender newPooledTransactionHashesMessageSender) {
    this(
        ethContext,
        transactionTracker,
        transactionsMessageSender,
        newPooledTransactionHashesMessageSender,
        null);
  }

  @VisibleForTesting
  protected TransactionBroadcaster(
      final EthContext ethContext,
      final PeerTransactionTracker transactionTracker,
      final TransactionsMessageSender transactionsMessageSender,
      final NewPooledTransactionHashesMessageSender newPooledTransactionHashesMessageSender,
      final Long seed) {
    this.transactionTracker = transactionTracker;
    this.transactionsMessageSender = transactionsMessageSender;
    this.newPooledTransactionHashesMessageSender = newPooledTransactionHashesMessageSender;
    this.ethContext = ethContext;
    this.random = seed != null ? new Random(seed) : new Random();
  }

  public void relayTransactionPoolTo(
      final EthPeer peer, final Collection<PendingTransaction> pendingTransactions) {
    if (!pendingTransactions.isEmpty()) {
      if (peer.hasSupportForMessage(EthPV65.NEW_POOLED_TRANSACTION_HASHES)) {
        sendTransactionHashes(toTransactionList(pendingTransactions), List.of(peer));
      } else {
        // we need to exclude txs that support hash only broadcasting
        final var fullBroadcastTxs =
            pendingTransactions.stream()
                .map(PendingTransaction::getTransaction)
                .filter(tx -> !ANNOUNCE_HASH_ONLY_TX_TYPES.contains(tx.getType()))
                .toList();
        sendFullTransactions(fullBroadcastTxs, List.of(peer));
      }
    }
  }

  @Override
  public void onTransactionsAdded(final Collection<Transaction> transactions) {
    final int currPeerCount = ethContext.getEthPeers().peerCount();
    if (currPeerCount == 0) {
      return;
    }

    final int numPeersToSendFullTransactions = (int) Math.round(Math.sqrt(currPeerCount));

    final Map<Boolean, List<Transaction>> transactionByBroadcastMode =
        transactions.stream()
            .collect(
                Collectors.partitioningBy(
                    tx -> ANNOUNCE_HASH_ONLY_TX_TYPES.contains(tx.getType())));

    final List<EthPeer> sendOnlyFullTransactionPeers = new ArrayList<>(currPeerCount);
    final List<EthPeer> sendOnlyHashPeers = new ArrayList<>(currPeerCount);
    final List<EthPeer> sendMixedPeers = new ArrayList<>(currPeerCount);

    ethContext
        .getEthPeers()
        .streamAvailablePeers()
        .forEach(
            peer -> {
              if (peer.hasSupportForMessage(EthPV65.NEW_POOLED_TRANSACTION_HASHES)) {
                sendOnlyHashPeers.add(peer);
              } else {
                sendOnlyFullTransactionPeers.add(peer);
              }
            });

    if (sendOnlyFullTransactionPeers.size() < numPeersToSendFullTransactions) {
      final int delta =
          Math.min(
              numPeersToSendFullTransactions - sendOnlyFullTransactionPeers.size(),
              sendOnlyHashPeers.size());

      Collections.shuffle(sendOnlyHashPeers, random);

      // move peers from the mixed list to reach the required size for full transaction peers
      movePeersBetweenLists(sendOnlyHashPeers, sendMixedPeers, delta);
    }

    LOG.atTrace()
        .setMessage(
            "Sending full transactions to {} peers, transaction hashes only to {} peers and mixed to {} peers."
                + " Peers w/o eth/65 {}, peers with eth/65 {}")
        .addArgument(sendOnlyFullTransactionPeers::size)
        .addArgument(sendOnlyHashPeers::size)
        .addArgument(sendMixedPeers::size)
        .addArgument(sendOnlyFullTransactionPeers)
        .addArgument(() -> sendOnlyHashPeers.toString() + sendMixedPeers)
        .log();

    sendToFullTransactionsPeers(
        transactionByBroadcastMode.get(FULL_BROADCAST), sendOnlyFullTransactionPeers);

    sendToOnlyHashPeers(transactionByBroadcastMode, sendOnlyHashPeers);

    sendToMixedPeers(transactionByBroadcastMode, sendMixedPeers);
  }

  private void sendToFullTransactionsPeers(
      final List<Transaction> fullBroadcastTransactions, final List<EthPeer> fullTransactionPeers) {
    sendFullTransactions(fullBroadcastTransactions, fullTransactionPeers);
  }

  private void sendToOnlyHashPeers(
      final Map<Boolean, List<Transaction>> txsByHashOnlyBroadcast,
      final List<EthPeer> hashOnlyPeers) {
    final List<Transaction> allTransactions =
        txsByHashOnlyBroadcast.values().stream().flatMap(List::stream).toList();

    sendTransactionHashes(allTransactions, hashOnlyPeers);
  }

  private void sendToMixedPeers(
      final Map<Boolean, List<Transaction>> txsByHashOnlyBroadcast,
      final List<EthPeer> mixedPeers) {
    sendFullTransactions(txsByHashOnlyBroadcast.get(FULL_BROADCAST), mixedPeers);
    sendTransactionHashes(txsByHashOnlyBroadcast.get(HASH_ONLY_BROADCAST), mixedPeers);
  }

  private void sendFullTransactions(
      final List<Transaction> transactions, final List<EthPeer> fullTransactionPeers) {
    if (!transactions.isEmpty()) {
      fullTransactionPeers.forEach(
          peer -> {
            transactions.forEach(
                transaction -> transactionTracker.addToPeerSendQueue(peer, transaction));
            ethContext
                .getScheduler()
                .scheduleSyncWorkerTask(
                    () -> transactionsMessageSender.sendTransactionsToPeer(peer));
          });
    }
  }

  private void sendTransactionHashes(
      final List<Transaction> transactions, final List<EthPeer> transactionHashPeers) {
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

  private void movePeersBetweenLists(
      final List<EthPeer> sourceList, final List<EthPeer> destinationList, final int num) {

    final int stopIndex = sourceList.size() - num;
    for (int i = sourceList.size() - 1; i >= stopIndex; i--) {
      destinationList.add(sourceList.remove(i));
    }
  }

  @Override
  public void onTransactionDropped(final Transaction transaction, final RemovalReason reason) {
    transactionTracker.onTransactionDropped(transaction, reason);
  }
}
