/*
 *
 *  * Copyright Hyperledger Besu Contributors.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum.eth.sync.fullsync;

import org.hyperledger.besu.ethereum.eth.sync.TrailingPeerRequirements;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class CompositeSyncDownloader implements SyncDownloader {

  List<SyncDownloader> syncs;
  private CompletableFuture<Void> future;

  public CompositeSyncDownloader(final SyncDownloader... syncs) {
    this.syncs = Arrays.asList(syncs);
  }

  @Override
  public CompletableFuture<Void> start() {
    future = CompletableFuture.completedFuture(null); // TODO: what about error handling
    for (SyncDownloader sync : syncs) {
      final CompletableFuture<Void> start = sync.start();
      future = future.thenCompose(unused -> start);
    }
    return future;
  }

  @Override
  public void stop() {
    future.cancel(true);
  }

  @Override
  public Optional<TrailingPeerRequirements> calculateTrailingPeerRequirements() {
    return syncs.stream()
        .map(SyncDownloader::calculateTrailingPeerRequirements)
        .findFirst()
        .orElse(Optional.empty());
  }
}
