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
package tech.pegasys.pantheon.ethereum.eth.sync;

import static com.google.common.base.Preconditions.checkNotNull;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.sync.fastsync.FastDownloaderFactory;
import tech.pegasys.pantheon.ethereum.eth.sync.fastsync.FastSyncDownloader;
import tech.pegasys.pantheon.ethereum.eth.sync.fastsync.FastSyncException;
import tech.pegasys.pantheon.ethereum.eth.sync.fastsync.FastSyncState;
import tech.pegasys.pantheon.ethereum.eth.sync.fullsync.FullSyncDownloader;
import tech.pegasys.pantheon.ethereum.eth.sync.state.PendingBlocks;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.worldstate.Pruner;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateStorage;
import tech.pegasys.pantheon.metrics.PantheonMetricCategory;
import tech.pegasys.pantheon.plugin.data.SyncStatus;
import tech.pegasys.pantheon.plugin.services.MetricsSystem;
import tech.pegasys.pantheon.plugin.services.PantheonEvents.SyncStatusListener;
import tech.pegasys.pantheon.util.ExceptionUtils;
import tech.pegasys.pantheon.util.Subscribers;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DefaultSynchronizer<C> implements Synchronizer {

  private static final Logger LOG = LogManager.getLogger();

  private final Optional<Pruner> maybePruner;
  private final SyncState syncState;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final Subscribers<SyncStatusListener> syncStatusListeners = Subscribers.create();
  private final BlockPropagationManager<C> blockPropagationManager;
  private final Optional<FastSyncDownloader<C>> fastSyncDownloader;
  private final FullSyncDownloader<C> fullSyncDownloader;

  public DefaultSynchronizer(
      final SynchronizerConfiguration syncConfig,
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
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

    ChainHeadTracker.trackChainHeadForPeers(
        ethContext,
        protocolSchedule,
        protocolContext.getBlockchain(),
        this::calculateTrailingPeerRequirements,
        metricsSystem);

    this.blockPropagationManager =
        new BlockPropagationManager<>(
            syncConfig,
            protocolSchedule,
            protocolContext,
            ethContext,
            syncState,
            new PendingBlocks(),
            metricsSystem,
            blockBroadcaster);

    this.fullSyncDownloader =
        new FullSyncDownloader<>(
            syncConfig, protocolSchedule, protocolContext, ethContext, syncState, metricsSystem);
    this.fastSyncDownloader =
        FastDownloaderFactory.create(
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
        PantheonMetricCategory.ETHEREUM,
        "best_known_block_number",
        "The estimated highest block available",
        () -> syncState.syncStatus().getHighestBlock());
    metricsSystem.createIntegerGauge(
        PantheonMetricCategory.SYNCHRONIZER,
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
      syncState.addSyncStatusListener(this::syncStatusCallback);
      blockPropagationManager.start();
      if (fastSyncDownloader.isPresent()) {
        fastSyncDownloader.get().start().whenComplete(this::handleFastSyncResult);
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
    }
  }

  private void handleFastSyncResult(final FastSyncState result, final Throwable error) {
    if (!running.get()) {
      // We've been shutdown which will have triggered the fast sync future to complete
      return;
    }
    final Throwable rootCause = ExceptionUtils.rootCause(error);
    if (rootCause instanceof FastSyncException) {
      LOG.error(
          "Fast sync failed ({}), switching to full sync.",
          ((FastSyncException) rootCause).getError());
    } else if (error != null) {
      LOG.error("Fast sync failed, switching to full sync.", error);
    } else {
      LOG.info(
          "Fast sync completed successfully with pivot block {}",
          result.getPivotBlockNumber().getAsLong());
    }
    fastSyncDownloader.ifPresent(FastSyncDownloader::deleteFastSyncState);

    startFullSync();
  }

  private void startFullSync() {
    fullSyncDownloader.start();
    maybePruner.ifPresent(Pruner::start);
  }

  @Override
  public Optional<SyncStatus> getSyncStatus() {
    if (!running.get()) {
      return Optional.empty();
    }
    final SyncStatus syncStatus = syncState.syncStatus();
    if (syncStatus.inSync()) {
      return Optional.empty();
    }
    return Optional.of(syncStatus);
  }

  @Override
  public long observeSyncStatus(final SyncStatusListener listener) {
    checkNotNull(listener);
    return syncStatusListeners.subscribe(listener);
  }

  @Override
  public boolean removeObserver(final long observerId) {
    return syncStatusListeners.unsubscribe(observerId);
  }

  private void syncStatusCallback(final SyncStatus status) {
    syncStatusListeners.forEach(c -> c.onSyncStatusChanged(status));
  }
}
