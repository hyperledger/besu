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
package org.hyperledger.besu.ethereum.eth.sync.worldstate;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
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
import org.apache.tuweni.bytes.Bytes;

public class WorldStateDownloader implements WorldStateDownloadStatus {
  private static final Logger LOG = LogManager.getLogger();

  private final long minMillisBeforeStalling;
  private final Clock clock;
  private final MetricsSystem metricsSystem;

  private final EthContext ethContext;
  private final CachingTaskCollection<NodeDataRequest> taskCollection;
  private final int hashCountPerRequest;
  private final int maxOutstandingRequests;
  private final int maxNodeRequestsWithoutProgress;
  private final WorldStateStorage worldStateStorage;

  private final AtomicReference<WorldDownloadState> downloadState = new AtomicReference<>();

  private Optional<CompleteTaskStep> maybeCompleteTask = Optional.empty();

  public WorldStateDownloader(
      final EthContext ethContext,
      final WorldStateStorage worldStateStorage,
      final CachingTaskCollection<NodeDataRequest> taskCollection,
      final int hashCountPerRequest,
      final int maxOutstandingRequests,
      final int maxNodeRequestsWithoutProgress,
      final long minMillisBeforeStalling,
      final Clock clock,
      final MetricsSystem metricsSystem) {
    this.ethContext = ethContext;
    this.worldStateStorage = worldStateStorage;
    this.taskCollection = taskCollection;
    this.hashCountPerRequest = hashCountPerRequest;
    this.maxOutstandingRequests = maxOutstandingRequests;
    this.maxNodeRequestsWithoutProgress = maxNodeRequestsWithoutProgress;
    this.minMillisBeforeStalling = minMillisBeforeStalling;
    this.clock = clock;
    this.metricsSystem = metricsSystem;

    metricsSystem.createIntegerGauge(
        BesuMetricCategory.SYNCHRONIZER,
        "world_state_node_requests_since_last_progress_current",
        "Number of world state requests made since the last time new data was returned",
        downloadStateValue(WorldDownloadState::getRequestsSinceLastProgress));

    metricsSystem.createIntegerGauge(
        BesuMetricCategory.SYNCHRONIZER,
        "world_state_inflight_requests_current",
        "Number of in progress requests for world state data",
        downloadStateValue(WorldDownloadState::getOutstandingTaskCount));
  }

  private IntSupplier downloadStateValue(final Function<WorldDownloadState, Integer> getter) {
    return () -> {
      final WorldDownloadState state = this.downloadState.get();
      return state != null ? getter.apply(state) : 0;
    };
  }

  public CompletableFuture<Void> run(final BlockHeader header) {
    synchronized (this) {
      final WorldDownloadState oldDownloadState = this.downloadState.get();
      if (oldDownloadState != null && oldDownloadState.isDownloading()) {
        final CompletableFuture<Void> failed = new CompletableFuture<>();
        failed.completeExceptionally(
            new IllegalStateException(
                "Cannot run an already running " + this.getClass().getSimpleName()));
        return failed;
      }

      final Hash stateRoot = header.getStateRoot();
      if (worldStateStorage.isWorldStateAvailable(stateRoot, header.getHash())) {
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

      final WorldDownloadState newDownloadState =
          new WorldDownloadState(
              taskCollection, maxNodeRequestsWithoutProgress, minMillisBeforeStalling, clock);
      this.downloadState.set(newDownloadState);

      if (!newDownloadState.downloadWasResumed()) {
        // Only queue the root node if we're starting a new download from scratch
        newDownloadState.enqueueRequest(
            NodeDataRequest.createAccountDataRequest(stateRoot, Optional.of(Bytes.EMPTY)));
      }

      maybeCompleteTask =
          Optional.of(new CompleteTaskStep(worldStateStorage, metricsSystem, taskCollection::size));
      final WorldStateDownloadProcess downloadProcess =
          WorldStateDownloadProcess.builder()
              .hashCountPerRequest(hashCountPerRequest)
              .maxOutstandingRequests(maxOutstandingRequests)
              .loadLocalDataStep(new LoadLocalDataStep(worldStateStorage, metricsSystem))
              .requestDataStep(new RequestDataStep(ethContext, metricsSystem))
              .persistDataStep(new PersistDataStep(worldStateStorage))
              .completeTaskStep(maybeCompleteTask.get())
              .downloadState(newDownloadState)
              .pivotBlockHeader(header)
              .metricsSystem(metricsSystem)
              .build();

      newDownloadState.setWorldStateDownloadProcess(downloadProcess);

      return newDownloadState.startDownload(downloadProcess, ethContext.getScheduler());
    }
  }

  public void cancel() {
    synchronized (this) {
      final WorldDownloadState downloadState = this.downloadState.get();
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
