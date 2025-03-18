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

import static org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode.DETACHED_ONLY;
import static org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode.FULL;
import static org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode.LIGHT;
import static org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode.LIGHT_DETACHED_ONLY;
import static org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode.LIGHT_SKIP_DETACHED;
import static org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode.SKIP_DETACHED;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
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
  protected final FastSyncValidationPolicy detachedValidationPolicy;
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
    detachedValidationPolicy =
        new FastSyncValidationPolicy(
            this.syncConfig.getFastSyncFullValidationRate(),
            LIGHT_DETACHED_ONLY,
            DETACHED_ONLY,
            fastSyncValidationCounter);
  }

  @Override
  public CompletionStage<Void> startPipeline(
      final EthScheduler scheduler,
      final SyncState syncState,
      final SyncTarget syncTarget,
      final Pipeline<?> pipeline) {
    return scheduler
        .startPipeline(createDownloadHeadersPipeline(syncTarget))
        .thenCompose(__ -> scheduler.startPipeline(pipeline));
  }

  protected Pipeline<SyncTargetNumberRange> createDownloadHeadersPipeline(final SyncTarget target) {
    final int downloaderHeaderParallelism = syncConfig.getDownloaderHeaderParallelism();
    final int headerRequestSize = syncConfig.getDownloaderHeaderRequestSize();

    final PosSyncSource syncSource =
        new PosSyncSource(
            0,
            () -> fastSyncState.getPivotBlockHeader().get().getNumber(),
            headerRequestSize,
            true);
    final DownloadPosHeadersStep downloadHeadersStep =
        new DownloadPosHeadersStep(
            protocolSchedule,
            protocolContext,
            ethContext,
            detachedValidationPolicy,
            syncConfig,
            metricsSystem);
    final ImportHeadersStep importHeadersStep =
        new ImportHeadersStep(
            protocolContext.getBlockchain(),
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
      final SyncTarget target) {
    final int downloaderParallelism = syncConfig.getDownloaderParallelism();

    final PosSyncSource syncSource =
        new PosSyncSource(
            getCommonAncestor(target).getNumber() + 1,
            () -> fastSyncState.getPivotBlockHeader().get().getNumber(),
            downloaderParallelism,
            false);
    final LoadHeadersStep loadHeadersStep = new LoadHeadersStep(protocolContext.getBlockchain());
    final PosDownloadAndStoreSyncBodiesStep downloadSyncBodiesStep =
        new PosDownloadAndStoreSyncBodiesStep(
            protocolSchedule, ethContext, metricsSystem, syncConfig);
    final PosDownloadAndStoreSyncReceiptsStep downloadSyncReceiptsStep =
        new PosDownloadAndStoreSyncReceiptsStep(
            protocolSchedule, ethContext, syncConfig, metricsSystem);
    final FinishPosSyncStep importSyncBlocksStep =
        new FinishPosSyncStep(
            protocolSchedule,
            protocolContext,
            ethContext,
            fastSyncState.getPivotBlockHeader().get(),
            syncConfig.getSnapSyncConfiguration().isSnapSyncTransactionIndexingEnabled());

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
        .thenProcessAsync("loadHeaders", loadHeadersStep, downloaderParallelism)
        .thenProcessAsyncOrdered(
            "downloadSyncBodies", downloadSyncBodiesStep, downloaderParallelism)
        .thenProcessAsyncOrdered(
            "downloadReceipts", downloadSyncReceiptsStep, downloaderParallelism)
        .andFinishWith("importBlock", importSyncBlocksStep);
  }

  protected BlockHeader getCommonAncestor(final SyncTarget syncTarget) {
    return syncTarget.commonAncestor();
  }

  protected boolean shouldContinueDownloadingFromPeer(
      final EthPeer peer, final BlockHeader lastRoundHeader) {
    final BlockHeader pivotBlockHeader = fastSyncState.getPivotBlockHeader().get();
    final boolean shouldContinue =
        !peer.isDisconnected() && lastRoundHeader.getNumber() < pivotBlockHeader.getNumber();

    if (!shouldContinue && peer.isDisconnected()) {
      LOG.debug("Stopping chain download due to disconnected peer {}", peer);
    } else if (!shouldContinue && lastRoundHeader.getNumber() >= pivotBlockHeader.getNumber()) {
      LOG.debug(
          "Stopping chain download as lastRoundHeader={} is not less than pivotBlockHeader={} for peer {}",
          lastRoundHeader.getNumber(),
          pivotBlockHeader.getNumber(),
          peer);
    }
    return shouldContinue;
  }
}
