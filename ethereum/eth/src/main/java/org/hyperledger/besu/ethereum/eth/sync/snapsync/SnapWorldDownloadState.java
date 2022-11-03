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

import org.hyperledger.besu.ethereum.chain.BlockAddedObserver;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
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
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnapWorldDownloadState extends WorldDownloadState<SnapDataRequest> {

  private static final Logger LOG = LoggerFactory.getLogger(SnapWorldDownloadState.class);

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
  public HashSet<Bytes> inconsistentAccounts = new HashSet<>();

  private DynamicPivotBlockManager dynamicPivotBlockManager;

  private final SnapPersistedContext snapContext;
  private final SnapSyncState snapSyncState;

  // blockchain
  private final Blockchain blockchain;
  private OptionalLong blockObserverId;

  // metrics around the snapsync
  private final SnapsyncMetricsManager metricsManager;

  public SnapWorldDownloadState(
      final WorldStateStorage worldStateStorage,
      final SnapPersistedContext snapContext,
      final Blockchain blockchain,
      final SnapSyncState snapSyncState,
      final InMemoryTasksPriorityQueues<SnapDataRequest> pendingRequests,
      final int maxRequestsWithoutProgress,
      final long minMillisBeforeStalling,
      final SnapsyncMetricsManager metricsManager,
      final Clock clock) {
    super(
        worldStateStorage,
        pendingRequests,
        maxRequestsWithoutProgress,
        minMillisBeforeStalling,
        clock);
    this.snapContext = snapContext;
    this.blockchain = blockchain;
    this.snapSyncState = snapSyncState;
    this.metricsManager = metricsManager;
    this.blockObserverId = OptionalLong.empty();
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
        startHeal();
      } else if (dynamicPivotBlockManager.isBlockchainBehind()) {
        LOG.info("Pausing world state download while waiting for sync to complete");
        if (blockObserverId.isEmpty())
          blockObserverId = OptionalLong.of(blockchain.observeBlockAdded(getBlockAddedListener()));
        snapSyncState.setWaitingBlockchain(true);
      } else {
        final WorldStateStorage.Updater updater = worldStateStorage.updater();
        updater.saveWorldState(header.getHash(), header.getStateRoot(), rootNodeData);
        updater.commit();
        metricsManager.notifySnapSyncCompleted();
        snapContext.clear();
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
    snapContext.clearAccountRangeTasks();
    snapSyncState.setHealStatus(true);
    // try to find new pivot block before healing
    dynamicPivotBlockManager.switchToNewPivotBlock(
        (blockHeader, newPivotBlockFound) -> {
          snapContext.clearAccountRangeTasks();
          LOG.info(
              "Running world state heal process from peers with pivot block {}",
              blockHeader.getNumber());
          enqueueRequest(
              createAccountTrieNodeDataRequest(
                  blockHeader.getStateRoot(), Bytes.EMPTY, inconsistentAccounts));
        });
  }

  public synchronized void reloadHeal() {
    worldStateStorage.clearFlatDatabase();
    pendingTrieNodeRequests.clear();
    pendingCodeRequests.clear();
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

  public synchronized void setInconsistentAccounts(final HashSet<Bytes> inconsistentAccounts) {
    this.inconsistentAccounts = inconsistentAccounts;
  }

  public synchronized void addInconsistentAccount(final Bytes account) {
    if (!inconsistentAccounts.contains(account)) {
      snapContext.addInconsistentAccount(account);
      inconsistentAccounts.add(account);
    }
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
    boolean isWaiting = false;
    while (!internalFuture.isDone()) {
      while (queueDependencies.stream()
          .map(TaskCollection::allTasksCompleted)
          .anyMatch(Predicate.isEqual(false))) {
        try {
          isWaiting = true;
          wait();
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
          return null;
        }
      }
      if (isWaiting) {
        unBlocked.accept(null);
      }
      isWaiting = false;
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
        unused -> snapContext.updatePersistedTasks(pendingAccountRequests.asList()));
  }

  public synchronized Task<SnapDataRequest> dequeueBigStorageRequestBlocking() {
    return dequeueRequestBlocking(Collections.emptyList(), pendingBigStorageRequests, __ -> {});
  }

  public synchronized Task<SnapDataRequest> dequeueStorageRequestBlocking() {
    return dequeueRequestBlocking(Collections.emptyList(), pendingStorageRequests, __ -> {});
  }

  public synchronized Task<SnapDataRequest> dequeueCodeRequestBlocking() {
    return dequeueRequestBlocking(List.of(pendingStorageRequests), pendingCodeRequests, __ -> {});
  }

  public synchronized Task<SnapDataRequest> dequeueTrieNodeRequestBlocking() {
    return dequeueRequestBlocking(
        List.of(pendingAccountRequests, pendingStorageRequests, pendingBigStorageRequests),
        pendingTrieNodeRequests,
        __ -> {});
  }

  public SnapsyncMetricsManager getMetricsManager() {
    return metricsManager;
  }

  public void setDynamicPivotBlockManager(final DynamicPivotBlockManager dynamicPivotBlockManager) {
    this.dynamicPivotBlockManager = dynamicPivotBlockManager;
  }

  public BlockAddedObserver getBlockAddedListener() {
    return addedBlockContext -> {
      if (snapSyncState.isWaitingBlockchain()) {
        // if we receive a new pivot block we can restart the heal
        dynamicPivotBlockManager.check(
            (____, isNewPivotBlock) -> {
              if (isNewPivotBlock) {
                snapSyncState.setWaitingBlockchain(false);
              }
            });
        // if we are close to the head we can also restart the heal and finish snapsync
        if (!dynamicPivotBlockManager.isBlockchainBehind()) {
          snapSyncState.setWaitingBlockchain(false);
        }
        if (!snapSyncState.isWaitingBlockchain()) {
          blockObserverId.ifPresent(blockchain::removeObserver);
          blockObserverId = OptionalLong.empty();
          reloadHeal();
        }
      }
    };
  }
}
