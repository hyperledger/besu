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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.task.GetHeadersFromPeerByHashTask;
import org.hyperledger.besu.ethereum.eth.manager.task.RetryingGetHeadersEndingAtFromPeerByHashTask;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackwardSyncPhase extends BackwardSyncTask {
  private static final Logger LOG = LoggerFactory.getLogger(BackwardSyncPhase.class);

  public BackwardSyncPhase(final BackwardSyncContext context, final BackwardChain backwardChain) {
    super(context, backwardChain);
  }

  @VisibleForTesting
  protected CompletableFuture<Void> waitForTTD() {
    if (context.isOnTTD()) {
      return CompletableFuture.completedFuture(null);
    }
    LOG.debug("Did not reach TTD yet, falling asleep...");
    return context
        .getEthContext()
        .getScheduler()
        .scheduleFutureTask(this::waitForTTD, Duration.ofSeconds(5));
  }

  @Override
  public CompletableFuture<Void> executeStep() {
    return CompletableFuture.supplyAsync(this::waitForTTD)
        .thenCompose(Function.identity())
        .thenApply(this::earliestUnprocessedHash)
        .thenCompose(this::requestHeaders)
        .thenApply(this::saveHeaders)
        .thenApply(this::possibleMerge)
        .thenCompose(this::possiblyMoreBackwardSteps);
  }

  @VisibleForTesting
  protected Hash earliestUnprocessedHash(final Void unused) {
    BlockHeader firstHeader =
        backwardChain
            .getFirstAncestorHeader()
            .orElseThrow(
                () ->
                    new BackwardSyncException(
                        "No unprocessed hashes during backward sync. that is probably a bug.",
                        true));
    Hash parentHash = firstHeader.getParentHash();
    debugLambda(
        LOG,
        "First unprocessed hash for current pivot is {} expected on height {}",
        parentHash::toHexString,
        () -> firstHeader.getNumber() - 1);
    return parentHash;
  }

  @VisibleForTesting
  protected CompletableFuture<BlockHeader> requestHeader(final Hash hash) {
    debugLambda(LOG, "Requesting header for hash {}", hash::toHexString);
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
                    "Did not receive a header for hash {}" + hash.toHexString(), true);
              }
              BlockHeader blockHeader = result.get(0);
              debugLambda(
                  LOG,
                  "Got header {} with height {}",
                  () -> blockHeader.getHash().toHexString(),
                  blockHeader::getNumber);
              return blockHeader;
            });
  }

  @VisibleForTesting
  protected CompletableFuture<List<BlockHeader>> requestHeaders(final Hash hash) {
    debugLambda(LOG, "Requesting header for hash {}", hash::toHexString);
    final RetryingGetHeadersEndingAtFromPeerByHashTask
        retryingGetHeadersEndingAtFromPeerByHashTask =
            RetryingGetHeadersEndingAtFromPeerByHashTask.endingAtHash(
                context.getProtocolSchedule(),
                context.getEthContext(),
                hash,
                context.getProtocolContext().getBlockchain().getChainHead().getHeight(),
                BackwardSyncContext.BATCH_SIZE,
                context.getMetricsSystem());
    return context
        .getEthContext()
        .getScheduler()
        .scheduleSyncWorkerTask(retryingGetHeadersEndingAtFromPeerByHashTask::run)
        .thenApply(
            blockHeaders -> {
              if (blockHeaders.isEmpty()) {
                throw new BackwardSyncException(
                    "Did not receive a header for hash {}" + hash.toHexString(), true);
              }
              debugLambda(
                  LOG,
                  "Got headers {} -> {}",
                  blockHeaders.get(0)::getNumber,
                  blockHeaders.get(blockHeaders.size() - 1)::getNumber);
              return blockHeaders;
            });
  }

  @VisibleForTesting
  protected Void saveHeader(final BlockHeader blockHeader) {
    backwardChain.prependAncestorsHeader(blockHeader);
    context.putCurrentChainToHeight(blockHeader.getNumber(), backwardChain);
    return null;
  }

  @VisibleForTesting
  protected Void saveHeaders(final List<BlockHeader> blockHeaders) {
    for (BlockHeader blockHeader : blockHeaders) {
      saveHeader(blockHeader);
    }
    infoLambda(
        LOG,
        "Saved headers {} -> {}",
        () -> blockHeaders.get(0).getNumber(),
        () -> blockHeaders.get(blockHeaders.size() - 1).getNumber());
    return null;
  }

  @VisibleForTesting
  protected BlockHeader possibleMerge(final Void unused) {
    Optional<BackwardChain> maybeHistoricalBackwardChain =
        context.findCorrectChainFromPivot(
            backwardChain.getFirstAncestorHeader().orElseThrow().getNumber() - 1);
    maybeHistoricalBackwardChain.ifPresent(backwardChain::prependChain);
    return backwardChain.getFirstAncestorHeader().orElseThrow();
  }

  // if the previous header is not present yet, we need to go deeper
  @VisibleForTesting
  protected CompletableFuture<Void> possiblyMoreBackwardSteps(final BlockHeader blockHeader) {
    CompletableFuture<Void> completableFuture = new CompletableFuture<>();
    if (context.getProtocolContext().getBlockchain().contains(blockHeader.getHash())) {
      LOG.info("Backward Phase finished.");
      completableFuture.complete(null);
      return completableFuture;
    }
    if (context.getProtocolContext().getBlockchain().getChainHead().getHeight()
        > blockHeader.getNumber() - 1) {
      LOG.warn(
          "Backward sync is following unknown branch {} ({}) and reached bellow previous head {}({})",
          blockHeader.getNumber(),
          blockHeader.getHash(),
          context.getProtocolContext().getBlockchain().getChainHead().getHeight(),
          context.getProtocolContext().getBlockchain().getChainHead().getHash().toHexString());
    }
    LOG.debug("Backward sync did not reach a know block, need to go deeper");
    completableFuture.complete(null);
    return completableFuture.thenCompose(this::executeAsync);
  }
}
