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
package org.hyperledger.besu.ethereum.eth.sync.fastsync.worldstate;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncActions;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldStateDownloader;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.metrics.SyncDurationMetrics;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.services.tasks.InMemoryTasksPriorityQueues;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.IntSupplier;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FastWorldStateDownloader implements WorldStateDownloader {
  private static final Logger LOG = LoggerFactory.getLogger(FastWorldStateDownloader.class);

  private final long minMillisBeforeStalling;
  private final Clock clock;
  private final MetricsSystem metricsSystem;

  private final EthContext ethContext;
  private final InMemoryTasksPriorityQueues<NodeDataRequest> taskCollection;
  private final int hashCountPerRequest;
  private final int maxOutstandingRequests;
  private final int maxNodeRequestsWithoutProgress;
  private final WorldStateStorageCoordinator worldStateStorageCoordinator;

  private final AtomicReference<FastWorldDownloadState> downloadState = new AtomicReference<>();
  private final SyncDurationMetrics syncDurationMetrics;

  private Optional<CompleteTaskStep> maybeCompleteTask = Optional.empty();

  public FastWorldStateDownloader(
      final EthContext ethContext,
      final WorldStateStorageCoordinator worldStateStorageCoordinator,
      final InMemoryTasksPriorityQueues<NodeDataRequest> taskCollection,
      final int hashCountPerRequest,
      final int maxOutstandingRequests,
      final int maxNodeRequestsWithoutProgress,
      final long minMillisBeforeStalling,
      final Clock clock,
      final MetricsSystem metricsSystem,
      final SyncDurationMetrics syncDurationMetrics) {
    this.ethContext = ethContext;
    this.worldStateStorageCoordinator = worldStateStorageCoordinator;
    this.taskCollection = taskCollection;
    this.hashCountPerRequest = hashCountPerRequest;
    this.maxOutstandingRequests = maxOutstandingRequests;
    this.maxNodeRequestsWithoutProgress = maxNodeRequestsWithoutProgress;
    this.minMillisBeforeStalling = minMillisBeforeStalling;
    this.clock = clock;
    this.metricsSystem = metricsSystem;
    this.syncDurationMetrics = syncDurationMetrics;

    metricsSystem.createIntegerGauge(
        BesuMetricCategory.SYNCHRONIZER,
        "world_state_node_requests_since_last_progress_current",
        "Number of world state requests made since the last time new data was returned",
        downloadStateValue(FastWorldDownloadState::getRequestsSinceLastProgress));

    metricsSystem.createIntegerGauge(
        BesuMetricCategory.SYNCHRONIZER,
        "world_state_inflight_requests_current",
        "Number of in progress requests for world state data",
        downloadStateValue(FastWorldDownloadState::getOutstandingTaskCount));
  }

  private IntSupplier downloadStateValue(final Function<FastWorldDownloadState, Integer> getter) {
    return () -> {
      final FastWorldDownloadState state = this.downloadState.get();
      return state != null ? getter.apply(state) : 0;
    };
  }

  @Override
  public CompletableFuture<Void> run(
      final FastSyncActions fastSyncActions, final FastSyncState fastSyncState) {
    synchronized (this) {
      final FastWorldDownloadState oldDownloadState = this.downloadState.get();
      if (oldDownloadState != null && oldDownloadState.isDownloading()) {
        final CompletableFuture<Void> failed = new CompletableFuture<>();
        failed.completeExceptionally(
            new IllegalStateException(
                "Cannot run an already running " + this.getClass().getSimpleName()));
        return failed;
      }

      Optional<BlockHeader> checkNull = Optional.of(fastSyncState.getPivotBlockHeader().get());
      if (checkNull.isEmpty()) {
        LOG.error("Pivot Block not present");
        final CompletableFuture<Void> failed = new CompletableFuture<>();
        failed.completeExceptionally(new NullPointerException("Pivot Block not present"));
        return failed;
      }

      final BlockHeader header = fastSyncState.getPivotBlockHeader().get();
      final Hash stateRoot = header.getStateRoot();
      if (worldStateStorageCoordinator.isWorldStateAvailable(stateRoot, header.getHash())) {
        LOG.info(
            "World state already available for block {} ({}). State root {}",
            header.getNumber(),
            header.getHash(),
            stateRoot);
        return CompletableFuture.completedFuture(null);
      }
      LOG.info(
          "Begin downloading world state from peers for block {} ({}). State root {}",
          header.getNumber(),
          header.getHash(),
          stateRoot);

      final FastWorldDownloadState newDownloadState =
          new FastWorldDownloadState(
              worldStateStorageCoordinator,
              taskCollection,
              maxNodeRequestsWithoutProgress,
              minMillisBeforeStalling,
              clock,
              syncDurationMetrics);
      this.downloadState.set(newDownloadState);

      if (!newDownloadState.downloadWasResumed()) {
        // Only queue the root node if we're starting a new download from scratch
        newDownloadState.enqueueRequest(
            NodeDataRequest.createAccountDataRequest(stateRoot, Optional.of(Bytes.EMPTY)));
      }

      maybeCompleteTask = Optional.of(new CompleteTaskStep(metricsSystem, taskCollection::size));
      final FastWorldStateDownloadProcess downloadProcess =
          FastWorldStateDownloadProcess.builder()
              .hashCountPerRequest(hashCountPerRequest)
              .maxOutstandingRequests(maxOutstandingRequests)
              .loadLocalDataStep(new LoadLocalDataStep(worldStateStorageCoordinator, metricsSystem))
              .requestDataStep(new RequestDataStep(ethContext, metricsSystem))
              .persistDataStep(new PersistDataStep(worldStateStorageCoordinator))
              .completeTaskStep(maybeCompleteTask.get())
              .downloadState(newDownloadState)
              .pivotBlockHeader(header)
              .metricsSystem(metricsSystem)
              .build();

      newDownloadState.setWorldStateDownloadProcess(downloadProcess);

      return newDownloadState.startDownload(downloadProcess, ethContext.getScheduler());
    }
  }

  @Override
  public void cancel() {
    synchronized (this) {
      final FastWorldDownloadState downloadState = this.downloadState.get();
      if (downloadState != null) {
        downloadState.getDownloadFuture().cancel(true);
      }
    }
  }

  @Override
  public Optional<Long> getPulledStates() {
    return maybeCompleteTask.map(CompleteTaskStep::getCompletedRequests);
  }

  @Override
  public Optional<Long> getKnownStates() {
    return maybeCompleteTask.map(task -> task.getCompletedRequests() + task.getPendingRequests());
  }
}
