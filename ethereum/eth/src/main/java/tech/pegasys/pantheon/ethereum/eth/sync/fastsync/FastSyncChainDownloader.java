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

import static java.util.Collections.emptyList;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.sync.ChainDownloader;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.PipelinedImportChainSegmentTask;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.OperationTimer;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class FastSyncChainDownloader<C> {
  private final ChainDownloader<C> chainDownloader;
  private final SynchronizerConfiguration config;
  private final ProtocolSchedule<C> protocolSchedule;
  private final ProtocolContext<C> protocolContext;
  private final EthContext ethContext;
  private final LabelledMetric<OperationTimer> ethTasksTimer;
  private final BlockHeader pivotBlockHeader;

  FastSyncChainDownloader(
      final SynchronizerConfiguration config,
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final SyncState syncState,
      final LabelledMetric<OperationTimer> ethTasksTimer,
      final BlockHeader pivotBlockHeader) {
    this.config = config;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.ethTasksTimer = ethTasksTimer;
    this.pivotBlockHeader = pivotBlockHeader;
    chainDownloader =
        new ChainDownloader<>(
            config,
            ethContext,
            syncState,
            ethTasksTimer,
            new FastSyncTargetManager<>(
                config,
                protocolSchedule,
                protocolContext,
                ethContext,
                syncState,
                ethTasksTimer,
                pivotBlockHeader),
            new FastSyncCheckpointHeaderManager<>(
                config,
                protocolContext,
                ethContext,
                syncState,
                protocolSchedule,
                ethTasksTimer,
                pivotBlockHeader),
            this::importBlocksForCheckpoints);
  }

  public CompletableFuture<Void> start() {
    return chainDownloader.start();
  }

  private CompletableFuture<List<Block>> importBlocksForCheckpoints(
      final List<BlockHeader> checkpointHeaders) {
    if (checkpointHeaders.size() < 2) {
      final BlockHeader chainHeadHeader = protocolContext.getBlockchain().getChainHeadHeader();
      if (chainHeadHeader.getNumber() < pivotBlockHeader.getNumber()) {
        checkpointHeaders.add(0, chainHeadHeader);
      } else {
        return CompletableFuture.completedFuture(emptyList());
      }
    }
    final PipelinedImportChainSegmentTask<C, BlockWithReceipts> importTask =
        PipelinedImportChainSegmentTask.forCheckpoints(
            protocolSchedule,
            protocolContext,
            ethContext,
            config.downloaderParallelism(),
            ethTasksTimer,
            new FastSyncBlockHandler<>(
                protocolSchedule, protocolContext, ethContext, ethTasksTimer),
            HeaderValidationMode.LIGHT_DETACHED_ONLY,
            checkpointHeaders);
    return importTask
        .run()
        .thenApply(
            results ->
                results.stream().map(BlockWithReceipts::getBlock).collect(Collectors.toList()));
  }
}
