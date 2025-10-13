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

import static java.util.Collections.emptySet;
import static org.hyperledger.besu.ethereum.core.Transaction.toHashList;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PeerTransactionTracker
    implements EthPeer.DisconnectCallback, PendingTransactionDroppedListener {
  private static final Logger LOG = LoggerFactory.getLogger(PeerTransactionTracker.class);

  private static final int MAX_TRACKED_SEEN_TRANSACTIONS = 100_000;

  private final EthPeers ethPeers;
  private final Map<EthPeer, Set<Hash>> seenTransactions = new ConcurrentHashMap<>();
  private final Map<EthPeer, Set<Transaction>> transactionsToSend = new ConcurrentHashMap<>();
  private final Map<EthPeer, Set<Transaction>> transactionHashesToSend = new ConcurrentHashMap<>();

  public PeerTransactionTracker(final EthPeers ethPeers) {
    this.ethPeers = ethPeers;
  }

  public void reset() {
    seenTransactions.clear();
    transactionsToSend.clear();
    transactionHashesToSend.clear();
  }

  public synchronized void markTransactionsAsSeen(
      final EthPeer peer, final Collection<Transaction> transactions) {
    markTransactionHashesAsSeen(peer, toHashList(transactions));
  }

  public synchronized void markTransactionHashesAsSeen(
      final EthPeer peer, final Collection<Hash> txHashes) {
    final Set<Hash> seenTransactionsForPeer = getOrCreateSeenTransactionsForPeer(peer);
    seenTransactionsForPeer.addAll(txHashes);
  }

  public synchronized void addToPeerSendQueue(final EthPeer peer, final Transaction transaction) {
    if (!hasPeerSeenTransaction(peer, transaction)) {
      transactionsToSend.computeIfAbsent(peer, key -> createTransactionsSet()).add(transaction);
    }
  }

  public synchronized void addToPeerHashSendQueue(
      final EthPeer peer, final Transaction transaction) {
    if (!hasPeerSeenTransaction(peer, transaction)) {
      transactionHashesToSend
          .computeIfAbsent(peer, key -> createTransactionsSet())
          .add(transaction);
    }
  }

  public Iterable<EthPeer> getEthPeersWithUnsentTransactions() {
    return transactionsToSend.keySet();
  }

  public synchronized Set<Transaction> claimTransactionsToSendToPeer(final EthPeer peer) {
    final Set<Transaction> transactionsToSend = this.transactionsToSend.remove(peer);
    if (transactionsToSend != null) {
      markTransactionsAsSeen(peer, transactionsToSend);
      return transactionsToSend;
    } else {
      return emptySet();
    }
  }

  public synchronized Set<Transaction> claimTransactionHashesToSendToPeer(final EthPeer peer) {
    final Set<Transaction> transactionHashesToSend = this.transactionHashesToSend.remove(peer);
    if (transactionHashesToSend != null) {
      markTransactionHashesAsSeen(peer, toHashList(transactionHashesToSend));
      return transactionHashesToSend;
    } else {
      return emptySet();
    }
  }

  public boolean hasSeenTransaction(final Hash txHash) {
    return seenTransactions.values().stream().anyMatch(seen -> seen.contains(txHash));
  }

  private Set<Hash> getOrCreateSeenTransactionsForPeer(final EthPeer peer) {
    return seenTransactions.computeIfAbsent(peer, key -> createTransactionsSet());
  }

  boolean hasPeerSeenTransaction(final EthPeer peer, final Transaction transaction) {
    return hasPeerSeenTransaction(peer, transaction.getHash());
  }

  boolean hasPeerSeenTransaction(final EthPeer peer, final Hash txHash) {
    final Set<Hash> seenTransactionsForPeer = seenTransactions.get(peer);
    return seenTransactionsForPeer != null && seenTransactionsForPeer.contains(txHash);
  }

  private <T> Set<T> createTransactionsSet() {
    return Collections.synchronizedSet(
        Collections.newSetFromMap(
            new LinkedHashMap<>(16, 0.75f, true) {
              @Override
              protected boolean removeEldestEntry(final Map.Entry<T, Boolean> eldest) {
                return size() > MAX_TRACKED_SEEN_TRANSACTIONS;
              }
            }));
  }

  @Override
  public void onDisconnect(final EthPeer peer) {
    LOG.atTrace().setMessage("onDisconnect for peer {}").addArgument(peer::getLoggableId).log();

    // here we reconcile all the trackers with the active peers, since due to the asynchronous
    // processing of incoming messages it could seldom happen that a tracker is recreated just
    // after a peer was disconnected, resulting in a memory leak.
    final Set<EthPeer> trackedPeers = new HashSet<>(seenTransactions.keySet());
    trackedPeers.addAll(transactionsToSend.keySet());
    trackedPeers.addAll(transactionHashesToSend.keySet());

    LOG.atTrace()
        .setMessage("{} tracked peers ({})")
        .addArgument(trackedPeers.size())
        .addArgument(() -> logPeerSet(trackedPeers))
        .log();

    final Set<EthPeer> connectedPeers =
        ethPeers.streamAllPeers().collect(Collectors.toUnmodifiableSet());

    final var disconnectedPeers = trackedPeers;
    disconnectedPeers.removeAll(connectedPeers);
    LOG.atTrace()
        .setMessage("Removing {} transaction trackers for disconnected peers ({})")
        .addArgument(disconnectedPeers.size())
        .addArgument(() -> logPeerSet(disconnectedPeers))
        .log();

    disconnectedPeers.stream()
        .forEach(
            disconnectedPeer -> {
              seenTransactions.remove(disconnectedPeer);
              transactionsToSend.remove(disconnectedPeer);
              transactionHashesToSend.remove(disconnectedPeer);
              LOG.atTrace()
                  .setMessage("Removed transaction trackers for disconnected peer {}")
                  .addArgument(disconnectedPeer::getLoggableId)
                  .log();
            });
  }

  private String logPeerSet(final Set<EthPeer> peers) {
    return peers.stream().map(EthPeer::getLoggableId).collect(Collectors.joining(","));
  }

  @Override
  public void onTransactionDropped(final Transaction transaction, final RemovalReason reason) {
    if (reason.stopTracking()) {
      seenTransactions.values().stream().forEach(st -> st.remove(transaction.getHash()));
    }
  }
}
