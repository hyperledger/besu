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
import static org.hyperledger.besu.util.FutureUtils.exceptionallyCompose;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.task.WaitForPeersTask;
import org.hyperledger.besu.ethereum.eth.sync.ChainDownloader;
import org.hyperledger.besu.ethereum.eth.sync.PivotBlockSelector;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.TrailingPeerLimiter;
import org.hyperledger.besu.ethereum.eth.sync.TrailingPeerRequirements;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.sync.tasks.RetryingGetHeaderFromPeerByHashTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.util.ExceptionUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FastSyncActions {

  private static final Logger LOG = LoggerFactory.getLogger(FastSyncActions.class);
  protected final SynchronizerConfiguration syncConfig;
  protected final WorldStateStorage worldStateStorage;
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
      final WorldStateStorage worldStateStorage,
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

  public CompletableFuture<FastSyncState> waitForSuitablePeers(final FastSyncState fastSyncState) {
    if (fastSyncState.hasPivotBlockHeader()) {
      return waitForAnyPeer().thenApply(ignore -> fastSyncState);
    }

    LOG.debug("Waiting for at least {} peers.", syncConfig.getFastSyncMinimumPeerCount());
    return waitForPeers(syncConfig.getFastSyncMinimumPeerCount())
        .thenApply(successfulWaitResult -> fastSyncState);
  }

  public <T> CompletableFuture<T> scheduleFutureTask(
      final Supplier<CompletableFuture<T>> future, final Duration duration) {
    return ethContext.getScheduler().scheduleFutureTask(future, duration);
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

  public CompletableFuture<FastSyncState> selectPivotBlock(final FastSyncState fastSyncState) {
    return fastSyncState.hasPivotBlockHeader()
        ? completedFuture(fastSyncState)
        : selectNewPivotBlock();
  }

  private CompletableFuture<FastSyncState> selectNewPivotBlock() {

    return selectBestPeer()
        .map(
            bestPeer ->
                pivotBlockSelector
                    .selectNewPivotBlock(bestPeer)
                    .map(CompletableFuture::completedFuture)
                    .orElse(null))
        .orElseGet(this::retrySelectPivotBlockAfterDelay);
  }

  private Optional<EthPeer> selectBestPeer() {
    return ethContext
        .getEthPeers()
        .bestPeerMatchingCriteria(this::canPeerDeterminePivotBlock)
        // Only select a pivot block number when we have a minimum number of height estimates
        .filter(unused -> enoughFastSyncPeersArePresent());
  }

  private boolean enoughFastSyncPeersArePresent() {
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
        peer.getShortNodeId(),
        peer.chainState().hasEstimatedHeight(),
        peer.isFullyValidated());
    return peer.chainState().hasEstimatedHeight() && peer.isFullyValidated();
  }

  private CompletableFuture<FastSyncState> retrySelectPivotBlockAfterDelay() {
    return ethContext
        .getScheduler()
        .scheduleFutureTask(
            this::limitTrailingPeersAndRetrySelectPivotBlock, Duration.ofSeconds(5));
  }

  private long conservativelyEstimatedPivotBlock() {
    long estimatedNextPivot =
        syncState.getLocalChainHeight() + syncConfig.getFastSyncPivotDistance();
    return Math.min(syncState.bestChainHeight(), estimatedNextPivot);
  }

  private CompletableFuture<FastSyncState> limitTrailingPeersAndRetrySelectPivotBlock() {
    final TrailingPeerLimiter trailingPeerLimiter =
        new TrailingPeerLimiter(
            ethContext.getEthPeers(),
            () ->
                new TrailingPeerRequirements(
                    conservativelyEstimatedPivotBlock(), syncConfig.getMaxTrailingPeers()));
    trailingPeerLimiter.enforceTrailingPeerLimit();

    return waitForPeers(syncConfig.getFastSyncMinimumPeerCount())
        .thenCompose(ignore -> selectNewPivotBlock());
  }

  public CompletableFuture<FastSyncState> downloadPivotBlockHeader(
      final FastSyncState currentState) {
    return internalDownloadPivotBlockHeader(currentState).thenApply(this::updateStats);
  }

  private CompletableFuture<FastSyncState> internalDownloadPivotBlockHeader(
      final FastSyncState currentState) {
    if (currentState.hasPivotBlockHeader()) {
      return completedFuture(currentState);
    }

    return currentState
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
                    .downloadPivotBlockHeader());
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
    return RetryingGetHeaderFromPeerByHashTask.byHash(
            protocolSchedule, ethContext, hash, metricsSystem)
        .getHeader()
        .thenApply(
            blockHeader -> {
              LOG.trace(
                  "Successfully downloaded pivot block header by hash: {}",
                  blockHeader.toLogString());
              return new FastSyncState(blockHeader);
            });
  }
}
