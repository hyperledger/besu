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

import org.hyperledger.besu.ethereum.core.BlockHeader;

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
      if (context.getProtocolContext().getBlockchain().contains(header.getHash())) {
        debugLambda(
            LOG,
            "Block {} is already imported, we can ignore it for the sync process",
            () -> header.toLogString());
        backwardChain.dropFirstHeader();
      } else if (context.getProtocolContext().getBlockchain().contains(header.getParentHash())
          && backwardChain.isTrusted(header.getHash())) {
        debugLambda(LOG, "Importing trusted block {}", header::toLogString);
        context.saveBlock(backwardChain.getTrustedBlock(header.getHash()));
      } else {
        debugLambda(LOG, "First unprocessed header is {}", header::toLogString);
        return;
      }
    }
  }
}
