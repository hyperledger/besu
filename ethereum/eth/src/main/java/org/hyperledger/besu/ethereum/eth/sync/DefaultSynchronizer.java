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
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncDownloader;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncException;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;
import org.hyperledger.besu.ethereum.eth.sync.fullsync.FullSyncDownloader;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapDataRequest;
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
import org.hyperledger.besu.util.ExceptionUtils;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DefaultSynchronizer implements Synchronizer {

  private static final Logger LOG = LogManager.getLogger();

  private final Optional<Pruner> maybePruner;
  private final SyncState syncState;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final BlockPropagationManager blockPropagationManager;
  private final Optional<FastSyncDownloader<SnapDataRequest>> fastSyncDownloader;
  private final FullSyncDownloader fullSyncDownloader;
  private final ProtocolContext protocolContext;

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
      final MetricsSystem metricsSystem) {
    this.maybePruner = maybePruner;
    this.syncState = syncState;

    this.protocolContext = protocolContext;
    ChainHeadTracker.trackChainHeadForPeers(
        ethContext,
        protocolSchedule,
        protocolContext.getBlockchain(),
        this::calculateTrailingPeerRequirements,
        metricsSystem);

    this.blockPropagationManager =
        new BlockPropagationManager(
            syncConfig,
            protocolSchedule,
            protocolContext,
            ethContext,
            syncState,
            new PendingBlocksManager(syncConfig),
            metricsSystem,
            blockBroadcaster);

    this.fullSyncDownloader =
        new FullSyncDownloader(
            syncConfig, protocolSchedule, protocolContext, ethContext, syncState, metricsSystem);
    this.fastSyncDownloader =
        SnapDownloaderFactory.createSnapDownloader(
            syncConfig,
            dataDirectory,
            protocolSchedule,
            protocolContext,
            metricsSystem,
            ethContext,
            worldStateStorage,
            syncState,
            clock);

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
        .orElseGet(fullSyncDownloader::calculateTrailingPeerRequirements);
  }

  @Override
  public void start() {
    if (running.compareAndSet(false, true)) {
      LOG.info("Starting synchronizer.");
      blockPropagationManager.start();
      if (fastSyncDownloader.isPresent()) {
        fastSyncDownloader
            .get()
            .start()
            .whenComplete(this::handleFastSyncResult)
            .exceptionally(
                ex -> {
                  LOG.warn("Exiting FastSync process");
                  System.exit(0);
                  return null;
                });

      } else {
        startFullSync();
      }
    } else {
      throw new IllegalStateException("Attempt to start an already started synchronizer.");
    }
  }

  @Override
  public void stop() {
    if (running.compareAndSet(true, false)) {
      LOG.info("Stopping synchronizer");
      fastSyncDownloader.ifPresent(FastSyncDownloader::stop);
      fullSyncDownloader.stop();
      maybePruner.ifPresent(Pruner::stop);
    }
  }

  @Override
  public void awaitStop() throws InterruptedException {
    if (maybePruner.isPresent()) {
      maybePruner.get().awaitStop();
    }
  }

  private void handleFastSyncResult(final FastSyncState result, final Throwable error) {
    if (!running.get()) {
      // We've been shutdown which will have triggered the fast sync future to complete
      return;
    }
    fastSyncDownloader.ifPresent(FastSyncDownloader::deleteFastSyncState);
    final Throwable rootCause = ExceptionUtils.rootCause(error);
    if (rootCause instanceof FastSyncException) {
      LOG.error(
          "Fast sync failed ({}), please try again.", ((FastSyncException) rootCause).getError());
      throw new FastSyncException(rootCause);
    } else if (error != null) {
      LOG.error("Fast sync failed, please try again.", error);
      throw new FastSyncException(error);
    } else {
      result
          .getPivotBlockHeader()
          .ifPresent(
              blockHeader ->
                  protocolContext.getWorldStateArchive().setArchiveStateUnSafe(blockHeader));
      LOG.info(
          "Fast sync completed successfully with pivot block {}",
          result.getPivotBlockNumber().getAsLong());
    }
    startFullSync();
  }

  private void startFullSync() {
    maybePruner.ifPresent(Pruner::start);
    fullSyncDownloader.start();
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
}
