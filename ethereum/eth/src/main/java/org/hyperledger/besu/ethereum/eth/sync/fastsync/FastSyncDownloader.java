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

public abstract class FastSyncDownloader<REQUEST> {

  protected static final Duration FAST_SYNC_RETRY_DELAY = Duration.ofSeconds(5);

  private static final Logger LOG = LogManager.getLogger();
  protected final FastSyncActions fastSyncActions;
  protected final WorldStateDownloader worldStateDownloader;
  protected final FastSyncStateStorage fastSyncStateStorage;
  protected final TaskCollection<REQUEST> taskCollection;
  protected final Path fastSyncDataDirectory;
  protected FastSyncState initialFastSyncState;
  protected volatile Optional<TrailingPeerRequirements> trailingPeerRequirements = Optional.empty();
  protected final AtomicBoolean running = new AtomicBoolean(false);

  public FastSyncDownloader(
      final FastSyncActions fastSyncActions,
      final WorldStateDownloader worldStateDownloader,
      final FastSyncStateStorage fastSyncStateStorage,
      final TaskCollection<REQUEST> taskCollection,
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

  protected CompletableFuture<FastSyncState> start(final FastSyncState fastSyncState) {
    LOG.info("Starting fast sync.");
    return exceptionallyCompose(
        fastSyncActions
            .waitForSuitablePeers(fastSyncState)
            .thenCompose(fastSyncActions::selectPivotBlock)
            .thenCompose(fastSyncActions::downloadPivotBlockHeader)
            .thenApply(this::updateMaxTrailingPeers)
            .thenApply(this::storeState)
            .thenCompose(fss -> downloadChainAndWorldState(fastSyncActions, fss)),
        this::handleFailure);
  }

  protected CompletableFuture<FastSyncState> handleFailure(final Throwable error) {
    trailingPeerRequirements = Optional.empty();
    if (ExceptionUtils.rootCause(error) instanceof FastSyncException) {
      return CompletableFuture.failedFuture(error);
    } else if (ExceptionUtils.rootCause(error) instanceof StalledDownloadException) {
      LOG.info("Re-pivoting to newer block.");
      return actionOnStalledDownloadException();
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

  protected CompletableFuture<FastSyncState> actionOnStalledDownloadException() {
    return start(FastSyncState.EMPTY_SYNC_STATE);
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

  protected FastSyncState updateMaxTrailingPeers(final FastSyncState state) {
    if (state.getPivotBlockNumber().isPresent()) {
      trailingPeerRequirements =
          Optional.of(new TrailingPeerRequirements(state.getPivotBlockNumber().getAsLong(), 0));
    } else {
      trailingPeerRequirements = Optional.empty();
    }
    return state;
  }

  protected FastSyncState storeState(final FastSyncState state) {
    initialFastSyncState = state;
    fastSyncStateStorage.storeState(state);
    return state;
  }

  protected CompletableFuture<FastSyncState> downloadChainAndWorldState(
      final FastSyncActions fastSyncActions, final FastSyncState currentState) {
    // Synchronized ensures that stop isn't called while we're in the process of starting a
    // world state and chain download. If it did we might wind up starting a new download
    // after the stop method had called cancel.
    synchronized (this) {
      if (!running.get()) {
        return CompletableFuture.failedFuture(
            new CancellationException("FastSyncDownloader stopped"));
      }
      final CompletableFuture<Void> worldStateFuture =
          worldStateDownloader.run(fastSyncActions, currentState);
      final ChainDownloader chainDownloader =
          fastSyncActions.createFastChainDownloader(currentState);
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
