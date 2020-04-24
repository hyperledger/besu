/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import static org.hyperledger.besu.util.FutureUtils.exceptionallyCompose;

import org.hyperledger.besu.ethereum.eth.sync.ChainDownloader;
import org.hyperledger.besu.ethereum.eth.sync.TrailingPeerRequirements;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.NodeDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.StalledDownloadException;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldStateDownloader;
import org.hyperledger.besu.services.tasks.TaskCollection;
import org.hyperledger.besu.util.ExceptionUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FastSyncDownloader<C> {

  private static final Duration FAST_SYNC_RETRY_DELAY = Duration.ofSeconds(5);

  private static final Logger LOG = LogManager.getLogger();
  private final FastSyncActions<C> fastSyncActions;
  private final WorldStateDownloader worldStateDownloader;
  private final FastSyncStateStorage fastSyncStateStorage;
  private final TaskCollection<NodeDataRequest> taskCollection;
  private final Path fastSyncDataDirectory;
  private final FastSyncState initialFastSyncState;
  private volatile Optional<TrailingPeerRequirements> trailingPeerRequirements = Optional.empty();
  private final AtomicBoolean running = new AtomicBoolean(false);

  public FastSyncDownloader(
      final FastSyncActions<C> fastSyncActions,
      final WorldStateDownloader worldStateDownloader,
      final FastSyncStateStorage fastSyncStateStorage,
      final TaskCollection<NodeDataRequest> taskCollection,
      final Path fastSyncDataDirectory,
      final FastSyncState initialFastSyncState) {
    this.fastSyncActions = fastSyncActions;
    this.worldStateDownloader = worldStateDownloader;
    this.fastSyncStateStorage = fastSyncStateStorage;
    this.taskCollection = taskCollection;
    this.fastSyncDataDirectory = fastSyncDataDirectory;
    this.initialFastSyncState = initialFastSyncState;
  }

  public CompletableFuture<FastSyncState> start() {
    if (!running.compareAndSet(false, true)) {
      throw new IllegalStateException("FastSyncDownloader already running");
    }
    return start(initialFastSyncState);
  }

  private CompletableFuture<FastSyncState> start(final FastSyncState fastSyncState) {
    LOG.info("Starting fast sync.");
    return exceptionallyCompose(
        fastSyncActions
            .waitForSuitablePeers(fastSyncState)
            .thenCompose(fastSyncActions::selectPivotBlock)
            .thenCompose(fastSyncActions::downloadPivotBlockHeader)
            .thenApply(this::updateMaxTrailingPeers)
            .thenApply(this::storeState)
            .thenCompose(this::downloadChainAndWorldState),
        this::handleFailure);
  }

  private CompletableFuture<FastSyncState> handleFailure(final Throwable error) {
    trailingPeerRequirements = Optional.empty();
    if (ExceptionUtils.rootCause(error) instanceof FastSyncException) {
      return CompletableFuture.failedFuture(error);
    } else if (ExceptionUtils.rootCause(error) instanceof StalledDownloadException) {
      LOG.warn(
          "Fast sync was unable to download the world state. Retrying with a new pivot block.");
      return start(FastSyncState.EMPTY_SYNC_STATE);
    } else {
      LOG.error(
          "Encountered an unexpected error during fast sync. Restarting fast sync in "
              + FAST_SYNC_RETRY_DELAY
              + " seconds.",
          error);
      return fastSyncActions.scheduleFutureTask(
          () -> start(FastSyncState.EMPTY_SYNC_STATE), FAST_SYNC_RETRY_DELAY);
    }
  }

  public void stop() {
    synchronized (this) {
      if (running.compareAndSet(true, false)) {
        // Cancelling the world state download will also cause the chain download to be cancelled.
        worldStateDownloader.cancel();
      }
    }
  }

  public void deleteFastSyncState() {
    // Make sure downloader is stopped before we start cleaning up its dependencies
    worldStateDownloader.cancel();
    try {
      taskCollection.close();
      if (fastSyncDataDirectory.toFile().exists()) {
        // Clean up this data for now (until fast sync resume functionality is in place)
        MoreFiles.deleteRecursively(fastSyncDataDirectory, RecursiveDeleteOption.ALLOW_INSECURE);
      }
    } catch (final IOException e) {
      LOG.error("Unable to clean up fast sync state", e);
    }
  }

  private FastSyncState updateMaxTrailingPeers(final FastSyncState state) {
    if (state.getPivotBlockNumber().isPresent()) {
      trailingPeerRequirements =
          Optional.of(new TrailingPeerRequirements(state.getPivotBlockNumber().getAsLong(), 0));
    } else {
      trailingPeerRequirements = Optional.empty();
    }
    return state;
  }

  private FastSyncState storeState(final FastSyncState state) {
    fastSyncStateStorage.storeState(state);
    return state;
  }

  private CompletableFuture<FastSyncState> downloadChainAndWorldState(
      final FastSyncState currentState) {
    // Synchronized ensures that stop isn't called while we're in the process of starting a
    // world state and chain download. If it did we might wind up starting a new download
    // after the stop method had called cancel.
    synchronized (this) {
      if (!running.get()) {
        return CompletableFuture.failedFuture(
            new CancellationException("FastSyncDownloader stopped"));
      }
      final CompletableFuture<Void> worldStateFuture =
          worldStateDownloader.run(currentState.getPivotBlockHeader().get());
      final ChainDownloader chainDownloader = fastSyncActions.createChainDownloader(currentState);
      final CompletableFuture<Void> chainFuture = chainDownloader.start();

      // If either download fails, cancel the other one.
      chainFuture.exceptionally(
          error -> {
            worldStateFuture.cancel(true);
            return null;
          });
      worldStateFuture.exceptionally(
          error -> {
            chainDownloader.cancel();
            return null;
          });

      return CompletableFuture.allOf(worldStateFuture, chainFuture)
          .thenApply(
              complete -> {
                trailingPeerRequirements = Optional.empty();
                return currentState;
              });
    }
  }

  public Optional<TrailingPeerRequirements> calculateTrailingPeerRequirements() {
    return trailingPeerRequirements;
  }
}
