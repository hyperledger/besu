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
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractPeerTask.PeerTaskResult;
import org.hyperledger.besu.ethereum.eth.manager.task.GetBlockFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.GetReceiptsFromPeerTask;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.checkpoint.Checkpoint;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class CheckpointDownloadBlockStep {

  private final ProtocolSchedule protocolSchedule;
  private final EthContext ethContext;
  private final Checkpoint checkpoint;
  private final MetricsSystem metricsSystem;

  public CheckpointDownloadBlockStep(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final Checkpoint checkpoint,
      final MetricsSystem metricsSystem) {
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
    this.checkpoint = checkpoint;
    this.metricsSystem = metricsSystem;
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
    final GetReceiptsFromPeerTask getReceiptsFromPeerTask =
        GetReceiptsFromPeerTask.forHeaders(ethContext, List.of(block.getHeader()), metricsSystem);
    return getReceiptsFromPeerTask
        .run()
        .thenCompose(
            receiptTaskResult -> {
              final Optional<List<TransactionReceipt>> transactionReceipts =
                  Optional.ofNullable(receiptTaskResult.getResult().get(block.getHeader()));
              return CompletableFuture.completedFuture(
                  transactionReceipts.map(receipts -> new BlockWithReceipts(block, receipts)));
            })
        .exceptionally(throwable -> Optional.empty());
  }
}
