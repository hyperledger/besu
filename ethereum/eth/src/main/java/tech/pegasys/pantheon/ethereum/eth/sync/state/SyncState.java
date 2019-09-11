/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.eth.sync.state;

import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.chain.ChainHead;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.SyncStatus;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeers;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.plugin.services.PantheonEvents.SyncStatusListener;
import tech.pegasys.pantheon.util.Subscribers;

import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;

public class SyncState {
  private static final long SYNC_TOLERANCE = 5;
  private final Blockchain blockchain;
  private final EthPeers ethPeers;

  private final long startingBlock;
  private boolean lastInSync = true;
  private final Subscribers<InSyncListener> inSyncListeners = Subscribers.create();
  private final Subscribers<SyncStatusListener> syncStatusListeners = Subscribers.create();
  private Optional<SyncTarget> syncTarget = Optional.empty();
  private long chainHeightListenerId;

  public SyncState(final Blockchain blockchain, final EthPeers ethPeers) {
    this.blockchain = blockchain;
    this.ethPeers = ethPeers;
    this.startingBlock = this.blockchain.getChainHeadBlockNumber();
    blockchain.observeBlockAdded(
        (event, chain) -> {
          if (event.isNewCanonicalHead()) {
            checkInSync();
          }
          publishSyncStatus();
        });
  }

  @VisibleForTesting
  public void publishSyncStatus() {
    final SyncStatus syncStatus = syncStatus();
    syncStatusListeners.forEach(c -> c.onSyncStatusChanged(syncStatus));
  }

  public void addInSyncListener(final InSyncListener observer) {
    inSyncListeners.subscribe(observer);
  }

  public long addSyncStatusListener(final SyncStatusListener observer) {
    return syncStatusListeners.subscribe(observer);
  }

  public void removeSyncStatusListener(final long listenerId) {
    syncStatusListeners.unsubscribe(listenerId);
  }

  public SyncStatus syncStatus() {
    final long chainHeadBlockNumber = blockchain.getChainHeadBlockNumber();
    return new SyncStatus(
        startingBlock(), chainHeadBlockNumber, bestChainHeight(chainHeadBlockNumber));
  }

  public long startingBlock() {
    return startingBlock;
  }

  public Optional<SyncTarget> syncTarget() {
    return syncTarget;
  }

  public void setSyncTarget(final EthPeer peer, final BlockHeader commonAncestor) {
    final SyncTarget syncTarget = new SyncTarget(peer, commonAncestor);
    replaceSyncTarget(Optional.of(syncTarget));
  }

  public boolean isInSync() {
    return isInSync(SYNC_TOLERANCE);
  }

  public boolean isInSync(final long syncTolerance) {
    // Sync target may be temporarily empty while we switch sync targets during a sync, so
    // check both the sync target and our best peer to determine if we're in sync or not
    return isInSyncWithTarget(syncTolerance) && isInSyncWithBestPeer(syncTolerance);
  }

  private boolean isInSyncWithTarget(final long syncTolerance) {
    return syncTarget
        .map(t -> t.estimatedTargetHeight() - blockchain.getChainHeadBlockNumber() <= syncTolerance)
        .orElse(true);
  }

  private boolean isInSyncWithBestPeer(final long syncTolerance) {
    final ChainHead chainHead = blockchain.getChainHead();
    return ethPeers
        .bestPeerWithHeightEstimate()
        .filter(peer -> peer.chainState().chainIsBetterThan(chainHead))
        .map(EthPeer::chainState)
        .map(chainState -> chainState.getEstimatedHeight() - chainHead.getHeight() <= syncTolerance)
        .orElse(true);
  }

  public void disconnectSyncTarget(final DisconnectReason reason) {
    syncTarget.ifPresent(syncTarget -> syncTarget.peer().disconnect(reason));
  }

  public void clearSyncTarget() {
    replaceSyncTarget(Optional.empty());
  }

  private void replaceSyncTarget(final Optional<SyncTarget> newTarget) {
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
    final boolean currentInSync = isInSync();
    if (lastInSync != currentInSync) {
      lastInSync = currentInSync;
      inSyncListeners.forEach(c -> c.onSyncStatusChanged(currentInSync));
    }
  }

  @FunctionalInterface
  public interface InSyncListener {
    void onSyncStatusChanged(boolean newSyncStatus);
  }
}
