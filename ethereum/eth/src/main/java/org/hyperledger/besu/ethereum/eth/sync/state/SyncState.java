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
package org.hyperledger.besu.ethereum.eth.sync.state;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.ChainHead;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.SyncStatus;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.core.Synchronizer.InSyncListener;
import org.hyperledger.besu.ethereum.eth.manager.ChainHeadEstimate;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.plugin.services.BesuEvents.SyncStatusListener;
import org.hyperledger.besu.util.Subscribers;
import org.hyperledger.besu.util.Subscribers.Unsubscriber;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.annotations.VisibleForTesting;
import io.vertx.core.impl.ConcurrentHashSet;

public class SyncState {

  private final Blockchain blockchain;
  private final EthPeers ethPeers;

  // A set of sync status trackers, organized by sync tolerance
  private final Set<InSyncTracker> inSyncTrackers = new ConcurrentHashSet<>();
  private final Subscribers<SyncStatusListener> syncStatusListeners = Subscribers.create();
  private volatile long chainHeightListenerId;
  private volatile Optional<SyncTarget> syncTarget = Optional.empty();
  private volatile long startingBlock;
  private final AtomicBoolean lastInSync = new AtomicBoolean(true);

  public SyncState(final Blockchain blockchain, final EthPeers ethPeers) {
    this.blockchain = blockchain;
    this.ethPeers = ethPeers;
    this.startingBlock = this.blockchain.getChainHeadBlockNumber();
    blockchain.observeBlockAdded(
        (event, chain) -> {
          if (event.isNewCanonicalHead()) {
            checkInSync();
          }
          switch (event.getEventType()) {
            case CHAIN_REORG:
              publishReorg();
              // fall through
            case HEAD_ADVANCED:
              publishSyncStatus();
              break;
            case FORK:
              // don't broadcast detected forks
              break;
          }
        });
  }

  @VisibleForTesting
  public void publishSyncStatus() {
    final SyncStatus syncStatus = syncStatus();
    syncStatusListeners.forEach(c -> c.onSyncStatusChanged(syncStatus));
  }

  private void publishReorg() {
    final long chainHeadBlockNumber = blockchain.getChainHeadBlockNumber();
    final SyncStatus syncStatus =
        new SyncStatus(
            startingBlock, chainHeadBlockNumber, bestChainHeight(chainHeadBlockNumber), false);

    syncStatusListeners.forEach(c -> c.onSyncStatusChanged(syncStatus));
  }

  /**
   * Add a listener that will be notified when this node's sync status changes. A node is considered
   * in-sync if the local chain height is no more than {@code SYNC_TOLERANCE} behind the highest
   * estimated remote chain height.
   *
   * @param listener The callback to invoke when the sync status changes
   * @return An {@code Unsubscriber} that can be used to stop listening for these events
   */
  public Unsubscriber subscribeInSync(final InSyncListener listener) {
    return subscribeInSync(listener, Synchronizer.DEFAULT_IN_SYNC_TOLERANCE);
  }

  /**
   * Add a listener that will be notified when this node's sync status changes. A node is considered
   * in-sync if the local chain height is no more than {@code syncTolerance} behind the highest
   * estimated remote chain height.
   *
   * @param listener The callback to invoke when the sync status changes
   * @param syncTolerance The tolerance used to determine whether this node is in-sync. A value of
   *     zero means that the node is considered in-sync only when the local chain height is greater
   *     than or equal to the best estimated remote chain height.
   * @return An {@code Unsubscriber} that can be used to stop listening for these events
   */
  public Unsubscriber subscribeInSync(final InSyncListener listener, final long syncTolerance) {
    final InSyncTracker inSyncTracker = InSyncTracker.create(listener, syncTolerance);
    inSyncTrackers.add(inSyncTracker);

    return () -> inSyncTrackers.remove(inSyncTracker);
  }

  public long subscribeSyncStatus(final SyncStatusListener listener) {
    return syncStatusListeners.subscribe(listener);
  }

  public boolean unsubscribeSyncStatus(final long listenerId) {
    return syncStatusListeners.unsubscribe(listenerId);
  }

  public SyncStatus syncStatus() {
    final long chainHeadBlockNumber = blockchain.getChainHeadBlockNumber();
    return new SyncStatus(
        startingBlock, chainHeadBlockNumber, bestChainHeight(chainHeadBlockNumber));
  }

  public Optional<SyncTarget> syncTarget() {
    return syncTarget;
  }

  public void setSyncTarget(final EthPeer peer, final BlockHeader commonAncestor) {
    final SyncTarget syncTarget = new SyncTarget(peer, commonAncestor);
    replaceSyncTarget(Optional.of(syncTarget));
  }

  public boolean isInSync() {
    return isInSync(Synchronizer.DEFAULT_IN_SYNC_TOLERANCE);
  }

  public boolean isInSync(final long syncTolerance) {
    return isInSync(
        getLocalChainHead(), getSyncTargetChainHead(), getBestPeerChainHead(), syncTolerance);
  }

  private boolean isInSync(
      final ChainHead localChain,
      final Optional<ChainHeadEstimate> syncTargetChain,
      final Optional<ChainHeadEstimate> bestPeerChain,
      final long syncTolerance) {
    // Sync target may be temporarily empty while we switch sync targets during a sync, so
    // check both the sync target and our best peer to determine if we're in sync or not
    return isInSync(localChain, syncTargetChain, syncTolerance)
        && isInSync(localChain, bestPeerChain, syncTolerance);
  }

  private boolean isInSync(
      final ChainHead localChain,
      final Optional<ChainHeadEstimate> remoteChain,
      final long syncTolerance) {
    return remoteChain
        .map(remoteState -> InSyncTracker.isInSync(localChain, remoteState, syncTolerance))
        .orElse(true);
  }

  private ChainHead getLocalChainHead() {
    return blockchain.getChainHead();
  }

  private Optional<ChainHeadEstimate> getSyncTargetChainHead() {
    return syncTarget.map(SyncTarget::peer).map(EthPeer::chainStateSnapshot);
  }

  private Optional<ChainHeadEstimate> getBestPeerChainHead() {
    return ethPeers.bestPeerWithHeightEstimate().map(EthPeer::chainStateSnapshot);
  }

  public void disconnectSyncTarget(final DisconnectReason reason) {
    syncTarget.ifPresent(syncTarget -> syncTarget.peer().disconnect(reason));
  }

  public void clearSyncTarget() {
    replaceSyncTarget(Optional.empty());
  }

  private synchronized void replaceSyncTarget(final Optional<SyncTarget> newTarget) {
    syncTarget.ifPresent(this::removeEstimatedHeightListener);
    syncTarget = newTarget;
    newTarget.ifPresent(this::addEstimatedHeightListener);
    checkInSync();
  }

  private void removeEstimatedHeightListener(final SyncTarget target) {
    target.removePeerChainEstimatedHeightListener(chainHeightListenerId);
  }

  private void addEstimatedHeightListener(final SyncTarget target) {
    chainHeightListenerId =
        target.addPeerChainEstimatedHeightListener(estimatedHeight -> checkInSync());
  }

  public long bestChainHeight(final long localChainHeight) {
    return Math.max(
        localChainHeight,
        ethPeers
            .bestPeerWithHeightEstimate()
            .map(p -> p.chainState().getEstimatedHeight())
            .orElse(localChainHeight));
  }

  private synchronized void checkInSync() {
    ChainHead localChain = getLocalChainHead();
    Optional<ChainHeadEstimate> syncTargetChain = getSyncTargetChainHead();
    Optional<ChainHeadEstimate> bestPeerChain = getBestPeerChainHead();
    final boolean currentInSync = isInSync(localChain, syncTargetChain, bestPeerChain, 0);
    if (lastInSync.compareAndSet(!currentInSync, currentInSync)) {
      if (currentInSync) {
        // when we fall out of sync change our starting block
        startingBlock = localChain.getHeight();
      }
    }

    inSyncTrackers.forEach(
        (syncTracker) -> syncTracker.checkState(localChain, syncTargetChain, bestPeerChain));
  }
}
