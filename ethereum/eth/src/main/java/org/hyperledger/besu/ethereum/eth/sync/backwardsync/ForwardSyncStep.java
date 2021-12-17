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

import static org.apache.logging.log4j.LogManager.getLogger;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.GetBlockFromPeerTask;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.Logger;

public class ForwardSyncStep extends BackwardSyncTask {

  private static final Logger LOG = getLogger();

  public ForwardSyncStep(final BackwardsSyncContext context, final BackwardChain backwardChain) {
    super(context, backwardChain);
  }

  @Override
  public CompletableFuture<Void> executeStep() {
    return CompletableFuture.supplyAsync(() -> processKnownAncestors(null))
        .thenCompose(this::possibleRequestBlock)
        .thenApply(this::processKnownAncestors)
        .thenCompose(this::possiblyMoreForwardSteps);
  }

  @VisibleForTesting
  protected BlockHeader processKnownAncestors(final Void unused) {
    while (backwardChain.getFirstAncestorHeader().isPresent()) {
      BlockHeader header = backwardChain.getFirstAncestorHeader().orElseThrow();
      if (context.getProtocolContext().getBlockchain().contains(header.getHash())) {
        LOG.debug(
            "Block {} is already imported, we can ignore it for the sync process",
            () -> header.getHash().toString().substring(0, 20));
        backwardChain.dropFirstHeader();
      } else if (backwardChain.isTrusted(header.getHash())) {
        LOG.debug(
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
  public CompletableFuture<Void> possibleRequestBlock(final BlockHeader blockHeader) {
    if (blockHeader == null) {
      return CompletableFuture.completedFuture(null);
    } else {
      LOG.debug(
          "We don't have body of block {}, going to request it",
          () -> blockHeader.getHash().toString().substring(0, 20));
      return requestBlock(blockHeader).thenApply(this::saveBlock);
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
  protected Void saveBlock(final Block block) {
    LOG.debug(
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

    optResult.ifPresent(
        result -> {
          LOG.debug(
              "Block {} was validated, going to import it",
              () -> block.getHeader().getHash().toString().substring(0, 20));
          result.worldState.persist(block.getHeader());
          context.getProtocolContext().getBlockchain().appendBlock(block, result.receipts);
        });
    return null;
  }

  @VisibleForTesting
  protected CompletableFuture<Void> possiblyMoreForwardSteps(final BlockHeader firstUnsynced) {
    CompletableFuture<Void> completableFuture = CompletableFuture.completedFuture(null);
    if (firstUnsynced == null) {
      LOG.debug("The only work left is to import blocks provided by consensus layer...");
      backwardChain
          .getSuccessors()
          .forEach(
              block -> {
                if (!context.getProtocolContext().getBlockchain().contains(block.getHash())) {
                  saveBlock(block);
                }
              });
      LOG.debug("The sync is done...");
      return CompletableFuture.completedFuture(null);
    }
    LOG.debug(
        "Block {} is not yet imported, we need to run another step of ForwardSync",
        () -> firstUnsynced.getHash().toString().substring(0, 20));
    return completableFuture.thenCompose(this::executeAsync);
  }
}
