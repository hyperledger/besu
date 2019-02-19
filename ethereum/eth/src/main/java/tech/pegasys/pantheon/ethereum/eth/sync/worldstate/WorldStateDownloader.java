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
import tech.pegasys.pantheon.ethereum.eth.manager.task.AbstractPeerTask;
import tech.pegasys.pantheon.ethereum.eth.manager.task.AbstractPeerTask.PeerTaskResult;
import tech.pegasys.pantheon.ethereum.eth.manager.task.EthTask;
import tech.pegasys.pantheon.ethereum.eth.manager.task.GetNodeDataFromPeerTask;
import tech.pegasys.pantheon.ethereum.eth.manager.task.WaitForPeerTask;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateStorage;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateStorage.Updater;
import tech.pegasys.pantheon.metrics.Counter;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.MetricCategory;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.OperationTimer;
import tech.pegasys.pantheon.services.queue.TaskQueue;
import tech.pegasys.pantheon.services.queue.TaskQueue.Task;
import tech.pegasys.pantheon.util.ExceptionUtils;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WorldStateDownloader {
  private static final Logger LOG = LogManager.getLogger();
  private final Counter completedRequestsCounter;
  private final Counter retriedRequestsTotal;

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
  private final LabelledMetric<OperationTimer> ethTasksTimer;
  private final WorldStateStorage worldStateStorage;
  private final AtomicBoolean sendingRequests = new AtomicBoolean(false);
  private volatile CompletableFuture<Void> future;
  private volatile Status status = Status.IDLE;
  private volatile BytesValue rootNode;
  private final AtomicInteger highestRetryCount = new AtomicInteger(0);

  public WorldStateDownloader(
      final EthContext ethContext,
      final WorldStateStorage worldStateStorage,
      final TaskQueue<NodeDataRequest> pendingRequests,
      final int hashCountPerRequest,
      final int maxOutstandingRequests,
      final int maxNodeRequestRetries,
      final LabelledMetric<OperationTimer> ethTasksTimer,
      final MetricsSystem metricsSystem) {
    this.ethContext = ethContext;
    this.worldStateStorage = worldStateStorage;
    this.pendingRequests = pendingRequests;
    this.hashCountPerRequest = hashCountPerRequest;
    this.maxOutstandingRequests = maxOutstandingRequests;
    this.maxNodeRequestRetries = maxNodeRequestRetries;
    this.ethTasksTimer = ethTasksTimer;
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
    retriedRequestsTotal =
        metricsSystem.createCounter(
            MetricCategory.SYNCHRONIZER,
            "world_state_retried_requests_total",
            "Total number of node data requests repeated as part of fast sync world state download");

    metricsSystem.createIntegerGauge(
        MetricCategory.SYNCHRONIZER,
        "world_state_node_request_failures_max",
        "Highest number of times a node data request has been retried in this download",
        highestRetryCount::get);
  }

  public CompletableFuture<Void> run(final BlockHeader header) {
    LOG.info(
        "Begin downloading world state from peers for block {} ({})",
        header.getNumber(),
        header.getHash());
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
      }
    }
    ethContext.getScheduler().scheduleSyncWorkerTask(() -> requestNodeData(header));
    return future;
  }

  public void cancel() {
    getFuture().cancel(true);
  }

  private void requestNodeData(final BlockHeader header) {
    if (sendingRequests.compareAndSet(false, true)) {
      while (shouldRequestNodeData()) {
        final Optional<EthPeer> maybePeer = ethContext.getEthPeers().idlePeer(header.getNumber());

        if (!maybePeer.isPresent()) {
          // If no peer is available, wait and try again
          waitForNewPeer().whenComplete((r, t) -> requestNodeData(header));
          break;
        } else {
          final EthPeer peer = maybePeer.get();

          // Collect data to be requested
          final List<Task<NodeDataRequest>> toRequest = new ArrayList<>();
          while (toRequest.size() < hashCountPerRequest) {
            final Task<NodeDataRequest> pendingRequestTask = pendingRequests.dequeue();
            if (pendingRequestTask == null) {
              break;
            }
            final NodeDataRequest pendingRequest = pendingRequestTask.getData();
            final Optional<BytesValue> existingData =
                pendingRequest.getExistingData(worldStateStorage);
            if (existingData.isPresent()) {
              pendingRequest.setData(existingData.get());
              queueChildRequests(pendingRequest);
              completedRequestsCounter.inc();
              pendingRequestTask.markCompleted();
              continue;
            }
            toRequest.add(pendingRequestTask);
          }

          // Request and process node data
          sendAndProcessRequests(peer, toRequest, header)
              .whenComplete(
                  (task, error) -> {
                    final boolean done;
                    synchronized (this) {
                      outstandingRequests.remove(task);
                      done =
                          status == Status.RUNNING
                              && outstandingRequests.size() == 0
                              && pendingRequests.allTasksCompleted();
                    }
                    if (done) {
                      // We're done
                      final Updater updater = worldStateStorage.updater();
                      updater.putAccountStateTrieNode(header.getStateRoot(), rootNode);
                      updater.commit();
                      markDone();
                    } else {
                      // Send out additional requests
                      requestNodeData(header);
                    }
                  });
        }
      }
      sendingRequests.set(false);
    }
  }

  private boolean shouldRequestNodeData() {
    return !future.isDone()
        && outstandingRequests.size() < maxOutstandingRequests
        && !pendingRequests.isEmpty();
  }

  private CompletableFuture<?> waitForNewPeer() {
    return ethContext
        .getScheduler()
        .timeout(WaitForPeerTask.create(ethContext, ethTasksTimer), Duration.ofSeconds(5));
  }

  private CompletableFuture<AbstractPeerTask<List<BytesValue>>> sendAndProcessRequests(
      final EthPeer peer,
      final List<Task<NodeDataRequest>> requestTasks,
      final BlockHeader blockHeader) {
    final List<Hash> hashes =
        requestTasks.stream()
            .map(Task::getData)
            .map(NodeDataRequest::getHash)
            .distinct()
            .collect(Collectors.toList());
    final AbstractPeerTask<List<BytesValue>> ethTask =
        GetNodeDataFromPeerTask.forHashes(ethContext, hashes, ethTasksTimer).assignPeer(peer);
    outstandingRequests.add(ethTask);
    return ethTask
        .run()
        .thenApply(PeerTaskResult::getResult)
        .thenApply(this::mapNodeDataByHash)
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

  private CompletableFuture<AbstractPeerTask<List<BytesValue>>> storeData(
      final List<Task<NodeDataRequest>> requestTasks,
      final BlockHeader blockHeader,
      final AbstractPeerTask<List<BytesValue>> ethTask,
      final Map<Hash, BytesValue> data) {
    final Updater storageUpdater = worldStateStorage.updater();
    for (final Task<NodeDataRequest> task : requestTasks) {
      final NodeDataRequest request = task.getData();
      final BytesValue matchingData = data.get(request.getHash());
      if (matchingData == null) {
        retriedRequestsTotal.inc();
        final int requestFailures = request.trackFailure();
        updateHighestRetryCount(requestFailures);
        if (requestFailures > maxNodeRequestRetries) {
          handleStalledDownload();
        }
        task.markFailed();
      } else {
        completedRequestsCounter.inc();
        // Persist request data
        request.setData(matchingData);
        if (isRootState(blockHeader, request)) {
          rootNode = request.getData();
        } else {
          request.persist(storageUpdater);
        }

        queueChildRequests(request);
        task.markCompleted();
      }
    }
    storageUpdater.commit();
    return CompletableFuture.completedFuture(ethTask);
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

  private Map<Hash, BytesValue> mapNodeDataByHash(final List<BytesValue> data) {
    // Map data by hash
    final Map<Hash, BytesValue> dataByHash = new HashMap<>();
    data.forEach(d -> dataByHash.put(Hash.hash(d), d));
    return dataByHash;
  }
}
