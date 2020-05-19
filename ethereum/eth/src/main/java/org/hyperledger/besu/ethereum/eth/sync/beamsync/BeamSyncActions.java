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
package org.hyperledger.besu.ethereum.eth.sync.beamsync;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.hyperledger.besu.util.FutureUtils.exceptionallyCompose;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.task.WaitForPeersTask;
import org.hyperledger.besu.ethereum.eth.sync.ChainDownloader;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncChainDownloader;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.util.ExceptionUtils;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BeamSyncActions<C> {

  private static final Logger LOG = LogManager.getLogger();
  private final SynchronizerConfiguration syncConfig;
  private final ProtocolSchedule<C> protocolSchedule;
  private final ProtocolContext<C> protocolContext;
  private final EthContext ethContext;
  private final SyncState syncState;
  private final MetricsSystem metricsSystem;
  private final Counter launchBlockSelectionCounter;
  private final AtomicLong launchBlockGauge = new AtomicLong(0);

  public BeamSyncActions(
      final SynchronizerConfiguration syncConfig,
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final SyncState syncState,
      final MetricsSystem metricsSystem) {
    this.syncConfig = syncConfig;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.syncState = syncState;
    this.metricsSystem = metricsSystem;

    launchBlockSelectionCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.SYNCHRONIZER,
            "fast_sync_pivot_block_selected_count",
            "Number of times a fast sync pivot block has been selected");
    metricsSystem.createLongGauge(
        BesuMetricCategory.SYNCHRONIZER,
        "fast_sync_pivot_block_current",
        "The current fast sync pivot block",
        launchBlockGauge::get);
  }

  public CompletableFuture<BeamSyncState> waitForSuitablePeers(final BeamSyncState beamSyncState) {
    if (beamSyncState.hasLaunchBlockHeader()) {
      return waitForAnyPeer().thenApply(ignore -> beamSyncState);
    }

    LOG.debug("Waiting for at least {} peers.", syncConfig.getFastSyncMinimumPeerCount());
    return waitForPeers(syncConfig.getFastSyncMinimumPeerCount())
        .thenApply(successfulWaitResult -> beamSyncState);
  }

  private CompletableFuture<Void> waitForAnyPeer() {
    final CompletableFuture<Void> waitForPeerResult =
        ethContext.getScheduler().timeout(WaitForPeersTask.create(ethContext, 1, metricsSystem));
    return exceptionallyCompose(
        waitForPeerResult,
        throwable -> {
          if (ExceptionUtils.rootCause(throwable) instanceof TimeoutException) {
            return waitForAnyPeer();
          }
          return CompletableFuture.failedFuture(throwable);
        });
  }

  private CompletableFuture<Void> waitForPeers(final int count) {
    final WaitForPeersTask waitForPeersTask =
        WaitForPeersTask.create(ethContext, count, metricsSystem);
    return waitForPeersTask.run();
  }

  public CompletableFuture<BeamSyncState> selectLaunchBlock(final BeamSyncState beamSyncState) {
    return beamSyncState.hasLaunchBlockHeader()
        ? completedFuture(beamSyncState)
        : selectLaunchBlockFromPeers();
  }

  private CompletableFuture<BeamSyncState> selectLaunchBlockFromPeers() {
    return ethContext
        .getEthPeers()
        .bestPeerMatchingCriteria(this::canPeerDeterminePivotBlock)
        // Only select a pivot block number when we have a minimum number of height estimates
        .filter(
            peer -> {
              final long peerCount = countPeersThatCanDeterminePivotBlock();
              final int minPeerCount = syncConfig.getFastSyncMinimumPeerCount();
              if (peerCount < minPeerCount) {
                LOG.info(
                    "Waiting for valid peers with chain height information.  {} / {} required peers currently available.",
                    peerCount,
                    minPeerCount);
                return false;
              }
              return true;
            })
        .map(
            peer -> {
              final long pivotBlockNumber =
                  peer.chainState().getEstimatedHeight() - syncConfig.getFastSyncPivotDistance();
              if (pivotBlockNumber <= BlockHeader.GENESIS_BLOCK_NUMBER) {
                // Peer's chain isn't long enough, return an empty value so we can try again.
                LOG.info("Waiting for peers with sufficient chain height");
                return null;
              }
              LOG.info("Selecting block number {} as fast sync pivot block.", pivotBlockNumber);
              launchBlockSelectionCounter.inc();
              launchBlockGauge.set(pivotBlockNumber);
              return completedFuture(new BeamSyncState(pivotBlockNumber));
            })
        .orElseGet(this::retrySelectLaunchBlockAfterDelay);
  }

  private long countPeersThatCanDeterminePivotBlock() {
    return ethContext
        .getEthPeers()
        .streamAvailablePeers()
        .filter(this::canPeerDeterminePivotBlock)
        .count();
  }

  private boolean canPeerDeterminePivotBlock(final EthPeer peer) {
    return peer.chainState().hasEstimatedHeight() && peer.isFullyValidated();
  }

  private CompletableFuture<BeamSyncState> retrySelectLaunchBlockAfterDelay() {
    return ethContext
        .getScheduler()
        .scheduleFutureTask(
            () ->
                waitForPeers(syncConfig.getFastSyncMinimumPeerCount())
                    .thenCompose(ignore -> selectLaunchBlockFromPeers()),
            Duration.ofSeconds(5));
  }

  public CompletableFuture<BeamSyncState> downloadLaunchBlockHeader(
      final BeamSyncState currentState) {
    if (currentState.getLaunchBlockHeader().isPresent()) {
      return completedFuture(currentState);
    }
    // TODO - getFastSyncPivotDistance is not applicable to retrieving the beam sync launch block.
    return new LaunchBlockRetriever<>(
            protocolSchedule,
            ethContext,
            metricsSystem,
            currentState.getLaunchBlockNumber().getAsLong(),
            syncConfig.getFastSyncMinimumPeerCount(),
            syncConfig.getFastSyncPivotDistance())
        .downloadLaunchBlockHeader();
  }

  public ChainDownloader createChainDownloader(final BeamSyncState currentState) {
    return FastSyncChainDownloader.create(
        syncConfig,
        protocolSchedule,
        protocolContext,
        ethContext,
        syncState,
        metricsSystem,
        currentState.getLaunchBlockHeader().get());
  }
}
