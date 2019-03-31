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
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.SyncStatus;
import tech.pegasys.pantheon.ethereum.core.Synchronizer.SyncStatusListener;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeers;
import tech.pegasys.pantheon.util.Subscribers;

import java.util.Optional;

public class SyncState {
  private static final long SYNC_TOLERANCE = 5;
  private final Blockchain blockchain;
  private final EthPeers ethPeers;

  private final long startingBlock;
  private boolean lastInSync = true;
  private final Subscribers<InSyncListener> inSyncListeners = new Subscribers<>();
  private final Subscribers<SyncStatusListener> syncStatusListeners = new Subscribers<>();
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

  private void publishSyncStatus() {
    final SyncStatus syncStatus = syncStatus();
    syncStatusListeners.forEach(c -> c.onSyncStatus(syncStatus));
  }

  public void addInSyncListener(final InSyncListener observer) {
    inSyncListeners.subscribe(observer);
  }

  public void addSyncStatusListener(final SyncStatusListener observer) {
    syncStatusListeners.subscribe(observer);
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

  public SyncTarget setSyncTarget(final EthPeer peer, final BlockHeader commonAncestor) {
    final SyncTarget syncTarget = new SyncTarget(peer, commonAncestor);
    replaceSyncTarget(Optional.of(syncTarget));
    return syncTarget;
  }

  public boolean isInSync() {
    return syncTarget
        .map(
            t -> t.estimatedTargetHeight() - blockchain.getChainHeadBlockNumber() <= SYNC_TOLERANCE)
        .orElse(true);
  }

  public void setCommonAncestor(final BlockHeader commonAncestor) {
    syncTarget.ifPresent(target -> target.setCommonAncestor(commonAncestor));
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
        ethPeers.bestPeer().map(p -> p.chainState().getEstimatedHeight()).orElse(localChainHeight));
  }

  private synchronized void checkInSync() {
    final boolean currentSyncStatus = isInSync();
    if (lastInSync != currentSyncStatus) {
      lastInSync = currentSyncStatus;
      inSyncListeners.forEach(c -> c.onSyncStatusChanged(currentSyncStatus));
    }
  }

  @FunctionalInterface
  public interface InSyncListener {
    void onSyncStatusChanged(boolean newSyncStatus);
  }
}
