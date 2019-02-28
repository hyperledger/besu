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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WorldStateDownloader {
  private static final Logger LOG = LogManager.getLogger();

  private final Counter completedRequestsCounter;
  private final Counter retriedRequestsCounter;
  private final Counter existingNodeCounter;
  private final MetricsSystem metricsSystem;

  private final EthContext ethContext;
  private final TaskQueue<NodeDataRequest> taskQueue;
  private final int hashCountPerRequest;
  private final int maxOutstandingRequests;
  private final int maxNodeRequestsWithoutProgress;
  private final WorldStateStorage worldStateStorage;

  private final AtomicReference<WorldDownloadState> downloadState = new AtomicReference<>();

  public WorldStateDownloader(
      final EthContext ethContext,
      final WorldStateStorage worldStateStorage,
      final TaskQueue<NodeDataRequest> taskQueue,
      final int hashCountPerRequest,
      final int maxOutstandingRequests,
      final int maxNodeRequestsWithoutProgress,
      final MetricsSystem metricsSystem) {
    this.ethContext = ethContext;
    this.worldStateStorage = worldStateStorage;
    this.taskQueue = taskQueue;
    this.hashCountPerRequest = hashCountPerRequest;
    this.maxOutstandingRequests = maxOutstandingRequests;
    this.maxNodeRequestsWithoutProgress = maxNodeRequestsWithoutProgress;
    this.metricsSystem = metricsSystem;

    metricsSystem.createLongGauge(
        MetricCategory.SYNCHRONIZER,
        "world_state_pending_requests_current",
        "Number of pending requests for fast sync world state download",
        taskQueue::size);

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
        "world_state_node_requests_since_last_progress_current",
        "Number of world state requests made since the last time new data was returned",
        downloadStateValue(WorldDownloadState::getRequestsSinceLastProgress));

    metricsSystem.createIntegerGauge(
        MetricCategory.SYNCHRONIZER,
        "world_state_node_persistence_queue_length_current",
        "Current number of node data requests waiting to be persisted",
        downloadStateValue(WorldDownloadState::getPersistenceQueueSize));

    metricsSystem.createIntegerGauge(
        MetricCategory.SYNCHRONIZER,
        "world_state_inflight_requests_current",
        "Number of requests currently in flight for fast sync world state download",
        downloadStateValue(WorldDownloadState::getOutstandingRequestCount));
  }

  private Supplier<Integer> downloadStateValue(final Function<WorldDownloadState, Integer> getter) {
    return () -> {
      final WorldDownloadState state = this.downloadState.get();
      return state != null ? getter.apply(state) : 0;
    };
  }

  public CompletableFuture<Void> run(final BlockHeader header) {
    LOG.info(
        "Begin downloading world state from peers for block {} ({}). State root {}",
        header.getNumber(),
        header.getHash(),
        header.getStateRoot());
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
      if (worldStateStorage.isWorldStateAvailable(stateRoot)) {
        return CompletableFuture.completedFuture(null);
      }

      // Room for the requests we expect to do in parallel plus some buffer but not unlimited.
      final int persistenceQueueCapacity = hashCountPerRequest * maxOutstandingRequests * 2;
      final WorldDownloadState newDownloadState =
          new WorldDownloadState(
              taskQueue,
              new ArrayBlockingQueue<>(persistenceQueueCapacity),
              maxOutstandingRequests,
              maxNodeRequestsWithoutProgress);
      this.downloadState.set(newDownloadState);

      newDownloadState.enqueueRequest(NodeDataRequest.createAccountDataRequest(stateRoot));

      ethContext
          .getScheduler()
          .scheduleSyncWorkerTask(() -> requestNodeData(header, newDownloadState));

      final PersistNodeDataTask persistenceTask = new PersistNodeDataTask(header, newDownloadState);
      newDownloadState.setPersistenceTask(persistenceTask);
      ethContext.getScheduler().scheduleServiceTask(persistenceTask);
      return newDownloadState.getDownloadFuture();
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

  private void requestNodeData(final BlockHeader header, final WorldDownloadState downloadState) {
    downloadState.whileAdditionalRequestsCanBeSent(
        () -> {
          final Optional<EthPeer> maybePeer = ethContext.getEthPeers().idlePeer(header.getNumber());
          if (!maybePeer.isPresent()) {
            // If no peer is available, wait and try again
            downloadState.setWaitingForNewPeer(true);
            waitForNewPeer()
                .whenComplete(
                    (r, t) -> {
                      downloadState.setWaitingForNewPeer(false);
                      requestNodeData(header, downloadState);
                    });
          } else {
            requestDataFromPeer(header, maybePeer.get(), downloadState);
          }
        });
  }

  private void requestDataFromPeer(
      final BlockHeader header, final EthPeer peer, final WorldDownloadState downloadState) {
    // Collect data to be requested
    final List<Task<NodeDataRequest>> toRequest = getTasksForNextRequest(downloadState);

    if (toRequest.isEmpty()) {
      return;
    }

    // Request and process node data
    sendAndProcessRequests(peer, toRequest, header, downloadState)
        .whenComplete(
            (task, error) -> {
              if (error != null
                  && !(ExceptionUtils.rootCause(error) instanceof RejectedExecutionException)) {
                LOG.error("World state data request failed", error);
              }
              downloadState.removeOutstandingTask(task);
              requestNodeData(header, downloadState);
            });
  }

  private List<Task<NodeDataRequest>> getTasksForNextRequest(
      final WorldDownloadState downloadState) {
    final List<Task<NodeDataRequest>> toRequest = new ArrayList<>();
    while (toRequest.size() < hashCountPerRequest) {
      final Task<NodeDataRequest> pendingRequestTask = downloadState.dequeueRequest();
      if (pendingRequestTask == null) {
        break;
      }
      final NodeDataRequest pendingRequest = pendingRequestTask.getData();
      final Optional<BytesValue> existingData = pendingRequest.getExistingData(worldStateStorage);
      if (existingData.isPresent()) {
        existingNodeCounter.inc();
        pendingRequest.setData(existingData.get()).setRequiresPersisting(false);
        downloadState.addToPersistenceQueue(pendingRequestTask);
        continue;
      }
      toRequest.add(pendingRequestTask);
    }
    return toRequest;
  }

  private CompletableFuture<?> waitForNewPeer() {
    return ethContext
        .getScheduler()
        .timeout(WaitForPeerTask.create(ethContext, metricsSystem), Duration.ofSeconds(5));
  }

  private CompletableFuture<AbstractPeerTask<Map<Hash, BytesValue>>> sendAndProcessRequests(
      final EthPeer peer,
      final List<Task<NodeDataRequest>> requestTasks,
      final BlockHeader blockHeader,
      final WorldDownloadState downloadState) {
    final List<Hash> hashes =
        requestTasks.stream()
            .map(Task::getData)
            .map(NodeDataRequest::getHash)
            .distinct()
            .collect(Collectors.toList());
    final AbstractPeerTask<Map<Hash, BytesValue>> ethTask =
        GetNodeDataFromPeerTask.forHashes(ethContext, hashes, metricsSystem).assignPeer(peer);
    downloadState.addOutstandingTask(ethTask);
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
                        () -> {
                          storeData(requestTasks, blockHeader, data, downloadState);
                          return CompletableFuture.completedFuture(ethTask);
                        }));
  }

  private void storeData(
      final List<Task<NodeDataRequest>> requestTasks,
      final BlockHeader blockHeader,
      final Map<Hash, BytesValue> data,
      final WorldDownloadState downloadState) {
    boolean madeProgress = false;
    for (final Task<NodeDataRequest> task : requestTasks) {
      final NodeDataRequest request = task.getData();
      final BytesValue matchingData = data.get(request.getHash());
      if (matchingData == null) {
        retriedRequestsCounter.inc();
        task.markFailed();
      } else {
        madeProgress = true;
        request.setData(matchingData);
        if (isRootState(blockHeader, request)) {
          downloadState.enqueueRequests(request.getChildRequests());
          downloadState.setRootNodeData(request.getData());
          task.markCompleted();
        } else {
          downloadState.addToPersistenceQueue(task);
        }
      }
    }
    downloadState.requestComplete(madeProgress);
    requestNodeData(blockHeader, downloadState);
  }

  private boolean isRootState(final BlockHeader blockHeader, final NodeDataRequest request) {
    return request.getHash().equals(blockHeader.getStateRoot());
  }

  private class PersistNodeDataTask extends AbstractEthTask<Void> {

    private final List<Task<NodeDataRequest>> batch;
    private final BlockHeader header;
    private final WorldDownloadState downloadState;

    public PersistNodeDataTask(final BlockHeader header, final WorldDownloadState downloadState) {
      super(metricsSystem);
      this.header = header;
      this.downloadState = downloadState;
      batch = new ArrayList<>();
    }

    @Override
    protected void executeTask() {
      while (!isDone()) {
        try {
          final ArrayBlockingQueue<Task<NodeDataRequest>> requestsToPersist =
              downloadState.getRequestsToPersist();
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
                  downloadState.enqueueRequests(request.getChildRequests());
                  taskToPersist.markCompleted();
                  completedRequestsCounter.inc();
                });
            storageUpdater.commit();

            if (downloadState.checkCompletion(worldStateStorage, header)) {
              result.get().complete(null);
            } else {
              ethContext
                  .getScheduler()
                  .scheduleSyncWorkerTask(() -> requestNodeData(header, downloadState));
            }
          }
        } catch (final InterruptedException ignore) {
          Thread.currentThread().interrupt();
        } catch (final RuntimeException e) {
          LOG.error("Unexpected error while persisting world state", e);
          // Assume we failed to persist any of the requests and ensure we have something
          // scheduled to kick off another round of requests.
          batch.forEach(Task::markFailed);
          ethContext
              .getScheduler()
              .scheduleSyncWorkerTask(() -> requestNodeData(header, downloadState));
        }
      }
    }
  }
}
