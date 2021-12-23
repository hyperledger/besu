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
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
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
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.services.tasks.CachingTaskCollection;
import org.hyperledger.besu.services.tasks.FlatFileTaskCollection;

import java.io.File;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FastDownloaderFactory {

  protected static final String FAST_SYNC_FOLDER = "fastsync";

  private static final Logger LOG = LogManager.getLogger();

  public static Optional<FastSyncDownloader<NodeDataRequest>> create(
      final SynchronizerConfiguration syncConfig,
      final Path dataDirectory,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final MetricsSystem metricsSystem,
      final EthContext ethContext,
      final WorldStateStorage worldStateStorage,
      final SyncState syncState,
      final Clock clock) {

    final Path fastSyncDataDirectory = dataDirectory.resolve(FAST_SYNC_FOLDER);
    final FastSyncStateStorage fastSyncStateStorage =
        new FastSyncStateStorage(fastSyncDataDirectory);

    if (syncConfig.getSyncMode() != SyncMode.FAST) {
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
    if (fastSyncState.getPivotBlockHeader().isEmpty()
        && protocolContext.getBlockchain().getChainHeadBlockNumber()
            != BlockHeader.GENESIS_BLOCK_NUMBER) {
      LOG.info(
          "Fast sync was requested, but cannot be enabled because the local blockchain is not empty.");
      return Optional.empty();
    }
    if (worldStateStorage instanceof BonsaiWorldStateKeyValueStorage) {
      worldStateStorage.clear();
    }
    final CachingTaskCollection<NodeDataRequest> taskCollection =
        createWorldStateDownloaderTaskCollection(
            getStateQueueDirectory(dataDirectory),
            metricsSystem,
            syncConfig.getWorldStateTaskCacheSize());
    final WorldStateDownloader worldStateDownloader =
        new FastWorldStateDownloader(
            ethContext,
            worldStateStorage,
            taskCollection,
            syncConfig.getWorldStateHashCountPerRequest(),
            syncConfig.getWorldStateRequestParallelism(),
            syncConfig.getWorldStateMaxRequestsWithoutProgress(),
            syncConfig.getWorldStateMinMillisBeforeStalling(),
            clock,
            metricsSystem);
    final FastSyncDownloader<NodeDataRequest> fastSyncDownloader =
        new FastSyncDownloader<>(
            new FastSyncActions(
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
    syncState.setWorldStateDownloadStatus(worldStateDownloader);
    return Optional.of(fastSyncDownloader);
  }

  protected static Path getStateQueueDirectory(final Path dataDirectory) {
    final Path queueDataDir = getFastSyncDataDirectory(dataDirectory).resolve("statequeue");
    ensureDirectoryExists(queueDataDir.toFile());
    return queueDataDir;
  }

  private static Path getFastSyncDataDirectory(final Path dataDirectory) {
    final Path fastSyncDataDir = dataDirectory.resolve(FAST_SYNC_FOLDER);
    ensureDirectoryExists(fastSyncDataDir.toFile());
    return fastSyncDataDir;
  }

  protected static void ensureDirectoryExists(final File dir) {
    if (!dir.mkdirs() && !dir.isDirectory()) {
      throw new IllegalStateException("Unable to create directory: " + dir.getAbsolutePath());
    }
  }

  protected static CachingTaskCollection<NodeDataRequest> createWorldStateDownloaderTaskCollection(
      final Path dataDirectory,
      final MetricsSystem metricsSystem,
      final int worldStateTaskCacheSize) {
    final CachingTaskCollection<NodeDataRequest> taskCollection =
        new CachingTaskCollection<>(
            new FlatFileTaskCollection<>(
                dataDirectory, NodeDataRequest::serialize, NodeDataRequest::deserialize),
            worldStateTaskCacheSize);

    metricsSystem.createLongGauge(
        BesuMetricCategory.SYNCHRONIZER,
        "world_state_pending_requests_current",
        "Number of pending requests for fast sync world state download",
        taskCollection::size);

    metricsSystem.createIntegerGauge(
        BesuMetricCategory.SYNCHRONIZER,
        "world_state_pending_requests_cache_size",
        "Pending request cache size for fast sync world state download",
        taskCollection::cacheSize);

    return taskCollection;
  }
}
