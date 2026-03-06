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
import static java.util.Collections.emptyNavigableMap;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.SequencedSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PeerTransactionTracker
    implements EthPeer.DisconnectCallback, PendingTransactionDroppedListener, BlockAddedObserver {
  private static final Logger LOG = LoggerFactory.getLogger(PeerTransactionTracker.class);

  private final EthPeers ethPeers;
  private final EthScheduler ethScheduler;
  private final int maxTrackedSeenTxsPerPeer;
  private final int maxSendQueueSizePerPeer;
  private final boolean forgetEvictedTxsEnabled;
  private final Map<EthPeer, Set<Hash>> seenTransactions = new HashMap<>();
  private final Map<EthPeer, Set<Hash>> seenAnnouncements = new HashMap<>();
  private final Map<EthPeer, SequencedSet<Transaction>> transactionsToSend = new HashMap<>();
  private final Map<EthPeer, SequencedSet<Transaction>> announcementsToSend = new HashMap<>();
  private final Map<EthPeer, SequencedMap<Hash, TransactionAnnouncement>>
      announcementsToRequestByHash = new HashMap<>();
  private final Set<Hash> inProgressAnnouncements = new HashSet<>();
  private final Set<Hash> recentlyConfirmedTransactions;

  public PeerTransactionTracker(
      final TransactionPoolConfiguration txPoolConfig,
      final EthPeers ethPeers,
      final EthScheduler scheduler) {
    this.ethPeers = ethPeers;
    this.ethScheduler = scheduler;
    this.maxTrackedSeenTxsPerPeer = txPoolConfig.getUnstable().getMaxTrackedSeenTxsPerPeer();
    this.maxSendQueueSizePerPeer = txPoolConfig.getUnstable().getMaxSendQueueSizePerPeer();
    this.forgetEvictedTxsEnabled = txPoolConfig.getUnstable().getPeerTrackerForgetEvictedTxs();
    this.recentlyConfirmedTransactions = createBoundedSet(maxTrackedSeenTxsPerPeer);
    ethScheduler.scheduleFutureTaskWithFixedDelay(
        this::logStats, Duration.ofMinutes(1), Duration.ofMinutes(1));
  }

  public synchronized void reset() {
    seenTransactions.clear();
    seenAnnouncements.clear();
    transactionsToSend.clear();
    announcementsToSend.clear();
    announcementsToRequestByHash.clear();
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
      announcementsToSend.forEach(
          (ethPeer, txs) ->
              LOG.trace("Announcements to send: peer={} size={}", ethPeer, txs.size()));
      announcementsToRequestByHash.forEach(
          (ethPeer, hashes) ->
              LOG.trace("Announcements to request: peer={} size={}", ethPeer, hashes.size()));
      LOG.trace("In progress announcements {}", inProgressAnnouncements.size());
      LOG.trace("Recently confirmed txs {}", recentlyConfirmedTransactions.size());
    }
  }

  public synchronized void markTransactionsAsSeen(
      final EthPeer peer, final Collection<Hash> seenHashes) {
    seenTransactions
        .computeIfAbsent(peer, key -> createBoundedSet(maxTrackedSeenTxsPerPeer))
        .addAll(seenHashes);
    // remove the seen txs from any request queue
    announcementsToRequestByHash.values().forEach(hashes -> seenHashes.forEach(hashes::remove));
  }

  public synchronized void markTransactionAsSeen(
      final EthPeer peer, final Transaction transaction) {
    final var seenHash = transaction.getHash();
    seenTransactions
        .computeIfAbsent(peer, key -> createBoundedSet(maxTrackedSeenTxsPerPeer))
        .add(seenHash);
    // remove the seen txs from any request queue
    announcementsToRequestByHash.values().forEach(hashes -> hashes.remove(seenHash));
  }

  public synchronized void markAnnouncementsAsSeenByTransaction(
      final EthPeer peer, final Collection<Transaction> announcements) {
    final Set<Hash> seenAnnouncementForPeer =
        seenAnnouncements.computeIfAbsent(peer, key -> createBoundedSet(maxTrackedSeenTxsPerPeer));
    announcements.stream().map(Transaction::getHash).forEach(seenAnnouncementForPeer::add);
    // do not clean transactionAnnouncementsToRequest to allow for retries with other peers
  }

  public synchronized void markAnnouncementsAsSeen(
      final EthPeer peer, final Collection<TransactionAnnouncement> announcements) {
    final Set<Hash> seenAnnouncementForPeer =
        seenAnnouncements.computeIfAbsent(peer, key -> createBoundedSet(maxTrackedSeenTxsPerPeer));
    announcements.stream().map(TransactionAnnouncement::hash).forEach(seenAnnouncementForPeer::add);
    // do not clean transactionAnnouncementsToRequest to allow for retries with other peers
  }

  public synchronized void addToPeerSendQueue(
      final EthPeer peer, final List<Transaction> transactions) {
    transactionsToSend
        .computeIfAbsent(peer, key -> createBoundedSet(maxSendQueueSizePerPeer))
        .addAll(transactions);
  }

  public synchronized void addToPeerAnnouncementsSendQueue(
      final EthPeer peer, final List<Transaction> transactions) {
    announcementsToSend
        .computeIfAbsent(peer, key -> createBoundedSet(maxSendQueueSizePerPeer))
        .addAll(transactions);
  }

  public synchronized Transaction claimTransactionToSendToPeer(final EthPeer peer) {
    final SequencedSet<Transaction> txsToSend = this.transactionsToSend.get(peer);
    if (txsToSend != null && !txsToSend.isEmpty()) {
      return txsToSend.removeFirst();
    } else {
      return null;
    }
  }

  public synchronized Transaction claimAnnouncementToSendToPeer(final EthPeer peer) {
    final SequencedSet<Transaction> announcementsToSend = this.announcementsToSend.get(peer);
    if (announcementsToSend != null && !announcementsToSend.isEmpty()) {
      return announcementsToSend.removeFirst();
    } else {
      return null;
    }
  }

  @SuppressWarnings("MixedMutabilityReturnType")
  public synchronized List<TransactionAnnouncement> claimAnnouncementsToRequestFromPeer(
      final EthPeer peer, final int maxHashes, final long maxSize) {
    final SequencedMap<Hash, TransactionAnnouncement> announcements =
        announcementsToRequestByHash.getOrDefault(peer, emptyNavigableMap());

    if (announcements.isEmpty()) {
      return emptyList();
    }

    final List<TransactionAnnouncement> returnAnnouncements = new ArrayList<>(maxHashes);
    final Iterator<TransactionAnnouncement> itAnnouncements = announcements.values().iterator();
    long currentSize = 0;
    while (returnAnnouncements.size() < maxHashes
        && currentSize < maxSize
        && itAnnouncements.hasNext()) {
      final TransactionAnnouncement announcement = itAnnouncements.next();
      currentSize += announcement.size();
      if (alreadySeenTransaction(announcement.hash())) {
        itAnnouncements.remove();
      } else if (!inProgressAnnouncements.contains(announcement.hash())) {
        returnAnnouncements.add(announcement);
        inProgressAnnouncements.add(announcement.hash());
        itAnnouncements.remove();
      }
      // if announcement is in progress, then keep in the queue, since it could be
      // tried later if the current in progress retrieval should fail
    }
    return returnAnnouncements;
  }

  public synchronized void consumedAnnouncements(final List<Hash> requestedHashes) {
    requestedHashes.forEach(inProgressAnnouncements::remove);
  }

  public synchronized Collection<TransactionAnnouncement> receivedAnnouncements(
      final EthPeer peer, final List<TransactionAnnouncement> incomingAnnouncements) {

    final List<TransactionAnnouncement> freshAnnouncements =
        new ArrayList<>(incomingAnnouncements.size());

    final Map<Hash, TransactionAnnouncement> announcementsByHashForPeer =
        announcementsToRequestByHash.computeIfAbsent(
            peer, key -> createBoundedMap(maxSendQueueSizePerPeer));

    for (final TransactionAnnouncement txAnnouncement : incomingAnnouncements) {
      if (!alreadySeenTransaction(txAnnouncement.hash())) {
        freshAnnouncements.add(txAnnouncement);
        announcementsByHashForPeer.put(txAnnouncement.hash(), txAnnouncement);
      }
    }

    markAnnouncementsAsSeen(peer, incomingAnnouncements);

    return freshAnnouncements;
  }

  public synchronized Collection<Transaction> receivedTransactions(
      final EthPeer peer, final List<Transaction> incomingTransactions) {
    final List<Transaction> freshTransactions = new ArrayList<>(incomingTransactions.size());

    for (final Transaction transaction : incomingTransactions) {
      if (!alreadySeenTransaction(transaction.getHash())) {
        freshTransactions.add(transaction);
      }
    }

    markTransactionsAsSeen(peer, toHashList(incomingTransactions));

    return freshTransactions;
  }

  public synchronized boolean alreadySeenTransaction(final Hash txHash) {
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

  public synchronized boolean hasPeerSeenAnnouncement(final EthPeer peer, final Hash txHash) {
    if (recentlyConfirmedTransactions.contains(txHash)) {
      return true;
    }
    final Set<Hash> seenAnnouncementsForPeer = seenAnnouncements.get(peer);
    return seenAnnouncementsForPeer != null && seenAnnouncementsForPeer.contains(txHash);
  }

  public synchronized boolean hasPeerSeenTransactionOrAnnouncement(
      final EthPeer peer, final Hash txHash) {
    if (recentlyConfirmedTransactions.contains(txHash)) {
      return true;
    }
    final Set<Hash> seenTransactionsForPeer = seenTransactions.get(peer);
    if (seenTransactionsForPeer != null && seenTransactionsForPeer.contains(txHash)) {
      return true;
    }
    final Set<Hash> seenAnnouncementsForPeer = seenAnnouncements.get(peer);
    return seenAnnouncementsForPeer != null && seenAnnouncementsForPeer.contains(txHash);
  }

  private <T> SequencedSet<T> createBoundedSet(final int maxSize) {
    return Collections.newSequencedSetFromMap(
        new LinkedHashMap<>(16, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(final Map.Entry<T, Boolean> eldest) {
            return size() > maxSize;
          }
        });
  }

  private <K, V> SequencedMap<K, V> createBoundedMap(final int maxSize) {
    return new LinkedHashMap<>(16, 0.75f, true) {
      @Override
      protected boolean removeEldestEntry(final Map.Entry<K, V> eldest) {
        return size() > maxSize;
      }
    };
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
    trackedPeers.addAll(announcementsToSend.keySet());
    trackedPeers.addAll(announcementsToRequestByHash.keySet());

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

    final Set<EthPeer> disconnectedPeers = trackedPeers;
    disconnectedPeers.removeAll(connectedPeers);
    LOG.atTrace()
        .setMessage(
            "{} transaction trackers for disconnected peers ({}) will be remove after a graceful delay")
        .addArgument(disconnectedPeers.size())
        .addArgument(() -> logPeerSet(disconnectedPeers))
        .log();

    disconnectedPeers.forEach(
        disconnectedPeer -> {
          seenTransactions.remove(disconnectedPeer);
          seenAnnouncements.remove(disconnectedPeer);
          transactionsToSend.remove(disconnectedPeer);
          announcementsToSend.remove(disconnectedPeer);
          announcementsToRequestByHash.remove(disconnectedPeer);
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
      announcementsToSend.values().forEach(txs -> txs.remove(transaction));
      announcementsToRequestByHash.values().forEach(txs -> txs.remove(transaction.getHash()));
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
            final List<Transaction> confirmedTxs = event.getBlock().getBody().getTransactions();
            final List<Hash> confirmedTxHashes = toHashList(confirmedTxs);

            synchronized (this) {
              recentlyConfirmedTransactions.addAll(confirmedTxHashes);
              transactionsToSend.values().forEach(txs -> confirmedTxs.forEach(txs::remove));
              announcementsToSend.values().forEach(txs -> confirmedTxs.forEach(txs::remove));
              announcementsToRequestByHash
                  .values()
                  .forEach(txs -> confirmedTxHashes.forEach(txs::remove));
            }
          }
        });
  }
}
