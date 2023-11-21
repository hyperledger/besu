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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.task.WaitForPeersTask;
import org.hyperledger.besu.ethereum.eth.sync.ChainDownloader;
import org.hyperledger.besu.ethereum.eth.sync.PivotBlockSelector;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.sync.tasks.RetryingGetHeaderFromPeerByHashTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorageFormatCoordinator;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FastSyncActions {

  private static final Logger LOG = LoggerFactory.getLogger(FastSyncActions.class);
  protected final SynchronizerConfiguration syncConfig;
  protected final WorldStateStorageFormatCoordinator worldStateStorage;
  protected final ProtocolSchedule protocolSchedule;
  protected final ProtocolContext protocolContext;
  protected final EthContext ethContext;
  protected final SyncState syncState;
  protected final PivotBlockSelector pivotBlockSelector;
  protected final MetricsSystem metricsSystem;
  protected final Counter pivotBlockSelectionCounter;
  protected final AtomicLong pivotBlockGauge = new AtomicLong(0);

  public FastSyncActions(
      final SynchronizerConfiguration syncConfig,
      final WorldStateStorageFormatCoordinator worldStateStorage,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final SyncState syncState,
      final PivotBlockSelector pivotBlockSelector,
      final MetricsSystem metricsSystem) {
    this.syncConfig = syncConfig;
    this.worldStateStorage = worldStateStorage;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.syncState = syncState;
    this.pivotBlockSelector = pivotBlockSelector;
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

  public SyncState getSyncState() {
    return syncState;
  }

  public long getBestChainHeight() {
    return pivotBlockSelector.getBestChainHeight();
  }

  public CompletableFuture<FastSyncState> selectPivotBlock(final FastSyncState fastSyncState) {
    return fastSyncState.hasPivotBlockHeader()
        ? completedFuture(fastSyncState)
        : selectNewPivotBlock();
  }

  private CompletableFuture<FastSyncState> selectNewPivotBlock() {

    return pivotBlockSelector
        .selectNewPivotBlock()
        .map(CompletableFuture::completedFuture)
        .orElseGet(this::retrySelectPivotBlockAfterDelay);
  }

  <T> CompletableFuture<T> scheduleFutureTask(
      final Supplier<CompletableFuture<T>> future, final Duration duration) {
    return ethContext.getScheduler().scheduleFutureTask(future, duration);
  }

  private CompletableFuture<FastSyncState> retrySelectPivotBlockAfterDelay() {
    return ethContext
        .getScheduler()
        .scheduleFutureTask(pivotBlockSelector::prepareRetry, Duration.ofSeconds(5))
        .thenCompose(ignore -> selectNewPivotBlock());
  }

  public CompletableFuture<FastSyncState> downloadPivotBlockHeader(
      final FastSyncState currentState) {
    return internalDownloadPivotBlockHeader(currentState).thenApply(this::updateStats);
  }

  private CompletableFuture<FastSyncState> internalDownloadPivotBlockHeader(
      final FastSyncState currentState) {
    if (currentState.hasPivotBlockHeader()) {
      LOG.debug("Initial sync state {} already contains the block header", currentState);
      return completedFuture(currentState);
    }

    return waitForPeers(1)
        .thenCompose(
            unused ->
                currentState
                    .getPivotBlockHash()
                    .map(this::downloadPivotBlockHeader)
                    .orElseGet(
                        () ->
                            new PivotBlockRetriever(
                                    protocolSchedule,
                                    ethContext,
                                    metricsSystem,
                                    currentState.getPivotBlockNumber().getAsLong(),
                                    syncConfig.getFastSyncMinimumPeerCount(),
                                    syncConfig.getFastSyncPivotDistance())
                                .downloadPivotBlockHeader()));
  }

  private FastSyncState updateStats(final FastSyncState fastSyncState) {
    pivotBlockSelectionCounter.inc();
    fastSyncState
        .getPivotBlockHeader()
        .ifPresent(blockHeader -> pivotBlockGauge.set(blockHeader.getNumber()));
    return fastSyncState;
  }

  public ChainDownloader createChainDownloader(final FastSyncState currentState) {
    return FastSyncChainDownloader.create(
        syncConfig,
        worldStateStorage,
        protocolSchedule,
        protocolContext,
        ethContext,
        syncState,
        metricsSystem,
        currentState);
  }

  private CompletableFuture<FastSyncState> downloadPivotBlockHeader(final Hash hash) {
    LOG.debug("Downloading pivot block header by hash {}", hash);
    return RetryingGetHeaderFromPeerByHashTask.byHash(
            protocolSchedule,
            ethContext,
            hash,
            pivotBlockSelector.getMinRequiredBlockNumber(),
            metricsSystem)
        .getHeader()
        .whenComplete(
            (blockHeader, throwable) -> {
              if (throwable != null) {
                LOG.debug("Error downloading block header by hash {}", hash);
              } else {
                LOG.atDebug()
                    .setMessage("Successfully downloaded pivot block header by hash {}")
                    .addArgument(blockHeader::toLogString)
                    .log();
              }
            })
        .thenApply(FastSyncState::new);
  }

  public boolean isBlockchainBehind(final long blockNumber) {
    return protocolContext.getBlockchain().getChainHeadHeader().getNumber() < blockNumber;
  }

  private CompletableFuture<Void> waitForPeers(final int count) {

    final WaitForPeersTask waitForPeersTask =
        WaitForPeersTask.create(ethContext, count, metricsSystem);
    return waitForPeersTask.run();
  }
}
