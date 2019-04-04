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

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.sync.CheckpointHeaderFetcher;
import tech.pegasys.pantheon.ethereum.eth.sync.CheckpointRangeSource;
import tech.pegasys.pantheon.ethereum.eth.sync.DownloadPipelineFactory;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncTarget;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.metrics.MetricCategory;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.services.pipeline.Pipeline;
import tech.pegasys.pantheon.services.pipeline.PipelineBuilder;

import java.time.Duration;
import java.util.Optional;

public class FastSyncDownloadPipelineFactory implements DownloadPipelineFactory {
  private final SynchronizerConfiguration syncConfig;
  private final ProtocolSchedule<?> protocolSchedule;
  private final EthContext ethContext;
  private final BlockHeader pivotBlockHeader;
  private final MetricsSystem metricsSystem;

  public FastSyncDownloadPipelineFactory(
      final SynchronizerConfiguration syncConfig,
      final ProtocolSchedule<?> protocolSchedule,
      final EthContext ethContext,
      final BlockHeader pivotBlockHeader,
      final MetricsSystem metricsSystem) {
    this.syncConfig = syncConfig;
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
    this.pivotBlockHeader = pivotBlockHeader;
    this.metricsSystem = metricsSystem;
  }

  @Override
  public Pipeline<?> createDownloadPipelineForSyncTarget(final SyncTarget target) {
    final int downloaderParallelism = syncConfig.downloaderParallelism();
    final int headerRequestSize = syncConfig.downloaderHeaderRequestSize();
    final int singleHeaderBufferSize = downloaderParallelism * headerRequestSize * 10;
    return PipelineBuilder.createPipelineFrom(
            "fetchCheckpoints",
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
                syncConfig.downloaderCheckpointTimeoutsPermitted(),
                Duration.ofSeconds(5)),
            singleHeaderBufferSize,
            metricsSystem.createLabelledCounter(
                MetricCategory.SYNCHRONIZER,
                "chain_download_pipeline_processed_total",
                "Number of entries process by each chain download pipeline stage",
                "step",
                "action"))
        .andFinishWith("complete", result -> {});
  }

  private boolean shouldContinueDownloadingFromPeer(
      final EthPeer peer, final BlockHeader lastCheckpointHeader) {
    return !peer.isDisconnected()
        && lastCheckpointHeader.getNumber() < pivotBlockHeader.getNumber();
  }
}
