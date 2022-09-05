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

import static com.google.common.base.Preconditions.checkNotNull;
import static org.hyperledger.besu.ethereum.eth.sync.snapsync.DynamicPivotBlockManager.doNothingOnPivotChange;
import static org.hyperledger.besu.services.pipeline.PipelineBuilder.createPipelineFrom;

import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.BytecodeRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.TaskQueueIterator;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldStateDownloadProcess;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.services.pipeline.Pipe;
import org.hyperledger.besu.services.pipeline.Pipeline;
import org.hyperledger.besu.services.pipeline.PipelineBuilder;
import org.hyperledger.besu.services.pipeline.WritePipe;
import org.hyperledger.besu.services.tasks.Task;
import org.hyperledger.besu.util.ExceptionUtils;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnapWorldStateDownloadProcess implements WorldStateDownloadProcess {

  private static final Logger LOG = LoggerFactory.getLogger(SnapWorldStateDownloadProcess.class);
  private final Pipeline<Task<SnapDataRequest>> completionPipeline;
  private final Pipeline<Task<SnapDataRequest>> fetchAccountPipeline;
  private final Pipeline<Task<SnapDataRequest>> fetchStorageDataPipeline;
  private final Pipeline<Task<SnapDataRequest>> fetchBigStorageDataPipeline;
  private final Pipeline<Task<SnapDataRequest>> fetchCodePipeline;
  private final Pipeline<Task<SnapDataRequest>> fetchHealPipeline;
  private final WritePipe<Task<SnapDataRequest>> requestsToComplete;

  private SnapWorldStateDownloadProcess(
      final Pipeline<Task<SnapDataRequest>> fetchAccountPipeline,
      final Pipeline<Task<SnapDataRequest>> fetchStorageDataPipeline,
      final Pipeline<Task<SnapDataRequest>> fetchBigStorageDataPipeline,
      final Pipeline<Task<SnapDataRequest>> fetchCodePipeline,
      final Pipeline<Task<SnapDataRequest>> fetchHealPipeline,
      final Pipeline<Task<SnapDataRequest>> completionPipeline,
      final WritePipe<Task<SnapDataRequest>> requestsToComplete) {
    this.fetchStorageDataPipeline = fetchStorageDataPipeline;
    this.fetchAccountPipeline = fetchAccountPipeline;
    this.fetchBigStorageDataPipeline = fetchBigStorageDataPipeline;
    this.fetchCodePipeline = fetchCodePipeline;
    this.fetchHealPipeline = fetchHealPipeline;
    this.completionPipeline = completionPipeline;
    this.requestsToComplete = requestsToComplete;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public CompletableFuture<Void> start(final EthScheduler ethScheduler) {
    final CompletableFuture<Void> fetchAccountFuture =
        ethScheduler.startPipeline(fetchAccountPipeline);
    final CompletableFuture<Void> fetchStorageFuture =
        ethScheduler.startPipeline(fetchStorageDataPipeline);
    final CompletableFuture<Void> fetchBigStorageFuture =
        ethScheduler.startPipeline(fetchBigStorageDataPipeline);
    final CompletableFuture<Void> fetchCodeFuture = ethScheduler.startPipeline(fetchCodePipeline);
    final CompletableFuture<Void> fetchHealFuture = ethScheduler.startPipeline(fetchHealPipeline);
    final CompletableFuture<Void> completionFuture = ethScheduler.startPipeline(completionPipeline);

    fetchAccountFuture
        .thenCombine(fetchStorageFuture, (unused, unused2) -> null)
        .thenCombine(fetchBigStorageFuture, (unused, unused2) -> null)
        .thenCombine(fetchCodeFuture, (unused, unused2) -> null)
        .thenCombine(fetchHealFuture, (unused, unused2) -> null)
        .whenComplete(
            (result, error) -> {
              if (error != null) {
                if (!(ExceptionUtils.rootCause(error) instanceof CancellationException)) {
                  LOG.error("Pipeline failed", error);
                }
                completionPipeline.abort();
              } else {
                // No more data to fetch, so propagate the pipe closure onto the completion pipe.
                requestsToComplete.close();
              }
            });

    completionFuture.exceptionally(
        error -> {
          if (!(ExceptionUtils.rootCause(error) instanceof CancellationException)) {
            LOG.error("Pipeline failed", error);
          }
          fetchAccountPipeline.abort();
          fetchStorageDataPipeline.abort();
          fetchBigStorageDataPipeline.abort();
          fetchCodePipeline.abort();
          fetchHealPipeline.abort();
          return null;
        });
    return completionFuture;
  }

  @Override
  public void abort() {
    fetchAccountPipeline.abort();
    fetchStorageDataPipeline.abort();
    fetchBigStorageDataPipeline.abort();
    fetchCodePipeline.abort();
    fetchHealPipeline.abort();
    completionPipeline.abort();
  }

  public static class Builder {

    private SnapSyncConfiguration snapSyncConfiguration;
    private int maxOutstandingRequests;
    private SnapWorldDownloadState downloadState;
    private MetricsSystem metricsSystem;
    private LoadLocalDataStep loadLocalDataStep;
    private RequestDataStep requestDataStep;
    private SnapSyncState snapSyncState;
    private PersistDataStep persistDataStep;
    private CompleteTaskStep completeTaskStep;
    private DynamicPivotBlockManager pivotBlockManager;

    public Builder configuration(final SnapSyncConfiguration snapSyncConfiguration) {
      this.snapSyncConfiguration = snapSyncConfiguration;
      return this;
    }

    public Builder pivotBlockManager(final DynamicPivotBlockManager pivotBlockManager) {
      this.pivotBlockManager = pivotBlockManager;
      return this;
    }

    public Builder maxOutstandingRequests(final int maxOutstandingRequests) {
      this.maxOutstandingRequests = maxOutstandingRequests;
      return this;
    }

    public Builder loadLocalDataStep(final LoadLocalDataStep loadLocalDataStep) {
      this.loadLocalDataStep = loadLocalDataStep;
      return this;
    }

    public Builder requestDataStep(final RequestDataStep requestDataStep) {
      this.requestDataStep = requestDataStep;
      return this;
    }

    public Builder persistDataStep(final PersistDataStep persistDataStep) {
      this.persistDataStep = persistDataStep;
      return this;
    }

    public Builder completeTaskStep(final CompleteTaskStep completeTaskStep) {
      this.completeTaskStep = completeTaskStep;
      return this;
    }

    public Builder downloadState(final SnapWorldDownloadState downloadState) {
      this.downloadState = downloadState;
      return this;
    }

    public Builder fastSyncState(final SnapSyncState fastSyncState) {
      this.snapSyncState = fastSyncState;
      return this;
    }

    public Builder metricsSystem(final MetricsSystem metricsSystem) {
      this.metricsSystem = metricsSystem;
      return this;
    }

    public SnapWorldStateDownloadProcess build() {
      checkNotNull(loadLocalDataStep);
      checkNotNull(requestDataStep);
      checkNotNull(persistDataStep);
      checkNotNull(completeTaskStep);
      checkNotNull(downloadState);
      checkNotNull(snapSyncState);
      checkNotNull(metricsSystem);

      // Room for the requests we expect to do in parallel plus some buffer but not unlimited.
      final int bufferCapacity = snapSyncConfiguration.getTrienodeCountPerRequest() * 2;
      final LabelledMetric<Counter> outputCounter =
          metricsSystem.createLabelledCounter(
              BesuMetricCategory.SYNCHRONIZER,
              "snap_world_state_pipeline_processed_total",
              "Number of entries processed by each world state download pipeline stage",
              "step",
              "action");

      final Pipeline<Task<SnapDataRequest>> completionPipeline =
          PipelineBuilder.<Task<SnapDataRequest>>createPipeline(
                  "requestDataAvailable", bufferCapacity, outputCounter, true, "node_data_request")
              .andFinishWith(
                  "requestCompleteTask",
                  task -> completeTaskStep.markAsCompleteOrFailed(downloadState, task));

      final Pipe<Task<SnapDataRequest>> requestsToComplete = completionPipeline.getInputPipe();

      final Pipeline<Task<SnapDataRequest>> fetchAccountDataPipeline =
          createPipelineFrom(
                  "dequeueAccountRequestBlocking",
                  new TaskQueueIterator<>(
                      downloadState, () -> downloadState.dequeueAccountRequestBlocking()),
                  bufferCapacity,
                  outputCounter,
                  true,
                  "world_state_download")
              .thenProcess(
                  "checkNewPivotBlock",
                  tasks -> {
                    pivotBlockManager.check(doNothingOnPivotChange);
                    return tasks;
                  })
              .thenProcessAsync(
                  "batchDownloadAccountData",
                  requestTask -> requestDataStep.requestAccount(requestTask),
                  maxOutstandingRequests)
              .thenProcess("batchPersistAccountData", task -> persistDataStep.persist(task))
              .andFinishWith("batchAccountDataDownloaded", requestsToComplete::put);

      final Pipeline<Task<SnapDataRequest>> fetchStorageDataPipeline =
          createPipelineFrom(
                  "dequeueStorageRequestBlocking",
                  new TaskQueueIterator<>(
                      downloadState, () -> downloadState.dequeueStorageRequestBlocking()),
                  bufferCapacity,
                  outputCounter,
                  true,
                  "world_state_download")
              .inBatches(snapSyncConfiguration.getStorageCountPerRequest())
              .thenProcess(
                  "checkNewPivotBlock",
                  tasks -> {
                    pivotBlockManager.check(doNothingOnPivotChange);
                    return tasks;
                  })
              .thenProcessAsyncOrdered(
                  "batchDownloadStorageData",
                  requestTask -> requestDataStep.requestStorage(requestTask),
                  maxOutstandingRequests)
              .thenProcess("batchPersistStorageData", task -> persistDataStep.persist(task))
              .andFinishWith(
                  "batchStorageDataDownloaded",
                  tasks -> {
                    tasks.forEach(requestsToComplete::put);
                  });

      final Pipeline<Task<SnapDataRequest>> fetchBigStorageDataPipeline =
          createPipelineFrom(
                  "dequeueBigStorageRequestBlocking",
                  new TaskQueueIterator<>(
                      downloadState, () -> downloadState.dequeueBigStorageRequestBlocking()),
                  bufferCapacity,
                  outputCounter,
                  true,
                  "world_state_download")
              .thenProcess(
                  "checkNewPivotBlock",
                  tasks -> {
                    pivotBlockManager.check(doNothingOnPivotChange);
                    return tasks;
                  })
              .thenProcessAsyncOrdered(
                  "batchDownloadBigStorageData",
                  requestTask -> requestDataStep.requestStorage(List.of(requestTask)),
                  maxOutstandingRequests)
              .thenProcess(
                  "batchPersistBigStorageData",
                  task -> {
                    persistDataStep.persist(task);
                    return task;
                  })
              .andFinishWith(
                  "batchBigStorageDataDownloaded", tasks -> tasks.forEach(requestsToComplete::put));

      final Pipeline<Task<SnapDataRequest>> fetchCodePipeline =
          createPipelineFrom(
                  "dequeueCodeRequestBlocking",
                  new TaskQueueIterator<>(
                      downloadState, () -> downloadState.dequeueCodeRequestBlocking()),
                  bufferCapacity,
                  outputCounter,
                  true,
                  "code_blocks_download_pipeline")
              .inBatches(
                  snapSyncConfiguration.getBytecodeCountPerRequest() * 2,
                  tasks ->
                      snapSyncConfiguration.getBytecodeCountPerRequest()
                          - (int)
                              tasks.stream()
                                  .map(Task::getData)
                                  .map(BytecodeRequest.class::cast)
                                  .map(BytecodeRequest::getCodeHash)
                                  .distinct()
                                  .count())
              .thenProcess(
                  "checkNewPivotBlock",
                  tasks -> {
                    pivotBlockManager.check(
                        (blockHeader, newBlockFound) ->
                            reloadHealWhenNeeded(snapSyncState, downloadState, newBlockFound));
                    return tasks;
                  })
              .thenProcessAsyncOrdered(
                  "batchDownloadCodeData",
                  tasks -> requestDataStep.requestCode(tasks),
                  maxOutstandingRequests)
              .thenProcess(
                  "batchPersistCodeData",
                  tasks -> {
                    persistDataStep.persist(tasks);
                    return tasks;
                  })
              .andFinishWith(
                  "batchCodeDataDownloaded", tasks -> tasks.forEach(requestsToComplete::put));

      final Pipeline<Task<SnapDataRequest>> fetchHealDataPipeline =
          createPipelineFrom(
                  "requestTrieNodeDequeued",
                  new TaskQueueIterator<>(
                      downloadState, () -> downloadState.dequeueTrieNodeRequestBlocking()),
                  bufferCapacity,
                  outputCounter,
                  true,
                  "world_state_download")
              .thenFlatMapInParallel(
                  "requestLoadLocalTrieNodeData",
                  task -> loadLocalDataStep.loadLocalDataTrieNode(task, requestsToComplete),
                  3,
                  bufferCapacity)
              .inBatches(snapSyncConfiguration.getTrienodeCountPerRequest())
              .thenProcess(
                  "checkNewPivotBlock",
                  tasks -> {
                    pivotBlockManager.check(
                        (blockHeader, newBlockFound) ->
                            reloadHealWhenNeeded(snapSyncState, downloadState, newBlockFound));
                    return tasks;
                  })
              .thenProcessAsync(
                  "batchDownloadTrieNodeData",
                  tasks -> requestDataStep.requestTrieNodeByPath(tasks),
                  maxOutstandingRequests)
              .thenProcess(
                  "batchPersistTrieNodeData",
                  tasks -> {
                    persistDataStep.persist(tasks);
                    return tasks;
                  })
              .andFinishWith(
                  "batchTrieNodeDataDownloaded", tasks -> tasks.forEach(requestsToComplete::put));

      return new SnapWorldStateDownloadProcess(
          fetchAccountDataPipeline,
          fetchStorageDataPipeline,
          fetchBigStorageDataPipeline,
          fetchCodePipeline,
          fetchHealDataPipeline,
          completionPipeline,
          requestsToComplete);
    }
  }

  private static void reloadHealWhenNeeded(
      final SnapSyncState snapSyncState,
      final SnapWorldDownloadState downloadState,
      final boolean newBlockFound) {
    if (snapSyncState.isHealInProgress() && newBlockFound) {
      downloadState.reloadHeal();
    }
  }
}
