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
package org.hyperledger.besu.ethereum.eth.sync.checkpointsync;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetReceiptsFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractPeerTask.PeerTaskResult;
import org.hyperledger.besu.ethereum.eth.manager.task.GetBlockFromPeerTask;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.checkpoint.Checkpoint;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class CheckpointDownloadBlockStep {

  private final ProtocolSchedule protocolSchedule;
  private final EthContext ethContext;
  private final Checkpoint checkpoint;
  private final MetricsSystem metricsSystem;
  private final PeerTaskExecutor peerTaskExecutor;

  public CheckpointDownloadBlockStep(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final Checkpoint checkpoint,
      final MetricsSystem metricsSystem,
      final PeerTaskExecutor peerTaskExecutor) {
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
    this.checkpoint = checkpoint;
    this.metricsSystem = metricsSystem;
    this.peerTaskExecutor = peerTaskExecutor;
  }

  public CompletableFuture<Optional<BlockWithReceipts>> downloadBlock(final Hash hash) {
    final GetBlockFromPeerTask getBlockFromPeerTask =
        GetBlockFromPeerTask.create(
            protocolSchedule,
            ethContext,
            Optional.of(hash),
            checkpoint.blockNumber(),
            metricsSystem);
    return getBlockFromPeerTask
        .run()
        .thenCompose(this::downloadReceipts)
        .exceptionally(throwable -> Optional.empty());
  }

  private CompletableFuture<Optional<BlockWithReceipts>> downloadReceipts(
      final PeerTaskResult<Block> peerTaskResult) {
    final Block block = peerTaskResult.getResult();
    GetReceiptsFromPeerTask getReceiptsFromPeerTask =
        new GetReceiptsFromPeerTask(List.of(block.getHeader()));
    PeerTaskExecutorResult<Map<BlockHeader, List<TransactionReceipt>>> getReceiptsFromPeerResult =
        peerTaskExecutor.execute(getReceiptsFromPeerTask);

    CompletableFuture<Optional<BlockWithReceipts>> result = new CompletableFuture<>();
    if (getReceiptsFromPeerResult.getResponseCode() == PeerTaskExecutorResponseCode.SUCCESS) {
      List<TransactionReceipt> transactionReceipts =
          getReceiptsFromPeerResult
              .getResult()
              .map((map) -> map.get(block.getHeader()))
              .orElseThrow(
                  () -> new IllegalStateException("PeerTask response code was success, but empty"));
      BlockWithReceipts blockWithReceipts = new BlockWithReceipts(block, transactionReceipts);
      result.complete(Optional.of(blockWithReceipts));
    } else {
      result.complete(Optional.empty());
    }
    return result;
  }
}
