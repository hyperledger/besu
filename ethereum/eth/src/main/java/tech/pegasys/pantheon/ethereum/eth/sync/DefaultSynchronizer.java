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

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.SyncStatus;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.sync.fastsync.FastSyncActions;
import tech.pegasys.pantheon.ethereum.eth.sync.fastsync.FastSyncDownloader;
import tech.pegasys.pantheon.ethereum.eth.sync.fastsync.FastSyncException;
import tech.pegasys.pantheon.ethereum.eth.sync.fastsync.FastSyncState;
import tech.pegasys.pantheon.ethereum.eth.sync.fullsync.FullSyncDownloader;
import tech.pegasys.pantheon.ethereum.eth.sync.state.PendingBlocks;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.eth.sync.worldstate.NodeDataRequest;
import tech.pegasys.pantheon.ethereum.eth.sync.worldstate.WorldStateDownloader;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateStorage;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.MetricCategory;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.OperationTimer;
import tech.pegasys.pantheon.services.queue.BigQueue;
import tech.pegasys.pantheon.services.queue.BytesQueue;
import tech.pegasys.pantheon.services.queue.BytesQueueAdapter;
import tech.pegasys.pantheon.services.queue.RocksDbQueue;
import tech.pegasys.pantheon.util.ExceptionUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DefaultSynchronizer<C> implements Synchronizer {

  private static final Logger LOG = LogManager.getLogger();

  private final SynchronizerConfiguration syncConfig;
  private final EthContext ethContext;
  private final SyncState syncState;
  private final AtomicBoolean started = new AtomicBoolean(false);
  private final BlockPropagationManager<C> blockPropagationManager;
  private final FullSyncDownloader<C> fullSyncDownloader;
  private final Optional<FastSyncDownloader<C>> fastSyncDownloader;
  private final Path stateQueueDirectory;
  private final BigQueue<NodeDataRequest> stateQueue;
  private final WorldStateDownloader worldStateDownloader;

  public DefaultSynchronizer(
      final SynchronizerConfiguration syncConfig,
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final WorldStateStorage worldStateStorage,
      final EthContext ethContext,
      final SyncState syncState,
      final Path dataDirectory,
      final MetricsSystem metricsSystem) {
    this.syncConfig = syncConfig;
    this.ethContext = ethContext;
    this.syncState = syncState;

    final LabelledMetric<OperationTimer> ethTasksTimer =
        metricsSystem.createLabelledTimer(
            MetricCategory.SYNCHRONIZER, "task", "Internal processing tasks", "taskName");
    this.blockPropagationManager =
        new BlockPropagationManager<>(
            syncConfig,
            protocolSchedule,
            protocolContext,
            ethContext,
            syncState,
            new PendingBlocks(),
            ethTasksTimer);

    ChainHeadTracker.trackChainHeadForPeers(
        ethContext, protocolSchedule, protocolContext.getBlockchain(), syncConfig, ethTasksTimer);

    this.fullSyncDownloader =
        new FullSyncDownloader<>(
            syncConfig, protocolSchedule, protocolContext, ethContext, syncState, ethTasksTimer);

    if (syncConfig.syncMode() == SyncMode.FAST) {
      this.stateQueueDirectory = getStateQueueDirectory(dataDirectory);
      this.stateQueue = createWorldStateDownloaderQueue(stateQueueDirectory, metricsSystem);
      this.worldStateDownloader =
          new WorldStateDownloader(
              ethContext,
              worldStateStorage,
              stateQueue,
              syncConfig.getWorldStateHashCountPerRequest(),
              syncConfig.getWorldStateRequestParallelism(),
              ethTasksTimer);
      this.fastSyncDownloader =
          Optional.of(
              new FastSyncDownloader<>(
                  new FastSyncActions<>(
                      syncConfig,
                      protocolSchedule,
                      protocolContext,
                      ethContext,
                      syncState,
                      ethTasksTimer,
                      metricsSystem.createLabelledCounter(
                          MetricCategory.SYNCHRONIZER,
                          "fast_sync_validation_mode",
                          "Number of blocks validated using light vs full validation during fast sync",
                          "validationMode")),
                  worldStateDownloader));
    } else {
      this.fastSyncDownloader = Optional.empty();
      this.worldStateDownloader = null;
      this.stateQueueDirectory = null;
      this.stateQueue = null;
    }
  }

  @Override
  public void start() {
    if (started.compareAndSet(false, true)) {
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
    cleanupFastSync();
  }

  private void cleanupFastSync() {
    if (!fastSyncDownloader.isPresent()) {
      // We're not fast syncing - nothing to do
      return;
    }

    // Make sure downloader is stopped before we start cleaning up its dependencies
    worldStateDownloader.cancel();
    try {
      stateQueue.close();
      if (stateQueueDirectory.toFile().exists()) {
        // Clean up this data for now (until fast sync resume functionality is in place)
        MoreFiles.deleteRecursively(stateQueueDirectory, RecursiveDeleteOption.ALLOW_INSECURE);
      }
    } catch (IOException e) {
      LOG.error("Unable to clean up fast sync state", e);
    }
  }

  private Path getStateQueueDirectory(final Path dataDirectory) {
    Path queueDataDir = dataDirectory.resolve("fastsync/statequeue");
    queueDataDir.toFile().mkdirs();
    return queueDataDir;
  }

  private BigQueue<NodeDataRequest> createWorldStateDownloaderQueue(
      final Path dataDirectory, final MetricsSystem metricsSystem) {
    BytesQueue bytesQueue = RocksDbQueue.create(dataDirectory, metricsSystem);
    return new BytesQueueAdapter<>(
        bytesQueue, NodeDataRequest::serialize, NodeDataRequest::deserialize);
  }

  private void handleFastSyncResult(final FastSyncState result, final Throwable error) {

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
    cleanupFastSync();

    startFullSync();
  }

  private void startFullSync() {
    LOG.info("Starting synchronizer.");
    blockPropagationManager.start();
    fullSyncDownloader.start();
  }

  @Override
  public Optional<SyncStatus> getSyncStatus() {
    if (!started.get()) {
      return Optional.empty();
    }
    return Optional.of(syncState.syncStatus());
  }

  @Override
  public boolean hasSufficientPeers() {
    final int requiredPeerCount =
        fastSyncDownloader.isPresent() ? syncConfig.getFastSyncMinimumPeerCount() : 1;
    return ethContext.getEthPeers().availablePeerCount() >= requiredPeerCount;
  }
}
