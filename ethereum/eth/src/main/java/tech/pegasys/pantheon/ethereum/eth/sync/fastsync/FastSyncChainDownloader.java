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
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.ParallelImportChainSegmentTask;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.metrics.Counter;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.MetricCategory;
import tech.pegasys.pantheon.metrics.MetricsSystem;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class FastSyncChainDownloader<C> {
  private final ChainDownloader<C> chainDownloader;
  private final SynchronizerConfiguration config;
  private final ProtocolSchedule<C> protocolSchedule;
  private final ProtocolContext<C> protocolContext;
  private final EthContext ethContext;
  private final MetricsSystem metricsSystem;
  private final LabelledMetric<Counter> fastSyncValidationCounter;

  FastSyncChainDownloader(
      final SynchronizerConfiguration config,
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final SyncState syncState,
      final MetricsSystem metricsSystem,
      final BlockHeader pivotBlockHeader) {
    this.config = config;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.metricsSystem = metricsSystem;

    chainDownloader =
        new ChainDownloader<>(
            config,
            ethContext,
            syncState,
            new FastSyncTargetManager<>(
                config,
                protocolSchedule,
                protocolContext,
                ethContext,
                metricsSystem,
                pivotBlockHeader),
            new FastSyncCheckpointHeaderManager<>(
                config,
                protocolContext,
                ethContext,
                syncState,
                protocolSchedule,
                metricsSystem,
                pivotBlockHeader),
            this::importBlocksForCheckpoints,
            metricsSystem);
    this.fastSyncValidationCounter =
        metricsSystem.createLabelledCounter(
            MetricCategory.SYNCHRONIZER,
            "fast_sync_validation_mode",
            "Number of blocks validated using light vs full validation during fast sync",
            "validationMode");
  }

  public CompletableFuture<Void> start() {
    return chainDownloader.start();
  }

  private CompletableFuture<List<Block>> importBlocksForCheckpoints(
      final List<BlockHeader> checkpointHeaders) {
    if (checkpointHeaders.size() < 2) {
      return CompletableFuture.completedFuture(emptyList());
    }
    final FastSyncValidationPolicy attachedValidationPolicy =
        new FastSyncValidationPolicy(
            config.fastSyncFullValidationRate(),
            HeaderValidationMode.LIGHT_SKIP_DETACHED,
            HeaderValidationMode.SKIP_DETACHED,
            fastSyncValidationCounter);
    final FastSyncValidationPolicy detatchedValidationPolicy =
        new FastSyncValidationPolicy(
            config.fastSyncFullValidationRate(),
            HeaderValidationMode.LIGHT_DETACHED_ONLY,
            HeaderValidationMode.DETACHED_ONLY,
            fastSyncValidationCounter);

    final ParallelImportChainSegmentTask<C, BlockWithReceipts> importTask =
        ParallelImportChainSegmentTask.forCheckpoints(
            protocolSchedule,
            protocolContext,
            ethContext,
            config.downloaderParallelism(),
            new FastSyncBlockHandler<>(
                protocolSchedule,
                protocolContext,
                ethContext,
                metricsSystem,
                attachedValidationPolicy),
            detatchedValidationPolicy,
            checkpointHeaders,
            metricsSystem);
    return importTask
        .run()
        .thenApply(
            results ->
                results.stream().map(BlockWithReceipts::getBlock).collect(Collectors.toList()));
  }
}
