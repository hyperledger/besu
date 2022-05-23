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
import static org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode.FULL;
import static org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode.LIGHT;
import static org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode.LIGHT_DETACHED_ONLY;
import static org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode.LIGHT_SKIP_DETACHED;
import static org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode.SKIP_DETACHED;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.sync.CheckpointHeaderFetcher;
import org.hyperledger.besu.ethereum.eth.sync.CheckpointHeaderValidationStep;
import org.hyperledger.besu.ethereum.eth.sync.CheckpointRange;
import org.hyperledger.besu.ethereum.eth.sync.CheckpointRangeSource;
import org.hyperledger.besu.ethereum.eth.sync.DownloadBodiesStep;
import org.hyperledger.besu.ethereum.eth.sync.DownloadHeadersStep;
import org.hyperledger.besu.ethereum.eth.sync.DownloadPipelineFactory;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.fullsync.SyncTerminationCondition;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncTarget;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.services.pipeline.Pipeline;
import org.hyperledger.besu.services.pipeline.PipelineBuilder;

import java.util.Optional;

public class FastSyncDownloadPipelineFactory implements DownloadPipelineFactory {
  private final SynchronizerConfiguration syncConfig;
  private final ProtocolSchedule protocolSchedule;
  private final ProtocolContext protocolContext;
  private final EthContext ethContext;
  private final PivotProvider pivotProvider;
  private final MetricsSystem metricsSystem;
  private final FastSyncValidationPolicy attachedValidationPolicy;
  private final FastSyncValidationPolicy detachedValidationPolicy;
  private final FastSyncValidationPolicy ommerValidationPolicy;

  public FastSyncDownloadPipelineFactory(
      final SynchronizerConfiguration syncConfig,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final MetricsSystem metricsSystem,
      final PivotProvider pivotProvider) {
    this.syncConfig = syncConfig;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.pivotProvider = pivotProvider;
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
  public Pipeline<CheckpointRange> createDownloadPipelineForSyncTarget(final SyncTarget target) {
    final int downloaderParallelism = syncConfig.getDownloaderParallelism();
    final int headerRequestSize = syncConfig.getDownloaderHeaderRequestSize();
    final int singleHeaderBufferSize = headerRequestSize * downloaderParallelism;
    final CheckpointRangeSource checkpointRangeSource =
        new CheckpointRangeSource(
            new CheckpointHeaderFetcher(
                syncConfig,
                protocolSchedule,
                ethContext,
                metricsSystem,
                Optional.of(pivotProvider)),
            this::shouldContinueDownloadingFromPeer,
            ethContext.getScheduler(),
            target.peer(),
            target.commonAncestor(),
            syncConfig.getDownloaderCheckpointTimeoutsPermitted(),
            SyncTerminationCondition.never());
    final DownloadHeadersStep downloadHeadersStep =
        new DownloadHeadersStep(
            protocolSchedule,
            protocolContext,
            ethContext,
            detachedValidationPolicy,
            headerRequestSize,
            metricsSystem);
    final CheckpointHeaderValidationStep validateHeadersJoinUpStep =
        new CheckpointHeaderValidationStep(
            protocolSchedule, protocolContext, detachedValidationPolicy);
    final DownloadBodiesStep downloadBodiesStep =
        new DownloadBodiesStep(protocolSchedule, ethContext, metricsSystem);
    final DownloadReceiptsStep downloadReceiptsStep =
        new DownloadReceiptsStep(ethContext, metricsSystem);
    final FastImportBlocksStep importBlockStep =
        new FastImportBlocksStep(
            protocolSchedule,
            protocolContext,
            attachedValidationPolicy,
            ommerValidationPolicy,
            ethContext);

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
        .inBatches(headerRequestSize)
        .thenProcessAsyncOrdered("downloadBodies", downloadBodiesStep, downloaderParallelism)
        .thenProcessAsyncOrdered("downloadReceipts", downloadReceiptsStep, downloaderParallelism)
        .andFinishWith("importBlock", importBlockStep);
  }

  private boolean shouldContinueDownloadingFromPeer(
      final EthPeer peer, final BlockHeader lastCheckpointHeader) {
    final BlockHeader pivotBlockHeader = pivotProvider.providePivot();
    return !peer.isDisconnected()
        && lastCheckpointHeader.getNumber() < pivotBlockHeader.getNumber();
  }
}
