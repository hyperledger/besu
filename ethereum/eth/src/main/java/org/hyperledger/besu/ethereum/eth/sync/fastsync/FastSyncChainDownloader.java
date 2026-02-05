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

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.sync.ChainDownloader;
import org.hyperledger.besu.ethereum.eth.sync.PipelineChainDownloader;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator;
import org.hyperledger.besu.metrics.SyncDurationMetrics;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.nio.file.Path;

public class FastSyncChainDownloader {

  protected FastSyncChainDownloader() {}

  /**
   * Creates a traditional fast sync chain downloader using pipeline-based single-stage sync.
   *
   * @param config the synchronizer configuration
   * @param worldStateStorageCoordinator the world state storage coordinator
   * @param protocolSchedule the protocol schedule
   * @param protocolContext the protocol context
   * @param ethContext the Ethereum context
   * @param syncState the sync state
   * @param metricsSystem the metrics system
   * @param fastSyncState the fast sync state
   * @param syncDurationMetrics the sync duration metrics
   * @param fastSyncDataDirectory the directory for storing sync state (unused for traditional fast
   *     sync)
   * @return a PipelineChainDownloader configured for traditional fast sync
   */
  public static ChainDownloader create(
      final SynchronizerConfiguration config,
      final WorldStateStorageCoordinator worldStateStorageCoordinator,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final SyncState syncState,
      final MetricsSystem metricsSystem,
      final FastSyncState fastSyncState,
      final SyncDurationMetrics syncDurationMetrics,
      final Path fastSyncDataDirectory) {

    final SyncTargetManager syncTargetManager =
        new SyncTargetManager(
            config,
            worldStateStorageCoordinator,
            protocolSchedule,
            protocolContext,
            ethContext,
            metricsSystem,
            fastSyncState);

    final FastSyncDownloadPipelineFactory pipelineFactory =
        new FastSyncDownloadPipelineFactory(
            config, protocolSchedule, protocolContext, ethContext, fastSyncState, metricsSystem);

    return new PipelineChainDownloader(
        syncState,
        syncTargetManager,
        pipelineFactory,
        ethContext.getScheduler(),
        metricsSystem,
        syncDurationMetrics);
  }
}
