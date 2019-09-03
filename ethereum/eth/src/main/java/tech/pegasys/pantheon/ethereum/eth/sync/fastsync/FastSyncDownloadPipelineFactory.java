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

import static tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode.DETACHED_ONLY;
import static tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode.FULL;
import static tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode.LIGHT;
import static tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode.LIGHT_DETACHED_ONLY;
import static tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode.LIGHT_SKIP_DETACHED;
import static tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode.SKIP_DETACHED;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.sync.CheckpointHeaderFetcher;
import tech.pegasys.pantheon.ethereum.eth.sync.CheckpointHeaderValidationStep;
import tech.pegasys.pantheon.ethereum.eth.sync.CheckpointRangeSource;
import tech.pegasys.pantheon.ethereum.eth.sync.DownloadBodiesStep;
import tech.pegasys.pantheon.ethereum.eth.sync.DownloadHeadersStep;
import tech.pegasys.pantheon.ethereum.eth.sync.DownloadPipelineFactory;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncTarget;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.metrics.PantheonMetricCategory;
import tech.pegasys.pantheon.plugin.services.MetricsSystem;
import tech.pegasys.pantheon.plugin.services.metrics.Counter;
import tech.pegasys.pantheon.plugin.services.metrics.LabelledMetric;
import tech.pegasys.pantheon.services.pipeline.Pipeline;
import tech.pegasys.pantheon.services.pipeline.PipelineBuilder;

import java.util.Optional;

public class FastSyncDownloadPipelineFactory<C> implements DownloadPipelineFactory {
  private final SynchronizerConfiguration syncConfig;
  private final ProtocolSchedule<C> protocolSchedule;
  private final ProtocolContext<C> protocolContext;
  private final EthContext ethContext;
  private final BlockHeader pivotBlockHeader;
  private final MetricsSystem metricsSystem;
  private final FastSyncValidationPolicy attachedValidationPolicy;
  private final FastSyncValidationPolicy detachedValidationPolicy;
  private final FastSyncValidationPolicy ommerValidationPolicy;

  public FastSyncDownloadPipelineFactory(
      final SynchronizerConfiguration syncConfig,
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final BlockHeader pivotBlockHeader,
      final MetricsSystem metricsSystem) {
    this.syncConfig = syncConfig;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.pivotBlockHeader = pivotBlockHeader;
    this.metricsSystem = metricsSystem;
    final LabelledMetric<Counter> fastSyncValidationCounter =
        metricsSystem.createLabelledCounter(
            PantheonMetricCategory.SYNCHRONIZER,
            "fast_sync_validation_mode",
            "Number of blocks validated using light vs full validation during fast sync",
            "validationMode");
    attachedValidationPolicy =
        new FastSyncValidationPolicy(
            this.syncConfig.getFastSyncFullValidationRate(),
            LIGHT_SKIP_DETACHED,
            SKIP_DETACHED,
            fastSyncValidationCounter);
    ommerValidationPolicy =
        new FastSyncValidationPolicy(
            this.syncConfig.getFastSyncFullValidationRate(),
            LIGHT,
            FULL,
            fastSyncValidationCounter);
    detachedValidationPolicy =
        new FastSyncValidationPolicy(
            this.syncConfig.getFastSyncFullValidationRate(),
            LIGHT_DETACHED_ONLY,
            DETACHED_ONLY,
            fastSyncValidationCounter);
  }

  @Override
  public Pipeline<?> createDownloadPipelineForSyncTarget(final SyncTarget target) {
    final int downloaderParallelism = syncConfig.getDownloaderParallelism();
    final int headerRequestSize = syncConfig.getDownloaderHeaderRequestSize();
    final int singleHeaderBufferSize = headerRequestSize * downloaderParallelism;
    final CheckpointRangeSource checkpointRangeSource =
        new CheckpointRangeSource(
            new CheckpointHeaderFetcher(
                syncConfig,
                protocolSchedule,
                ethContext,
                Optional.of(pivotBlockHeader),
                metricsSystem),
            this::shouldContinueDownloadingFromPeer,
            ethContext.getScheduler(),
            target.peer(),
            target.commonAncestor(),
            syncConfig.getDownloaderCheckpointTimeoutsPermitted());
    final DownloadHeadersStep<C> downloadHeadersStep =
        new DownloadHeadersStep<>(
            protocolSchedule,
            protocolContext,
            ethContext,
            detachedValidationPolicy,
            headerRequestSize,
            metricsSystem);
    final CheckpointHeaderValidationStep<C> validateHeadersJoinUpStep =
        new CheckpointHeaderValidationStep<>(
            protocolSchedule, protocolContext, detachedValidationPolicy);
    final DownloadBodiesStep<C> downloadBodiesStep =
        new DownloadBodiesStep<>(protocolSchedule, ethContext, metricsSystem);
    final DownloadReceiptsStep downloadReceiptsStep =
        new DownloadReceiptsStep(ethContext, metricsSystem);
    final FastImportBlocksStep<C> importBlockStep =
        new FastImportBlocksStep<>(
            protocolSchedule, protocolContext, attachedValidationPolicy, ommerValidationPolicy);

    return PipelineBuilder.createPipelineFrom(
            "fetchCheckpoints",
            checkpointRangeSource,
            downloaderParallelism,
            metricsSystem.createLabelledCounter(
                PantheonMetricCategory.SYNCHRONIZER,
                "chain_download_pipeline_processed_total",
                "Number of entries process by each chain download pipeline stage",
                "step",
                "action"))
        .thenProcessAsyncOrdered("downloadHeaders", downloadHeadersStep, downloaderParallelism)
        .thenFlatMap("validateHeadersJoin", validateHeadersJoinUpStep, singleHeaderBufferSize)
        .inBatches(headerRequestSize)
        .thenProcessAsyncOrdered("downloadBodies", downloadBodiesStep, downloaderParallelism)
        .thenProcessAsyncOrdered("downloadReceipts", downloadReceiptsStep, downloaderParallelism)
        .andFinishWith("importBlock", importBlockStep);
  }

  private boolean shouldContinueDownloadingFromPeer(
      final EthPeer peer, final BlockHeader lastCheckpointHeader) {
    return !peer.isDisconnected()
        && lastCheckpointHeader.getNumber() < pivotBlockHeader.getNumber();
  }
}
