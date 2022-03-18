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
import java.util.concurrent.CompletionStage;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForwardSyncPhase extends BackwardSyncTask {

  private static final Logger LOG = LoggerFactory.getLogger(ForwardSyncPhase.class);
  private int batchSize = BackwardsSyncContext.BATCH_SIZE;

  public ForwardSyncPhase(final BackwardsSyncContext context, final BackwardChain backwardChain) {
    super(context, backwardChain);
  }

  @Override
  public CompletableFuture<Void> executeStep() {
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
            () -> header.getHash().toHexString());
        backwardChain.dropFirstHeader();
      } else if (backwardChain.isTrusted(header.getHash())) {
        debugLambda(
            LOG,
            "Importing trusted block {}({})",
            header::getNumber,
            () -> header.getHash().toHexString());
        saveBlock(backwardChain.getTrustedBlock(header.getHash()));
      } else {
        debugLambda(LOG, "First unprocessed header is {}", header::getNumber);
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
            "Block {}({}) is already imported, we can ignore it for the sync process",
            () -> header.getNumber(),
            () -> header.getHash().toHexString());
        backwardChain.dropFirstHeader();
      } else if (backwardChain.isTrusted(header.getHash())) {
        debugLambda(
            LOG,
            "Block {} was added by consensus layer, we can trust it and should therefore import it.",
            () -> header.getHash().toHexString());
        saveBlock(backwardChain.getTrustedBlock(header.getHash()));
      } else {
        return backwardChain.getFirstNAncestorHeaders(batchSize);
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
    debugLambda(LOG, "Going to validate block {}", () -> block.getHeader().getHash().toHexString());
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
              () -> block.getHeader().getHash().toHexString());
          result.worldState.persist(block.getHeader());
          context.getProtocolContext().getBlockchain().appendBlock(block, result.receipts);
        });
    return null;
  }

  @VisibleForTesting
  protected Void saveBlocks(final List<Block> blocks) {

    for (Block block : blocks) {
      final Optional<Block> parent =
          context
              .getProtocolContext()
              .getBlockchain()
              .getBlockByHash(block.getHeader().getParentHash());
      if (parent.isEmpty()) {
        batchSize = batchSize / 2 + 1;
        return null;
      } else {
        batchSize = BackwardsSyncContext.BATCH_SIZE;
        saveBlock(block);
      }
    }
    backwardChain.commit();
    infoLambda(
        LOG,
        "Saved blocks {}->{}",
        () -> blocks.get(0).getHeader().getNumber(),
        () -> blocks.get(blocks.size() - 1).getHeader().getNumber());
    return null;
  }

  @VisibleForTesting
  protected CompletableFuture<Void> possiblyMoreForwardSteps(final BlockHeader firstNotSynced) {
    CompletableFuture<Void> completableFuture = CompletableFuture.completedFuture(null);
    if (firstNotSynced == null) {
      final List<Block> successors = backwardChain.getSuccessors();
      LOG.info(
          "Forward Sync Phase is finished. Importing {} block(s) provided by consensus layer...",
          successors.size());
      successors.forEach(
          block -> {
            if (!context.getProtocolContext().getBlockchain().contains(block.getHash())) {
              saveBlock(block);
            }
          });
      LOG.info("The Backward sync is done...");
      backwardChain.clear();
      return CompletableFuture.completedFuture(null);
    }
    if (context.getProtocolContext().getBlockchain().contains(firstNotSynced.getParentHash())) {
      debugLambda(
          LOG,
          "Block {}({}) is not yet imported, we need to run another step of ForwardSync",
          firstNotSynced::getNumber,
          () -> firstNotSynced.getHash().toHexString());
      return completableFuture.thenCompose(this::executeAsync);
    }

    warnLambda(
        LOG,
        "Block {} is not yet imported but its parent {} is not imported either... "
            + "This should not normally happen and indicates a wrong behaviour somewhere...",
        () -> firstNotSynced.getHash().toHexString(),
        () -> firstNotSynced.getParentHash().toHexString());
    return completableFuture.thenCompose(this::executeBackwardAsync);
  }

  @VisibleForTesting
  protected CompletionStage<Void> executeBackwardAsync(final Void unused) {
    return new BackwardSyncPhase(context, backwardChain).executeAsync(unused);
  }
}
