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
import static tech.pegasys.pantheon.util.FutureUtils.completedExceptionally;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockImporter;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.sync.BlockHandler;
import tech.pegasys.pantheon.ethereum.eth.sync.ValidationPolicy;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.CompleteBlocksTask;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.GetReceiptsForHeadersTask;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.exceptions.InvalidBlockException;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;
import tech.pegasys.pantheon.metrics.MetricsSystem;

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
  private final MetricsSystem metricsSystem;
  private final ValidationPolicy validationPolicy;

  public FastSyncBlockHandler(
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final MetricsSystem metricsSystem,
      final ValidationPolicy validationPolicy) {
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.metricsSystem = metricsSystem;
    this.validationPolicy = validationPolicy;
  }

  @Override
  public CompletableFuture<List<BlockWithReceipts>> downloadBlocks(
      final List<BlockHeader> headers) {
    return downloadBodies(headers)
        .thenCombine(downloadReceipts(headers), this::combineBlocksAndReceipts);
  }

  private CompletableFuture<List<Block>> downloadBodies(final List<BlockHeader> headers) {
    return CompleteBlocksTask.forHeaders(protocolSchedule, ethContext, headers, metricsSystem)
        .run();
  }

  private CompletableFuture<Map<BlockHeader, List<TransactionReceipt>>> downloadReceipts(
      final List<BlockHeader> headers) {
    return GetReceiptsForHeadersTask.forHeaders(ethContext, headers, metricsSystem).run();
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
          validationPolicy.getValidationModeForNextBlock())) {
        return invalidBlockFailure(block);
      }
    }
    return CompletableFuture.completedFuture(blocksWithReceipts);
  }

  private CompletableFuture<List<BlockWithReceipts>> invalidBlockFailure(final Block block) {
    return completedExceptionally(
        new InvalidBlockException(
            "Failed to import block", block.getHeader().getNumber(), block.getHash()));
  }

  private BlockImporter<C> getBlockImporter(final BlockWithReceipts blockWithReceipt) {
    final ProtocolSpec<C> protocolSpec =
        protocolSchedule.getByBlockNumber(blockWithReceipt.getHeader().getNumber());

    return protocolSpec.getBlockImporter();
  }

  @Override
  public long extractBlockNumber(final BlockWithReceipts blockWithReceipt) {
    return blockWithReceipt.getHeader().getNumber();
  }

  @Override
  public Hash extractBlockHash(final BlockWithReceipts block) {
    return block.getHash();
  }

  @Override
  public CompletableFuture<Void> executeParallelCalculations(final List<BlockWithReceipts> blocks) {
    return CompletableFuture.completedFuture(null);
  }
}
