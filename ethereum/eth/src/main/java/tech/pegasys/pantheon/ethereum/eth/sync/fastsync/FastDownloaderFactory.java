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
package tech.pegasys.pantheon.ethereum.eth.sync.fastsync;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.sync.SyncMode;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.eth.sync.worldstate.NodeDataRequest;
import tech.pegasys.pantheon.ethereum.eth.sync.worldstate.WorldStateDownloader;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateStorage;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.PantheonMetricCategory;
import tech.pegasys.pantheon.services.tasks.CachingTaskCollection;
import tech.pegasys.pantheon.services.tasks.FlatFileTaskCollection;

import java.io.File;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FastDownloaderFactory {
  private static final Logger LOG = LogManager.getLogger();

  public static <C> Optional<FastSyncDownloader<C>> create(
      final SynchronizerConfiguration syncConfig,
      final Path dataDirectory,
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final MetricsSystem metricsSystem,
      final EthContext ethContext,
      final WorldStateStorage worldStateStorage,
      final SyncState syncState,
      final Clock clock) {
    if (syncConfig.getSyncMode() != SyncMode.FAST) {
      return Optional.empty();
    }

    final Path fastSyncDataDirectory = getFastSyncDataDirectory(dataDirectory);
    final FastSyncStateStorage fastSyncStateStorage =
        new FastSyncStateStorage(fastSyncDataDirectory);
    final FastSyncState fastSyncState =
        fastSyncStateStorage.loadState(ScheduleBasedBlockHeaderFunctions.create(protocolSchedule));
    if (!fastSyncState.getPivotBlockHeader().isPresent()
        && protocolContext.getBlockchain().getChainHeadBlockNumber()
            != BlockHeader.GENESIS_BLOCK_NUMBER) {
      LOG.info(
          "Fast sync was requested, but cannot be enabled because the local blockchain is not empty.");
      return Optional.empty();
    }

    final CachingTaskCollection<NodeDataRequest> taskCollection =
        createWorldStateDownloaderTaskCollection(
            getStateQueueDirectory(dataDirectory),
            metricsSystem,
            syncConfig.getWorldStateTaskCacheSize());
    final WorldStateDownloader worldStateDownloader =
        new WorldStateDownloader(
            ethContext,
            worldStateStorage,
            taskCollection,
            syncConfig.getWorldStateHashCountPerRequest(),
            syncConfig.getWorldStateRequestParallelism(),
            syncConfig.getWorldStateMaxRequestsWithoutProgress(),
            syncConfig.getWorldStateMinMillisBeforeStalling(),
            clock,
            metricsSystem);
    final FastSyncDownloader<C> fastSyncDownloader =
        new FastSyncDownloader<>(
            new FastSyncActions<>(
                syncConfig,
                protocolSchedule,
                protocolContext,
                ethContext,
                syncState,
                metricsSystem),
            worldStateDownloader,
            fastSyncStateStorage,
            taskCollection,
            fastSyncDataDirectory,
            fastSyncState);
    return Optional.of(fastSyncDownloader);
  }

  private static Path getStateQueueDirectory(final Path dataDirectory) {
    final Path queueDataDir = getFastSyncDataDirectory(dataDirectory).resolve("statequeue");
    ensureDirectoryExists(queueDataDir.toFile());
    return queueDataDir;
  }

  private static Path getFastSyncDataDirectory(final Path dataDirectory) {
    final Path fastSyncDataDir = dataDirectory.resolve("fastsync");
    ensureDirectoryExists(fastSyncDataDir.toFile());
    return fastSyncDataDir;
  }

  private static void ensureDirectoryExists(final File dir) {
    if (!dir.mkdirs() && !dir.isDirectory()) {
      throw new IllegalStateException("Unable to create directory: " + dir.getAbsolutePath());
    }
  }

  private static CachingTaskCollection<NodeDataRequest> createWorldStateDownloaderTaskCollection(
      final Path dataDirectory,
      final MetricsSystem metricsSystem,
      final int worldStateTaskCacheSize) {
    final CachingTaskCollection<NodeDataRequest> taskCollection =
        new CachingTaskCollection<>(
            new FlatFileTaskCollection<>(
                dataDirectory, NodeDataRequest::serialize, NodeDataRequest::deserialize),
            worldStateTaskCacheSize);

    metricsSystem.createLongGauge(
        PantheonMetricCategory.SYNCHRONIZER,
        "world_state_pending_requests_current",
        "Number of pending requests for fast sync world state download",
        taskCollection::size);

    metricsSystem.createIntegerGauge(
        PantheonMetricCategory.SYNCHRONIZER,
        "world_state_pending_requests_cache_size",
        "Pending request cache size for fast sync world state download",
        taskCollection::cacheSize);

    return taskCollection;
  }
}
