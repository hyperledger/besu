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
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractGetHeadersFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.GetHeadersFromPeerByHashTask;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.checkpoint.Checkpoint;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class CheckPointDownloadHeaderStep {

  private final ProtocolSchedule protocolSchedule;
  private final EthContext ethContext;
  private final Checkpoint checkpoint;
  private final MetricsSystem metricsSystem;

  public CheckPointDownloadHeaderStep(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final Checkpoint checkpoint,
      final MetricsSystem metricsSystem) {
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
    this.checkpoint = checkpoint;
    this.metricsSystem = metricsSystem;
  }

  public CompletableFuture<Optional<BlockHeader>> downloadHeader(final Hash hash) {
    final CompletableFuture<Optional<BlockHeader>> future = new CompletableFuture<>();
    final AbstractGetHeadersFromPeerTask getHeadersFromPeerTask =
        GetHeadersFromPeerByHashTask.forSingleHash(
            protocolSchedule, ethContext, hash, checkpoint.blockNumber(), metricsSystem);
    getHeadersFromPeerTask
        .run()
        .whenComplete(
            (listPeerTaskResult, throwable) -> {
              if (throwable != null || listPeerTaskResult.getResult().size() != 1) {
                future.complete(Optional.empty());
              } else {
                future.complete(Optional.of(listPeerTaskResult.getResult().get(0)));
              }
            });
    return future;
  }
}
