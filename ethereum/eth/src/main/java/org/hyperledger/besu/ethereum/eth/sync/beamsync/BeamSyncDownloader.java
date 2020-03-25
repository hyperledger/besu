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
package org.hyperledger.besu.ethereum.eth.sync.beamsync;

import static org.hyperledger.besu.util.FutureUtils.completedExceptionally;
import static org.hyperledger.besu.util.FutureUtils.exceptionallyCompose;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.eth.sync.ChainDownloader;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.TrailingPeerRequirements;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.StalledDownloadException;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.util.ExceptionUtils;

import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BeamSyncDownloader<C> {
  private static final Logger LOG = LogManager.getLogger();
  private final BeamSyncActions<C> beamSyncActions;
  private final SynchronizerConfiguration syncConfig;
  private final ProtocolContext<C> protocolContext;
  private final SyncState syncState;
  private final BeamSyncStateStorage beamSyncStateStorage;
  private final BeamSyncState initialBeamSyncState;
  private volatile Optional<TrailingPeerRequirements> trailingPeerRequirements = Optional.empty();
  private final AtomicBoolean running = new AtomicBoolean(false);

  public BeamSyncDownloader(
      final BeamSyncActions<C> beamSyncActions,
      final SynchronizerConfiguration syncConfig,
      final ProtocolContext<C> protocolContext,
      final SyncState syncState,
      final BeamSyncStateStorage beamSyncStateStorage,
      final MetricsSystem metricsSystem,
      final BeamSyncState initialBeamSyncState) {
    this.beamSyncActions = beamSyncActions;
    this.syncConfig = syncConfig;
    this.protocolContext = protocolContext;
    this.syncState = syncState;
    this.beamSyncStateStorage = beamSyncStateStorage;
    this.initialBeamSyncState = initialBeamSyncState;
  }

  public CompletableFuture<BeamSyncState> start() {
    if (!running.compareAndSet(false, true)) {
      throw new IllegalStateException("BeamSyncDownloader already running");
    }
    return start(initialBeamSyncState);
  }

  private CompletableFuture<BeamSyncState> start(final BeamSyncState beamSyncState) {
    return exceptionallyCompose(
        beamSyncActions
            .waitForSuitablePeers(beamSyncState)
            .thenCompose(beamSyncActions::selectLaunchBlock)
            .thenCompose(beamSyncActions::downloadLaunchBlockHeader)
            .thenApply(this::updateMaxTrailingPeers)
            .thenApply(this::storeState)
            .thenCompose(this::downloadChain),
        this::handleWorldStateUnavailable);
  }

  public void stop() {
    synchronized (this) {
      if (running.compareAndSet(true, false)) {
        // Cancelling the world state download will also cause the chain download to be cancelled.
        // TODO: Stop the chain download/chain future.
      }
    }
  }

  // TODO: Remove this as it is not applicable to BeamSync.
  private CompletableFuture<BeamSyncState> handleWorldStateUnavailable(final Throwable error) {
    trailingPeerRequirements = Optional.empty();
    if (ExceptionUtils.rootCause(error) instanceof StalledDownloadException) {
      LOG.warn(
          "Beam sync was unable to download the world state. Retrying with a new pivot block.");
      return start(BeamSyncState.EMPTY_SYNC_STATE);
    } else {
      return completedExceptionally(error);
    }
  }

  public TrailingPeerRequirements calculateTrailingPeerRequirements() {
    return syncState.isInSync()
        ? TrailingPeerRequirements.UNRESTRICTED
        : new TrailingPeerRequirements(
            protocolContext.getBlockchain().getChainHeadBlockNumber(),
            syncConfig.getMaxTrailingPeers());
  }

  private BeamSyncState updateMaxTrailingPeers(final BeamSyncState state) {
    if (state.getLaunchBlockNumber().isPresent()) {
      trailingPeerRequirements =
          Optional.of(new TrailingPeerRequirements(state.getLaunchBlockNumber().getAsLong(), 0));
    } else {
      trailingPeerRequirements = Optional.empty();
    }
    return state;
  }

  private BeamSyncState storeState(final BeamSyncState state) {
    beamSyncStateStorage.storeState(state);
    return state;
  }

  private CompletableFuture<BeamSyncState> downloadChain(final BeamSyncState currentState) {
    // Synchronized ensures that stop isn't called while we're in the process of starting a
    // world state and chain download. If it did we might wind up starting a new download
    // after the stop method had called cancel.
    synchronized (this) {
      if (!running.get()) {
        return completedExceptionally(new CancellationException("BeamSyncDownloader stopped"));
      }
      final ChainDownloader chainDownloader = beamSyncActions.createChainDownloader(currentState);
      final CompletableFuture<Void> chainFuture = chainDownloader.start();

      // If either download fails, cancel the other one.
      chainFuture.exceptionally(
          error -> {
            // TODO: WorldStateDownloader is not as in FastSync. Log and move on for now.
            // worldStateFuture.cancel(true);
            return null;
          });

      return CompletableFuture.allOf(chainFuture)
          .thenApply(
              complete -> {
                trailingPeerRequirements = Optional.empty();
                return currentState;
              });
    }
  }
}
