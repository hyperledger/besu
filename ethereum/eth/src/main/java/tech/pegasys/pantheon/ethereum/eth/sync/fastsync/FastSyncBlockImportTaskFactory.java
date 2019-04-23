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
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.sync.EthTaskChainDownloader.BlockImportTaskFactory;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.ParallelImportChainSegmentTask;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.metrics.Counter;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.MetricCategory;
import tech.pegasys.pantheon.metrics.MetricsSystem;

import java.util.List;
import java.util.concurrent.CompletableFuture;

class FastSyncBlockImportTaskFactory<C> implements BlockImportTaskFactory {

  private final SynchronizerConfiguration config;
  private final ProtocolSchedule<C> protocolSchedule;
  private final ProtocolContext<C> protocolContext;
  private final EthContext ethContext;
  private final MetricsSystem metricsSystem;
  private final LabelledMetric<Counter> fastSyncValidationCounter;

  FastSyncBlockImportTaskFactory(
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
    this.fastSyncValidationCounter =
        metricsSystem.createLabelledCounter(
            MetricCategory.SYNCHRONIZER,
            "fast_sync_validation_mode",
            "Number of blocks validated using light vs full validation during fast sync",
            "validationMode");
  }

  @Override
  public CompletableFuture<List<Hash>> importBlocksForCheckpoints(
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
    final FastSyncValidationPolicy ommerValidationPolicy =
        new FastSyncValidationPolicy(
            config.fastSyncFullValidationRate(),
            HeaderValidationMode.LIGHT,
            HeaderValidationMode.FULL,
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
                attachedValidationPolicy,
                ommerValidationPolicy),
            detatchedValidationPolicy,
            checkpointHeaders,
            metricsSystem);
    return importTask.run();
  }
}
