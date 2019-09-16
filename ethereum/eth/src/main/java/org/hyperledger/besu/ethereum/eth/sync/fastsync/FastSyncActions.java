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
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.hyperledger.besu.util.FutureUtils.completedExceptionally;
import static org.hyperledger.besu.util.FutureUtils.exceptionallyCompose;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.task.WaitForPeersTask;
import org.hyperledger.besu.ethereum.eth.sync.ChainDownloader;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
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

public class FastSyncActions<C> {

  private static final Logger LOG = LogManager.getLogger();
  private final SynchronizerConfiguration syncConfig;
  private final ProtocolSchedule<C> protocolSchedule;
  private final ProtocolContext<C> protocolContext;
  private final EthContext ethContext;
  private final SyncState syncState;
  private final MetricsSystem metricsSystem;
  private final Counter pivotBlockSelectionCounter;
  private final AtomicLong pivotBlockGauge = new AtomicLong(0);

  public FastSyncActions(
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

    pivotBlockSelectionCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.SYNCHRONIZER,
            "fast_sync_pivot_block_selected_count",
            "Number of times a fast sync pivot block has been selected");
    metricsSystem.createLongGauge(
        BesuMetricCategory.SYNCHRONIZER,
        "fast_sync_pivot_block_current",
        "The current fast sync pivot block",
        pivotBlockGauge::get);
  }

  public CompletableFuture<FastSyncState> waitForSuitablePeers(final FastSyncState fastSyncState) {
    if (fastSyncState.hasPivotBlockHeader()) {
      return waitForAnyPeer().thenApply(ignore -> fastSyncState);
    }

    LOG.debug("Waiting for at least {} peers.", syncConfig.getFastSyncMinimumPeerCount());
    return waitForPeers(syncConfig.getFastSyncMinimumPeerCount())
        .thenApply(successfulWaitResult -> fastSyncState);
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
          return completedExceptionally(throwable);
        });
  }

  private CompletableFuture<Void> waitForPeers(final int count) {
    final WaitForPeersTask waitForPeersTask =
        WaitForPeersTask.create(ethContext, count, metricsSystem);
    return waitForPeersTask.run();
  }

  public CompletableFuture<FastSyncState> selectPivotBlock(final FastSyncState fastSyncState) {
    return fastSyncState.hasPivotBlockHeader()
        ? completedFuture(fastSyncState)
        : selectPivotBlockFromPeers();
  }

  private CompletableFuture<FastSyncState> selectPivotBlockFromPeers() {
    return ethContext
        .getEthPeers()
        .bestPeerWithHeightEstimate()
        // Only select a pivot block number when we have a minimum number of height estimates
        .filter(
            peer -> {
              final long peerCount = countPeersWithEstimatedHeight();
              final int minPeerCount = syncConfig.getFastSyncMinimumPeerCount();
              if (peerCount < minPeerCount) {
                LOG.info(
                    "Waiting for peers with chain height information.  {} / {} required peers currently available.",
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
                LOG.info("Waiting for peer with sufficient chain height");
                return null;
              }
              LOG.info("Selecting block number {} as fast sync pivot block.", pivotBlockNumber);
              pivotBlockSelectionCounter.inc();
              pivotBlockGauge.set(pivotBlockNumber);
              return completedFuture(new FastSyncState(pivotBlockNumber));
            })
        .orElseGet(this::retrySelectPivotBlockAfterDelay);
  }

  private long countPeersWithEstimatedHeight() {
    return ethContext
        .getEthPeers()
        .streamAvailablePeers()
        .filter(peer -> peer.chainState().hasEstimatedHeight())
        .count();
  }

  private CompletableFuture<FastSyncState> retrySelectPivotBlockAfterDelay() {
    return ethContext
        .getScheduler()
        .scheduleFutureTask(
            () ->
                waitForPeers(syncConfig.getFastSyncMinimumPeerCount())
                    .thenCompose(ignore -> selectPivotBlockFromPeers()),
            Duration.ofSeconds(1));
  }

  public CompletableFuture<FastSyncState> downloadPivotBlockHeader(
      final FastSyncState currentState) {
    if (currentState.getPivotBlockHeader().isPresent()) {
      return completedFuture(currentState);
    }
    return new PivotBlockRetriever<>(
            protocolSchedule,
            ethContext,
            metricsSystem,
            currentState.getPivotBlockNumber().getAsLong())
        .downloadPivotBlockHeader();
  }

  public ChainDownloader createChainDownloader(final FastSyncState currentState) {
    return FastSyncChainDownloader.create(
        syncConfig,
        protocolSchedule,
        protocolContext,
        ethContext,
        syncState,
        metricsSystem,
        currentState.getPivotBlockHeader().get());
  }
}
