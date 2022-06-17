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

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.GetBodiesFromPeerTask;

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
    return CompletableFuture.supplyAsync(
            () -> backwardChain.getFirstNAncestorHeaders(context.getBatchSize()))
        .thenCompose(this::possibleRequestBodies);
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
  protected Void saveBlocks(final List<Block> blocks) {
    if (blocks.isEmpty()) {
      LOG.info("No blocks to save...");
      context.halveBatchSize();
      return null;
    }

    for (Block block : blocks) {
      final Optional<Block> parent =
          context
              .getProtocolContext()
              .getBlockchain()
              .getBlockByHash(block.getHeader().getParentHash());
      if (parent.isEmpty()) {
        context.halveBatchSize();
        return null;
      } else {
        context.saveBlock(block);
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
