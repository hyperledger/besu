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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.task.GetHeadersFromPeerByHashTask;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackwardSyncStep extends BackwardSyncTask {
  private static final Logger LOG = LoggerFactory.getLogger(BackwardSyncStep.class);

  public BackwardSyncStep(final BackwardsSyncContext context, final BackwardChain backwardChain) {
    super(context, backwardChain);
  }

  @Override
  public CompletableFuture<Void> executeStep() {
    return CompletableFuture.supplyAsync(this::earliestUnprocessedHash)
        .thenCompose(this::requestHeader)
        .thenApply(this::saveHeader)
        .thenApply(this::possibleMerge)
        .thenCompose(this::possiblyMoreBackwardSteps);
  }

  @VisibleForTesting
  protected Hash earliestUnprocessedHash() {
    BlockHeader firstHeader =
        backwardChain
            .getFirstAncestorHeader()
            .orElseThrow(
                () ->
                    new BackwardSyncException(
                        "No unprocessed hashes during backward sync. that is probably a bug."));
    Hash parentHash = firstHeader.getParentHash();
    debugLambda(
        LOG,
        "First unprocessed hash for current pivot is {} expected on height {}",
        () -> parentHash.toString().substring(0, 20),
        () -> firstHeader.getNumber() - 1);
    return parentHash;
  }

  @VisibleForTesting
  protected CompletableFuture<BlockHeader> requestHeader(final Hash hash) {
    debugLambda(LOG, "Requesting header for hash {}", () -> hash.toString().substring(0, 20));
    return GetHeadersFromPeerByHashTask.forSingleHash(
            context.getProtocolSchedule(),
            context.getEthContext(),
            hash,
            context.getProtocolContext().getBlockchain().getChainHead().getHeight(),
            context.getMetricsSystem())
        .run()
        .thenApply(
            peerResult -> {
              final List<BlockHeader> result = peerResult.getResult();
              if (result.isEmpty()) {
                throw new BackwardSyncException(
                    "Did not receive a header for hash {}" + hash.toString().substring(0, 20));
              }
              BlockHeader blockHeader = result.get(0);
              debugLambda(
                  LOG,
                  "Got header {} with height {}",
                  () -> blockHeader.getHash().toString().substring(0, 20),
                  blockHeader::getNumber);
              return blockHeader;
            });
  }

  @VisibleForTesting
  protected Void saveHeader(final BlockHeader blockHeader) {
    backwardChain.saveHeader(blockHeader);
    context.putCurrentChainToHeight(blockHeader.getNumber(), backwardChain);
    return null;
  }

  @VisibleForTesting
  protected BlockHeader possibleMerge(final Void unused) {
    Optional<BackwardChain> maybeHistoricalBackwardChain =
        Optional.ofNullable(
            context.findCorrectChainFromPivot(
                backwardChain.getFirstAncestorHeader().orElseThrow().getNumber() - 1));
    maybeHistoricalBackwardChain.ifPresent(backwardChain::merge);
    return backwardChain.getFirstAncestorHeader().orElseThrow();
  }

  // if the previous header is not present yet, we need to go deeper
  @VisibleForTesting
  protected CompletableFuture<Void> possiblyMoreBackwardSteps(final BlockHeader blockHeader) {
    CompletableFuture<Void> completableFuture = new CompletableFuture<>();
    if (context.getProtocolContext().getBlockchain().contains(blockHeader.getHash())) {
      LOG.debug(
          "The backward sync let us to a block that we already know... We will init forward sync...");
      completableFuture.complete(null); // we finished backward sync
      return completableFuture;
    }
    if (context.getProtocolContext().getBlockchain().getChainHead().getHeight()
        > blockHeader.getNumber() - 1) {
      completableFuture.completeExceptionally(
          new RuntimeException("Backward sync would reach under know head of blockchain"));
      return completableFuture;
    }
    LOG.debug("Backward sync did not reach a know block, need to go deeper");
    completableFuture.complete(null);
    return completableFuture.thenCompose(this::executeAsync);
  }
}
