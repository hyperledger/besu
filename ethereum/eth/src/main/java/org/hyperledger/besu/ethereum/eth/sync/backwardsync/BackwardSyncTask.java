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

import static org.slf4j.LoggerFactory.getLogger;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;

public abstract class BackwardSyncTask {
  protected BackwardsSyncContext context;
  protected BackwardSyncStorage backwardChain;
  private static final Logger LOG = getLogger(BackwardSyncTask.class);

  protected BackwardSyncTask(
      final BackwardsSyncContext context, final BackwardSyncStorage backwardChain) {
    this.context = context;
    this.backwardChain = backwardChain;
  }

  CompletableFuture<Void> executeAsync(final Void unused) {
    Optional<BackwardSyncStorage> currentChain = context.getCurrentChain();
    if (currentChain.isPresent()) {
      if (!backwardChain.equals(currentChain.get())) {
        LOG.info(
            "The pivot changed, we should stop current flow, some new flow is waiting to take over...");
        return CompletableFuture.completedFuture(null);
      }
      if (backwardChain.getFirstAncestorHeader().isEmpty()) {
        LOG.debug("The Backwards sync is already finished..."); // todo fishy....
        return CompletableFuture.completedFuture(null);
      }
      return executeStep();

    } else {
      CompletableFuture<Void> result = new CompletableFuture<>();
      result.completeExceptionally(
          new BackwardSyncException(
              "No pivot... that is weird and should not have happened. This method should have been called after the pivot was set..."));
      return result;
    }
  }

  CompletableFuture<Void> executeStep() {
    return executeBatchStep();
  }

  abstract CompletableFuture<Void> executeOneStep();

  CompletableFuture<Void> executeBatchStep() {
    return executeOneStep();
  }
}
