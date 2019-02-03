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

import static java.util.Collections.emptyList;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockImporter;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;
import tech.pegasys.pantheon.ethereum.eth.manager.AbstractPeerTask.PeerTaskResult;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.CompleteBlocksTask;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.GetReceiptsFromPeerTask;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.PipelinedImportChainSegmentTask.BlockHandler;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.exceptions.InvalidBlockException;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.OperationTimer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FastSyncBlockHandler<C> implements BlockHandler<BlockWithReceipts> {
  private static final Logger LOG = LogManager.getLogger();

  private final ProtocolSchedule<C> protocolSchedule;
  private final ProtocolContext<C> protocolContext;
  private final EthContext ethContext;
  private final LabelledMetric<OperationTimer> ethTasksTimer;

  public FastSyncBlockHandler(
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final LabelledMetric<OperationTimer> ethTasksTimer) {
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.ethTasksTimer = ethTasksTimer;
  }

  @Override
  public CompletableFuture<List<BlockWithReceipts>> downloadBlocks(
      final List<BlockHeader> headers) {
    return downloadBodies(headers)
        .thenCombine(downloadReceipts(headers), this::combineBlocksAndReceipts);
  }

  private CompletableFuture<List<Block>> downloadBodies(final List<BlockHeader> headers) {
    return CompleteBlocksTask.forHeaders(protocolSchedule, ethContext, headers, ethTasksTimer)
        .run();
  }

  private CompletableFuture<Map<BlockHeader, List<TransactionReceipt>>> downloadReceipts(
      final List<BlockHeader> headers) {
    return GetReceiptsFromPeerTask.forHeaders(ethContext, headers, ethTasksTimer)
        .run()
        .thenApply(PeerTaskResult::getResult);
  }

  private List<BlockWithReceipts> combineBlocksAndReceipts(
      final List<Block> blocks, final Map<BlockHeader, List<TransactionReceipt>> receiptsByHeader) {
    return blocks
        .stream()
        .map(
            block -> {
              final List<TransactionReceipt> receipts =
                  receiptsByHeader.getOrDefault(block.getHeader(), emptyList());
              return new BlockWithReceipts(block, receipts);
            })
        .collect(Collectors.toList());
  }

  @Override
  public CompletableFuture<List<BlockWithReceipts>> validateAndImportBlocks(
      final List<BlockWithReceipts> blocksWithReceipts) {
    LOG.debug(
        "Storing blocks {} to {}",
        blocksWithReceipts.get(0).getHeader().getNumber(),
        blocksWithReceipts.get(blocksWithReceipts.size() - 1).getHeader().getNumber());

    for (final BlockWithReceipts blockWithReceipt : blocksWithReceipts) {
      final BlockImporter<C> blockImporter = getBlockImporter(blockWithReceipt);
      final Block block = blockWithReceipt.getBlock();
      if (!blockImporter.fastImportBlock(
          protocolContext,
          block,
          blockWithReceipt.getReceipts(),
          HeaderValidationMode.LIGHT_SKIP_DETACHED)) {
        return invalidBlockFailure(block);
      }
    }
    return CompletableFuture.completedFuture(blocksWithReceipts);
  }

  private CompletableFuture<List<BlockWithReceipts>> invalidBlockFailure(final Block block) {
    final CompletableFuture<List<BlockWithReceipts>> result = new CompletableFuture<>();
    result.completeExceptionally(
        new InvalidBlockException(
            "Failed to import block", block.getHeader().getNumber(), block.getHash()));
    return result;
  }

  private BlockImporter<C> getBlockImporter(final BlockWithReceipts blockWithReceipt) {
    final ProtocolSpec<C> protocolSpec =
        protocolSchedule.getByBlockNumber(blockWithReceipt.getHeader().getNumber());

    return protocolSpec.getBlockImporter();
  }
}
