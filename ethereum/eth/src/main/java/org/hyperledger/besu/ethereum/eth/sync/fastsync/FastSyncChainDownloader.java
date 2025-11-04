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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.sync.ChainDownloader;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator;
import org.hyperledger.besu.metrics.SyncDurationMetrics;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FastSyncChainDownloader {
  private static final Logger LOG = LoggerFactory.getLogger(FastSyncChainDownloader.class);

  protected FastSyncChainDownloader() {}

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
      final FastSyncStateStorage fastSyncStateStorage,
      final java.nio.file.Path fastSyncDataDirectory) {

    final FastSyncDownloadPipelineFactory pipelineFactory =
        new FastSyncDownloadPipelineFactory(
            config, protocolSchedule, protocolContext, ethContext, fastSyncState, metricsSystem);

    // Use two-stage sync approach which works directly with pivot block hash
    // and doesn't require SyncTargetManager
    final BlockHeader pivotBlockHeader =
        fastSyncState
            .getPivotBlockHeader()
            .orElseThrow(() -> new RuntimeException("pivot block header not available"));
    final Hash pivotBlockHash = pivotBlockHeader.getHash();
    LOG.info(
        "Using two-stage fast sync with pivotHash={}, pivotBlockNumber={}, ",
        pivotBlockHash,
        pivotBlockHeader.getNumber());

    // Create chain sync state storage (separate from world state storage)
    final ChainSyncStateStorage chainStateStorage =
        new ChainSyncStateStorage(fastSyncDataDirectory);

    // Determine checkpoint block for bodies download
    final long checkpointBlock =
        syncState.getCheckpoint().map(checkpoint -> checkpoint.blockNumber()).orElse(0L);

    final Hash genesisHash = protocolContext.getBlockchain().getChainHeadHeader().getHash();

    return new TwoStageFastSyncChainDownloader(
        pipelineFactory,
        ethContext.getScheduler(),
        syncState,
            metricsSystem,
        syncDurationMetrics,
        pivotBlockHeader,
        chainStateStorage,
        checkpointBlock,
        genesisHash
    );
  }
}
