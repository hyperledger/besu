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
import static org.hyperledger.besu.util.Slf4jLambdaHelper.warnLambda;

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

public class ForwardSyncStep extends BackwardSyncTask {

  private static final Logger LOG = LoggerFactory.getLogger(ForwardSyncStep.class);

  public ForwardSyncStep(final BackwardsSyncContext context, final BackwardChain backwardChain) {
    super(context, backwardChain);
  }

  @Override
  public CompletableFuture<Void> executeOneStep() {
    return CompletableFuture.supplyAsync(() -> processKnownAncestors(null))
        .thenCompose(this::possibleRequestBlock)
        .thenApply(this::processKnownAncestors)
        .thenCompose(this::possiblyMoreForwardSteps);
  }

  @Override
  public CompletableFuture<Void> executeBatchStep() {
    return CompletableFuture.supplyAsync(() -> returnFirstNUnknownHeaders(null))
        .thenCompose(this::possibleRequestBodies)
        .thenApply(this::processKnownAncestors)
        .thenCompose(this::possiblyMoreForwardSteps);
  }

  @VisibleForTesting
  protected BlockHeader processKnownAncestors(final Void unused) {
    while (backwardChain.getFirstAncestorHeader().isPresent()) {
      BlockHeader header = backwardChain.getFirstAncestorHeader().orElseThrow();
      if (context.getProtocolContext().getBlockchain().contains(header.getHash())) {
        debugLambda(
            LOG,
            "Block {} is already imported, we can ignore it for the sync process",
            () -> header.getHash().toString().substring(0, 20));
        backwardChain.dropFirstHeader();
      } else if (backwardChain.isTrusted(header.getHash())) {
        debugLambda(
            LOG,
            "Block {} was added by consensus layer, we can trust it and should therefore import it.",
            () -> header.getHash().toString().substring(0, 20));
        saveBlock(backwardChain.getTrustedBlock(header.getHash()));
      } else {
        return header;
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
            "Block {} is already imported, we can ignore it for the sync process",
            () -> header.getHash().toString().substring(0, 20));
        backwardChain.dropFirstHeader();
      } else if (backwardChain.isTrusted(header.getHash())) {
        debugLambda(
            LOG,
            "Block {} was added by consensus layer, we can trust it and should therefore import it.",
            () -> header.getHash().toString().substring(0, 20));
        saveBlock(backwardChain.getTrustedBlock(header.getHash()));
      } else {
        return backwardChain.getFirstNAncestorHeaders(BackwardsSyncContext.BATCH_SIZE);
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
          "We don't have body of block {}, going to request it",
          () -> blockHeader.getHash().toString().substring(0, 20));
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
          "We don't have body of {} blocks starting from {}, going to request it",
          blockHeaders::size,
          () -> blockHeaders.get(0).getHash().toString().substring(0, 20));
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
    debugLambda(
        LOG,
        "Going to validate block {}",
        () -> block.getHeader().getHash().toString().substring(0, 20));
    var optResult =
        context
            .getBlockValidator(block.getHeader().getNumber())
            .validateAndProcessBlock(
                context.getProtocolContext(),
                block,
                HeaderValidationMode.FULL,
                HeaderValidationMode.NONE);

    optResult.blockProcessingOutputs.ifPresent(
        result -> {
          debugLambda(
              LOG,
              "Block {} was validated, going to import it",
              () -> block.getHeader().getHash().toString().substring(0, 20));
          result.worldState.persist(block.getHeader());
          context.getProtocolContext().getBlockchain().appendBlock(block, result.receipts);
        });
    return null;
  }

  @VisibleForTesting
  protected Void saveBlocks(final List<Block> blocks) {
    for (Block block : blocks) {
      saveBlock(block);
    }
    infoLambda(
        LOG,
        "Saved blocks {}->{}",
        () -> blocks.get(0).getHeader().getNumber(),
        () -> blocks.get(blocks.size() - 1).getHeader().getNumber());
    return null;
  }

  @VisibleForTesting
  protected CompletableFuture<Void> possiblyMoreForwardSteps(final BlockHeader firstUnsynced) {
    CompletableFuture<Void> completableFuture = CompletableFuture.completedFuture(null);
    if (firstUnsynced == null) {
      LOG.info("Importing blocks provided by consensus layer...");
      backwardChain
          .getSuccessors()
          .forEach(
              block -> {
                if (!context.getProtocolContext().getBlockchain().contains(block.getHash())) {
                  saveBlock(block);
                }
              });
      LOG.info("The Backward sync is done...");
      return CompletableFuture.completedFuture(null);
    }
    if (context.getProtocolContext().getBlockchain().contains(firstUnsynced.getParentHash())) {
      debugLambda(
          LOG,
          "Block {} is not yet imported, we need to run another step of ForwardSync",
          () -> firstUnsynced.getHash().toString().substring(0, 20));
      return completableFuture.thenCompose(this::executeAsync);
    }

    warnLambda(
        LOG,
        "Block {} is not yet imported but its parent {} is not imported either... "
            + "This should not normally happen and indicates a wrong behaviour somewhere...",
        () -> firstUnsynced.getHash().toHexString(),
        () -> firstUnsynced.getParentHash().toHexString());
    return completableFuture.thenCompose(
        new BackwardSyncStep(context, backwardChain)::executeAsync);
  }
}
