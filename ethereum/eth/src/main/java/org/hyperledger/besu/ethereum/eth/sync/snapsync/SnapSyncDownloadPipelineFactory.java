/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import static org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode.DETACHED_ONLY;
import static org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode.LIGHT_DETACHED_ONLY;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.sync.DownloadSyncBodiesStep;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.ValidationPolicy;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.BackwardHeaderSource;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.BlockHeaderSource;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.ChainSyncState;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.DownloadBackwardHeadersStep;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.DownloadSyncReceiptsStep;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncValidationPolicy;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.ImportHeadersStep;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncTarget;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.services.pipeline.Pipeline;
import org.hyperledger.besu.services.pipeline.PipelineBuilder;

import java.util.List;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnapSyncDownloadPipelineFactory {
  private static final Logger LOG = LoggerFactory.getLogger(SnapSyncDownloadPipelineFactory.class);

  protected final SynchronizerConfiguration syncConfig;
  protected final ProtocolSchedule protocolSchedule;
  protected final ProtocolContext protocolContext;
  protected final EthContext ethContext;
  protected final FastSyncState fastSyncState;
  protected final MetricsSystem metricsSystem;
  protected final FastSyncValidationPolicy detachedValidationPolicy;
  protected final ValidationPolicy downloadHeaderValidation;

  public SnapSyncDownloadPipelineFactory(
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
    detachedValidationPolicy =
        new FastSyncValidationPolicy(
            this.syncConfig.getFastSyncFullValidationRate(),
            LIGHT_DETACHED_ONLY,
            DETACHED_ONLY,
            fastSyncValidationCounter);
    final ValidationPolicy noneValidationPolicy = () -> HeaderValidationMode.NONE;
    downloadHeaderValidation =
        fastSyncState.isSourceTrusted() ? noneValidationPolicy : detachedValidationPolicy;
    if (fastSyncState.isSourceTrusted()) {
      LOG.trace("Pivot block is from trusted source, skipping header validation");
    }
  }

  public CompletionStage<Void> startPipeline(
      final EthScheduler scheduler,
      final SyncState syncState,
      final SyncTarget syncTarget,
      final Pipeline<?> pipeline) {
    return scheduler.startPipeline(pipeline);
  }

  /**
   * Creates Pipeline 1: Backward header download from pivot block to checkpoint block. Downloads
   * headers in reverse direction, validates boundaries, and stores in database. Supports
   * out-of-order parallel execution with resume capability.
   *
   * @param chainState chain sync state containing pivot and progress
   * @return the backward header download pipeline
   */
  public Pipeline<Long> createBackwardHeaderDownloadPipeline(final ChainSyncState chainState) {
    final int downloaderParallelism = syncConfig.getDownloaderParallelism();
    final int headerRequestSize = syncConfig.getDownloaderHeaderRequestSize();

    BlockHeader anchorForHeaderDownload =
        chainState.headerDownloadAnchor() == null
            ? chainState.checkpointBlockHeader()
            : chainState.headerDownloadAnchor();

    final long pivotBlockNumber = chainState.pivotBlockHeader().getNumber();
    LOG.debug(
        "Creating backward header download pipeline from pivot={} down to lowest block={}, parallelism={}, batchSize={}",
        pivotBlockNumber,
        anchorForHeaderDownload.getNumber(),
        downloaderParallelism,
        headerRequestSize);

    final BackwardHeaderSource headerSource =
        new BackwardHeaderSource(
            headerRequestSize, anchorForHeaderDownload.getNumber() + 1L, pivotBlockNumber - 1L);

    final DownloadBackwardHeadersStep downloadStep =
        new DownloadBackwardHeadersStep(
            protocolSchedule,
            ethContext,
            syncConfig,
            headerRequestSize,
            metricsSystem,
            anchorForHeaderDownload.getNumber());

    final ImportHeadersStep importHeadersStep =
        new ImportHeadersStep(
            protocolContext.getBlockchain(),
            anchorForHeaderDownload,
            chainState.pivotBlockHeader());

    return PipelineBuilder.createPipelineFrom(
            "backwardHeaderSource",
            headerSource,
            downloaderParallelism,
            metricsSystem.createLabelledCounter(
                BesuMetricCategory.SYNCHRONIZER,
                "backward_header_download_pipeline_processed_total",
                "Number of entries processed by each backward header download pipeline stage",
                "step",
                "action"),
            true,
            "backwardHeaderSync")
        .thenProcessAsyncOrdered(
            "downloadBackwardHeaders", downloadStep, downloaderParallelism * 20)
        .andFinishWith("importHeadersStep", importHeadersStep);
  }

  /**
   * Creates Pipeline 2 with custom start and end blocks: Forward bodies and receipts download from
   * start block to end block. Used for continuing sync to an updated pivot.
   *
   * @param startBlock the block to start from
   * @param pivotHeader the block to end at
   * @param syncState the sync state for reporting progress
   * @return the forward bodies and receipts download pipeline
   */
  public Pipeline<List<BlockHeader>> createForwardBodiesAndReceiptsDownloadPipeline(
      final long startBlock, final BlockHeader pivotHeader, final SyncState syncState) {

    long endBlock = pivotHeader.getNumber();

    final int downloaderParallelism = syncConfig.getDownloaderParallelism();
    final int headerRequestSize = 128; // syncConfig.getDownloaderHeaderRequestSize();

    final MutableBlockchain blockchain = protocolContext.getBlockchain();

    LOG.info(
        "Creating forward bodies and receipts download pipeline: startBlock={}, endBlock={}, parallelism={}, batchSize={}",
        startBlock,
        endBlock,
        downloaderParallelism,
        headerRequestSize);

    final BlockHeaderSource headerSource =
        new BlockHeaderSource(blockchain, startBlock, endBlock, headerRequestSize);

    final DownloadSyncBodiesStep downloadBodiesStep =
        new DownloadSyncBodiesStep(protocolSchedule, ethContext, metricsSystem, syncConfig);

    final DownloadSyncReceiptsStep downloadReceiptsStep =
        new DownloadSyncReceiptsStep(protocolSchedule, ethContext, syncConfig, metricsSystem);

    final ImportSnapSyncBlocksStep importBlocksStep =
        new ImportSnapSyncBlocksStep(
            protocolContext,
            ethContext,
            syncState,
            startBlock,
            pivotHeader,
            syncConfig.getSnapSyncConfiguration().isSnapSyncTransactionIndexingEnabled());

    return PipelineBuilder.createPipelineFrom(
            "forwardHeaderSource",
            headerSource,
            downloaderParallelism,
            metricsSystem.createLabelledCounter(
                BesuMetricCategory.SYNCHRONIZER,
                "forward_bodies_receipts_pipeline_processed_total",
                "Number of entries processed by each forward bodies/receipts pipeline stage",
                "step",
                "action"),
            true,
            "forwardBodiesReceipts")
        .thenProcessAsyncOrdered("downloadBodies", downloadBodiesStep, downloaderParallelism)
        .thenProcessAsyncOrdered("downloadReceipts", downloadReceiptsStep, downloaderParallelism)
        .andFinishWith("importBlocks", importBlocksStep);
  }
}
