/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.ethereum.eth.sync.checkpointsync;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncDownloadPipelineFactory;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.checkpoint.Checkpoint;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncTarget;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.services.pipeline.Pipeline;
import org.hyperledger.besu.services.pipeline.PipelineBuilder;

import java.util.concurrent.CompletionStage;

public class CheckpointSyncDownloadPipelineFactory extends FastSyncDownloadPipelineFactory {

  public CheckpointSyncDownloadPipelineFactory(
      final SynchronizerConfiguration syncConfig,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final FastSyncState fastSyncState,
      final MetricsSystem metricsSystem) {
    super(syncConfig, protocolSchedule, protocolContext, ethContext, fastSyncState, metricsSystem);
  }

  @Override
  public CompletionStage<Void> startPipeline(
      final EthScheduler scheduler,
      final SyncState syncState,
      final SyncTarget syncTarget,
      final Pipeline<?> pipeline) {
    return scheduler
        .startPipeline(createDownloadCheckPointPipeline(syncState, syncTarget))
        .thenCompose(unused -> scheduler.startPipeline(pipeline));
  }

  protected Pipeline<Hash> createDownloadCheckPointPipeline(
      final SyncState syncState, final SyncTarget target) {

    final Checkpoint checkpoint = syncState.getCheckpoint().orElseThrow();

    final CheckpointSource checkPointSource =
        new CheckpointSource(
            syncState,
            target.peer(),
            protocolSchedule.getByBlockNumber(checkpoint.blockNumber()).getBlockHeaderFunctions());

    final CheckpointBlockImportStep checkPointBlockImportStep =
        new CheckpointBlockImportStep(
            checkPointSource, checkpoint, protocolContext.getBlockchain());

    final CheckpointDownloadBlockStep checkPointDownloadBlockStep =
        new CheckpointDownloadBlockStep(protocolSchedule, ethContext, checkpoint, metricsSystem);

    return PipelineBuilder.createPipelineFrom(
            "fetchCheckpoints",
            checkPointSource,
            1,
            metricsSystem.createLabelledCounter(
                BesuMetricCategory.SYNCHRONIZER,
                "chain_download_pipeline_processed_total",
                "Number of header process by each chain download pipeline stage",
                "step",
                "action"),
            true,
            "checkpointSync")
        .thenProcessAsyncOrdered("downloadBlock", checkPointDownloadBlockStep::downloadBlock, 1)
        .andFinishWith("importBlock", checkPointBlockImportStep);
  }

  @Override
  protected BlockHeader getCommonAncestor(final SyncTarget target) {
    return target
        .peer()
        .getCheckpointHeader()
        .filter(checkpoint -> checkpoint.getNumber() > target.commonAncestor().getNumber())
        .orElse(target.commonAncestor());
  }
}
