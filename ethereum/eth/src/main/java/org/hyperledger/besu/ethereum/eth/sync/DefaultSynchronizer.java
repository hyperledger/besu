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
package org.hyperledger.besu.ethereum.eth.sync;

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.sync.checkpointsync.CheckpointDownloaderFactory;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncDownloader;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.worldstate.FastDownloaderFactory;
import org.hyperledger.besu.ethereum.eth.sync.fullsync.FullSyncDownloader;
import org.hyperledger.besu.ethereum.eth.sync.fullsync.SyncTerminationCondition;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapDownloaderFactory;
import org.hyperledger.besu.ethereum.eth.sync.state.PendingBlocksManager;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.worldstate.Pruner;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.data.SyncStatus;
import org.hyperledger.besu.plugin.services.BesuEvents.SyncStatusListener;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultSynchronizer implements Synchronizer {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultSynchronizer.class);

  private final Optional<Pruner> maybePruner;
  private final SyncState syncState;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final Optional<BlockPropagationManager> blockPropagationManager;
  private final Optional<FastSyncDownloader<?>> fastSyncDownloader;
  private final Optional<FullSyncDownloader> fullSyncDownloader;
  private final ProtocolContext protocolContext;
  private final PivotBlockSelector pivotBlockSelector;
  private final SyncTerminationCondition terminationCondition;

  public DefaultSynchronizer(
      final SynchronizerConfiguration syncConfig,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final WorldStateStorage worldStateStorage,
      final BlockBroadcaster blockBroadcaster,
      final Optional<Pruner> maybePruner,
      final EthContext ethContext,
      final SyncState syncState,
      final Path dataDirectory,
      final Clock clock,
      final MetricsSystem metricsSystem,
      final SyncTerminationCondition terminationCondition,
      final PivotBlockSelector pivotBlockSelector) {
    this.maybePruner = maybePruner;
    this.syncState = syncState;
    this.pivotBlockSelector = pivotBlockSelector;
    this.protocolContext = protocolContext;
    this.terminationCondition = terminationCondition;

    ChainHeadTracker.trackChainHeadForPeers(
        ethContext,
        protocolSchedule,
        protocolContext.getBlockchain(),
        this::calculateTrailingPeerRequirements,
        metricsSystem);

    this.blockPropagationManager =
        terminationCondition.shouldStopDownload()
            ? Optional.empty()
            : Optional.of(
                new BlockPropagationManager(
                    syncConfig,
                    protocolSchedule,
                    protocolContext,
                    ethContext,
                    syncState,
                    new PendingBlocksManager(syncConfig),
                    metricsSystem,
                    blockBroadcaster));

    this.fullSyncDownloader =
        terminationCondition.shouldStopDownload()
            ? Optional.empty()
            : Optional.of(
                new FullSyncDownloader(
                    syncConfig,
                    protocolSchedule,
                    protocolContext,
                    ethContext,
                    syncState,
                    metricsSystem,
                    terminationCondition));

    if (SyncMode.FAST.equals(syncConfig.getSyncMode())) {
      this.fastSyncDownloader =
          FastDownloaderFactory.create(
              pivotBlockSelector,
              syncConfig,
              dataDirectory,
              protocolSchedule,
              protocolContext,
              metricsSystem,
              ethContext,
              worldStateStorage,
              syncState,
              clock);
    } else if (SyncMode.X_CHECKPOINT.equals(syncConfig.getSyncMode())) {
      this.fastSyncDownloader =
          CheckpointDownloaderFactory.createCheckpointDownloader(
              pivotBlockSelector,
              syncConfig,
              dataDirectory,
              protocolSchedule,
              protocolContext,
              metricsSystem,
              ethContext,
              worldStateStorage,
              syncState,
              clock);
    } else {
      this.fastSyncDownloader =
          SnapDownloaderFactory.createSnapDownloader(
              pivotBlockSelector,
              syncConfig,
              dataDirectory,
              protocolSchedule,
              protocolContext,
              metricsSystem,
              ethContext,
              worldStateStorage,
              syncState,
              clock);
    }

    metricsSystem.createLongGauge(
        BesuMetricCategory.ETHEREUM,
        "best_known_block_number",
        "The estimated highest block available",
        syncState::bestChainHeight);
    metricsSystem.createIntegerGauge(
        BesuMetricCategory.SYNCHRONIZER,
        "in_sync",
        "Whether or not the local node has caught up to the best known peer",
        () -> getSyncStatus().isPresent() ? 0 : 1);
  }

  private TrailingPeerRequirements calculateTrailingPeerRequirements() {
    return fastSyncDownloader
        .flatMap(FastSyncDownloader::calculateTrailingPeerRequirements)
        .orElse(
            fullSyncDownloader
                .map(FullSyncDownloader::calculateTrailingPeerRequirements)
                .orElse(TrailingPeerRequirements.UNRESTRICTED));
  }

  @Override
  public CompletableFuture<Void> start() {
    if (running.compareAndSet(false, true)) {
      LOG.info("Starting synchronizer.");
      blockPropagationManager.ifPresent(
          manager -> {
            if (!manager.isRunning()) {
              manager.start();
            }
          });
      CompletableFuture<Void> future;
      if (fastSyncDownloader.isPresent()) {
        future = fastSyncDownloader.get().start().thenCompose(this::handleSyncResult);

      } else {
        syncState.markInitialSyncPhaseAsDone();
        future = startFullSync();
      }
      return future.thenApply(this::finalizeSync);
    } else {
      throw new IllegalStateException("Attempt to start an already started synchronizer.");
    }
  }

  @Override
  public void stop() {
    if (running.compareAndSet(true, false)) {
      LOG.info("Stopping synchronizer");
      fastSyncDownloader.ifPresent(FastSyncDownloader::stop);
      fullSyncDownloader.ifPresent(FullSyncDownloader::stop);
      maybePruner.ifPresent(Pruner::stop);
      blockPropagationManager.ifPresent(
          manager -> {
            if (manager.isRunning()) {
              manager.stop();
            }
          });
    }
  }

  @Override
  public void awaitStop() throws InterruptedException {
    if (maybePruner.isPresent()) {
      maybePruner.get().awaitStop();
    }
  }

  private CompletableFuture<Void> handleSyncResult(final FastSyncState result) {
    if (!running.get()) {
      // We've been shutdown which will have triggered the fast sync future to complete
      return CompletableFuture.completedFuture(null);
    }
    fastSyncDownloader.ifPresent(FastSyncDownloader::deleteFastSyncState);
    result
        .getPivotBlockHeader()
        .ifPresent(
            blockHeader ->
                protocolContext.getWorldStateArchive().setArchiveStateUnSafe(blockHeader));
    LOG.info(
        "Sync completed successfully with pivot block {}",
        result.getPivotBlockNumber().getAsLong());
    pivotBlockSelector.close();
    syncState.markInitialSyncPhaseAsDone();

    if (terminationCondition.shouldContinueDownload()) {
      return startFullSync();
    } else {
      syncState.setReachedTerminalDifficulty(true);
      return CompletableFuture.completedFuture(null);
    }
  }

  private CompletableFuture<Void> startFullSync() {
    maybePruner.ifPresent(Pruner::start);
    return fullSyncDownloader
        .map(FullSyncDownloader::start)
        .orElse(CompletableFuture.completedFuture(null))
        .thenRun(
            () -> {
              if (terminationCondition.shouldStopDownload()) {
                syncState.setReachedTerminalDifficulty(true);
              }
            });
  }

  @Override
  public Optional<SyncStatus> getSyncStatus() {
    if (!running.get()) {
      return Optional.empty();
    }
    return syncState.syncStatus();
  }

  @Override
  public long subscribeSyncStatus(final SyncStatusListener listener) {
    checkNotNull(listener);
    return syncState.subscribeSyncStatus(listener);
  }

  @Override
  public boolean unsubscribeSyncStatus(final long subscriberId) {
    return syncState.unsubscribeSyncStatus(subscriberId);
  }

  @Override
  public long subscribeInSync(final InSyncListener listener) {
    return syncState.subscribeInSync(listener);
  }

  @Override
  public long subscribeInSync(final InSyncListener listener, final long syncTolerance) {
    return syncState.subscribeInSync(listener, syncTolerance);
  }

  @Override
  public boolean unsubscribeInSync(final long listenerId) {
    return syncState.unsubscribeSyncStatus(listenerId);
  }

  private Void finalizeSync(final Void unused) {
    LOG.info("Stopping block propagation.");
    blockPropagationManager.ifPresent(BlockPropagationManager::stop);
    LOG.info("Stopping the pruner.");
    maybePruner.ifPresent(Pruner::stop);
    running.set(false);
    return null;
  }
}
