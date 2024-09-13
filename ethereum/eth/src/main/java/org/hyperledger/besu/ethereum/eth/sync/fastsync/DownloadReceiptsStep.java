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

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetReceiptsFromPeerTask;
import org.hyperledger.besu.util.FutureUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class DownloadReceiptsStep
    implements Function<List<Block>, CompletableFuture<List<BlockWithReceipts>>> {
  private final PeerTaskExecutor peerTaskExecutor;

  public DownloadReceiptsStep(final PeerTaskExecutor peerTaskExecutor) {
    this.peerTaskExecutor = peerTaskExecutor;
  }

  @Override
  public CompletableFuture<List<BlockWithReceipts>> apply(final List<Block> blocks) {
    final List<BlockHeader> headers = blocks.stream().map(Block::getHeader).collect(toList());
    final CompletableFuture<PeerTaskExecutorResult<Map<BlockHeader, List<TransactionReceipt>>>>
        getReceipts = peerTaskExecutor.executeAsync(new GetReceiptsFromPeerTask(headers));
    final CompletableFuture<List<BlockWithReceipts>> combineWithBlocks =
        getReceipts.thenApply(
            receiptsByHeader -> {
              if (receiptsByHeader.getResponseCode() == PeerTaskExecutorResponseCode.SUCCESS
                  && receiptsByHeader.getResult().isPresent()) {
                return combineBlocksAndReceipts(blocks, receiptsByHeader.getResult().get());
              } else {
                throw new RuntimeException("Unable to get receipts for blocks");
              }
            });
    FutureUtils.propagateCancellation(combineWithBlocks, getReceipts);
    return combineWithBlocks;
  }

  private List<BlockWithReceipts> combineBlocksAndReceipts(
      final List<Block> blocks, final Map<BlockHeader, List<TransactionReceipt>> receiptsByHeader) {
    return blocks.stream()
        .map(
            block -> {
              final List<TransactionReceipt> receipts =
                  receiptsByHeader.getOrDefault(block.getHeader(), emptyList());
              return new BlockWithReceipts(block, receipts);
            })
        .collect(toList());
  }
}
