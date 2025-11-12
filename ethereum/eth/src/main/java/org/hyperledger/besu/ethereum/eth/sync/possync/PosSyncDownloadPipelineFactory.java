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
package org.hyperledger.besu.ethereum.eth.sync.possync;

import static org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode.FULL;
import static org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode.LIGHT;
import static org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode.LIGHT_SKIP_DETACHED;
import static org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode.NONE;
import static org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode.SKIP_DETACHED;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.sync.DownloadPipelineFactory;
import org.hyperledger.besu.ethereum.eth.sync.PosDownloadAndStoreSyncBodiesStep;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncValidationPolicy;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FinishPosSyncStep;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.PosDownloadAndStoreSyncReceiptsStep;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncTarget;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.services.pipeline.Pipeline;
import org.hyperledger.besu.services.pipeline.PipelineBuilder;

import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PosSyncDownloadPipelineFactory implements DownloadPipelineFactory {
  private static final Logger LOG = LoggerFactory.getLogger(PosSyncDownloadPipelineFactory.class);

  protected final SynchronizerConfiguration syncConfig;
  protected final ProtocolSchedule protocolSchedule;
  protected final ProtocolContext protocolContext;
  protected final EthContext ethContext;
  protected final FastSyncState fastSyncState;
  protected final MetricsSystem metricsSystem;
  protected final FastSyncValidationPolicy attachedValidationPolicy;
  protected final FastSyncValidationPolicy headerValidationPolicy;
  protected final FastSyncValidationPolicy ommerValidationPolicy;

  public PosSyncDownloadPipelineFactory(
      final SynchronizerConfiguration syncConfig,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final FastSyncState fastSyncState,
      final MetricsSystem metricsSystem) {
    this.syncConfig = syncConfig;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.fastSyncState = fastSyncState;
    this.metricsSystem = metricsSystem;
    final LabelledMetric<Counter> fastSyncValidationCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.SYNCHRONIZER,
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
    headerValidationPolicy =
        new FastSyncValidationPolicy(
            this.syncConfig.getFastSyncFullValidationRate(), NONE, NONE, fastSyncValidationCounter);
  }

  @Override
  public CompletionStage<Void> startPipeline(
      final EthScheduler scheduler,
      final SyncState syncState,
      final SyncTarget syncTarget,
      final Pipeline<?> pipeline) {
    if (!hasReachedHeaderDownloadTarget()) {
      return scheduler
          .startPipeline(createDownloadHeadersPipeline())
          .thenCompose(__ -> scheduler.startPipeline(pipeline));
    } else {
      LOG.info("Skipping header download pipeline as target header already reached");
      return scheduler.startPipeline(pipeline);
    }
  }

  protected Pipeline<SyncTargetNumberRange> createDownloadHeadersPipeline() {
    final int downloaderHeaderParallelism = syncConfig.getDownloaderHeaderParallelism();
    final int headerRequestSize = syncConfig.getDownloaderHeaderRequestSize();

    final PosSyncSource syncSource =
        new PosSyncSource(
            syncConfig.getDownloaderHeaderTarget(),
            () -> fastSyncState.getPivotBlockHeader().get().getNumber(),
            headerRequestSize,
            true);
    final DownloadPosHeadersStep downloadHeadersStep =
        new DownloadPosHeadersStep(
            protocolSchedule,
            protocolContext,
            ethContext,
            headerValidationPolicy,
            syncConfig,
            metricsSystem);
    final ImportHeadersStep importHeadersStep =
        new ImportHeadersStep(
            protocolContext.getBlockchain(),
            syncConfig.getDownloaderHeaderTarget(),
            () -> fastSyncState.getPivotBlockHeader().get().getNumber());

    return PipelineBuilder.createPipelineFrom(
            "fetchCheckpoints",
            syncSource,
            downloaderHeaderParallelism,
            metricsSystem.createLabelledCounter(
                BesuMetricCategory.SYNCHRONIZER,
                "chain_download_pipeline_processed_total",
                "Number of entries process by each chain download pipeline stage",
                "step",
                "action"),
            true,
            "headerDownload")
        .thenProcessAsync("downloadHeaders", downloadHeadersStep, downloaderHeaderParallelism)
        .andFinishWith("importHeaders", importHeadersStep);
  }

  @Override
  public Pipeline<SyncTargetNumberRange> createDownloadPipelineForSyncTarget(
      final SyncState syncState, final SyncTarget target) {
    final int downloaderParallelism = syncConfig.getDownloaderParallelism();
    final int headerRequestSize = syncConfig.getDownloaderHeaderRequestSize();
    final int downloaderBodyParallelism = syncConfig.getDownloaderBodyParallelism();
    final int downloaderReceiptParallelism = syncConfig.getDownloaderReceiptsParallelism();

    final long startingBlock = getCommonAncestor(target);
    final PosSyncSource syncSource =
        new PosSyncSource(
            startingBlock, // TODO remove the +1 when check in DefaultBlockChain is fixed
            () -> fastSyncState.getPivotBlockHeader().get().getNumber(),
            headerRequestSize,
            false);
    final DownloadPosHeadersStep downloadHeadersStep =
        new DownloadPosHeadersStep(
            protocolSchedule,
            protocolContext,
            ethContext,
            headerValidationPolicy,
            syncConfig,
            metricsSystem);
    final LoadHeadersStep loadHeadersStep =
        new LoadHeadersStep(protocolContext.getBlockchain(), downloadHeadersStep);
    final PosDownloadAndStoreSyncBodiesStep downloadSyncBodiesStep =
        new PosDownloadAndStoreSyncBodiesStep(
            protocolSchedule, ethContext, metricsSystem, syncConfig);
    final PosDownloadAndStoreSyncReceiptsStep downloadSyncReceiptsStep =
        new PosDownloadAndStoreSyncReceiptsStep(
            protocolSchedule, ethContext, syncConfig, metricsSystem);
    final FinishPosSyncStep finishPosSyncStep =
        new FinishPosSyncStep(
            protocolSchedule,
            protocolContext,
            ethContext,
            fastSyncState.getPivotBlockHeader().get(),
            syncConfig.getSnapSyncConfiguration().isSnapSyncTransactionIndexingEnabled(),
            startingBlock);

    return PipelineBuilder.createPipelineFrom(
            "fetchCheckpoints",
            syncSource,
            downloaderParallelism,
            metricsSystem.createLabelledCounter(
                BesuMetricCategory.SYNCHRONIZER,
                "chain_download_pipeline_processed_total",
                "Number of entries process by each chain download pipeline stage",
                "step",
                "action"),
            true,
            "fastSync")
        .thenProcessAsync("loadHeaders", loadHeadersStep, headerRequestSize)
        .thenProcessAsync("downloadSyncBodies", downloadSyncBodiesStep, downloaderBodyParallelism)
        .thenProcessAsync(
            "downloadReceipts", downloadSyncReceiptsStep, downloaderReceiptParallelism)
        .andFinishWith("importBlock", finishPosSyncStep);
  }

  private long getCommonAncestor(final SyncTarget syncTarget) {
    return Math.max(
        syncConfig.getDownloaderHeaderTarget(), syncTarget.commonAncestor().getNumber());
  }

  private boolean hasReachedHeaderDownloadTarget() {
    var targetToCheck =
        syncConfig.getDownloaderHeaderTarget() == 0 ? 1 : syncConfig.getDownloaderHeaderTarget();
    return ethContext.getBlockchain().getBlockHeader(targetToCheck).isPresent();
  }
}
