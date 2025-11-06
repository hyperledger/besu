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

import static org.hyperledger.besu.ethereum.eth.sync.fastsync.ChainSyncState.downloadCheckpointHeader;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.sync.ChainDownloader;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncChainDownloader;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncDownloadPipelineFactory;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncStateStorage;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.TwoStageFastSyncChainDownloader;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.checkpoint.Checkpoint;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.metrics.SyncDurationMetrics;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckpointSyncChainDownloader extends FastSyncChainDownloader {
  private static final Logger LOG = LoggerFactory.getLogger(CheckpointSyncChainDownloader.class);

  public static ChainDownloader create(
      final SynchronizerConfiguration config,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final SyncState syncState,
      final MetricsSystem metricsSystem,
      final FastSyncState fastSyncState,
      final SyncDurationMetrics syncDurationMetrics,
      final FastSyncStateStorage fastSyncStateStorage,
      final Path fastSyncDataDirectory) {

    final FastSyncDownloadPipelineFactory pipelineFactory =
        new CheckpointSyncDownloadPipelineFactory(
            config, protocolSchedule, protocolContext, ethContext, fastSyncState, metricsSystem);

    // Use two-stage sync approach for checkpoint sync
    final BlockHeader pivotBlockHeader =
        fastSyncState
            .getPivotBlockHeader()
            .orElseThrow(() -> new RuntimeException("checkpoint block header not available"));
    final Hash pivotBlockHash = pivotBlockHeader.getHash();
    LOG.info("Using two-stage checkpoint sync with checkpoint block={}", pivotBlockHash);

    // Create chain sync state storage (separate from world state storage)
    final org.hyperledger.besu.ethereum.eth.sync.fastsync.ChainSyncStateStorage chainStateStorage =
        new org.hyperledger.besu.ethereum.eth.sync.fastsync.ChainSyncStateStorage(
            fastSyncDataDirectory);

    // Checkpoint sync always starts from the checkpoint block
    final long checkpointBlock = syncState.getCheckpoint().map(Checkpoint::blockNumber).orElse(0L);

    final BlockHeader checkpointBlockHeader =
        protocolContext
            .getBlockchain()
            .getBlockHeader(checkpointBlock)
            .orElse(
                downloadCheckpointHeader(
                    protocolSchedule,
                    ethContext,
                    syncState
                        .getCheckpoint()
                        .map(Checkpoint::blockHash)
                        .orElseThrow(
                            () ->
                                new IllegalStateException(
                                    "Checkpoint block hash not available."))));

    final Hash genesisHash = protocolContext.getBlockchain().getChainHeadHeader().getHash();

    return new TwoStageFastSyncChainDownloader(
        pipelineFactory,
        protocolContext,
        ethContext.getScheduler(),
        syncState,
        metricsSystem,
        syncDurationMetrics,
        pivotBlockHeader,
        chainStateStorage,
        checkpointBlockHeader,
        genesisHash);
  }
}
