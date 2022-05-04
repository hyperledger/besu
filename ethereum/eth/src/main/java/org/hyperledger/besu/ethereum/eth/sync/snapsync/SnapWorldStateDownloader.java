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

import static org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest.createAccountRangeDataRequest;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncActions;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldStateDownloader;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.services.tasks.InMemoryTasksPriorityQueues;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.IntSupplier;

import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnapWorldStateDownloader implements WorldStateDownloader {

  private static final Logger LOG = LoggerFactory.getLogger(SnapWorldStateDownloader.class);
  private final long minMillisBeforeStalling;
  private final Clock clock;
  private final MetricsSystem metricsSystem;

  private final EthContext ethContext;
  private final InMemoryTasksPriorityQueues<SnapDataRequest> snapTaskCollection;
  private final SnapSyncConfiguration snapSyncConfiguration;
  private final int maxOutstandingRequests;
  private final int maxNodeRequestsWithoutProgress;
  private final WorldStateStorage worldStateStorage;
  private SnapWorldStateDownloadProcess downloadProcess;

  private final AtomicReference<SnapWorldDownloadState> downloadState = new AtomicReference<>();

  private Optional<CompleteTaskStep> maybeCompleteTask = Optional.empty();

  public SnapWorldStateDownloader(
      final EthContext ethContext,
      final WorldStateStorage worldStateStorage,
      final InMemoryTasksPriorityQueues<SnapDataRequest> snapTaskCollection,
      final SnapSyncConfiguration snapSyncConfiguration,
      final int maxOutstandingRequests,
      final int maxNodeRequestsWithoutProgress,
      final long minMillisBeforeStalling,
      final Clock clock,
      final MetricsSystem metricsSystem) {
    this.ethContext = ethContext;
    this.worldStateStorage = worldStateStorage;
    this.snapTaskCollection = snapTaskCollection;
    this.snapSyncConfiguration = snapSyncConfiguration;
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

      final SnapsyncMetricsManager snapsyncMetricsManager =
          new SnapsyncMetricsManager(metricsSystem);

      final SnapWorldDownloadState newDownloadState =
          new SnapWorldDownloadState(
              worldStateStorage,
              snapSyncState,
              snapTaskCollection,
              maxNodeRequestsWithoutProgress,
              minMillisBeforeStalling,
              snapsyncMetricsManager,
              clock);

      final Map<Bytes32, Bytes32> ranges = RangeManager.generateAllRanges(16);
      snapsyncMetricsManager.initRange(ranges);
      ranges.forEach(
          (key, value) ->
              newDownloadState.enqueueRequest(
                  createAccountRangeDataRequest(stateRoot, key, value)));

      maybeCompleteTask = Optional.of(new CompleteTaskStep(snapSyncState, metricsSystem));

      final DynamicPivotBlockManager dynamicPivotBlockManager =
          new DynamicPivotBlockManager(
              fastSyncActions,
              snapSyncState,
              snapSyncConfiguration.getPivotBlockWindowValidity(),
              snapSyncConfiguration.getPivotBlockDistanceBeforeCaching());

      downloadProcess =
          SnapWorldStateDownloadProcess.builder()
              .configuration(snapSyncConfiguration)
              .maxOutstandingRequests(maxOutstandingRequests)
              .pivotBlockManager(dynamicPivotBlockManager)
              .loadLocalDataStep(
                  new LoadLocalDataStep(
                      worldStateStorage, newDownloadState, metricsSystem, snapSyncState))
              .requestDataStep(
                  new RequestDataStep(
                      ethContext,
                      worldStateStorage,
                      snapSyncState,
                      newDownloadState,
                      metricsSystem))
              .persistDataStep(
                  new PersistDataStep(snapSyncState, worldStateStorage, newDownloadState))
              .completeTaskStep(maybeCompleteTask.get())
              .downloadState(newDownloadState)
              .fastSyncState(snapSyncState)
              .metricsSystem(metricsSystem)
              .build();

      newDownloadState.setDynamicPivotBlockManager(dynamicPivotBlockManager);

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

  @Override
  public Optional<Long> getPulledStates() {
    return Optional.empty();
  }

  @Override
  public Optional<Long> getKnownStates() {
    return Optional.empty();
  }
}
