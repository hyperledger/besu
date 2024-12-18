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
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.sync.PivotBlockSelector;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.TrailingPeerLimiter;
import org.hyperledger.besu.ethereum.eth.sync.TrailingPeerRequirements;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PivotSelectorFromPeers implements PivotBlockSelector {

  private static final Logger LOG = LoggerFactory.getLogger(PivotSelectorFromPeers.class);

  protected final EthContext ethContext;
  protected final SynchronizerConfiguration syncConfig;
  private final SyncState syncState;

  public PivotSelectorFromPeers(
      final EthContext ethContext,
      final SynchronizerConfiguration syncConfig,
      final SyncState syncState) {
    this.ethContext = ethContext;
    this.syncConfig = syncConfig;
    this.syncState = syncState;
  }

  @Override
  public Optional<FastSyncState> selectNewPivotBlock() {
    return selectBestPeer().flatMap(this::fromBestPeer);
  }

  @Override
  public CompletableFuture<Void> prepareRetry() {
    final long estimatedPivotBlock = conservativelyEstimatedPivotBlock();
    final TrailingPeerLimiter trailingPeerLimiter =
        new TrailingPeerLimiter(
            ethContext.getEthPeers(),
            () ->
                new TrailingPeerRequirements(
                    estimatedPivotBlock, syncConfig.getMaxTrailingPeers()));
    trailingPeerLimiter.enforceTrailingPeerLimit();

    return ethContext
        .getEthPeers()
        .waitForPeer((peer) -> peer.chainState().getEstimatedHeight() >= estimatedPivotBlock)
        .thenRun(() -> {});
  }

  @Override
  public long getBestChainHeight() {
    return syncState.bestChainHeight();
  }

  protected Optional<FastSyncState> fromBestPeer(final EthPeer peer) {
    final long pivotBlockNumber =
        peer.chainState().getEstimatedHeight() - syncConfig.getSyncPivotDistance();
    if (pivotBlockNumber <= BlockHeader.GENESIS_BLOCK_NUMBER) {
      // Peer's chain isn't long enough, return an empty value, so we can try again.
      LOG.info("Waiting for peers with sufficient chain height");
      return Optional.empty();
    }
    LOG.info("Selecting block number {} as fast sync pivot block.", pivotBlockNumber);
    return Optional.of(new FastSyncState(pivotBlockNumber));
  }

  protected Optional<EthPeer> selectBestPeer() {
    return ethContext
        .getEthPeers()
        .bestPeerMatchingCriteria(this::canPeerDeterminePivotBlock)
        // Only select a pivot block number when we have a minimum number of height estimates
        .filter(unused -> enoughFastSyncPeersArePresent());
  }

  private boolean enoughFastSyncPeersArePresent() {
    final long peerCount = countPeersThatCanDeterminePivotBlock();
    final int minPeerCount = syncConfig.getSyncMinimumPeerCount();
    if (peerCount < minPeerCount) {
      LOG.info(
          "Waiting for valid peers with chain height information.  {} / {} required peers currently available.",
          peerCount,
          minPeerCount);
      return false;
    }
    return true;
  }

  private long countPeersThatCanDeterminePivotBlock() {
    return ethContext
        .getEthPeers()
        .streamAvailablePeers()
        .filter(this::canPeerDeterminePivotBlock)
        .count();
  }

  private boolean canPeerDeterminePivotBlock(final EthPeer peer) {
    LOG.debug(
        "peer {} hasEstimatedHeight {} isFullyValidated? {}",
        peer.getLoggableId(),
        peer.chainState().hasEstimatedHeight(),
        peer.isFullyValidated());
    return peer.chainState().hasEstimatedHeight() && peer.isFullyValidated();
  }

  private long conservativelyEstimatedPivotBlock() {
    final long estimatedNextPivot =
        syncState.getLocalChainHeight() + syncConfig.getSyncPivotDistance();
    return Math.min(syncState.bestChainHeight(), estimatedNextPivot);
  }
}
