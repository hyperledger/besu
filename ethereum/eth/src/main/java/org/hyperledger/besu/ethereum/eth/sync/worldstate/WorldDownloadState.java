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
package org.hyperledger.besu.ethereum.eth.sync.worldstate;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.task.EthTask;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage.Updater;
import org.hyperledger.besu.services.tasks.CachingTaskCollection;
import org.hyperledger.besu.services.tasks.Task;
import org.hyperledger.besu.util.ExceptionUtils;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.time.Clock;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class WorldDownloadState {
  private static final Logger LOG = LogManager.getLogger();

  private final boolean downloadWasResumed;
  private final CachingTaskCollection<NodeDataRequest> pendingRequests;
  private final int maxRequestsWithoutProgress;
  private final Clock clock;
  private final Set<EthTask<?>> outstandingRequests =
      Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final CompletableFuture<Void> internalFuture;
  private final CompletableFuture<Void> downloadFuture;
  // Volatile so monitoring can access it without having to synchronize.
  private volatile int requestsSinceLastProgress = 0;
  private final long minMillisBeforeStalling;
  private volatile long timestampOfLastProgress;
  private BytesValue rootNodeData;
  private WorldStateDownloadProcess worldStateDownloadProcess;

  public WorldDownloadState(
      final CachingTaskCollection<NodeDataRequest> pendingRequests,
      final int maxRequestsWithoutProgress,
      final long minMillisBeforeStalling,
      final Clock clock) {
    this.minMillisBeforeStalling = minMillisBeforeStalling;
    this.timestampOfLastProgress = clock.millis();
    this.downloadWasResumed = !pendingRequests.isEmpty();
    this.pendingRequests = pendingRequests;
    this.maxRequestsWithoutProgress = maxRequestsWithoutProgress;
    this.clock = clock;
    this.internalFuture = new CompletableFuture<>();
    this.downloadFuture = new CompletableFuture<>();
    this.internalFuture.whenComplete(this::cleanup);
    this.downloadFuture.exceptionally(
        error -> {
          // Propagate cancellation back to our internal future.
          if (error instanceof CancellationException) {
            this.internalFuture.cancel(true);
          }
          return null;
        });
  }

  private synchronized void cleanup(final Void result, final Throwable error) {
    // Handle cancellations
    if (internalFuture.isCancelled()) {
      LOG.info("World state download cancelled");
    } else if (error != null) {
      if (!(ExceptionUtils.rootCause(error) instanceof StalledDownloadException)) {
        LOG.info("World state download failed. ", error);
      }
    }
    for (final EthTask<?> outstandingRequest : outstandingRequests) {
      outstandingRequest.cancel();
    }
    pendingRequests.clear();

    if (error != null) {
      if (worldStateDownloadProcess != null) {
        worldStateDownloadProcess.abort();
      }
      downloadFuture.completeExceptionally(error);
    } else {
      downloadFuture.complete(result);
    }
  }

  public boolean downloadWasResumed() {
    return downloadWasResumed;
  }

  public void addOutstandingTask(final EthTask<?> task) {
    outstandingRequests.add(task);
  }

  public void removeOutstandingTask(final EthTask<?> task) {
    outstandingRequests.remove(task);
  }

  public int getOutstandingTaskCount() {
    return outstandingRequests.size();
  }

  public CompletableFuture<Void> getDownloadFuture() {
    return downloadFuture;
  }

  public synchronized void enqueueRequest(final NodeDataRequest request) {
    if (!internalFuture.isDone()) {
      pendingRequests.add(request);
      notifyAll();
    }
  }

  public synchronized void enqueueRequests(final Stream<NodeDataRequest> requests) {
    if (!internalFuture.isDone()) {
      requests.forEach(pendingRequests::add);
      notifyAll();
    }
  }

  public synchronized Task<NodeDataRequest> dequeueRequestBlocking() {
    while (!internalFuture.isDone()) {
      final Task<NodeDataRequest> task = pendingRequests.remove();
      if (task != null) {
        return task;
      }
      try {
        wait();
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        return null;
      }
    }
    return null;
  }

  public synchronized void setRootNodeData(final BytesValue rootNodeData) {
    this.rootNodeData = rootNodeData;
  }

  public synchronized void requestComplete(final boolean madeProgress) {
    if (madeProgress) {
      requestsSinceLastProgress = 0;
      timestampOfLastProgress = clock.millis();
    } else {
      requestsSinceLastProgress++;
      if (requestsSinceLastProgress >= maxRequestsWithoutProgress
          && timestampOfLastProgress + minMillisBeforeStalling < clock.millis()) {
        markAsStalled(maxRequestsWithoutProgress);
      }
    }
  }

  public int getRequestsSinceLastProgress() {
    return requestsSinceLastProgress;
  }

  private synchronized void markAsStalled(final int maxNodeRequestRetries) {
    final String message =
        "Download stalled due to too many failures to retrieve node data (>"
            + maxNodeRequestRetries
            + " requests without making progress)";
    final WorldStateDownloaderException e = new StalledDownloadException(message);
    internalFuture.completeExceptionally(e);
  }

  public synchronized boolean checkCompletion(
      final WorldStateStorage worldStateStorage, final BlockHeader header) {
    if (!internalFuture.isDone() && pendingRequests.allTasksCompleted()) {
      if (rootNodeData == null) {
        enqueueRequest(NodeDataRequest.createAccountDataRequest(header.getStateRoot()));
        return false;
      }
      final Updater updater = worldStateStorage.updater();
      updater.putAccountStateTrieNode(header.getStateRoot(), rootNodeData);
      updater.commit();
      internalFuture.complete(null);
      // THere are no more inputs to process so make sure we wake up any threads waiting to dequeue
      // so they can give up waiting.
      notifyAll();
      LOG.info("Finished downloading world state from peers");
      return true;
    } else {
      return false;
    }
  }

  public synchronized boolean isDownloading() {
    return !internalFuture.isDone();
  }

  public synchronized void setWorldStateDownloadProcess(
      final WorldStateDownloadProcess worldStateDownloadProcess) {
    this.worldStateDownloadProcess = worldStateDownloadProcess;
  }

  public synchronized void notifyTaskAvailable() {
    notifyAll();
  }

  public CompletableFuture<Void> startDownload(
      final WorldStateDownloadProcess worldStateDownloadProcess, final EthScheduler ethScheduler) {
    this.worldStateDownloadProcess = worldStateDownloadProcess;
    final CompletableFuture<Void> processFuture = worldStateDownloadProcess.start(ethScheduler);

    processFuture.whenComplete(
        (result, error) -> {
          if (error != null
              && !(ExceptionUtils.rootCause(error) instanceof CancellationException)) {
            // The pipeline is only ever cancelled by us or shutdown closing the EthScheduler
            // In either case we don't want to consider the download failed as we either already
            // dealing with it or it's just a normal shutdown. Hence, don't propagate
            // CancellationException
            internalFuture.completeExceptionally(error);
          }
        });
    return downloadFuture;
  }
}
