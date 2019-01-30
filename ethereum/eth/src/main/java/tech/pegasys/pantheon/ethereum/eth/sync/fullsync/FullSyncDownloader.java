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
package tech.pegasys.pantheon.ethereum.eth.sync.fullsync;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.AbstractPeerTask.PeerTaskResult;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.sync.ChainDownloader;
import tech.pegasys.pantheon.ethereum.eth.sync.CheckpointHeaderManager;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.ImportBlocksTask;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.PipelinedImportChainSegmentTask;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.OperationTimer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FullSyncDownloader<C> {
  private static final Logger LOG = LogManager.getLogger();
  private final ChainDownloader<C> chainDownloader;
  private final SynchronizerConfiguration config;
  private final ProtocolSchedule<C> protocolSchedule;
  private final ProtocolContext<C> protocolContext;
  private final EthContext ethContext;
  private final LabelledMetric<OperationTimer> ethTasksTimer;

  public FullSyncDownloader(
      final SynchronizerConfiguration config,
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final SyncState syncState,
      final LabelledMetric<OperationTimer> ethTasksTimer) {
    this.config = config;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.ethTasksTimer = ethTasksTimer;
    chainDownloader =
        new ChainDownloader<>(
            config,
            ethContext,
            syncState,
            ethTasksTimer,
            new FullSyncTargetManager<>(
                config, protocolSchedule, protocolContext, ethContext, syncState, ethTasksTimer),
            new CheckpointHeaderManager<>(
                config, protocolContext, ethContext, syncState, protocolSchedule, ethTasksTimer),
            this::importBlocksForCheckpoints);
  }

  public void start() {
    chainDownloader.start();
  }

  @VisibleForTesting
  CompletableFuture<?> getCurrentTask() {
    return chainDownloader.getCurrentTask();
  }

  private CompletableFuture<List<Block>> importBlocksForCheckpoints(
      final List<BlockHeader> checkpointHeaders) {
    final CompletableFuture<List<Block>> importedBlocks;
    if (checkpointHeaders.size() < 2) {
      // Download blocks without constraining the end block
      final ImportBlocksTask<C> importTask =
          ImportBlocksTask.fromHeader(
              protocolSchedule,
              protocolContext,
              ethContext,
              checkpointHeaders.get(0),
              config.downloaderChainSegmentSize(),
              ethTasksTimer);
      importedBlocks = importTask.run().thenApply(PeerTaskResult::getResult);
    } else {
      final PipelinedImportChainSegmentTask<C, Block> importTask =
          PipelinedImportChainSegmentTask.forCheckpoints(
              protocolSchedule,
              protocolContext,
              ethContext,
              config.downloaderParallelism(),
              ethTasksTimer,
              new FullSyncBlockHandler<>(
                  protocolSchedule, protocolContext, ethContext, ethTasksTimer),
              HeaderValidationMode.DETACHED_ONLY,
              checkpointHeaders);
      importedBlocks = importTask.run();
    }
    return importedBlocks;
  }
}
