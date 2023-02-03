/*
 *
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
 *
 */

package org.hyperledger.besu.ethereum.eth.sync.backwardsync;

import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;
import static org.slf4j.LoggerFactory.getLogger;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;

public class ProcessKnownAncestorsStep {
  private static final Logger LOG = getLogger(ProcessKnownAncestorsStep.class);

  private final BackwardSyncContext context;
  private final BackwardChain backwardChain;

  public ProcessKnownAncestorsStep(
      final BackwardSyncContext backwardSyncContext, final BackwardChain backwardChain) {
    this.context = backwardSyncContext;
    this.backwardChain = backwardChain;
  }

  public CompletableFuture<Void> executeAsync() {
    return CompletableFuture.runAsync(this::processKnownAncestors);
  }

  @VisibleForTesting
  protected void processKnownAncestors() {
    while (backwardChain.getFirstAncestorHeader().isPresent()) {
      BlockHeader header = backwardChain.getFirstAncestorHeader().orElseThrow();
      final long chainHeadBlockNumber =
          context.getProtocolContext().getBlockchain().getChainHeadBlockNumber();
      boolean isFirstUnProcessedHeader = true;
      if (context.getProtocolContext().getBlockchain().contains(header.getHash())
          && header.getNumber() <= chainHeadBlockNumber) {
        debugLambda(
            LOG,
            "Block {} is already imported, we can ignore it for the sync process",
            header::toLogString);
        backwardChain.dropFirstHeader();
        isFirstUnProcessedHeader = false;
      } else if (context.getProtocolContext().getBlockchain().contains(header.getParentHash())) {
        final boolean isTrustedBlock = backwardChain.isTrusted(header.getHash());
        final Optional<Block> block =
            isTrustedBlock
                ? Optional.of(backwardChain.getTrustedBlock(header.getHash()))
                : context.getProtocolContext().getBlockchain().getBlockByHash(header.getHash());
        if (block.isPresent()) {
          debugLambda(LOG, "Importing block {}", header::toLogString);
          context.saveBlock(block.get());
          if (isTrustedBlock) {
            backwardChain.dropFirstHeader();
            isFirstUnProcessedHeader = false;
          }
        }
      }
      if (isFirstUnProcessedHeader) {
        debugLambda(LOG, "First unprocessed header is {}", header::toLogString);
        return;
      }
    }
  }
}
