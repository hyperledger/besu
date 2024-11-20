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
package org.hyperledger.besu.ethereum.eth.manager;

import org.hyperledger.besu.ethereum.eth.manager.bounded.BoundedQueue;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;

import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

public class MonitoredExecutors {

  public static ExecutorService newFixedThreadPool(
      final String name,
      final int minWorkerCount,
      final int workerCount,
      final MetricsSystem metricsSystem) {
    return newFixedThreadPool(
        name, minWorkerCount, workerCount, new LinkedBlockingQueue<>(), metricsSystem);
  }

  public static ExecutorService newBoundedThreadPool(
      final String name,
      final int workerCount,
      final int queueSize,
      final MetricsSystem metricsSystem) {
    return newBoundedThreadPool(name, 1, workerCount, queueSize, metricsSystem);
  }

  public static ExecutorService newBoundedThreadPool(
      final String name,
      final int minWorkerCount,
      final int maxWorkerCount,
      final int queueSize,
      final MetricsSystem metricsSystem) {
    return newFixedThreadPool(
        name,
        minWorkerCount,
        maxWorkerCount,
        new BoundedQueue(queueSize, toMetricName(name), metricsSystem),
        metricsSystem);
  }

  private static ExecutorService newFixedThreadPool(
      final String name,
      final int minWorkerCount,
      final int maxWorkerCount,
      final BlockingQueue<Runnable> workingQueue,
      final MetricsSystem metricsSystem) {
    return newMonitoredExecutor(
        name,
        metricsSystem,
        (rejectedExecutionHandler, threadFactory) ->
            new ThreadPoolExecutor(
                minWorkerCount,
                maxWorkerCount,
                60L,
                TimeUnit.SECONDS,
                workingQueue,
                threadFactory,
                rejectedExecutionHandler));
  }

  public static ExecutorService newCachedThreadPool(
      final String name, final MetricsSystem metricsSystem) {
    return newCachedThreadPool(name, 0, metricsSystem);
  }

  public static ExecutorService newCachedThreadPool(
      final String name, final int corePoolSize, final MetricsSystem metricsSystem) {
    return newMonitoredExecutor(
        name,
        metricsSystem,
        (rejectedExecutionHandler, threadFactory) ->
            new ThreadPoolExecutor(
                corePoolSize,
                Integer.MAX_VALUE,
                60L,
                TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>(),
                threadFactory,
                rejectedExecutionHandler));
  }

  public static ScheduledExecutorService newScheduledThreadPool(
      final String name, final int corePoolSize, final MetricsSystem metricsSystem) {
    return newMonitoredExecutor(
        name,
        metricsSystem,
        (rejectedExecutionHandler, threadFactory) ->
            new ScheduledThreadPoolExecutor(corePoolSize, threadFactory, rejectedExecutionHandler));
  }

  public static ExecutorService newSingleThreadExecutor(
      final String name, final MetricsSystem metricsSystem) {
    return newFixedThreadPool(name, 1, 1, metricsSystem);
  }

  private static <T extends ThreadPoolExecutor> T newMonitoredExecutor(
      final String name,
      final MetricsSystem metricsSystem,
      final BiFunction<RejectedExecutionHandler, ThreadFactory, T> creator) {

    final String metricName = toMetricName(name);

    final T executor =
        creator.apply(
            new CountingAbortPolicy(metricName, metricsSystem),
            new ThreadFactoryBuilder().setNameFormat(name + "-%d").build());

    metricsSystem.createIntegerGauge(
        BesuMetricCategory.EXECUTORS,
        metricName + "_queue_length_current",
        "Current number of tasks awaiting execution",
        executor.getQueue()::size);

    metricsSystem.createIntegerGauge(
        BesuMetricCategory.EXECUTORS,
        metricName + "_active_threads_current",
        "Current number of threads executing tasks",
        executor::getActiveCount);

    metricsSystem.createIntegerGauge(
        BesuMetricCategory.EXECUTORS,
        metricName + "_pool_size_current",
        "Current number of threads in the thread pool",
        executor::getPoolSize);

    metricsSystem.createCounter(
        BesuMetricCategory.EXECUTORS,
        metricName + "_completed_tasks_total",
        "Total number of tasks executed",
        executor::getCompletedTaskCount);

    metricsSystem.createCounter(
        BesuMetricCategory.EXECUTORS,
        metricName + "_submitted_tasks_total",
        "Total number of tasks executed",
        executor::getTaskCount);

    return executor;
  }

  private static String toMetricName(final String name) {
    return name.toLowerCase(Locale.US).replace('-', '_');
  }

  private static class CountingAbortPolicy extends AbortPolicy {

    private final Counter rejectedTaskCounter;

    public CountingAbortPolicy(final String metricName, final MetricsSystem metricsSystem) {
      this.rejectedTaskCounter =
          metricsSystem.createCounter(
              BesuMetricCategory.EXECUTORS,
              metricName + "_rejected_tasks_total",
              "Total number of tasks rejected by this executor");
    }

    @Override
    public void rejectedExecution(final Runnable r, final ThreadPoolExecutor e) {
      rejectedTaskCounter.inc();
      super.rejectedExecution(r, e);
    }
  }
}
