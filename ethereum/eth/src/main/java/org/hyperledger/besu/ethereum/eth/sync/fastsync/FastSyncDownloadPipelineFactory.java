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
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import static org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode.DETACHED_ONLY;
import static org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode.LIGHT_DETACHED_ONLY;

import org.hyperledger.besu.ethereum.ConsensusContext;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.sync.DownloadHeadersStep;
import org.hyperledger.besu.ethereum.eth.sync.DownloadPipelineFactory;
import org.hyperledger.besu.ethereum.eth.sync.DownloadSyncBodiesStep;
import org.hyperledger.besu.ethereum.eth.sync.SavePreMergeHeadersStep;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.ValidationPolicy;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.checkpoint.Checkpoint;
import org.hyperledger.besu.ethereum.eth.sync.fullsync.SyncTerminationCondition;
import org.hyperledger.besu.ethereum.eth.sync.range.RangeHeadersFetcher;
import org.hyperledger.besu.ethereum.eth.sync.range.RangeHeadersValidationStep;
import org.hyperledger.besu.ethereum.eth.sync.range.SyncTargetRange;
import org.hyperledger.besu.ethereum.eth.sync.range.SyncTargetRangeSource;
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

public class FastSyncDownloadPipelineFactory implements DownloadPipelineFactory {
  private static final Logger LOG = LoggerFactory.getLogger(FastSyncDownloadPipelineFactory.class);

  protected final SynchronizerConfiguration syncConfig;
  protected final ProtocolSchedule protocolSchedule;
  protected final ProtocolContext protocolContext;
  protected final EthContext ethContext;
  protected final FastSyncState fastSyncState;
  protected final MetricsSystem metricsSystem;
  protected final FastSyncValidationPolicy detachedValidationPolicy;
  protected final ValidationPolicy downloadHeaderValidation;

  public FastSyncDownloadPipelineFactory(
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

  @Override
  public CompletionStage<Void> startPipeline(
      final EthScheduler scheduler,
      final SyncState syncState,
      final SyncTarget syncTarget,
      final Pipeline<?> pipeline) {
    return scheduler.startPipeline(pipeline);
  }

  @Override
  public Pipeline<SyncTargetRange> createDownloadPipelineForSyncTarget(
      final SyncState syncState, final SyncTarget target) {
    final int downloaderParallelism = syncConfig.getDownloaderParallelism();
    final int headerRequestSize = syncConfig.getDownloaderHeaderRequestSize();
    final int singleHeaderBufferSize = headerRequestSize * downloaderParallelism;

    final SyncTargetRangeSource checkpointRangeSource =
        new SyncTargetRangeSource(
            new RangeHeadersFetcher(
                syncConfig, protocolSchedule, ethContext, fastSyncState, metricsSystem),
            this::shouldContinueDownloadingFromPeer,
            ethContext.getScheduler(),
            target.peer(),
            getCommonAncestor(target),
            syncConfig.getDownloaderCheckpointRetries(),
            SyncTerminationCondition.never());
    final DownloadHeadersStep downloadHeadersStep =
        new DownloadHeadersStep(
            protocolSchedule,
            protocolContext,
            ethContext,
            downloadHeaderValidation,
            syncConfig,
            headerRequestSize,
            metricsSystem);
    final RangeHeadersValidationStep validateHeadersJoinUpStep = new RangeHeadersValidationStep();
    final SavePreMergeHeadersStep savePreMergeHeadersStep =
        new SavePreMergeHeadersStep(
            protocolContext.getBlockchain(),
            protocolSchedule.anyMatch(s -> s.spec().isPoS()),
            getCheckpointBlockNumber(syncState),
            protocolContext.safeConsensusContext(ConsensusContext.class));
    final DownloadSyncBodiesStep downloadSyncBodiesStep =
        new DownloadSyncBodiesStep(protocolSchedule, ethContext, metricsSystem, syncConfig);
    final DownloadSyncReceiptsStep downloadSyncReceiptsStep =
        new DownloadSyncReceiptsStep(protocolSchedule, ethContext, syncConfig, metricsSystem);
    final ImportSyncBlocksStep importSyncBlocksStep =
        new ImportSyncBlocksStep(
            protocolSchedule,
            protocolContext,
            ethContext,
            fastSyncState.getPivotBlockHeader().get(),
            syncConfig.getSnapSyncConfiguration().isSnapSyncTransactionIndexingEnabled());

    return PipelineBuilder.createPipelineFrom(
            "fetchCheckpoints",
            checkpointRangeSource,
            downloaderParallelism,
            metricsSystem.createLabelledCounter(
                BesuMetricCategory.SYNCHRONIZER,
                "chain_download_pipeline_processed_total",
                "Number of entries process by each chain download pipeline stage",
                "step",
                "action"),
            true,
            "fastSync")
        .thenProcessAsyncOrdered("downloadHeaders", downloadHeadersStep, downloaderParallelism)
        .thenFlatMap("validateHeadersJoin", validateHeadersJoinUpStep, singleHeaderBufferSize)
        .thenFlatMap("savePreMergeHeadersStep", savePreMergeHeadersStep, singleHeaderBufferSize)
        .inBatches(headerRequestSize)
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

  private long getCheckpointBlockNumber(final SyncState syncState) {
    return syncConfig.isSnapSyncSavePreCheckpointHeadersOnlyEnabled()
        ? syncState.getCheckpoint().map(Checkpoint::blockNumber).orElse(0L)
        : 0L;
  }

  /**
   * Creates Pipeline 1: Backward header download from pivot block to genesis. Downloads headers in
   * reverse direction, validates boundaries, and stores in database. Supports out-of-order parallel
   * execution with resume capability.
   *
   * @param fastSyncState the fast sync state for tracking progress
   * @param fastSyncStateStorage the storage for persisting progress
   * @return the backward header download pipeline
   */
  public Pipeline<Long> createBackwardHeaderDownloadPipeline(
      final FastSyncState fastSyncState, final FastSyncStateStorage fastSyncStateStorage) {
    final int downloaderParallelism = syncConfig.getDownloaderParallelism();
    final int headerRequestSize = syncConfig.getDownloaderHeaderRequestSize();

    LOG.debug(
        "Creating backward header download pipeline: pivot={}, parallelism={}, batchSize={}",
        fastSyncState.getPivotBlockHeader().get(),
        downloaderParallelism,
        headerRequestSize);

    final BackwardHeaderSource headerSource =
        new BackwardHeaderSource(
            headerRequestSize,
            ethContext,
            protocolSchedule,
            metricsSystem,
            protocolContext.getBlockchain(),
            fastSyncState);

    final DownloadBackwardHeadersStep downloadStep =
        new DownloadBackwardHeadersStep(
            protocolSchedule, ethContext, syncConfig, headerRequestSize, metricsSystem);

    final ImportHeadersStep importHeadersStep =
        new ImportHeadersStep(protocolContext.getBlockchain(), 0L, fastSyncState);
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
   * Creates Pipeline 2: Forward bodies and receipts download from start block to pivot block. Reads
   * stored headers from database and downloads corresponding bodies and receipts.
   *
   * @param pivotBlockNumber the pivot block number (end of range)
   * @param syncState the sync state to use for determining the checkpoint block number and
   *     reporting progress
   * @return the forward bodies and receipts download pipeline
   */
  public Pipeline<List<BlockHeader>> createForwardBodiesAndReceiptsDownloadPipeline(
      final long pivotBlockNumber, final SyncState syncState) {
    final int downloaderParallelism = syncConfig.getDownloaderParallelism();
    final int headerRequestSize = syncConfig.getDownloaderHeaderRequestSize();
    final long startBlock = getCheckpointBlockNumber(syncState);

    LOG.info(
        "Creating forward bodies and receipts download pipeline: startBlock={}, pivot={}, parallelism={}, batchSize={}",
        startBlock,
        pivotBlockNumber,
        downloaderParallelism,
        headerRequestSize);

    final BlockHeaderSource headerSource =
        new BlockHeaderSource(
            protocolContext.getBlockchain(), startBlock, pivotBlockNumber, headerRequestSize);

    final DownloadSyncBodiesStep downloadBodiesStep =
        new DownloadSyncBodiesStep(protocolSchedule, ethContext, metricsSystem, syncConfig);

    final DownloadSyncReceiptsStep downloadReceiptsStep =
        new DownloadSyncReceiptsStep(protocolSchedule, ethContext, syncConfig, metricsSystem);

    final ImportSyncBlocksStep importBlocksStep =
        new ImportSyncBlocksStep(
            protocolSchedule,
            protocolContext,
            ethContext,
            syncState,
            startBlock,
            fastSyncState.getPivotBlockHeader().get(),
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
        .thenFlatMap(
            "flattenHeaders",
            headers -> headers.stream(),
            headerRequestSize * downloaderParallelism)
        .inBatches(headerRequestSize)
        .thenProcessAsyncOrdered("downloadBodies", downloadBodiesStep, downloaderParallelism)
        .thenProcessAsyncOrdered("downloadReceipts", downloadReceiptsStep, downloaderParallelism)
        .andFinishWith("importBlocks", importBlocksStep);
  }
}
