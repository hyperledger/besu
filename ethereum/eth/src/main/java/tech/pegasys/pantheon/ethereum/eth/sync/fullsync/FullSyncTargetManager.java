/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.eth.sync.fullsync;

import static java.util.concurrent.CompletableFuture.completedFuture;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.ChainState;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeers;
import tech.pegasys.pantheon.ethereum.eth.sync.SyncTargetManager;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncTarget;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class FullSyncTargetManager<C> extends SyncTargetManager<C> {

  private static final Logger LOG = LogManager.getLogger();
  private final SynchronizerConfiguration config;
  private final ProtocolContext<C> protocolContext;
  private final EthContext ethContext;

  FullSyncTargetManager(
      final SynchronizerConfiguration config,
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final MetricsSystem metricsSystem) {
    super(config, protocolSchedule, protocolContext, ethContext, metricsSystem);
    this.config = config;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
  }

  @Override
  protected Optional<SyncTarget> finalizeSelectedSyncTarget(final SyncTarget syncTarget) {
    final BlockHeader commonAncestor = syncTarget.commonAncestor();
    if (protocolContext
        .getWorldStateArchive()
        .isWorldStateAvailable(commonAncestor.getStateRoot())) {
      return Optional.of(syncTarget);
    } else {
      LOG.warn(
          "Disconnecting {} because world state is not available at common ancestor at block {}",
          syncTarget.peer(),
          commonAncestor.getNumber());
      syncTarget.peer().disconnect(DisconnectReason.USELESS_PEER);
      return Optional.empty();
    }
  }

  @Override
  protected CompletableFuture<Optional<EthPeer>> selectBestAvailableSyncTarget() {
    final Optional<EthPeer> maybeBestPeer = ethContext.getEthPeers().bestPeer();
    if (!maybeBestPeer.isPresent()) {
      LOG.info("No sync target, wait for peers.");
      return completedFuture(Optional.empty());
    } else {
      final EthPeer bestPeer = maybeBestPeer.get();
      if (isSyncTargetReached(bestPeer)) {
        // We're caught up to our best peer, try again when a new peer connects
        LOG.debug("Caught up to best peer: " + bestPeer.chainState().getEstimatedHeight());
        return completedFuture(Optional.empty());
      }
      return completedFuture(maybeBestPeer);
    }
  }

  @Override
  public boolean isSyncTargetReached(final EthPeer peer) {
    final long peerHeight = peer.chainState().getEstimatedHeight();
    final UInt256 peerTd = peer.chainState().getBestBlock().getTotalDifficulty();
    final MutableBlockchain blockchain = protocolContext.getBlockchain();

    return peerTd.compareTo(blockchain.getChainHead().getTotalDifficulty()) <= 0
        && peerHeight <= blockchain.getChainHeadBlockNumber();
  }

  @Override
  public boolean shouldSwitchSyncTarget(final SyncTarget currentTarget) {
    final EthPeer currentPeer = currentTarget.peer();
    final ChainState currentPeerChainState = currentPeer.chainState();
    final Optional<EthPeer> maybeBestPeer = ethContext.getEthPeers().bestPeer();

    return maybeBestPeer
        .map(
            bestPeer -> {
              if (EthPeers.BEST_CHAIN.compare(bestPeer, currentPeer) <= 0) {
                // Our current target is better or equal to the best peer
                return false;
              }
              // Require some threshold to be exceeded before switching targets to keep some
              // stability
              // when multiple peers are in range of each other
              final ChainState bestPeerChainState = bestPeer.chainState();
              final long heightDifference =
                  bestPeerChainState.getEstimatedHeight()
                      - currentPeerChainState.getEstimatedHeight();
              if (heightDifference == 0 && bestPeerChainState.getEstimatedHeight() == 0) {
                // Only check td if we don't have a height metric
                final UInt256 tdDifference =
                    bestPeerChainState
                        .getBestBlock()
                        .getTotalDifficulty()
                        .minus(currentPeerChainState.getBestBlock().getTotalDifficulty());
                return tdDifference.compareTo(config.downloaderChangeTargetThresholdByTd()) > 0;
              }
              return heightDifference > config.downloaderChangeTargetThresholdByHeight();
            })
        .orElse(false);
  }

  @Override
  public boolean shouldContinueDownloading() {
    return true;
  }
}
