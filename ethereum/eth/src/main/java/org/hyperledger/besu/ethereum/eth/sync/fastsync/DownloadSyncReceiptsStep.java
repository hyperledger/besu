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
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.SyncBlock;
import org.hyperledger.besu.ethereum.core.SyncBlockWithReceipts;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetReceiptsFromPeerTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class DownloadSyncReceiptsStep
    implements Function<List<SyncBlock>, CompletableFuture<List<SyncBlockWithReceipts>>> {

  private final ProtocolSchedule protocolSchedule;
  private final EthContext ethContext;

  public DownloadSyncReceiptsStep(
      final ProtocolSchedule protocolSchedule, final EthContext ethContext) {
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
  }

  @Override
  public CompletableFuture<List<SyncBlockWithReceipts>> apply(final List<SyncBlock> blocks) {
    final List<BlockHeader> headers = blocks.stream().map(SyncBlock::getHeader).collect(toList());
    return ethContext
        .getScheduler()
        .scheduleServiceTask(() -> getReceiptsWithPeerTaskSystem(headers))
        .thenApply((receipts) -> combineSyncBlocksAndReceipts(blocks, receipts));
  }

  private CompletableFuture<Map<BlockHeader, List<TransactionReceipt>>>
      getReceiptsWithPeerTaskSystem(final List<BlockHeader> headers) {
    Map<BlockHeader, List<TransactionReceipt>> getReceipts = new HashMap<>();
    do {
      GetReceiptsFromPeerTask task = new GetReceiptsFromPeerTask(headers, protocolSchedule);
      PeerTaskExecutorResult<Map<BlockHeader, List<TransactionReceipt>>> getReceiptsResult =
          ethContext.getPeerTaskExecutor().execute(task);
      if (getReceiptsResult.responseCode() == PeerTaskExecutorResponseCode.SUCCESS
          && getReceiptsResult.result().isPresent()) {
        Map<BlockHeader, List<TransactionReceipt>> taskResult = getReceiptsResult.result().get();
        taskResult
            .keySet()
            .forEach(
                (blockHeader) ->
                    getReceipts.merge(
                        blockHeader,
                        taskResult.get(blockHeader),
                        (initialReceipts, newReceipts) -> {
                          throw new IllegalStateException(
                              "Unexpectedly got receipts for block header already populated!");
                        }));
        // remove all the headers we found receipts for
        headers.removeAll(getReceipts.keySet());
      }
      // repeat until all headers have receipts
    } while (!headers.isEmpty());
    return CompletableFuture.completedFuture(getReceipts);
  }

  private List<SyncBlockWithReceipts> combineSyncBlocksAndReceipts(
      final List<SyncBlock> blocks,
      final Map<BlockHeader, List<TransactionReceipt>> receiptsByHeader) {
    return blocks.stream()
        .map(
            block -> {
              final List<TransactionReceipt> receipts =
                  receiptsByHeader.getOrDefault(block.getHeader(), emptyList());
              return new SyncBlockWithReceipts(block, receipts);
            })
        .toList();
  }
}
