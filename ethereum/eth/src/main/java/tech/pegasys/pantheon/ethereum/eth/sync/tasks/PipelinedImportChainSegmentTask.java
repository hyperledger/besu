/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.eth.sync.tasks;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.eth.manager.AbstractEthTask;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.exceptions.InvalidBlockException;
import tech.pegasys.pantheon.ethereum.mainnet.BlockHeaderValidator;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;
import tech.pegasys.pantheon.util.ExceptionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;

import com.google.common.collect.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PipelinedImportChainSegmentTask<C> extends AbstractEthTask<List<Block>> {
  private static final Logger LOG = LogManager.getLogger();

  private final EthContext ethContext;
  private final ProtocolContext<C> protocolContext;
  private final ProtocolSchedule<C> protocolSchedule;
  private final List<Block> importedBlocks = new ArrayList<>();

  // First header is assumed  to already be imported
  private final List<BlockHeader> checkpointHeaders;
  private final int chunksInTotal;
  private int chunksIssued;
  private int chunksCompleted;
  private final int maxActiveChunks;

  private final Deque<CompletableFuture<List<BlockHeader>>> downloadAndValidateHeadersTasks =
      new ConcurrentLinkedDeque<>();
  private final Deque<CompletableFuture<List<Block>>> downloadBodiesTasks =
      new ConcurrentLinkedDeque<>();
  private final Deque<CompletableFuture<List<Block>>> extractTransactionSendersTasks =
      new ConcurrentLinkedDeque<>();
  private final Deque<CompletableFuture<List<Block>>> validateAndImportBlocksTasks =
      new ConcurrentLinkedDeque<>();

  protected PipelinedImportChainSegmentTask(
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final int maxActiveChunks,
      final List<BlockHeader> checkpointHeaders) {
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.checkpointHeaders = checkpointHeaders;
    this.chunksInTotal = checkpointHeaders.size() - 1;
    this.chunksIssued = 0;
    this.chunksCompleted = 0;
    this.maxActiveChunks = maxActiveChunks;
  }

  public static <C> PipelinedImportChainSegmentTask<C> forCheckpoints(
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final int maxActiveChunks,
      final BlockHeader... checkpointHeaders) {
    return forCheckpoints(
        protocolSchedule,
        protocolContext,
        ethContext,
        maxActiveChunks,
        Arrays.asList(checkpointHeaders));
  }

  public static <C> PipelinedImportChainSegmentTask<C> forCheckpoints(
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final int maxActiveChunks,
      final List<BlockHeader> checkpointHeaders) {
    return new PipelinedImportChainSegmentTask<>(
        protocolSchedule, protocolContext, ethContext, maxActiveChunks, checkpointHeaders);
  }

  @Override
  protected void executeTask() {
    LOG.debug(
        "Importing chain segment from {} to {}.",
        firstHeader().getNumber(),
        lastHeader().getNumber());
    for (int i = 0; i < chunksInTotal && i < maxActiveChunks; i++) {
      createNextChunkPipeline();
    }
  }

  private void createNextChunkPipeline() {
    final BlockHeader firstChunkHeader = checkpointHeaders.get(chunksIssued);
    final BlockHeader lastChunkHeader = checkpointHeaders.get(chunksIssued + 1);

    final CompletableFuture<List<BlockHeader>> downloadAndValidateHeadersTask =
        lastDownloadAndValidateHeadersTask()
            .thenCompose((ignore) -> downloadNextHeaders(firstChunkHeader, lastChunkHeader))
            .thenCompose(this::validateHeaders);
    final CompletableFuture<List<Block>> downloadBodiesTask =
        downloadAndValidateHeadersTask
            .thenCombine(lastDownloadBodiesTask(), (headers, ignored) -> headers)
            .thenCompose(this::downloadBlocks);
    final CompletableFuture<List<Block>> extractTransactionSendersTask =
        downloadBodiesTask
            .thenCombine(lastExtractTransactionSendersTasks(), (blocks, ignored) -> blocks)
            .thenCompose(this::extractTransactionSenders);
    final CompletableFuture<List<Block>> validateAndImportBlocksTask =
        extractTransactionSendersTask
            .thenCombine(lastValidateAndImportBlocksTasks(), (blocks, ignored) -> blocks)
            .thenCompose(this::validateAndImportBlocks);
    validateAndImportBlocksTask.whenComplete(this::completeChunkPipelineAndMaybeLaunchNextOne);

    downloadAndValidateHeadersTasks.addLast(downloadAndValidateHeadersTask);
    downloadBodiesTasks.addLast(downloadBodiesTask);
    extractTransactionSendersTasks.addLast(extractTransactionSendersTask);
    validateAndImportBlocksTasks.addLast(validateAndImportBlocksTask);
    chunksIssued++;
  }

  public void completeChunkPipelineAndMaybeLaunchNextOne(
      final List<Block> blocks, final Throwable throwable) {
    if (throwable != null) {
      LOG.warn(
          "Import of chain segment ({} to {}) failed: {}.",
          firstHeader().getNumber(),
          lastHeader().getNumber(),
          ExceptionUtils.rootCause(throwable).getMessage());
      result.get().completeExceptionally(throwable);
    } else {
      importedBlocks.addAll(blocks);
      final BlockHeader firstHeader = blocks.get(0).getHeader();
      final BlockHeader lastHeader = blocks.get(blocks.size() - 1).getHeader();
      chunksCompleted++;
      LOG.debug(
          "Import chain segment from {} to {} succeeded (chunk {}/{}).",
          firstHeader.getNumber(),
          lastHeader.getNumber(),
          chunksCompleted,
          chunksInTotal);
      if (chunksCompleted == chunksInTotal) {
        LOG.info(
            "Completed importing chain segment {} to {}",
            firstHeader().getNumber(),
            lastHeader().getNumber());
        result.get().complete(importedBlocks);
      } else {
        downloadAndValidateHeadersTasks.removeFirst();
        downloadBodiesTasks.removeFirst();
        extractTransactionSendersTasks.removeFirst();
        validateAndImportBlocksTasks.removeFirst();
        if (chunksIssued < chunksInTotal) {
          createNextChunkPipeline();
        }
      }
    }
  }

  private CompletableFuture<List<BlockHeader>> downloadNextHeaders(
      final BlockHeader firstChunkHeader, final BlockHeader lastChunkHeader) {
    // Download the headers we're missing (between first and last)
    LOG.debug(
        "Downloading headers {} to {}",
        firstChunkHeader.getNumber() + 1,
        lastChunkHeader.getNumber());
    final int segmentLength =
        Math.toIntExact(lastChunkHeader.getNumber() - firstChunkHeader.getNumber() - 1);
    if (segmentLength == 0) {
      return CompletableFuture.completedFuture(
          Lists.newArrayList(firstChunkHeader, lastChunkHeader));
    }
    final DownloadHeaderSequenceTask<C> task =
        DownloadHeaderSequenceTask.endingAtHeader(
            protocolSchedule, protocolContext, ethContext, lastChunkHeader, segmentLength);
    return executeSubTask(task::run)
        .thenApply(
            headers -> {
              final List<BlockHeader> finalHeaders = Lists.newArrayList(firstChunkHeader);
              finalHeaders.addAll(headers);
              finalHeaders.add(lastChunkHeader);
              return finalHeaders;
            });
  }

  private CompletableFuture<List<BlockHeader>> validateHeaders(final List<BlockHeader> headers) {
    // First header needs to be validated
    return executeWorkerSubTask(
        ethContext.getScheduler(),
        () -> {
          final CompletableFuture<List<BlockHeader>> result = new CompletableFuture<>();
          final BlockHeader parentHeader = headers.get(0);
          final BlockHeader childHeader = headers.get(1);
          final ProtocolSpec<C> protocolSpec =
              protocolSchedule.getByBlockNumber(childHeader.getNumber());
          final BlockHeaderValidator<C> blockHeaderValidator =
              protocolSpec.getBlockHeaderValidator();
          if (blockHeaderValidator.validateHeader(
              childHeader, parentHeader, protocolContext, HeaderValidationMode.DETACHED_ONLY)) {
            // The first header will be imported by the previous request range.
            result.complete(headers.subList(1, headers.size()));
          } else {
            result.completeExceptionally(
                new InvalidBlockException(
                    "Provided first header does not connect to last header.",
                    parentHeader.getNumber(),
                    parentHeader.getHash()));
          }
          return result;
        });
  }

  private CompletableFuture<List<Block>> downloadBlocks(final List<BlockHeader> headers) {
    LOG.debug(
        "Downloading bodies {} to {}",
        headers.get(0).getNumber(),
        headers.get(headers.size() - 1).getNumber());
    final CompleteBlocksTask<C> task =
        CompleteBlocksTask.forHeaders(protocolSchedule, ethContext, headers);
    return executeSubTask(task::run);
  }

  private CompletableFuture<List<Block>> validateAndImportBlocks(final List<Block> blocks) {
    LOG.debug(
        "Validating and importing {} to {}",
        blocks.get(0).getHeader().getNumber(),
        blocks.get(blocks.size() - 1).getHeader().getNumber());
    final Supplier<CompletableFuture<List<Block>>> task =
        PersistBlockTask.forSequentialBlocks(
            protocolSchedule, protocolContext, blocks, HeaderValidationMode.SKIP_DETACHED);
    return executeWorkerSubTask(ethContext.getScheduler(), task);
  }

  private CompletableFuture<List<Block>> extractTransactionSenders(final List<Block> blocks) {
    LOG.debug(
        "Extracting sender {} to {}",
        blocks.get(0).getHeader().getNumber(),
        blocks.get(blocks.size() - 1).getHeader().getNumber());
    return executeWorkerSubTask(
        ethContext.getScheduler(),
        () -> {
          final CompletableFuture<List<Block>> result = new CompletableFuture<>();
          for (final Block block : blocks) {
            for (final Transaction transaction : block.getBody().getTransactions()) {
              // This method internally performs the transaction sender extraction.
              transaction.getSender();
            }
          }
          result.complete(blocks);
          return result;
        });
  }

  private BlockHeader firstHeader() {
    return checkpointHeaders.get(0);
  }

  private BlockHeader lastHeader() {
    return checkpointHeaders.get(checkpointHeaders.size() - 1);
  }

  private CompletableFuture<List<BlockHeader>> lastDownloadAndValidateHeadersTask() {
    if (downloadAndValidateHeadersTasks.isEmpty()) {
      return CompletableFuture.completedFuture(Collections.emptyList());
    } else {
      return downloadAndValidateHeadersTasks.getLast();
    }
  }

  private CompletableFuture<List<Block>> lastDownloadBodiesTask() {
    if (downloadBodiesTasks.isEmpty()) {
      return CompletableFuture.completedFuture(Lists.newArrayList());
    } else {
      return downloadBodiesTasks.getLast();
    }
  }

  private CompletableFuture<List<Block>> lastValidateAndImportBlocksTasks() {
    if (validateAndImportBlocksTasks.isEmpty()) {
      return CompletableFuture.completedFuture(Lists.newArrayList());
    } else {
      return validateAndImportBlocksTasks.getLast();
    }
  }

  private CompletableFuture<List<Block>> lastExtractTransactionSendersTasks() {
    if (extractTransactionSendersTasks.isEmpty()) {
      return CompletableFuture.completedFuture(Lists.newArrayList());
    } else {
      return extractTransactionSendersTasks.getLast();
    }
  }
}
