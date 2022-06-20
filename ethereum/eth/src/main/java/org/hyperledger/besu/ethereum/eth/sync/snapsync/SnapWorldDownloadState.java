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
import org.hyperledger.besu.ethereum.eth.sync.snapsync.context.PersistentTaskCollection;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.AccountRangeDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.BytecodeRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.StorageRangeDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldDownloadState;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.services.tasks.InMemoryTaskQueue;
import org.hyperledger.besu.services.tasks.InMemoryTasksPriorityQueues;
import org.hyperledger.besu.services.tasks.Task;
import org.hyperledger.besu.services.tasks.TaskCollection;

import java.time.Clock;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnapWorldDownloadState extends WorldDownloadState<SnapDataRequest> {

  private static final Logger LOG = LoggerFactory.getLogger(SnapWorldDownloadState.class);

  protected final PersistentTaskCollection<SnapDataRequest> pendingAccountRequests;
  protected final InMemoryTaskQueue<SnapDataRequest> pendingStorageRequests =
      new InMemoryTaskQueue<>();
  protected final InMemoryTaskQueue<SnapDataRequest> pendingBigStorageRequests =
      new InMemoryTaskQueue<>();
  protected final InMemoryTaskQueue<SnapDataRequest> pendingCodeRequests =
      new InMemoryTaskQueue<>();
  protected final InMemoryTasksPriorityQueues<SnapDataRequest> pendingTrieNodeRequests =
      new InMemoryTasksPriorityQueues<>();
  public final HashSet<Bytes> inconsistentAccounts = new HashSet<>();

  private DynamicPivotBlockManager dynamicPivotBlockManager;
  private final SnapSyncState snapSyncState;

  // metrics around the snapsync
  private final SnapsyncMetricsManager metricsManager;

  public SnapWorldDownloadState(
      final WorldStateStorage worldStateStorage,
      final SnapSyncState snapSyncState,
      final PersistentTaskCollection<SnapDataRequest> pendingAccountRequests,
      final int maxRequestsWithoutProgress,
      final long minMillisBeforeStalling,
      final SnapsyncMetricsManager metricsManager,
      final Clock clock) {
    super(
        worldStateStorage,
        new InMemoryTasksPriorityQueues<>(),
        maxRequestsWithoutProgress,
        minMillisBeforeStalling,
        clock);
    this.pendingAccountRequests = pendingAccountRequests;
    this.snapSyncState = snapSyncState;
    this.metricsManager = metricsManager;
    metricsManager
        .getMetricsSystem()
        .createLongGauge(
            BesuMetricCategory.SYNCHRONIZER,
            "snap_world_state_pending_account_requests_current",
            "Number of account pending requests for snap sync world state download",
            pendingAccountRequests::size);
    metricsManager
        .getMetricsSystem()
        .createLongGauge(
            BesuMetricCategory.SYNCHRONIZER,
            "snap_world_state_pending_storage_requests_current",
            "Number of storage pending requests for snap sync world state download",
            pendingStorageRequests::size);
    metricsManager
        .getMetricsSystem()
        .createLongGauge(
            BesuMetricCategory.SYNCHRONIZER,
            "snap_world_state_pending_big_storage_requests_current",
            "Number of storage pending requests for snap sync world state download",
            pendingBigStorageRequests::size);
    metricsManager
        .getMetricsSystem()
        .createLongGauge(
            BesuMetricCategory.SYNCHRONIZER,
            "snap_world_state_pending_code_requests_current",
            "Number of code pending requests for snap sync world state download",
            pendingCodeRequests::size);
    metricsManager
        .getMetricsSystem()
        .createLongGauge(
            BesuMetricCategory.SYNCHRONIZER,
            "snap_world_state_pending_trie_node_requests_current",
            "Number of trie node pending requests for snap sync world state download",
            pendingTrieNodeRequests::size);
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
        LOG.info("Starting world state heal process from peers");
        startHeal();
      } else {
        final WorldStateStorage.Updater updater = worldStateStorage.updater();
        updater.saveWorldState(header.getHash(), header.getStateRoot(), rootNodeData);
        updater.commit();
        metricsManager.notifySnapSyncCompleted();
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

  public synchronized void startHeal() {
    snapSyncState.setHealStatus(true);
    // try to find new pivot block before healing
    dynamicPivotBlockManager.switchToNewPivotBlock(
        (blockHeader, newPivotBlockFound) -> {
          enqueueRequest(
              createAccountTrieNodeDataRequest(
                  blockHeader.getStateRoot(), Bytes.EMPTY, inconsistentAccounts));
        });
  }

  public synchronized void reloadHeal() {
    worldStateStorage.clearFlatDatabase();
    pendingTrieNodeRequests.clearInternalQueues();
    pendingCodeRequests.clearInternalQueue();
    snapSyncState.setHealStatus(false);
    checkCompletion(snapSyncState.getPivotBlockHeader().orElseThrow());
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
      final TaskCollection<SnapDataRequest> queue,
      final Consumer<Void> unBlocked) {
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
      unBlocked.accept(null);
      Task<SnapDataRequest> task = queue.remove();
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

  public synchronized Task<SnapDataRequest> dequeueAccountRequestBlocking() {
    return dequeueRequestBlocking(
        List.of(pendingStorageRequests, pendingBigStorageRequests, pendingCodeRequests),
        pendingAccountRequests,
        unused -> {
          pendingAccountRequests.persist();
        });
  }

  public synchronized Task<SnapDataRequest> dequeueBigStorageRequestBlocking() {
    return dequeueRequestBlocking(Collections.emptyList(), pendingBigStorageRequests, unused -> {});
  }

  public synchronized Task<SnapDataRequest> dequeueStorageRequestBlocking() {
    return dequeueRequestBlocking(Collections.emptyList(), pendingStorageRequests, unused -> {});
  }

  public synchronized Task<SnapDataRequest> dequeueCodeRequestBlocking() {
    return dequeueRequestBlocking(
        List.of(pendingStorageRequests), pendingCodeRequests, unused -> {});
  }

  public synchronized Task<SnapDataRequest> dequeueTrieNodeRequestBlocking() {
    return dequeueRequestBlocking(
        List.of(pendingAccountRequests, pendingStorageRequests, pendingBigStorageRequests),
        pendingTrieNodeRequests,
        unused -> {});
  }

  public SnapsyncMetricsManager getMetricsManager() {
    return metricsManager;
  }

  public void setDynamicPivotBlockManager(final DynamicPivotBlockManager dynamicPivotBlockManager) {
    this.dynamicPivotBlockManager = dynamicPivotBlockManager;
  }
}
