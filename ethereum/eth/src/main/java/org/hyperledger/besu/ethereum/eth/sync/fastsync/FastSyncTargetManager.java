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
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.hyperledger.besu.ethereum.eth.sync.fastsync.PivotBlockRetriever.MAX_PIVOT_BLOCK_RETRIES;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.sync.SyncTargetManager;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.tasks.RetryingGetHeaderFromPeerByNumberTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class FastSyncTargetManager<C> extends SyncTargetManager<C> {
  private static final Logger LOG = LogManager.getLogger();

  private final ProtocolSchedule<C> protocolSchedule;
  private final ProtocolContext<C> protocolContext;
  private final EthContext ethContext;
  private final MetricsSystem metricsSystem;
  private final BlockHeader pivotBlockHeader;

  public FastSyncTargetManager(
      final SynchronizerConfiguration config,
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final MetricsSystem metricsSystem,
      final BlockHeader pivotBlockHeader) {
    super(config, protocolSchedule, protocolContext, ethContext, metricsSystem);
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.metricsSystem = metricsSystem;
    this.pivotBlockHeader = pivotBlockHeader;
  }

  @Override
  protected CompletableFuture<Optional<EthPeer>> selectBestAvailableSyncTarget() {
    final Optional<EthPeer> maybeBestPeer = ethContext.getEthPeers().bestPeer();
    if (!maybeBestPeer.isPresent()) {
      LOG.info("No sync target, wait for peers.");
      return completedFuture(Optional.empty());
    } else {
      final EthPeer bestPeer = maybeBestPeer.get();
      if (bestPeer.chainState().getEstimatedHeight() < pivotBlockHeader.getNumber()) {
        LOG.info("No sync target with sufficient chain height, wait for peers.");
        return completedFuture(Optional.empty());
      } else {
        return confirmPivotBlockHeader(bestPeer);
      }
    }
  }

  private CompletableFuture<Optional<EthPeer>> confirmPivotBlockHeader(final EthPeer bestPeer) {
    final RetryingGetHeaderFromPeerByNumberTask task =
        RetryingGetHeaderFromPeerByNumberTask.forSingleNumber(
            protocolSchedule,
            ethContext,
            metricsSystem,
            pivotBlockHeader.getNumber(),
            MAX_PIVOT_BLOCK_RETRIES);
    task.assignPeer(bestPeer);
    return ethContext
        .getScheduler()
        .timeout(task)
        .thenApply(
            result -> {
              if (peerHasDifferentPivotBlock(result)) {
                bestPeer.disconnect(DisconnectReason.USELESS_PEER);
                return Optional.<EthPeer>empty();
              } else {
                return Optional.of(bestPeer);
              }
            })
        .exceptionally(
            error -> {
              LOG.debug("Could not confirm best peer had pivot block", error);
              return Optional.empty();
            });
  }

  private boolean peerHasDifferentPivotBlock(final List<BlockHeader> result) {
    return result.size() != 1 || !result.get(0).equals(pivotBlockHeader);
  }

  @Override
  public boolean shouldContinueDownloading() {
    return !protocolContext.getBlockchain().getChainHeadHash().equals(pivotBlockHeader.getHash());
  }
}
