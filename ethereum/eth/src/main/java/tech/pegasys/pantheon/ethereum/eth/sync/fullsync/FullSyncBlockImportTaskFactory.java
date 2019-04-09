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
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.task.AbstractPeerTask.PeerTaskResult;
import tech.pegasys.pantheon.ethereum.eth.sync.EthTaskChainDownloader.BlockImportTaskFactory;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.ImportBlocksTask;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.ParallelImportChainSegmentTask;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.metrics.MetricsSystem;

import java.util.List;
import java.util.concurrent.CompletableFuture;

class FullSyncBlockImportTaskFactory<C> implements BlockImportTaskFactory {

  private final SynchronizerConfiguration config;
  private final ProtocolSchedule<C> protocolSchedule;
  private final ProtocolContext<C> protocolContext;
  private final EthContext ethContext;
  private final MetricsSystem metricsSystem;

  FullSyncBlockImportTaskFactory(
      final SynchronizerConfiguration config,
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final MetricsSystem metricsSystem) {
    this.config = config;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.metricsSystem = metricsSystem;
  }

  @Override
  public CompletableFuture<List<Hash>> importBlocksForCheckpoints(
      final List<BlockHeader> checkpointHeaders) {
    final CompletableFuture<List<Hash>> importedHashes;
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
      importedHashes = importTask.run().thenApply(PeerTaskResult::getResult);
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
      importedHashes = importTask.run();
    }
    return importedHashes;
  }
}
