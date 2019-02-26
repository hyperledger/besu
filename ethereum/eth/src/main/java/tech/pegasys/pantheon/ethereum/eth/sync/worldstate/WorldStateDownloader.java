/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.eth.sync.worldstate;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.exceptions.EthTaskException;
import tech.pegasys.pantheon.ethereum.eth.manager.task.AbstractEthTask;
import tech.pegasys.pantheon.ethereum.eth.manager.task.AbstractPeerTask;
import tech.pegasys.pantheon.ethereum.eth.manager.task.AbstractPeerTask.PeerTaskResult;
import tech.pegasys.pantheon.ethereum.eth.manager.task.EthTask;
import tech.pegasys.pantheon.ethereum.eth.manager.task.GetNodeDataFromPeerTask;
import tech.pegasys.pantheon.ethereum.eth.manager.task.WaitForPeerTask;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateStorage;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateStorage.Updater;
import tech.pegasys.pantheon.metrics.Counter;
import tech.pegasys.pantheon.metrics.MetricCategory;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.services.queue.TaskQueue;
import tech.pegasys.pantheon.services.queue.TaskQueue.Task;
import tech.pegasys.pantheon.util.ExceptionUtils;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WorldStateDownloader {
  private static final Logger LOG = LogManager.getLogger();
  private final Counter completedRequestsCounter;
  private final Counter retriedRequestsCounter;
  private final Counter existingNodeCounter;
  private final ArrayBlockingQueue<Task<NodeDataRequest>> requestsToPersist;

  private enum Status {
    IDLE,
    RUNNING,
    CANCELLED,
    COMPLETED
  }

  private final EthContext ethContext;
  private final TaskQueue<NodeDataRequest> pendingRequests;
  private final int hashCountPerRequest;
  private final int maxOutstandingRequests;
  private final int maxNodeRequestRetries;
  private final Set<EthTask<?>> outstandingRequests =
      Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final MetricsSystem metricsSystem;
  private final WorldStateStorage worldStateStorage;
  private final AtomicBoolean sendingRequests = new AtomicBoolean(false);
  private volatile CompletableFuture<Void> future;
  private volatile Status status = Status.IDLE;
  private volatile BytesValue rootNode;
  private volatile PersistNodeDataTask persistenceTask;
  private final AtomicInteger highestRetryCount = new AtomicInteger(0);

  public WorldStateDownloader(
      final EthContext ethContext,
      final WorldStateStorage worldStateStorage,
      final TaskQueue<NodeDataRequest> pendingRequests,
      final int hashCountPerRequest,
      final int maxOutstandingRequests,
      final int maxNodeRequestRetries,
      final MetricsSystem metricsSystem) {
    this.ethContext = ethContext;
    this.worldStateStorage = worldStateStorage;
    this.pendingRequests = pendingRequests;
    this.hashCountPerRequest = hashCountPerRequest;
    this.maxOutstandingRequests = maxOutstandingRequests;
    this.maxNodeRequestRetries = maxNodeRequestRetries;
    this.metricsSystem = metricsSystem;
    // Room for the requests we expect to do in parallel plus some buffer but not unlimited.
    this.requestsToPersist =
        new ArrayBlockingQueue<>(hashCountPerRequest * maxOutstandingRequests * 2);

    metricsSystem.createLongGauge(
        MetricCategory.SYNCHRONIZER,
        "world_state_pending_requests_current",
        "Number of pending requests for fast sync world state download",
        pendingRequests::size);

    metricsSystem.createIntegerGauge(
        MetricCategory.SYNCHRONIZER,
        "world_state_inflight_requests_current",
        "Number of requests currently in flight for fast sync world state download",
        outstandingRequests::size);

    completedRequestsCounter =
        metricsSystem.createCounter(
            MetricCategory.SYNCHRONIZER,
            "world_state_completed_requests_total",
            "Total number of node data requests completed as part of fast sync world state download");
    retriedRequestsCounter =
        metricsSystem.createCounter(
            MetricCategory.SYNCHRONIZER,
            "world_state_retried_requests_total",
            "Total number of node data requests repeated as part of fast sync world state download");

    existingNodeCounter =
        metricsSystem.createCounter(
            MetricCategory.SYNCHRONIZER,
            "world_state_existing_nodes_total",
            "Total number of node data requests completed using existing data");

    metricsSystem.createIntegerGauge(
        MetricCategory.SYNCHRONIZER,
        "world_state_node_request_failures_max",
        "Highest number of times a node data request has been retried in this download",
        highestRetryCount::get);

    metricsSystem.createIntegerGauge(
        MetricCategory.SYNCHRONIZER,
        "world_state_node_persistence_queue_length_current",
        "Current number of node data requests waiting to be persisted",
        requestsToPersist::size);
  }

  public CompletableFuture<Void> run(final BlockHeader header) {
    LOG.info(
        "Begin downloading world state from peers for block {} ({}). State root {}",
        header.getNumber(),
        header.getHash(),
        header.getStateRoot());
    synchronized (this) {
      if (status == Status.RUNNING) {
        final CompletableFuture<Void> failed = new CompletableFuture<>();
        failed.completeExceptionally(
            new IllegalStateException(
                "Cannot run an already running " + this.getClass().getSimpleName()));
        return failed;
      }
      status = Status.RUNNING;
      future = createFuture();
      highestRetryCount.set(0);

      final Hash stateRoot = header.getStateRoot();
      if (worldStateStorage.isWorldStateAvailable(stateRoot)) {
        // If we're requesting data for an existing world state, we're already done
        markDone();
      } else {
        pendingRequests.enqueue(NodeDataRequest.createAccountDataRequest(stateRoot));

        ethContext.getScheduler().scheduleSyncWorkerTask(() -> requestNodeData(header));
        persistenceTask = new PersistNodeDataTask(header);
        ethContext.getScheduler().scheduleServiceTask(persistenceTask);
      }
    }
    return future;
  }

  public void cancel() {
    getFuture().cancel(true);
  }

  private void requestNodeData(final BlockHeader header) {
    while (shouldRequestNodeData()) {
      if (sendingRequests.compareAndSet(false, true)) {
        final Optional<EthPeer> maybePeer = ethContext.getEthPeers().idlePeer(header.getNumber());
        if (!maybePeer.isPresent()) {
          // If no peer is available, wait and try again
          sendingRequests.set(false);
          waitForNewPeer().whenComplete((r, t) -> requestNodeData(header));
          break;
        } else {
          requestDataFromPeer(header, maybePeer.get());
        }
        sendingRequests.set(false);
      } else {
        break;
      }
    }
  }

  private void requestDataFromPeer(final BlockHeader header, final EthPeer peer) {
    // Collect data to be requested
    final List<Task<NodeDataRequest>> toRequest = getTasksForNextRequest();

    if (toRequest.isEmpty()) {
      requestNodeData(header);
      return;
    }

    // Request and process node data
    sendAndProcessRequests(peer, toRequest, header)
        .whenComplete(
            (task, error) -> {
              if (error != null
                  && !(ExceptionUtils.rootCause(error) instanceof RejectedExecutionException)) {
                LOG.error("World state data request failed", error);
              }
              outstandingRequests.remove(task);
              requestNodeData(header);
            });
  }

  private List<Task<NodeDataRequest>> getTasksForNextRequest() {
    final List<Task<NodeDataRequest>> toRequest = new ArrayList<>();
    while (toRequest.size() < hashCountPerRequest) {
      final Task<NodeDataRequest> pendingRequestTask = pendingRequests.dequeue();
      if (pendingRequestTask == null) {
        break;
      }
      final NodeDataRequest pendingRequest = pendingRequestTask.getData();
      final Optional<BytesValue> existingData = pendingRequest.getExistingData(worldStateStorage);
      if (existingData.isPresent()) {
        existingNodeCounter.inc();
        pendingRequest.setData(existingData.get()).setRequiresPersisting(false);
        addToPersistenceQueue(pendingRequestTask);
        continue;
      }
      toRequest.add(pendingRequestTask);
    }
    return toRequest;
  }

  private synchronized boolean shouldRequestNodeData() {
    return !future.isDone()
        && outstandingRequests.size() < maxOutstandingRequests
        && !pendingRequests.isEmpty();
  }

  private CompletableFuture<?> waitForNewPeer() {
    return ethContext
        .getScheduler()
        .timeout(WaitForPeerTask.create(ethContext, metricsSystem), Duration.ofSeconds(5));
  }

  private CompletableFuture<AbstractPeerTask<Map<Hash, BytesValue>>> sendAndProcessRequests(
      final EthPeer peer,
      final List<Task<NodeDataRequest>> requestTasks,
      final BlockHeader blockHeader) {
    final List<Hash> hashes =
        requestTasks.stream()
            .map(Task::getData)
            .map(NodeDataRequest::getHash)
            .distinct()
            .collect(Collectors.toList());
    final AbstractPeerTask<Map<Hash, BytesValue>> ethTask =
        GetNodeDataFromPeerTask.forHashes(ethContext, hashes, metricsSystem).assignPeer(peer);
    outstandingRequests.add(ethTask);
    return ethTask
        .run()
        .thenApply(PeerTaskResult::getResult)
        .exceptionally(
            error -> {
              final Throwable rootCause = ExceptionUtils.rootCause(error);
              if (!(rootCause instanceof TimeoutException
                  || rootCause instanceof InterruptedException
                  || rootCause instanceof CancellationException
                  || rootCause instanceof EthTaskException)) {
                LOG.debug("GetNodeDataRequest failed", error);
              }
              return Collections.emptyMap();
            })
        .thenCompose(
            data ->
                ethContext
                    .getScheduler()
                    .scheduleSyncWorkerTask(
                        () -> storeData(requestTasks, blockHeader, ethTask, data)));
  }

  private CompletableFuture<AbstractPeerTask<Map<Hash, BytesValue>>> storeData(
      final List<Task<NodeDataRequest>> requestTasks,
      final BlockHeader blockHeader,
      final AbstractPeerTask<Map<Hash, BytesValue>> ethTask,
      final Map<Hash, BytesValue> data) {
    for (final Task<NodeDataRequest> task : requestTasks) {
      final NodeDataRequest request = task.getData();
      final BytesValue matchingData = data.get(request.getHash());
      if (matchingData == null) {
        retriedRequestsCounter.inc();
        final int requestFailures = request.trackFailure();
        updateHighestRetryCount(requestFailures);
        if (requestFailures > maxNodeRequestRetries) {
          handleStalledDownload();
        }
        task.markFailed();
      } else {
        request.setData(matchingData);
        if (isRootState(blockHeader, request)) {
          queueChildRequests(request);
          rootNode = request.getData();
          task.markCompleted();
        } else {
          addToPersistenceQueue(task);
        }
      }
    }
    requestNodeData(blockHeader);
    return CompletableFuture.completedFuture(ethTask);
  }

  private void addToPersistenceQueue(final Task<NodeDataRequest> task) {
    while (!future.isDone()) {
      try {
        if (requestsToPersist.offer(task, 1, TimeUnit.SECONDS)) {
          break;
        }
      } catch (final InterruptedException e) {
        task.markFailed();
        Thread.currentThread().interrupt();
        break;
      }
    }
  }

  private void updateHighestRetryCount(final int requestFailures) {
    int previousHighestRetry = highestRetryCount.get();
    while (requestFailures > previousHighestRetry) {
      highestRetryCount.compareAndSet(previousHighestRetry, requestFailures);
      previousHighestRetry = highestRetryCount.get();
    }
  }

  private synchronized void queueChildRequests(final NodeDataRequest request) {
    if (status == Status.RUNNING) {
      request.getChildRequests().forEach(pendingRequests::enqueue);
    }
  }

  private synchronized CompletableFuture<Void> getFuture() {
    if (future == null) {
      future = createFuture();
    }
    return future;
  }

  private CompletableFuture<Void> createFuture() {
    final CompletableFuture<Void> future = new CompletableFuture<>();
    future.whenComplete(
        (res, err) -> {
          // Handle cancellations
          if (future.isCancelled()) {
            LOG.info("World state download cancelled");
            doCancelDownload();
          } else if (err != null) {
            if (!(ExceptionUtils.rootCause(err) instanceof StalledDownloadException)) {
              LOG.info("World state download failed. ", err);
            }
            doCancelDownload();
          }
        });
    return future;
  }

  private synchronized void handleStalledDownload() {
    final String message =
        "Download stalled due to too many failures to retrieve node data (>"
            + maxNodeRequestRetries
            + " failures)";
    final WorldStateDownloaderException e = new StalledDownloadException(message);
    future.completeExceptionally(e);
  }

  private synchronized void doCancelDownload() {
    status = Status.CANCELLED;
    persistenceTask.cancel();
    pendingRequests.clear();
    for (final EthTask<?> outstandingRequest : outstandingRequests) {
      outstandingRequest.cancel();
    }
  }

  private synchronized void markDone() {
    final boolean completed = getFuture().complete(null);
    if (completed) {
      LOG.info("Finished downloading world state from peers");
      status = Status.COMPLETED;
    }
  }

  private boolean isRootState(final BlockHeader blockHeader, final NodeDataRequest request) {
    return request.getHash().equals(blockHeader.getStateRoot());
  }

  private class PersistNodeDataTask extends AbstractEthTask<Void> {

    private final List<Task<NodeDataRequest>> batch;
    private final BlockHeader header;

    public PersistNodeDataTask(final BlockHeader header) {
      super(metricsSystem);
      this.header = header;
      batch = new ArrayList<>();
    }

    @Override
    protected void executeTask() {
      while (!isDone()) {
        try {
          final Task<NodeDataRequest> task = requestsToPersist.poll(1, TimeUnit.SECONDS);
          if (task != null) {
            batch.clear();
            batch.add(task);
            requestsToPersist.drainTo(batch, 1000);
            final Updater storageUpdater = worldStateStorage.updater();
            batch.forEach(
                taskToPersist -> {
                  final NodeDataRequest request = taskToPersist.getData();
                  request.persist(storageUpdater);
                  queueChildRequests(request);
                  taskToPersist.markCompleted();
                  completedRequestsCounter.inc();
                });
            storageUpdater.commit();

            if (pendingRequests.allTasksCompleted()) {
              final Updater updater = worldStateStorage.updater();
              updater.putAccountStateTrieNode(header.getStateRoot(), rootNode);
              updater.commit();
              markDone();
              result.get().complete(null);
            } else {
              ethContext.getScheduler().scheduleSyncWorkerTask(() -> requestNodeData(header));
            }
          }
        } catch (final InterruptedException ignore) {
          Thread.currentThread().interrupt();
        } catch (final RuntimeException e) {
          LOG.error("Unexpected error while persisting world state", e);
          // Assume we failed to persist any of the requests and ensure we have something
          // scheduled to kick off another round of requests.
          batch.forEach(Task::markFailed);
          ethContext.getScheduler().scheduleSyncWorkerTask(() -> requestNodeData(header));
        }
      }
    }
  }
}
