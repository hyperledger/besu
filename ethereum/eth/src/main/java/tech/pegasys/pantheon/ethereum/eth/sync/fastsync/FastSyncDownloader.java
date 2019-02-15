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

import static tech.pegasys.pantheon.util.FutureUtils.completedExceptionally;
import static tech.pegasys.pantheon.util.FutureUtils.exceptionallyCompose;

import tech.pegasys.pantheon.ethereum.eth.sync.worldstate.WorldStateDownloader;
import tech.pegasys.pantheon.ethereum.eth.sync.worldstate.WorldStateUnavailableException;
import tech.pegasys.pantheon.util.ExceptionUtils;

import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FastSyncDownloader<C> {
  private static final Logger LOG = LogManager.getLogger();
  private final FastSyncActions<C> fastSyncActions;
  private final WorldStateDownloader worldStateDownloader;
  private final FastSyncStateStorage fastSyncStateStorage;

  public FastSyncDownloader(
      final FastSyncActions<C> fastSyncActions,
      final WorldStateDownloader worldStateDownloader,
      final FastSyncStateStorage fastSyncStateStorage) {
    this.fastSyncActions = fastSyncActions;
    this.worldStateDownloader = worldStateDownloader;
    this.fastSyncStateStorage = fastSyncStateStorage;
  }

  public CompletableFuture<FastSyncState> start(final FastSyncState fastSyncState) {
    return exceptionallyCompose(
        fastSyncActions
            .waitForSuitablePeers(fastSyncState)
            .thenCompose(fastSyncActions::selectPivotBlock)
            .thenCompose(fastSyncActions::downloadPivotBlockHeader)
            .thenApply(this::storeState)
            .thenCompose(this::downloadChainAndWorldState),
        this::handleWorldStateUnavailable);
  }

  private CompletableFuture<FastSyncState> handleWorldStateUnavailable(final Throwable error) {
    if (ExceptionUtils.rootCause(error) instanceof WorldStateUnavailableException) {
      LOG.warn(
          "Fast sync was unable to download the world state. Retrying with a new pivot block.");
      return start(FastSyncState.EMPTY_SYNC_STATE);
    } else {
      return completedExceptionally(error);
    }
  }

  private FastSyncState storeState(final FastSyncState state) {
    fastSyncStateStorage.storeState(state);
    return state;
  }

  private CompletableFuture<FastSyncState> downloadChainAndWorldState(
      final FastSyncState currentState) {
    final CompletableFuture<Void> worldStateFuture =
        worldStateDownloader.run(currentState.getPivotBlockHeader().get());
    final CompletableFuture<FastSyncState> chainFuture =
        fastSyncActions.downloadChain(currentState);

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
