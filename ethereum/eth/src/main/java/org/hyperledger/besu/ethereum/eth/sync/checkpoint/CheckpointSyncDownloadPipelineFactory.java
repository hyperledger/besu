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
package org.hyperledger.besu.ethereum.eth.sync.checkpoint;

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
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class CheckpointSyncDownloadPipelineFactory extends FastSyncDownloadPipelineFactory {

  private final CheckPointImporter checkPointImporter;

  public CheckpointSyncDownloadPipelineFactory(
      final SynchronizerConfiguration syncConfig,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final FastSyncState fastSyncState,
      final MetricsSystem metricsSystem) {
    super(syncConfig, protocolSchedule, protocolContext, ethContext, fastSyncState, metricsSystem);
    checkPointImporter =
        new CheckPointImporter(protocolContext, protocolSchedule, ethContext, metricsSystem);
  }

  @Override
  public CompletionStage<Void> startPipeline(
      final EthScheduler scheduler, final SyncState syncState, final SyncTarget target) {
    return downloadCheckPoint(syncState, target)
        .thenCompose(
            syncTarget -> scheduler.startPipeline(createDownloadPipelineForSyncTarget(target)));
  }

  private CompletableFuture<Void> downloadCheckPoint(
      final SyncState syncState, final SyncTarget syncTarget) {
    Checkpoint checkpoint =
        syncState.getCheckpoint().orElseThrow(() -> new RuntimeException("missing checkpoint"));
    if (syncState.getLocalChainHeight() < checkpoint.blockNumber()) {
      return checkPointImporter.importCheckPointBlock(syncTarget, checkpoint);
    } else {
      return CompletableFuture.completedFuture(null);
    }
  }

  @Override
  protected BlockHeader getCommonAncestor(final SyncTarget target) {
    return target
        .peer()
        .getCheckPointHeader()
        .filter(checkpoint -> checkpoint.getNumber() > target.commonAncestor().getNumber())
        .orElse(target.commonAncestor());
  }
}
