/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.eth.sync.fastsync;

import tech.pegasys.pantheon.ethereum.eth.sync.worldstate.WorldStateDownloader;

import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FastSyncDownloader<C> {
  private static final Logger LOG = LogManager.getLogger();
  private final FastSyncActions<C> fastSyncActions;
  private final WorldStateDownloader worldStateDownloader;

  public FastSyncDownloader(
      final FastSyncActions<C> fastSyncActions, final WorldStateDownloader worldStateDownloader) {
    this.fastSyncActions = fastSyncActions;
    this.worldStateDownloader = worldStateDownloader;
  }

  public CompletableFuture<FastSyncState> start() {
    LOG.info("Fast sync enabled");
    return fastSyncActions
        .waitForSuitablePeers()
        .thenCompose(state -> fastSyncActions.selectPivotBlock())
        .thenCompose(fastSyncActions::downloadPivotBlockHeader)
        .thenCompose(this::downloadChainAndWorldState);
  }

  private CompletableFuture<FastSyncState> downloadChainAndWorldState(
      final FastSyncState currentState) {
    final CompletableFuture<Void> worldStateFuture =
        worldStateDownloader.run(currentState.getPivotBlockHeader().get());
    final CompletableFuture<Void> chainFuture = fastSyncActions.downloadChain(currentState);

    // If either download fails, cancel the other one.
    chainFuture.exceptionally(
        error -> {
          worldStateFuture.cancel(true);
          return null;
        });
    worldStateFuture.exceptionally(
        error -> {
          chainFuture.cancel(true);
          return null;
        });

    return CompletableFuture.allOf(worldStateFuture, chainFuture)
        .thenApply(complete -> currentState);
  }
}
