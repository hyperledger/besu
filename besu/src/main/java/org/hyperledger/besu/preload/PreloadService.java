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
package org.hyperledger.besu.preload;

import static org.hyperledger.besu.services.pipeline.PipelineBuilder.createPipelineFrom;

import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.mainnet.parallelization.preload.PreloadTask;
import org.hyperledger.besu.ethereum.mainnet.parallelization.preload.Preloader;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.cache.BonsaiCachedMerkleTrieLoader;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.services.pipeline.Pipeline;
import org.hyperledger.besu.services.tasks.InMemoryTaskQueue;
import org.hyperledger.besu.services.tasks.Task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PreloadService implements Preloader {

  private static final Logger LOG = LoggerFactory.getLogger(PreloadService.class);

  private final Pipeline<Task<PreloadTask>> pipeline;
  private volatile boolean processing = false;
  private BonsaiCachedMerkleTrieLoader bonsaiCachedMerkleTrieLoader;

  private static final InMemoryTaskQueue<PreloadTask> queue = new InMemoryTaskQueue<>();

  public PreloadService(final MetricsSystem metricsSystem) {
    LOG.info("Creating PreloadService " + this);
    LabelledMetric<Counter> outputCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.BLOCK_PROCESSING,
            "preload_tasks_processed_counter",
            "Counter for the processed preload tasks",
            "step",
            "action");
    this.pipeline =
        createPipelineFrom(
                "dequeuePreloadTask",
                new TaskQueueIterator(this, () -> this.dequeueRequest()),
                10,
                outputCounter,
                true,
                "dequeuePreloadTask")
            .thenProcessInParallel(
                "processPreloadTask",
                task -> {
                  bonsaiCachedMerkleTrieLoader.processPreloadTask(task.getData());
                  return task;
                },
                5)
            .andFinishWith("preloadComplete", task -> {});
  }

  @Override
  public synchronized void enqueueRequest(final PreloadTask request) {
    if (!processing) {
      throw new IllegalStateException("Cannot enqueue; PreloadService not running");
    }
    queue.add(request);
    notifyAll();
  }

  @Override
  public synchronized Task<PreloadTask> dequeueRequest() {
    while (processing && queue.isEmpty()) {
      try {
        wait();
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        return null;
      }
    }
    if (!processing) {
      return null;
    }
    return queue.remove();
  }

  @Override
  public synchronized void clearQueue() {
    queue.clear();
  }

  public void start(final EthScheduler scheduler) {
    if (processing) {
      throw new IllegalStateException("PreloadService already started");
    }
    processing = true;
    scheduler
        .startPipeline(pipeline)
        .whenComplete(
            (nothing, error) -> {
              if (error != null) {
                LOG.error("Preload pipeline terminated with error", error);
              } else {
                LOG.info("Preload pipeline cleanly shut down");
              }
              synchronized (PreloadService.this) {
                processing = false;
                PreloadService.this.notifyAll();
              }
            });
  }

  public synchronized void stop() {
    LOG.info("PreloadService >> stop");
    queue.clear();
    pipeline.abort();
    if (!processing) {
      return;
    }
    processing = false;
    notifyAll();
  }

  public boolean isProcessing() {
    return processing;
  }

  // TODO: Solve better
  public void setBonsaiCachedMerkleTrieLoader(
      final BonsaiCachedMerkleTrieLoader bonsaiCachedMerkleTrieLoader) {
    this.bonsaiCachedMerkleTrieLoader = bonsaiCachedMerkleTrieLoader;
  }
}
