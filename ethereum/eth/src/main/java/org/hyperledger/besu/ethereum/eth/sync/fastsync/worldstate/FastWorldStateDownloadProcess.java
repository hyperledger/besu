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

import static com.google.common.base.Preconditions.checkNotNull;
import static org.hyperledger.besu.services.pipeline.PipelineBuilder.createPipelineFrom;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
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

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FastWorldStateDownloadProcess implements WorldStateDownloadProcess {
  private static final Logger LOG = LoggerFactory.getLogger(FastWorldStateDownloadProcess.class);
  private final Pipeline<Task<NodeDataRequest>> fetchDataPipeline;
  private final Pipeline<Task<NodeDataRequest>> completionPipeline;
  private final WritePipe<Task<NodeDataRequest>> requestsToComplete;

  private FastWorldStateDownloadProcess(
      final Pipeline<Task<NodeDataRequest>> fetchDataPipeline,
      final Pipeline<Task<NodeDataRequest>> completionPipeline,
      final WritePipe<Task<NodeDataRequest>> requestsToComplete) {
    this.fetchDataPipeline = fetchDataPipeline;
    this.completionPipeline = completionPipeline;
    this.requestsToComplete = requestsToComplete;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public CompletableFuture<Void> start(final EthScheduler ethScheduler) {
    final CompletableFuture<Void> fetchDataFuture = ethScheduler.startPipeline(fetchDataPipeline);
    final CompletableFuture<Void> completionFuture = ethScheduler.startPipeline(completionPipeline);

    fetchDataFuture.whenComplete(
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
          fetchDataPipeline.abort();
          return null;
        });
    return completionFuture;
  }

  @Override
  public void abort() {
    fetchDataPipeline.abort();
    completionPipeline.abort();
  }

  public static class Builder {

    private int hashCountPerRequest;
    private int maxOutstandingRequests;
    private LoadLocalDataStep loadLocalDataStep;
    private FastWorldDownloadState downloadState;
    private MetricsSystem metricsSystem;
    private RequestDataStep requestDataStep;
    private BlockHeader pivotBlockHeader;
    private PersistDataStep persistDataStep;
    private CompleteTaskStep completeTaskStep;

    public Builder hashCountPerRequest(final int hashCountPerRequest) {
      this.hashCountPerRequest = hashCountPerRequest;
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

    public Builder downloadState(final FastWorldDownloadState downloadState) {
      this.downloadState = downloadState;
      return this;
    }

    public Builder pivotBlockHeader(final BlockHeader pivotBlockHeader) {
      this.pivotBlockHeader = pivotBlockHeader;
      return this;
    }

    public Builder metricsSystem(final MetricsSystem metricsSystem) {
      this.metricsSystem = metricsSystem;
      return this;
    }

    public FastWorldStateDownloadProcess build() {
      checkNotNull(loadLocalDataStep);
      checkNotNull(requestDataStep);
      checkNotNull(persistDataStep);
      checkNotNull(completeTaskStep);
      checkNotNull(downloadState);
      checkNotNull(pivotBlockHeader);
      checkNotNull(metricsSystem);

      // Room for the requests we expect to do in parallel plus some buffer but not unlimited.
      final int bufferCapacity = hashCountPerRequest * 2;
      final LabelledMetric<Counter> outputCounter =
          metricsSystem.createLabelledCounter(
              BesuMetricCategory.SYNCHRONIZER,
              "world_state_pipeline_processed_total",
              "Number of entries processed by each world state download pipeline stage",
              "step",
              "action");

      final Pipeline<Task<NodeDataRequest>> completionPipeline =
          PipelineBuilder.<Task<NodeDataRequest>>createPipeline(
                  "requestDataAvailable", bufferCapacity, outputCounter, true, "node_data_request")
              .andFinishWith(
                  "requestCompleteTask",
                  task ->
                      completeTaskStep.markAsCompleteOrFailed(
                          pivotBlockHeader, downloadState, task));

      final Pipe<Task<NodeDataRequest>> requestsToComplete = completionPipeline.getInputPipe();
      final Pipeline<Task<NodeDataRequest>> fetchDataPipeline =
          createPipelineFrom(
                  "requestDequeued",
                  new TaskQueueIterator<>(downloadState),
                  bufferCapacity,
                  outputCounter,
                  true,
                  "world_state_download")
              .thenFlatMapInParallel(
                  "requestLoadLocalData",
                  task -> loadLocalDataStep.loadLocalData(task, requestsToComplete),
                  3,
                  bufferCapacity)
              .inBatches(hashCountPerRequest)
              .thenProcessAsync(
                  "batchDownloadData",
                  requestTasks ->
                      requestDataStep.requestData(requestTasks, pivotBlockHeader, downloadState),
                  maxOutstandingRequests)
              .thenProcess(
                  "batchPersistData",
                  tasks -> persistDataStep.persist(tasks, pivotBlockHeader, downloadState))
              .andFinishWith(
                  "batchDataDownloaded", tasks -> tasks.forEach(requestsToComplete::put));

      return new FastWorldStateDownloadProcess(
          fetchDataPipeline, completionPipeline, requestsToComplete);
    }
  }
}
