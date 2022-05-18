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
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.task.GetBlockFromPeerTask;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.checkpoint.Checkpoint;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncTarget;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class CheckPointImporter {

  private final ProtocolContext protocolContext;
  private final ProtocolSchedule protocolSchedule;
  private final EthContext ethContext;
  private final MetricsSystem metricsSystem;

  public CheckPointImporter(
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final MetricsSystem metricsSystem) {
    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
    this.metricsSystem = metricsSystem;
  }

  public CompletableFuture<Void> importCheckPointBlock(
      final SyncTarget syncTarget, final Checkpoint checkpoint) {
    return downloadCheckPoint(ethContext, metricsSystem, protocolSchedule, syncTarget, checkpoint);
  }

  private CompletableFuture<Void> downloadCheckPoint(
      final EthContext ethContext,
      final MetricsSystem metricsSystem,
      final ProtocolSchedule protocolSchedule,
      final SyncTarget syncTarget,
      final Checkpoint checkpoint) {
    final CompletableFuture<Void> future = new CompletableFuture<>();
    GetBlockFromPeerTask getBlockFromPeerTask =
        GetBlockFromPeerTask.create(
            protocolSchedule,
            ethContext,
            Optional.of(checkpoint.blockHash()),
            checkpoint.blockNumber(),
            metricsSystem);
    getBlockFromPeerTask
        .assignPeer(syncTarget.peer())
        .run()
        .whenComplete(
            (blockPeerTaskResult, throwable) -> {
              if (throwable != null) {
                future.completeExceptionally(throwable);
              } else {
                protocolContext
                    .getBlockchain()
                    .unsafeImportBlock(
                        blockPeerTaskResult.getResult(),
                        Optional.empty(),
                        checkpoint.totalDifficulty(),
                        true);
                future.complete(null);
              }
            });
    return future;
  }
}
