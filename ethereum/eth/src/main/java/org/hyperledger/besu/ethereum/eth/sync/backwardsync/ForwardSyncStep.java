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

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.RetryingGetBlocksFromPeersTask;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForwardSyncStep {

  private static final Logger LOG = LoggerFactory.getLogger(ForwardSyncStep.class);
  private static final long MILLIS_DELAY_BETWEEN_PROGRESS_LOG = 10_000L;
  private static long lastLogAt = 0;
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
    final RetryingGetBlocksFromPeersTask getBodiesFromPeerTask =
        RetryingGetBlocksFromPeersTask.forHeaders(
            context.getProtocolSchedule(),
            context.getEthContext(),
            context.getMetricsSystem(),
            context.getEthContext().getEthPeers().peerCount(),
            blockHeaders);

    final CompletableFuture<AbstractPeerTask.PeerTaskResult<List<Block>>> run =
        getBodiesFromPeerTask.run();
    return run.thenApply(AbstractPeerTask.PeerTaskResult::getResult)
        .thenApply(
            blocks -> {
              LOG.debug("Got {} blocks from peers", blocks.size());
              blocks.sort(Comparator.comparing(block -> block.getHeader().getNumber()));
              return blocks;
            });
  }

  @VisibleForTesting
  protected Void saveBlocks(final List<Block> blocks) {
    if (blocks.isEmpty()) {
      context.halveBatchSize();
      LOG.debug("No blocks to save, reducing batch size to {}", context.getBatchSize());
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
        debugLambda(
            LOG,
            "Parent block {} not found, while saving block {}, reducing batch size to {}",
            block.getHeader().getParentHash()::toString,
            block::toLogString,
            context::getBatchSize);
        logProgress(blocks.get(blocks.size() - 1).getHeader().getNumber());
        return null;
      } else {
        context.saveBlock(block);
      }
    }

    logProgress(blocks.get(blocks.size() - 1).getHeader().getNumber());

    context.resetBatchSize();
    return null;
  }

  private void logProgress(final long currImportedHeight) {
    final long targetHeight = context.getStatus().getTargetChainHeight();
    final long initialHeight = context.getStatus().getInitialChainHeight();
    final long estimatedTotal = targetHeight - initialHeight;
    final long imported = currImportedHeight - initialHeight;

    final float completedPercentage = 100.0f * imported / estimatedTotal;

    if (currImportedHeight < targetHeight) {
      final long now = System.currentTimeMillis();
      if (now - lastLogAt > MILLIS_DELAY_BETWEEN_PROGRESS_LOG) {
        LOG.info(
            String.format(
                "Backward sync phase 2 of 2, %.2f%% completed, imported %d blocks of at least %d. Peers: %d",
                completedPercentage,
                imported,
                estimatedTotal,
                context.getEthContext().getEthPeers().peerCount()));
        lastLogAt = now;
      }
    } else {
      LOG.info(
          String.format(
              "Backward sync phase 2 of 2 completed, imported a total of %d blocks. Peers: %d",
              imported, context.getEthContext().getEthPeers().peerCount()));
    }
  }
}
