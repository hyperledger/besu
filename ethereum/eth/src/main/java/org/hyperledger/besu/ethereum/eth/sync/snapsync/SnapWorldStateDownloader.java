/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncActions;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.worldstate.FastWorldStateDownloader;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.worldstate.NodeDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldStateDownloader;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.services.tasks.CachingTaskCollection;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.IntSupplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SnapWorldStateDownloader implements WorldStateDownloader {
  private static final Logger LOG = LogManager.getLogger();

  private final long minMillisBeforeStalling;
  private final Clock clock;
  private final MetricsSystem metricsSystem;

  private final EthContext ethContext;
  private final CachingTaskCollection<SnapDataRequest> snapTaskCollection;
  private final CachingTaskCollection<NodeDataRequest> fastTaskCollection;
  private final int hashCountPerRequest;
  private final int maxOutstandingRequests;
  private final int maxNodeRequestsWithoutProgress;
  private final WorldStateStorage worldStateStorage;
  private SnapWorldStateDownloadProcess downloadProcess;

  private final AtomicReference<SnapWorldDownloadState> downloadState = new AtomicReference<>();

  private Optional<CompleteTaskStep> maybeCompleteTask = Optional.empty();

  public SnapWorldStateDownloader(
      final EthContext ethContext,
      final WorldStateStorage worldStateStorage,
      final CachingTaskCollection<SnapDataRequest> snapTaskCollection,
      final CachingTaskCollection<NodeDataRequest> fastTaskCollection,
      final int hashCountPerRequest,
      final int maxOutstandingRequests,
      final int maxNodeRequestsWithoutProgress,
      final long minMillisBeforeStalling,
      final Clock clock,
      final MetricsSystem metricsSystem) {
    this.ethContext = ethContext;
    this.worldStateStorage = worldStateStorage;
    this.snapTaskCollection = snapTaskCollection;
    this.fastTaskCollection = fastTaskCollection;
    this.hashCountPerRequest = hashCountPerRequest;
    this.maxOutstandingRequests = maxOutstandingRequests;
    this.maxNodeRequestsWithoutProgress = maxNodeRequestsWithoutProgress;
    this.minMillisBeforeStalling = minMillisBeforeStalling;
    this.clock = clock;
    this.metricsSystem = metricsSystem;

    metricsSystem.createIntegerGauge(
        BesuMetricCategory.SYNCHRONIZER,
        "snap_world_state_node_requests_since_last_progress_current",
        "Number of world state requests made since the last time new data was returned",
        downloadStateValue(SnapWorldDownloadState::getRequestsSinceLastProgress));

    metricsSystem.createIntegerGauge(
        BesuMetricCategory.SYNCHRONIZER,
        "snap_world_state_inflight_requests_current",
        "Number of in progress requests for world state data",
        downloadStateValue(SnapWorldDownloadState::getOutstandingTaskCount));
  }

  private IntSupplier downloadStateValue(final Function<SnapWorldDownloadState, Integer> getter) {
    return () -> {
      final SnapWorldDownloadState state = this.downloadState.get();
      return state != null ? getter.apply(state) : 0;
    };
  }

  @Override
  public CompletableFuture<Void> run(
      final FastSyncActions fastSyncActions, final FastSyncState fastSyncState) {
    synchronized (this) {
      final SnapWorldDownloadState oldDownloadState = this.downloadState.get();
      if (oldDownloadState != null && oldDownloadState.isDownloading()) {
        final CompletableFuture<Void> failed = new CompletableFuture<>();
        failed.completeExceptionally(
            new IllegalStateException(
                "Cannot run an already running " + this.getClass().getSimpleName()));
        return failed;
      }

      final SnapSyncState snapSyncState = (SnapSyncState) fastSyncState;
      final BlockHeader header = fastSyncState.getPivotBlockHeader().get();
      final Hash stateRoot = header.getStateRoot();

      LOG.info(
          "Downloading world state from peers for block {} ({}). State root {} pending request "
              + snapTaskCollection.size(),
          header.getNumber(),
          header.getHash(),
          stateRoot);

      final FastWorldStateDownloader healProcess = getHealProcess();

      final SnapWorldDownloadState newDownloadState =
          new SnapWorldDownloadState(
              fastSyncActions,
              snapSyncState,
              snapTaskCollection,
              maxNodeRequestsWithoutProgress,
              minMillisBeforeStalling,
              healProcess,
              clock);

      if (!newDownloadState.downloadWasResumed()) {
        RangeManager.generateAllRanges(256)
            .forEach(
                (key, value) ->
                    newDownloadState.enqueueRequest(
                        SnapDataRequest.createAccountRangeDataRequest(stateRoot, key, value)));
      }

      maybeCompleteTask =
          Optional.of(
              new CompleteTaskStep(worldStateStorage, metricsSystem, snapTaskCollection::size));

      downloadProcess =
          SnapWorldStateDownloadProcess.builder()
              .hashCountPerRequest(hashCountPerRequest)
              .maxOutstandingRequests(maxOutstandingRequests)
              .requestDataStep(new RequestDataStep(ethContext, worldStateStorage, metricsSystem))
              .persistDataStep(new PersistDataStep(worldStateStorage, metricsSystem))
              .completeTaskStep(maybeCompleteTask.get())
              .downloadState(newDownloadState)
              .fastSyncState(snapSyncState)
              .metricsSystem(metricsSystem)
              .build();

      newDownloadState.setWorldStateDownloadProcess(downloadProcess);

      return newDownloadState.startDownload(downloadProcess, ethContext.getScheduler());
    }
  }

  @Override
  public void cancel() {
    synchronized (this) {
      final SnapWorldDownloadState downloadState = this.downloadState.get();
      if (downloadState != null) {
        downloadState.getDownloadFuture().cancel(true);
      }
    }
  }

  public FastWorldStateDownloader getHealProcess() {
    return new FastWorldStateDownloader(
        ethContext,
        worldStateStorage,
        fastTaskCollection,
        hashCountPerRequest,
        maxOutstandingRequests,
        maxNodeRequestsWithoutProgress,
        minMillisBeforeStalling,
        clock,
        metricsSystem);
  }

  @Override
  public Optional<Long> getPulledStates() {
    return Optional.empty();
  }

  @Override
  public Optional<Long> getKnownStates() {
    return Optional.empty();
  }
}
