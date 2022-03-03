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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public abstract class BackwardSyncTask {
  protected BackwardsSyncContext context;
  protected BackwardChain backwardChain;

  protected BackwardSyncTask(
      final BackwardsSyncContext context, final BackwardChain backwardChain) {
    this.context = context;
    this.backwardChain = backwardChain;
  }

  CompletableFuture<Void> executeAsync(final Void unused) {
    CompletableFuture<Void> result = new CompletableFuture<>();
    Optional<BackwardChain> currentChain = context.getCurrentChain();
    if (currentChain.isPresent()) {
      if (!backwardChain.equals(currentChain.get())) {
        result.completeExceptionally(
            new BackwardSyncException(
                "The pivot changed, we should stop current flow, some new flow is waiting to take over..."));
        return result;
      } else {
        result.complete(null);
        return executeStep();
      }
    } else {
      result.completeExceptionally(
          new BackwardSyncException(
              "No pivot... that is weird and should not have happened. This method should have been called after the pivot was set..."));
      return result;
    }
  }

  abstract CompletableFuture<Void> executeStep();

  CompletableFuture<Void> executeBatchStep() {
    return executeStep();
  }
  ;
}
