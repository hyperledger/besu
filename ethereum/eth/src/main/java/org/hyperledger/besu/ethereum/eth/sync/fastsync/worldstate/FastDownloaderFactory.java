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
package org.hyperledger.besu.ethereum.eth.sync.fastsync.worldstate;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.sync.PivotBlockSelector;
import org.hyperledger.besu.ethereum.eth.sync.SyncMode;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncActions;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncDownloader;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncStateStorage;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldStateDownloader;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.metrics.SyncDurationMetrics;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.services.tasks.InMemoryTasksPriorityQueues;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FastDownloaderFactory {

  protected static final String FAST_SYNC_FOLDER = "fastsync";

  private static final Logger LOG = LoggerFactory.getLogger(FastDownloaderFactory.class);

  public static Optional<FastSyncDownloader<?>> create(
      final PivotBlockSelector pivotBlockSelector,
      final SynchronizerConfiguration syncConfig,
      final Path dataDirectory,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final MetricsSystem metricsSystem,
      final EthContext ethContext,
      final WorldStateStorageCoordinator worldStateStorageCoordinator,
      final SyncState syncState,
      final Clock clock,
      final SyncDurationMetrics syncDurationMetrics) {

    final Path fastSyncDataDirectory = dataDirectory.resolve(FAST_SYNC_FOLDER);
    final FastSyncStateStorage fastSyncStateStorage =
        new FastSyncStateStorage(fastSyncDataDirectory);

    if (SyncMode.isFullSync(syncConfig.getSyncMode())) {
      if (fastSyncStateStorage.isFastSyncInProgress()) {
        throw new IllegalStateException(
            "Unable to change the sync mode when fast sync is incomplete, please restart with fast sync mode");
      } else {
        return Optional.empty();
      }
    }

    ensureDirectoryExists(fastSyncDataDirectory.toFile());

    final FastSyncState fastSyncState =
        fastSyncStateStorage.loadState(ScheduleBasedBlockHeaderFunctions.create(protocolSchedule));

    if (!syncState.isResyncNeeded()
        && fastSyncState.getPivotBlockHeader().isEmpty()
        && protocolContext.getBlockchain().getChainHeadBlockNumber()
            != BlockHeader.GENESIS_BLOCK_NUMBER) {
      LOG.info(
          "Fast sync was requested, but cannot be enabled because the local blockchain is not empty.");
      return Optional.empty();
    }

    worldStateStorageCoordinator.consumeForStrategy(
        onBonsai -> {
          onBonsai.clearFlatDatabase();
        },
        onForest -> {
          final Path queueDataDir = fastSyncDataDirectory.resolve("statequeue");
          if (queueDataDir.toFile().exists()) {
            LOG.warn(
                "Fast sync is picking up after old fast sync version. Pruning the world state and starting from scratch.");
            clearOldFastSyncWorldStateData(worldStateStorageCoordinator, queueDataDir);
          }
        });
    final InMemoryTasksPriorityQueues<NodeDataRequest> taskCollection =
        createWorldStateDownloaderTaskCollection(
            metricsSystem, syncConfig.getWorldStateTaskCacheSize());
    final WorldStateDownloader worldStateDownloader =
        new FastWorldStateDownloader(
            ethContext,
            worldStateStorageCoordinator,
            taskCollection,
            syncConfig.getWorldStateHashCountPerRequest(),
            syncConfig.getWorldStateRequestParallelism(),
            syncConfig.getWorldStateMaxRequestsWithoutProgress(),
            syncConfig.getWorldStateMinMillisBeforeStalling(),
            clock,
            metricsSystem,
            syncDurationMetrics);
    final FastSyncDownloader<NodeDataRequest> fastSyncDownloader =
        new FastSyncDownloader<>(
            new FastSyncActions(
                syncConfig,
                worldStateStorageCoordinator,
                protocolSchedule,
                protocolContext,
                ethContext,
                syncState,
                pivotBlockSelector,
                metricsSystem),
            worldStateStorageCoordinator,
            worldStateDownloader,
            fastSyncStateStorage,
            taskCollection,
            fastSyncDataDirectory,
            fastSyncState,
            syncDurationMetrics);
    syncState.setWorldStateDownloadStatus(worldStateDownloader);
    return Optional.of(fastSyncDownloader);
  }

  private static void clearOldFastSyncWorldStateData(
      final WorldStateStorageCoordinator worldStateKeyValueStorage, final Path queueDataDir) {
    worldStateKeyValueStorage.clear();
    try (final Stream<Path> stream = Files.list(queueDataDir); ) {
      stream.forEach(FastDownloaderFactory::deleteFile);
      deleteFile(queueDataDir);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void deleteFile(final Path f) {
    try {
      Files.delete(f);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  protected static void ensureDirectoryExists(final File dir) {
    if (!dir.mkdirs() && !dir.isDirectory()) {
      throw new IllegalStateException("Unable to create directory: " + dir.getAbsolutePath());
    }
  }

  private static InMemoryTasksPriorityQueues<NodeDataRequest>
      createWorldStateDownloaderTaskCollection(
          final MetricsSystem metricsSystem, final int worldStateTaskCacheSize) {
    final InMemoryTasksPriorityQueues<NodeDataRequest> taskCollection =
        new InMemoryTasksPriorityQueues<>();

    metricsSystem.createLongGauge(
        BesuMetricCategory.SYNCHRONIZER,
        "world_state_pending_requests_current",
        "Number of pending requests for fast sync world state download",
        taskCollection::size);

    metricsSystem.createIntegerGauge(
        BesuMetricCategory.SYNCHRONIZER,
        "world_state_pending_requests_cache_size",
        "Pending request cache size for fast sync world state download",
        () -> worldStateTaskCacheSize);

    return taskCollection;
  }
}
