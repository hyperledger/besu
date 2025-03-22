/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.eth.sync.checkpointsync;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.sync.ChainDownloader;
import org.hyperledger.besu.ethereum.eth.sync.PipelineChainDownloader;
import org.hyperledger.besu.ethereum.eth.sync.QuickSyncChainDownloader;
import org.hyperledger.besu.ethereum.eth.sync.QuickSyncState;
import org.hyperledger.besu.ethereum.eth.sync.SyncTargetManager;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator;
import org.hyperledger.besu.metrics.SyncDurationMetrics;
import org.hyperledger.besu.plugin.services.MetricsSystem;

public class CheckpointSyncChainDownloader extends QuickSyncChainDownloader {

  public static ChainDownloader create(
      final SynchronizerConfiguration config,
      final WorldStateStorageCoordinator worldStateStorageCoordinator,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final SyncState syncState,
      final MetricsSystem metricsSystem,
      final QuickSyncState quickSyncState,
      final SyncDurationMetrics syncDurationMetrics) {

    final SyncTargetManager syncTargetManager =
        new SyncTargetManager(
            config,
            worldStateStorageCoordinator,
            protocolSchedule,
            protocolContext,
            ethContext,
            metricsSystem,
            quickSyncState);

    return new PipelineChainDownloader(
        syncState,
        syncTargetManager,
        new CheckpointSyncDownloadPipelineFactory(
            config, protocolSchedule, protocolContext, ethContext, quickSyncState, metricsSystem),
        ethContext.getScheduler(),
        metricsSystem,
        syncDurationMetrics);
  }
}
