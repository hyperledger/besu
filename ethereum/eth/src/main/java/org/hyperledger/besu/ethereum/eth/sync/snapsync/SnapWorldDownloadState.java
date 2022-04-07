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

import static org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest.createAccountTrieNodeDataRequest;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.AccountRangeDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.BytecodeRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.StorageRangeDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldDownloadState;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.metrics.RunnableCounter;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.services.tasks.InMemoryTaskQueue;
import org.hyperledger.besu.services.tasks.InMemoryTasksPriorityQueues;
import org.hyperledger.besu.services.tasks.Task;
import org.hyperledger.besu.services.tasks.TaskCollection;

import java.time.Clock;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnapWorldDownloadState extends WorldDownloadState<SnapDataRequest> {

  private static final Logger LOG = LoggerFactory.getLogger(SnapWorldDownloadState.class);

  private static final int DISPLAY_SNAP_PROGRESS_STEP = 200000;
  private static final int DISPLAY_HEAL_PROGRESS_STEP = 10000;

  protected final InMemoryTaskQueue<SnapDataRequest> pendingAccountRequests =
      new InMemoryTaskQueue<>();
  protected final InMemoryTaskQueue<SnapDataRequest> pendingStorageRequests =
      new InMemoryTaskQueue<>();
  protected final InMemoryTaskQueue<SnapDataRequest> pendingBigStorageRequests =
      new InMemoryTaskQueue<>();
  protected final InMemoryTaskQueue<SnapDataRequest> pendingCodeRequests =
      new InMemoryTaskQueue<>();
  protected final InMemoryTasksPriorityQueues<SnapDataRequest> pendingTrieNodeRequests =
      new InMemoryTasksPriorityQueues<>();
  public final HashSet<Bytes> inconsistentAccounts = new HashSet<>();

  private final SnapSyncState snapSyncState;

  // metrics around the snapsync
  private final RunnableCounter generatedNodes;
  private final RunnableCounter healedNodes;

  public SnapWorldDownloadState(
      final WorldStateStorage worldStateStorage,
      final SnapSyncState snapSyncState,
      final InMemoryTasksPriorityQueues<SnapDataRequest> pendingRequests,
      final int maxRequestsWithoutProgress,
      final long minMillisBeforeStalling,
      final MetricsSystem metricsSystem,
      final Clock clock) {
    super(
        worldStateStorage,
        pendingRequests,
        maxRequestsWithoutProgress,
        minMillisBeforeStalling,
        clock);
    this.snapSyncState = snapSyncState;
    this.generatedNodes =
        new SnapCounter(
            metricsSystem.createCounter(
                BesuMetricCategory.SYNCHRONIZER,
                "snapsync_world_state_generated_nodes_total",
                "Total number of data nodes generated as part of snap sync world state download"),
            this::displayWorldStateSyncProgress,
            DISPLAY_SNAP_PROGRESS_STEP);
    this.healedNodes =
        new SnapCounter(
            metricsSystem.createCounter(
                BesuMetricCategory.SYNCHRONIZER,
                "snapsync_world_state_healed_nodes_total",
                "Total number of data nodes healed as part of snap sync world state heal process"),
            this::displayHealProgress,
            DISPLAY_HEAL_PROGRESS_STEP);
  }

  @Override
  public synchronized void notifyTaskAvailable() {
    notifyAll();
  }

  @Override
  protected synchronized void markAsStalled(final int maxNodeRequestRetries) {
    // TODO retry when mark as stalled
  }

  @Override
  public synchronized boolean checkCompletion(final BlockHeader header) {

    if (!internalFuture.isDone()
        && pendingAccountRequests.allTasksCompleted()
        && pendingCodeRequests.allTasksCompleted()
        && pendingStorageRequests.allTasksCompleted()
        && pendingBigStorageRequests.allTasksCompleted()
        && pendingTrieNodeRequests.allTasksCompleted()) {
      if (!snapSyncState.isHealInProgress()) {
        LOG.info(
            "Starting world state heal process from peers (generated nodes {})",
            generatedNodes.get());
        snapSyncState.setHealStatus(true);
        enqueueRequest(
            createAccountTrieNodeDataRequest(
                snapSyncState.getPivotBlockHeader().orElseThrow().getStateRoot(),
                Bytes.EMPTY,
                inconsistentAccounts));
      } else {
        final WorldStateStorage.Updater updater = worldStateStorage.updater();
        updater.saveWorldState(header.getHash(), header.getStateRoot(), rootNodeData);
        updater.commit();
        LOG.info(
            "Finished downloading world state from peers (generated nodes {} / healed nodes {})",
            generatedNodes.get(),
            healedNodes.get());
        internalFuture.complete(null);
        return true;
      }
    }

    return false;
  }

  @Override
  protected synchronized void cleanupQueues() {
    super.cleanupQueues();
    pendingAccountRequests.clear();
    pendingStorageRequests.clear();
    pendingBigStorageRequests.clear();
    pendingCodeRequests.clear();
    pendingTrieNodeRequests.clear();
  }

  @Override
  public synchronized void enqueueRequest(final SnapDataRequest request) {
    if (!internalFuture.isDone()) {
      if (request instanceof BytecodeRequest) {
        pendingCodeRequests.add(request);
      } else if (request instanceof StorageRangeDataRequest) {
        if (!((StorageRangeDataRequest) request).getStartKeyHash().equals(RangeManager.MIN_RANGE)) {
          pendingBigStorageRequests.add(request);
        } else {
          pendingStorageRequests.add(request);
        }
      } else if (request instanceof AccountRangeDataRequest) {
        pendingAccountRequests.add(request);
      } else {
        pendingTrieNodeRequests.add(request);
      }
      notifyAll();
    }
  }

  public void addInconsistentAccount(final Bytes account) {
    inconsistentAccounts.add(account);
  }

  @Override
  public synchronized void enqueueRequests(final Stream<SnapDataRequest> requests) {
    if (!internalFuture.isDone()) {
      requests.forEach(this::enqueueRequest);
    }
  }

  public synchronized Task<SnapDataRequest> dequeueRequestBlocking(
      final List<TaskCollection<SnapDataRequest>> queueDependencies,
      final List<TaskCollection<SnapDataRequest>> queues) {
    while (!internalFuture.isDone()) {
      while (queueDependencies.stream()
          .map(TaskCollection::allTasksCompleted)
          .anyMatch(Predicate.isEqual(false))) {
        try {
          wait();
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
          return null;
        }
      }
      for (TaskCollection<SnapDataRequest> queue : queues) {
        Task<SnapDataRequest> task = queue.remove();
        if (task != null) {
          return task;
        }
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

  public synchronized Task<SnapDataRequest> dequeueAccountRequestBlocking() {
    return dequeueRequestBlocking(
        List.of(pendingStorageRequests, pendingBigStorageRequests, pendingCodeRequests),
        List.of(pendingAccountRequests));
  }

  public synchronized Task<SnapDataRequest> dequeueBigStorageRequestBlocking() {
    return dequeueRequestBlocking(Collections.emptyList(), List.of(pendingBigStorageRequests));
  }

  public synchronized Task<SnapDataRequest> dequeueStorageRequestBlocking() {
    return dequeueRequestBlocking(Collections.emptyList(), List.of(pendingStorageRequests));
  }

  public synchronized Task<SnapDataRequest> dequeueCodeRequestBlocking() {
    return dequeueRequestBlocking(List.of(pendingStorageRequests), List.of(pendingCodeRequests));
  }

  public synchronized Task<SnapDataRequest> dequeueTrieNodeRequestBlocking() {
    return dequeueRequestBlocking(
        List.of(pendingAccountRequests, pendingStorageRequests, pendingBigStorageRequests),
        List.of(pendingTrieNodeRequests));
  }

  public void clearTrieNodes() {
    worldStateStorage.clearFlatDatabase();
    pendingTrieNodeRequests.clearInternalQueues();
    pendingCodeRequests.clearInternalQueue();
    snapSyncState.setHealStatus(false);
  }

  public RunnableCounter getGeneratedNodes() {
    return generatedNodes;
  }

  public RunnableCounter getHealedNodes() {
    return healedNodes;
  }

  private void displayWorldStateSyncProgress() {
    LOG.info("Retrieved {} world state nodes", generatedNodes.get());
  }

  private void displayHealProgress() {
    LOG.info("Healed {} world sync nodes", healedNodes.get());
  }
}
