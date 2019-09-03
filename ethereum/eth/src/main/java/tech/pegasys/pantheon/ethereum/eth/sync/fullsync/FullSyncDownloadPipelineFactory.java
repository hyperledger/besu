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
import tech.pegasys.pantheon.ethereum.eth.sync.ValidationPolicy;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncTarget;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.metrics.PantheonMetricCategory;
import tech.pegasys.pantheon.plugin.services.MetricsSystem;
import tech.pegasys.pantheon.services.pipeline.Pipeline;
import tech.pegasys.pantheon.services.pipeline.PipelineBuilder;

import java.util.Optional;

public class FullSyncDownloadPipelineFactory<C> implements DownloadPipelineFactory {

  private final SynchronizerConfiguration syncConfig;
  private final ProtocolSchedule<C> protocolSchedule;
  private final ProtocolContext<C> protocolContext;
  private final EthContext ethContext;
  private final MetricsSystem metricsSystem;
  private final ValidationPolicy detachedValidationPolicy =
      () -> HeaderValidationMode.DETACHED_ONLY;
  private final BetterSyncTargetEvaluator betterSyncTargetEvaluator;

  public FullSyncDownloadPipelineFactory(
      final SynchronizerConfiguration syncConfig,
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final MetricsSystem metricsSystem) {
    this.syncConfig = syncConfig;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.metricsSystem = metricsSystem;
    betterSyncTargetEvaluator = new BetterSyncTargetEvaluator(syncConfig, ethContext.getEthPeers());
  }

  @Override
  public Pipeline<?> createDownloadPipelineForSyncTarget(final SyncTarget target) {
    final int downloaderParallelism = syncConfig.getDownloaderParallelism();
    final int headerRequestSize = syncConfig.getDownloaderHeaderRequestSize();
    final int singleHeaderBufferSize = headerRequestSize * downloaderParallelism;
    final CheckpointRangeSource checkpointRangeSource =
        new CheckpointRangeSource(
            new CheckpointHeaderFetcher(
                syncConfig, protocolSchedule, ethContext, Optional.empty(), metricsSystem),
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
    final ExtractTxSignaturesStep extractTxSignaturesStep = new ExtractTxSignaturesStep();
    final FullImportBlockStep<C> importBlockStep =
        new FullImportBlockStep<>(protocolSchedule, protocolContext);

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
        .thenFlatMap("extractTxSignatures", extractTxSignaturesStep, singleHeaderBufferSize)
        .andFinishWith("importBlock", importBlockStep);
  }

  private boolean shouldContinueDownloadingFromPeer(
      final EthPeer peer, final BlockHeader lastCheckpointHeader) {
    final boolean caughtUpToPeer =
        peer.chainState().getEstimatedHeight() <= lastCheckpointHeader.getNumber();
    return !peer.isDisconnected()
        && !caughtUpToPeer
        && !betterSyncTargetEvaluator.shouldSwitchSyncTarget(peer);
  }
}
