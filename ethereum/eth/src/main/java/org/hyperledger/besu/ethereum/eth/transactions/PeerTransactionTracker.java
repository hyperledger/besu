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

import static java.util.Collections.emptyList;
import static org.hyperledger.besu.ethereum.core.Transaction.toHashList;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.BlockAddedObserver;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeerImmutableAttributes;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.plugin.data.AddedBlockContext;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PeerTransactionTracker
    implements EthPeer.DisconnectCallback, PendingTransactionDroppedListener, BlockAddedObserver {
  private static final Logger LOG = LoggerFactory.getLogger(PeerTransactionTracker.class);

  private final EthPeers ethPeers;
  private final EthScheduler ethScheduler;
  private final int maxTrackedSeenTxsPerPeer;
  private final boolean forgetEvictedTxsEnabled;
  private final Map<EthPeer, Set<Hash>> seenTransactions = new HashMap<>();
  private final Map<EthPeer, Set<Hash>> seenAnnouncements = new HashMap<>();
  private final Map<EthPeer, SequencedSet<Transaction>> transactionsToSend = new HashMap<>();
  private final Map<EthPeer, SequencedSet<Transaction>> transactionAnnouncementsToSend =
      new HashMap<>();
  private final Map<EthPeer, SequencedSet<Hash>> transactionAnnouncementsToRequest =
      new HashMap<>();
  private final Set<Hash> inProgressAnnouncements = new HashSet<>();
  private final Set<Hash> recentlyConfirmedTransactions = createSeenSet();

  public PeerTransactionTracker(
      final TransactionPoolConfiguration txPoolConfig,
      final EthPeers ethPeers,
      final EthScheduler scheduler) {
    this.ethPeers = ethPeers;
    this.ethScheduler = scheduler;
    this.maxTrackedSeenTxsPerPeer = txPoolConfig.getUnstable().getMaxTrackedSeenTxsPerPeer();
    this.forgetEvictedTxsEnabled = txPoolConfig.getUnstable().getPeerTrackerForgetEvictedTxs();
    ethScheduler.scheduleFutureTaskWithFixedDelay(
        this::logStats, Duration.ofMinutes(1), Duration.ofMinutes(1));
  }

  public synchronized void reset() {
    seenTransactions.clear();
    seenAnnouncements.clear();
    transactionsToSend.clear();
    transactionAnnouncementsToSend.clear();
    transactionAnnouncementsToRequest.clear();
    inProgressAnnouncements.clear();
    recentlyConfirmedTransactions.clear();
  }

  private synchronized void logStats() {
    if (LOG.isTraceEnabled()) {
      seenTransactions.forEach(
          (ethPeer, hashes) -> LOG.trace("Seen txs: peer={} size={}", ethPeer, hashes.size()));
      seenAnnouncements.forEach(
          (ethPeer, hashes) ->
              LOG.trace("Seen announcements: peer={} size={}", ethPeer, hashes.size()));
      transactionsToSend.forEach(
          (ethPeer, txs) -> LOG.trace("Txs to send: peer={} size={}", ethPeer, txs.size()));
      transactionAnnouncementsToSend.forEach(
          (ethPeer, txs) ->
              LOG.trace("Announcements to send: peer={} size={}", ethPeer, txs.size()));
      transactionAnnouncementsToRequest.forEach(
          (ethPeer, hashes) ->
              LOG.trace("Announcements to request: peer={} size={}", ethPeer, hashes.size()));
      LOG.trace("In progress announcements {}", inProgressAnnouncements.size());
      LOG.trace("Recently confirmed txs {}", recentlyConfirmedTransactions.size());
    }
  }

  public synchronized void markTransactionsAsSeen(
      final EthPeer peer, final Collection<Hash> seenHashes) {
    seenTransactions.computeIfAbsent(peer, key -> createSeenSet()).addAll(seenHashes);
    transactionAnnouncementsToRequest
        .values()
        .forEach(hashes -> seenHashes.forEach(hashes::remove));
  }

  public synchronized void markTransactionAsSeen(
      final EthPeer peer, final Transaction transaction) {
    final var seenHash = transaction.getHash();
    seenTransactions.computeIfAbsent(peer, key -> createSeenSet()).add(seenHash);
    transactionAnnouncementsToRequest.values().forEach(hashes -> hashes.remove(seenHash));
  }

  public synchronized void markTransactionAnnouncementsAsSeen(
      final EthPeer peer, final Collection<Hash> announcements) {
    seenAnnouncements.computeIfAbsent(peer, key -> createSeenSet()).addAll(announcements);
    // do not clean transactionAnnouncementsToRequest to allow for retries with other peers
  }

  public synchronized void addToPeerSendQueue(
      final EthPeer peer, final List<Transaction> transactions) {
    transactionsToSend.computeIfAbsent(peer, key -> new LinkedHashSet<>()).addAll(transactions);
  }

  public synchronized void addToPeerAnnouncementsSendQueue(
      final EthPeer peer, final List<Transaction> transactions) {
    transactionAnnouncementsToSend
        .computeIfAbsent(peer, key -> new LinkedHashSet<>())
        .addAll(transactions);
  }

  @VisibleForTesting
  synchronized Iterable<EthPeer> getEthPeersWithUnsentTransactions() {
    return Set.copyOf(transactionsToSend.keySet());
  }

  public synchronized Transaction claimTransactionToSendToPeer(final EthPeer peer) {
    final var txToSend = this.transactionsToSend.get(peer);
    if (txToSend != null && !txToSend.isEmpty()) {
      return txToSend.removeFirst();
    } else {
      return null;
    }
  }

  public synchronized Transaction claimTransactionAnnouncementToSendToPeer(final EthPeer peer) {
    final var announcementToSend = this.transactionAnnouncementsToSend.get(peer);
    if (announcementToSend != null && !announcementToSend.isEmpty()) {
      return announcementToSend.removeFirst();
    } else {
      return null;
    }
  }

  @SuppressWarnings("MixedMutabilityReturnType")
  public synchronized List<Hash> claimTransactionAnnouncementsToRequestFromPeer(
      final EthPeer peer, final int maxHashes) {
    final var transactionAnnouncements = transactionAnnouncementsToRequest.get(peer);

    if (transactionAnnouncements != null && !transactionAnnouncements.isEmpty()) {
      final var returnAnnouncements = new ArrayList<Hash>(maxHashes);
      final var itAnnouncements = transactionAnnouncements.iterator();
      while (returnAnnouncements.size() < maxHashes && itAnnouncements.hasNext()) {
        final var hash = itAnnouncements.next();
        if (hasSeenTransaction(hash)) {
          itAnnouncements.remove();
        } else if (!inProgressAnnouncements.contains(hash)) {
          returnAnnouncements.add(hash);
          inProgressAnnouncements.add(hash);
          itAnnouncements.remove();
        }
        // if announcement not in progress, then keep in the queue, since it could be
        // tried later if the current in progress retrieval should fail
      }
      return returnAnnouncements;
    } else {
      return emptyList();
    }
  }

  public synchronized void missedTransactionAnnouncements(
      final EthPeer peer, final List<Hash> missedHashes) {
    final var transactionAnnouncements = transactionAnnouncementsToRequest.get(peer);

    if (transactionAnnouncements != null && !transactionAnnouncements.isEmpty()) {
      missedHashes.forEach(transactionAnnouncements::remove);
    }
  }

  public synchronized void consumedTransactionAnnouncements(final List<Hash> requestedHashes) {
    requestedHashes.forEach(inProgressAnnouncements::remove);
  }

  public synchronized Collection<Hash> receivedTransactionAnnouncements(
      final EthPeer peer, final List<Hash> incomingTransactionAnnouncements) {

    final var freshAnnouncements = new ArrayList<Hash>(incomingTransactionAnnouncements.size());

    for (final var txAnnouncement : incomingTransactionAnnouncements) {
      if (!hasSeenTransaction(txAnnouncement)) {
        freshAnnouncements.add(txAnnouncement);
      }
    }

    markTransactionAnnouncementsAsSeen(peer, incomingTransactionAnnouncements);

    transactionAnnouncementsToRequest
        .computeIfAbsent(peer, key -> new LinkedHashSet<>())
        .addAll(freshAnnouncements);

    return freshAnnouncements;
  }

  public synchronized Collection<Transaction> receivedTransactions(
      final EthPeer peer, final List<Transaction> incomingTransactions) {
    final var freshTransactions = new ArrayList<Transaction>(incomingTransactions.size());

    for (final var transaction : incomingTransactions) {
      if (!hasSeenTransaction(transaction.getHash())) {
        freshTransactions.add(transaction);
      }
    }

    markTransactionsAsSeen(peer, toHashList(incomingTransactions));

    return freshTransactions;
  }

  public synchronized boolean hasSeenTransaction(final Hash txHash) {
    return recentlyConfirmedTransactions.contains(txHash)
        || seenTransactions.values().stream().anyMatch(seen -> seen.contains(txHash));
  }

  public boolean hasPeerSeenTransaction(final EthPeer peer, final Transaction transaction) {
    return hasPeerSeenTransaction(peer, transaction.getHash());
  }

  public synchronized boolean hasPeerSeenTransaction(final EthPeer peer, final Hash txHash) {
    if (recentlyConfirmedTransactions.contains(txHash)) {
      return true;
    }
    final Set<Hash> seenTransactionsForPeer = seenTransactions.get(peer);
    return seenTransactionsForPeer != null && seenTransactionsForPeer.contains(txHash);
  }

  public synchronized boolean hasSeenTransactionAnnouncement(
      final EthPeer peer, final Hash txHash) {
    if (recentlyConfirmedTransactions.contains(txHash)) {
      return true;
    }
    final var seenAnnouncementsForPeer = seenAnnouncements.get(peer);
    return seenAnnouncementsForPeer != null && seenAnnouncementsForPeer.contains(txHash);
  }

  public synchronized boolean hasSeenTransactionOrAnnouncement(
      final EthPeer peer, final Hash txHash) {
    if (recentlyConfirmedTransactions.contains(txHash)) {
      return true;
    }
    final Set<Hash> seenTransactionsForPeer = seenTransactions.get(peer);
    if (seenTransactionsForPeer != null && seenTransactionsForPeer.contains(txHash)) {
      return true;
    }
    final var seenAnnouncementsForPeer = seenAnnouncements.get(peer);
    return seenAnnouncementsForPeer != null && seenAnnouncementsForPeer.contains(txHash);
  }

  private <T> Set<T> createSeenSet() {
    return Collections.newSetFromMap(
        new LinkedHashMap<>(16, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(final Map.Entry<T, Boolean> eldest) {
            return size() > maxTrackedSeenTxsPerPeer;
          }
        });
  }

  @Override
  public synchronized void onDisconnect(final EthPeer peer) {
    LOG.atTrace().setMessage("onDisconnect for peer {}").addArgument(peer::getLoggableId).log();

    // here we reconcile all the trackers with the active peers, since due to the asynchronous
    // processing of incoming messages it could seldom happen that a tracker is recreated just
    // after a peer was disconnected, resulting in a memory leak.
    final Set<EthPeer> trackedPeers = new HashSet<>(seenTransactions.keySet());
    trackedPeers.addAll(seenAnnouncements.keySet());
    trackedPeers.addAll(transactionsToSend.keySet());
    trackedPeers.addAll(transactionAnnouncementsToSend.keySet());
    trackedPeers.addAll(transactionAnnouncementsToRequest.keySet());

    LOG.atTrace()
        .setMessage("{} tracked peers ({})")
        .addArgument(trackedPeers.size())
        .addArgument(() -> logPeerSet(trackedPeers))
        .log();

    final Set<EthPeer> connectedPeers =
        ethPeers
            .streamAllPeers()
            .map(EthPeerImmutableAttributes::ethPeer)
            .collect(Collectors.toUnmodifiableSet());

    final var disconnectedPeers = trackedPeers;
    disconnectedPeers.removeAll(connectedPeers);
    LOG.atTrace()
        .setMessage("Removing {} transaction trackers for disconnected peers ({})")
        .addArgument(disconnectedPeers.size())
        .addArgument(() -> logPeerSet(disconnectedPeers))
        .log();

    disconnectedPeers.forEach(
        disconnectedPeer -> {
          seenTransactions.remove(disconnectedPeer);
          seenAnnouncements.remove(disconnectedPeer);
          transactionsToSend.remove(disconnectedPeer);
          transactionAnnouncementsToSend.remove(disconnectedPeer);
          transactionAnnouncementsToRequest.remove(disconnectedPeer);
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
  public synchronized void onTransactionDropped(
      final Transaction transaction, final RemovalReason reason) {
    if (reason.stopBroadcasting()) {
      transactionsToSend.values().forEach(txs -> txs.remove(transaction));
      transactionAnnouncementsToSend.values().forEach(txs -> txs.remove(transaction));
      transactionAnnouncementsToRequest.values().forEach(txs -> txs.remove(transaction.getHash()));
    }

    if (reason.stopTracking() && forgetEvictedTxsEnabled) {
      seenTransactions.values().forEach(st -> st.remove(transaction.getHash()));
    }
  }

  @Override
  public void onBlockAdded(final BlockAddedEvent event) {
    ethScheduler.scheduleServiceTask(
        () -> {
          if (event.getEventType().equals(AddedBlockContext.EventType.HEAD_ADVANCED)
              || event.getEventType().equals(AddedBlockContext.EventType.CHAIN_REORG)) {
            final var confirmedTxs = event.getBlock().getBody().getTransactions();
            final var confirmedTxHashes = toHashList(confirmedTxs);

            synchronized (this) {
              recentlyConfirmedTransactions.addAll(confirmedTxHashes);
              transactionsToSend.values().forEach(txs -> confirmedTxs.forEach(txs::remove));
              transactionAnnouncementsToSend
                  .values()
                  .forEach(txs -> confirmedTxs.forEach(txs::remove));
              transactionAnnouncementsToRequest
                  .values()
                  .forEach(txs -> confirmedTxHashes.forEach(txs::remove));
            }
          }
        });
  }
}
