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
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.task.AbstractPeerTask.PeerTaskResult;
import tech.pegasys.pantheon.ethereum.eth.sync.ChainDownloader;
import tech.pegasys.pantheon.ethereum.eth.sync.CheckpointHeaderManager;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.TrailingPeerRequirements;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.ImportBlocksTask;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.ParallelImportChainSegmentTask;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.metrics.MetricsSystem;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.google.common.annotations.VisibleForTesting;

public class FullSyncDownloader<C> {
  private final ChainDownloader<C> chainDownloader;
  private final SynchronizerConfiguration config;
  private final ProtocolSchedule<C> protocolSchedule;
  private final ProtocolContext<C> protocolContext;
  private final EthContext ethContext;
  private final SyncState syncState;
  private final MetricsSystem metricsSystem;

  public FullSyncDownloader(
      final SynchronizerConfiguration config,
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final SyncState syncState,
      final MetricsSystem metricsSystem) {
    this.config = config;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.syncState = syncState;
    this.metricsSystem = metricsSystem;
    chainDownloader =
        new ChainDownloader<>(
            config,
            ethContext,
            syncState,
            new FullSyncTargetManager<>(
                config, protocolSchedule, protocolContext, ethContext, syncState, metricsSystem),
            new CheckpointHeaderManager<>(
                config, protocolContext, ethContext, syncState, protocolSchedule, metricsSystem),
            this::importBlocksForCheckpoints,
            metricsSystem);
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
              metricsSystem);
      importedBlocks = importTask.run().thenApply(PeerTaskResult::getResult);
    } else {
      final ParallelImportChainSegmentTask<C, Block> importTask =
          ParallelImportChainSegmentTask.forCheckpoints(
              protocolSchedule,
              protocolContext,
              ethContext,
              config.downloaderParallelism(),
              new FullSyncBlockHandler<>(
                  protocolSchedule, protocolContext, ethContext, metricsSystem),
              () -> HeaderValidationMode.DETACHED_ONLY,
              checkpointHeaders,
              metricsSystem);
      importedBlocks = importTask.run();
    }
    return importedBlocks;
  }

  public TrailingPeerRequirements calculateTrailingPeerRequirements() {
    return syncState.isInSync()
        ? TrailingPeerRequirements.UNRESTRICTED
        : new TrailingPeerRequirements(syncState.chainHeadNumber(), config.getMaxTrailingPeers());
  }
}
