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
import org.hyperledger.besu.ethereum.eth.manager.peertask.InvalidPeerTaskResponseException;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetBodiesFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetHeadersFromPeerTask.Direction;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetReceiptsFromPeerTask;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.checkpoint.Checkpoint;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class CheckpointDownloadBlockStep {

  private final ProtocolSchedule protocolSchedule;
  private final EthContext ethContext;
  private final Checkpoint checkpoint;

  public CheckpointDownloadBlockStep(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final Checkpoint checkpoint) {
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
    this.checkpoint = checkpoint;
  }

  public CompletableFuture<Optional<BlockWithReceipts>> downloadBlock(final Hash hash) {
    GetHeadersFromPeerTask headersTask =
        new GetHeadersFromPeerTask(
            hash, checkpoint.blockNumber(), 1, 0, Direction.FORWARD, protocolSchedule);
    return ethContext
        .getScheduler()
        .scheduleServiceTask(
            () -> {
              PeerTaskExecutorResult<List<BlockHeader>> executorResult =
                  ethContext.getPeerTaskExecutor().execute(headersTask);
              if (executorResult.result().isEmpty()
                  || executorResult.responseCode() != PeerTaskExecutorResponseCode.SUCCESS) {
                return CompletableFuture.failedFuture(new InvalidPeerTaskResponseException());
              } else {
                return CompletableFuture.completedFuture(executorResult.result().get().getFirst());
              }
            })
        .thenApply(
            (blockHeader) -> {
              GetBodiesFromPeerTask bodiesTask =
                  new GetBodiesFromPeerTask(List.of(blockHeader), protocolSchedule);
              PeerTaskExecutorResult<List<Block>> executorResult =
                  ethContext.getPeerTaskExecutor().execute(bodiesTask);
              if (executorResult.result().isEmpty()
                  || executorResult.responseCode() != PeerTaskExecutorResponseCode.SUCCESS) {
                throw new RuntimeException(new InvalidPeerTaskResponseException());
              } else {
                return executorResult.result().get().getFirst();
              }
            })
        .thenApply(this::downloadReceipts);
  }

  private Optional<BlockWithReceipts> downloadReceipts(final Block block) {
    GetReceiptsFromPeerTask task =
        new GetReceiptsFromPeerTask(List.of(block.getHeader()), protocolSchedule);
    PeerTaskExecutorResult<Map<BlockHeader, List<TransactionReceipt>>> executorResult =
        ethContext.getPeerTaskExecutor().execute(task);

    if (executorResult.responseCode() == PeerTaskExecutorResponseCode.SUCCESS) {
      List<TransactionReceipt> transactionReceipts =
          executorResult
              .result()
              .map((map) -> map.get(block.getHeader()))
              .orElseThrow(
                  () -> new IllegalStateException("PeerTask response code was success, but empty"));
      if (block.getBody().getTransactions().size() != transactionReceipts.size()) {
        throw new IllegalStateException(
            "PeerTask response code was success, but incorrect number of receipts returned");
      }
      BlockWithReceipts blockWithReceipts = new BlockWithReceipts(block, transactionReceipts);
      return Optional.of(blockWithReceipts);
    } else {
      return Optional.empty();
    }
  }
}
