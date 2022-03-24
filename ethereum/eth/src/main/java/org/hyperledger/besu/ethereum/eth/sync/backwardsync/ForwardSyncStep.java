/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.eth.sync.backwardsync;

import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.infoLambda;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.GetBlockFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.GetBodiesFromPeerTask;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForwardSyncStep {

  private static final Logger LOG = LoggerFactory.getLogger(ForwardSyncStep.class);
  private final BackwardSyncContext context;
  private final BackwardChain backwardChain;

  public ForwardSyncStep(final BackwardSyncContext context, final BackwardChain backwardChain) {
    this.context = context;
    this.backwardChain = backwardChain;
  }

  public CompletableFuture<Void> executeAsync() {
    return CompletableFuture.supplyAsync(() -> returnFirstNUnknownHeaders(null))
        .thenCompose(this::possibleRequestBodies)
        .thenApply(this::processKnownAncestors)
        .thenCompose(context::executeNextStep);
  }

  @VisibleForTesting
  protected Void processKnownAncestors(final Void unused) {
    while (backwardChain.getFirstAncestorHeader().isPresent()) {
      BlockHeader header = backwardChain.getFirstAncestorHeader().orElseThrow();
      if (context.getProtocolContext().getBlockchain().contains(header.getHash())) {
        debugLambda(
            LOG,
            "Block {} is already imported, we can ignore it for the sync process",
            () -> header.getHash().toHexString());
        backwardChain.dropFirstHeader();
      } else if (context.getProtocolContext().getBlockchain().contains(header.getParentHash())
          && backwardChain.isTrusted(header.getHash())) {
        debugLambda(
            LOG,
            "Importing trusted block {}({})",
            header::getNumber,
            () -> header.getHash().toHexString());
        saveBlock(backwardChain.getTrustedBlock(header.getHash()));
      } else {
        debugLambda(LOG, "First unprocessed header is {}", header::getNumber);
        return null;
      }
    }
    return null;
  }

  @VisibleForTesting
  protected List<BlockHeader> returnFirstNUnknownHeaders(final Void unused) {
    while (backwardChain.getFirstAncestorHeader().isPresent()) {
      BlockHeader header = backwardChain.getFirstAncestorHeader().orElseThrow();
      if (context.getProtocolContext().getBlockchain().contains(header.getHash())) {
        debugLambda(
            LOG,
            "Block {}({}) is already imported, we can ignore it for the sync process",
            header::getNumber,
            () -> header.getHash().toHexString());
        backwardChain.dropFirstHeader();
      } else if (backwardChain.isTrusted(header.getHash())) {
        debugLambda(
            LOG,
            "Block {} was added by consensus layer, we can trust it and should therefore import it.",
            () -> header.getHash().toHexString());
        saveBlock(backwardChain.getTrustedBlock(header.getHash()));
      } else {
        return backwardChain.getFirstNAncestorHeaders(context.getBatchSize());
      }
    }
    return Collections.emptyList();
  }

  @VisibleForTesting
  public CompletableFuture<Void> possibleRequestBlock(final BlockHeader blockHeader) {
    if (blockHeader == null) {
      return CompletableFuture.completedFuture(null);
    } else {
      debugLambda(
          LOG,
          "Requesting body for {} ({})",
          blockHeader::getNumber,
          () -> blockHeader.getHash().toHexString());
      return requestBlock(blockHeader).thenApply(this::saveBlock);
    }
  }

  @VisibleForTesting
  public CompletableFuture<Void> possibleRequestBodies(final List<BlockHeader> blockHeaders) {
    if (blockHeaders.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    } else {
      debugLambda(
          LOG,
          "Requesting {} blocks {}->{} ({})",
          blockHeaders::size,
          () -> blockHeaders.get(0).getNumber(),
          () -> blockHeaders.get(blockHeaders.size() - 1).getNumber(),
          () -> blockHeaders.get(0).getHash().toHexString());
      return requestBodies(blockHeaders).thenApply(this::saveBlocks);
    }
  }

  @VisibleForTesting
  protected CompletableFuture<Block> requestBlock(final BlockHeader blockHeader) {
    final GetBlockFromPeerTask getBlockFromPeerTask =
        GetBlockFromPeerTask.create(
            context.getProtocolSchedule(),
            context.getEthContext(),
            Optional.of(blockHeader.getHash()),
            blockHeader.getNumber(),
            context.getMetricsSystem());
    final CompletableFuture<AbstractPeerTask.PeerTaskResult<Block>> run =
        getBlockFromPeerTask.run();
    return run.thenApply(AbstractPeerTask.PeerTaskResult::getResult);
  }

  @VisibleForTesting
  protected CompletableFuture<List<Block>> requestBodies(final List<BlockHeader> blockHeaders) {
    final GetBodiesFromPeerTask getBodiesFromPeerTask =
        GetBodiesFromPeerTask.forHeaders(
            context.getProtocolSchedule(),
            context.getEthContext(),
            blockHeaders,
            context.getMetricsSystem());

    final CompletableFuture<AbstractPeerTask.PeerTaskResult<List<Block>>> run =
        getBodiesFromPeerTask.run();
    return run.thenApply(AbstractPeerTask.PeerTaskResult::getResult)
        .thenApply(
            blocks -> {
              blocks.sort(Comparator.comparing(block -> block.getHeader().getNumber()));
              return blocks;
            });
  }

  @VisibleForTesting
  protected Void saveBlock(final Block block) {
    traceLambda(LOG, "Going to validate block {}", () -> block.getHeader().getHash().toHexString());
    var optResult =
        context
            .getBlockValidatorForBlock(block)
            .validateAndProcessBlock(
                context.getProtocolContext(),
                block,
                HeaderValidationMode.FULL,
                HeaderValidationMode.NONE);

    optResult.blockProcessingOutputs.ifPresent(
        result -> {
          traceLambda(
              LOG,
              "Block {} was validated, going to import it",
              () -> block.getHeader().getHash().toHexString());
          result.worldState.persist(block.getHeader());
          context.getProtocolContext().getBlockchain().appendBlock(block, result.receipts);
        });
    return null;
  }

  @VisibleForTesting
  protected Void saveBlocks(final List<Block> blocks) {
    if (blocks.isEmpty()) {
      LOG.info("No blocks to save...");
      context.halfBatchSize();
      return null;
    }

    for (Block block : blocks) {
      final Optional<Block> parent =
          context
              .getProtocolContext()
              .getBlockchain()
              .getBlockByHash(block.getHeader().getParentHash());
      if (parent.isEmpty()) {
        context.halfBatchSize();
        return null;
      } else {
        saveBlock(block);
      }
    }
    infoLambda(
        LOG,
        "Saved blocks {} -> {} (target: {})",
        () -> blocks.get(0).getHeader().getNumber(),
        () -> blocks.get(blocks.size() - 1).getHeader().getNumber(),
        () ->
            backwardChain.getPivot().orElse(blocks.get(blocks.size() - 1)).getHeader().getNumber());
    context.resetBatchSize();
    return null;
  }
}
