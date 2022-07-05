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
import org.hyperledger.besu.ethereum.eth.manager.task.RetryingGetHeadersEndingAtFromPeerByHashTask;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackwardSyncStep {
  private static final Logger LOG = LoggerFactory.getLogger(BackwardSyncStep.class);
  private final BackwardSyncContext context;
  private final BackwardChain backwardChain;

  public BackwardSyncStep(final BackwardSyncContext context, final BackwardChain backwardChain) {
    this.context = context;
    this.backwardChain = backwardChain;
  }

  public CompletableFuture<Void> executeAsync(final BlockHeader firstHeader) {
    return CompletableFuture.supplyAsync(() -> firstHeader)
        .thenApply(this::possibleRestoreOldNodes)
        .thenCompose(this::requestHeaders)
        .thenApply(this::saveHeaders);
  }

  @VisibleForTesting
  protected Hash possibleRestoreOldNodes(final BlockHeader firstAncestor) {
    Hash lastHash = firstAncestor.getParentHash();
    Optional<BlockHeader> iterator = backwardChain.getHeader(lastHash);
    while (iterator.isPresent()) {
      backwardChain.prependAncestorsHeader(iterator.get());
      lastHash = iterator.get().getParentHash();
      iterator = backwardChain.getHeader(lastHash);
    }
    return lastHash;
  }

  @VisibleForTesting
  protected CompletableFuture<List<BlockHeader>> requestHeaders(final Hash hash) {
    final int batchSize = context.getBatchSize();
    debugLambda(LOG, "Requesting header for hash {}", hash::toHexString);

    final RetryingGetHeadersEndingAtFromPeerByHashTask
        retryingGetHeadersEndingAtFromPeerByHashTask =
            RetryingGetHeadersEndingAtFromPeerByHashTask.endingAtHash(
                context.getProtocolSchedule(),
                context.getEthContext(),
                hash,
                batchSize,
                context.getMetricsSystem());
    return context
        .getEthContext()
        .getScheduler()
        .scheduleSyncWorkerTask(retryingGetHeadersEndingAtFromPeerByHashTask::run)
        .thenApply(
            blockHeaders -> {
              if (blockHeaders.isEmpty()) {
                throw new BackwardSyncException(
                    "Did not receive a headers for hash " + hash.toHexString(), true);
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
    return null;
  }

  @VisibleForTesting
  protected Void saveHeaders(final List<BlockHeader> blockHeaders) {
    for (BlockHeader blockHeader : blockHeaders) {
      saveHeader(blockHeader);
    }
    infoLambda(
        LOG,
        "Saved headers {} -> {} (head: {})",
        () -> blockHeaders.get(0).getNumber(),
        () -> blockHeaders.get(blockHeaders.size() - 1).getNumber(),
        () -> context.getProtocolContext().getBlockchain().getChainHead().getHeight());
    return null;
  }
}
