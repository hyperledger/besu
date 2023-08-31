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

import static org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest.createAccountFlatHealingRangeRequest;
import static org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest.createAccountTrieNodeDataRequest;

import org.hyperledger.besu.ethereum.chain.BlockAddedObserver;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.context.SnapSyncStatePersistenceManager;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.AccountRangeDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.BytecodeRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.StorageRangeDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.heal.AccountFlatDatabaseHealingRangeRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.heal.StorageFlatDatabaseHealingRangeRequest;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldDownloadState;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.FlatDbMode;
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
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnapWorldDownloadState extends WorldDownloadState<SnapDataRequest> {

  private static final Logger LOG = LoggerFactory.getLogger(SnapWorldDownloadState.class);

  protected final InMemoryTaskQueue<SnapDataRequest> pendingAccountRequests =
      new InMemoryTaskQueue<>();
  protected final InMemoryTaskQueue<SnapDataRequest> pendingStorageRequests =
      new InMemoryTaskQueue<>();
  protected final InMemoryTaskQueue<SnapDataRequest> pendingLargeStorageRequests =
      new InMemoryTaskQueue<>();
  protected final InMemoryTaskQueue<SnapDataRequest> pendingCodeRequests =
      new InMemoryTaskQueue<>();
  protected final InMemoryTasksPriorityQueues<SnapDataRequest> pendingTrieNodeRequests =
      new InMemoryTasksPriorityQueues<>();

  protected final InMemoryTasksPriorityQueues<SnapDataRequest>
      pendingAccountFlatDatabaseHealingRequests = new InMemoryTasksPriorityQueues<>();

  protected final InMemoryTasksPriorityQueues<SnapDataRequest>
      pendingStorageFlatDatabaseHealingRequests = new InMemoryTasksPriorityQueues<>();
  private HashSet<Bytes> accountsToBeRepaired = new HashSet<>();
  private DynamicPivotBlockSelector pivotBlockSelector;

  private final SnapSyncStatePersistenceManager snapContext;
  private final SnapSyncProcessState snapSyncState;

  // blockchain
  private final Blockchain blockchain;
  private OptionalLong blockObserverId;

  // metrics around the snapsync
  private final SnapsyncMetricsManager metricsManager;

  public SnapWorldDownloadState(
      final WorldStateStorage worldStateStorage,
      final SnapSyncStatePersistenceManager snapContext,
      final Blockchain blockchain,
      final SnapSyncProcessState snapSyncState,
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
            pendingLargeStorageRequests::size);
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
    /*System.out.println(internalFuture.isDone()+" "+pendingAccountRequests.allTasksCompleted()
    +" "+pendingCodeRequests.allTasksCompleted()
    +" "+pendingStorageRequests.allTasksCompleted()
    +" "+pendingLargeStorageRequests.allTasksCompleted()
    +" "+pendingAccountFlatDatabaseHealingRequests.allTasksCompleted()
    +" "+pendingStorageFlatDatabaseHealingRequests.allTasksCompleted()
    +" "+pendingTrieNodeRequests.size());*/
    if (!internalFuture.isDone()
        && pendingAccountRequests.allTasksCompleted()
        && pendingCodeRequests.allTasksCompleted()
        && pendingStorageRequests.allTasksCompleted()
        && pendingLargeStorageRequests.allTasksCompleted()
        && pendingTrieNodeRequests.allTasksCompleted()
        && pendingAccountFlatDatabaseHealingRequests.allTasksCompleted()
        && pendingStorageFlatDatabaseHealingRequests.allTasksCompleted()) {
      System.out.println("all task completed");
      if (!snapSyncState.isHealTrieInProgress()) {
        startTrieHeal();
      } else if (pivotBlockSelector.isBlockchainBehind()) {
        LOG.info("Pausing world state download while waiting for sync to complete");
        if (blockObserverId.isEmpty())
          blockObserverId = OptionalLong.of(blockchain.observeBlockAdded(getBlockAddedListener()));
        snapSyncState.setWaitingBlockchain(true);
      } else if (!snapSyncState.isHealFlatDatabaseInProgress()
          && worldStateStorage.getFlatDbMode().equals(FlatDbMode.FULL)) {
        // only doing a flat db heal for bonsai
        startFlatDatabaseHeal(header);
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
    pendingLargeStorageRequests.clear();
    pendingCodeRequests.clear();
    pendingTrieNodeRequests.clear();
  }

  public synchronized void startTrieHeal() {
    snapContext.clearAccountRangeTasks();
    snapSyncState.setHealTrieStatus(true);
    // try to find new pivot block before healing
    pivotBlockSelector.switchToNewPivotBlock(
        (blockHeader, newPivotBlockFound) -> {
          snapContext.clearAccountRangeTasks();
          LOG.info(
              "Running world state heal process from peers with pivot block {}",
              blockHeader.getNumber());
          enqueueRequest(
              createAccountTrieNodeDataRequest(
                  blockHeader.getStateRoot(), Bytes.EMPTY, accountsToBeRepaired));
        });
  }

  public synchronized void reloadTrieHeal() {
    worldStateStorage.clearFlatDatabase();
    worldStateStorage.clearTrieLog();
    pendingTrieNodeRequests.clear();
    pendingCodeRequests.clear();
    snapSyncState.setHealTrieStatus(false);
    checkCompletion(snapSyncState.getPivotBlockHeader().orElseThrow());
  }

  public synchronized void startFlatDatabaseHeal(final BlockHeader header) {
    LOG.info("Running flat database heal process");
    snapSyncState.setHealFlatDatabaseInProgress(true);
    final Map<Bytes32, Bytes32> ranges = RangeManager.generateAllRanges(16);
    ranges.forEach(
        (key, value) ->
            enqueueRequest(
                createAccountFlatHealingRangeRequest(header.getStateRoot(), key, value)));
  }

  public boolean isBonsaiStorageFormat() {
    return worldStateStorage.getDataStorageFormat().equals(DataStorageFormat.BONSAI);
  }

  @Override
  public synchronized void enqueueRequest(final SnapDataRequest request) {
    if (!internalFuture.isDone()) {
      if (request instanceof BytecodeRequest) {
        pendingCodeRequests.add(request);
      } else if (request instanceof StorageRangeDataRequest) {
        if (!((StorageRangeDataRequest) request).getStartKeyHash().equals(RangeManager.MIN_RANGE)) {
          pendingLargeStorageRequests.add(request);
        } else {
          pendingStorageRequests.add(request);
        }
      } else if (request instanceof AccountRangeDataRequest) {
        pendingAccountRequests.add(request);
      } else if (request instanceof AccountFlatDatabaseHealingRangeRequest) {
        pendingAccountFlatDatabaseHealingRequests.add(request);
      } else if (request instanceof StorageFlatDatabaseHealingRangeRequest) {
        pendingStorageFlatDatabaseHealingRequests.add(request);
      } else {
        pendingTrieNodeRequests.add(request);
      }
      notifyAll();
    }
  }

  public synchronized void setAccountsToBeRepaired(final HashSet<Bytes> accountsToBeRepaired) {
    this.accountsToBeRepaired = accountsToBeRepaired;
  }

  /**
   * Adds an account to the list of accounts to be repaired during the healing process. If the
   * account is not already in the list, it is added to both the snap context and the internal set
   * of accounts to be repaired.
   *
   * @param account The account to be added for repair.
   */
  public synchronized void addAccountToHealingList(final Bytes account) {
    if (!accountsToBeRepaired.contains(account)) {
      snapContext.addAccountsToBeRepaired(account);
      accountsToBeRepaired.add(account);
    }
  }

  public HashSet<Bytes> getAccountsToBeRepaired() {
    return accountsToBeRepaired;
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
        List.of(pendingStorageRequests, pendingLargeStorageRequests, pendingCodeRequests),
        pendingAccountRequests,
        unused -> snapContext.updatePersistedTasks(pendingAccountRequests.asList()));
  }

  public synchronized Task<SnapDataRequest> dequeueLargeStorageRequestBlocking() {
    return dequeueRequestBlocking(Collections.emptyList(), pendingLargeStorageRequests, __ -> {});
  }

  public synchronized Task<SnapDataRequest> dequeueStorageRequestBlocking() {
    return dequeueRequestBlocking(Collections.emptyList(), pendingStorageRequests, __ -> {});
  }

  public synchronized Task<SnapDataRequest> dequeueCodeRequestBlocking() {
    return dequeueRequestBlocking(List.of(pendingStorageRequests), pendingCodeRequests, __ -> {});
  }

  public synchronized Task<SnapDataRequest> dequeueTrieNodeRequestBlocking() {
    return dequeueRequestBlocking(
        List.of(pendingAccountRequests, pendingStorageRequests, pendingLargeStorageRequests),
        pendingTrieNodeRequests,
        __ -> {});
  }

  public synchronized Task<SnapDataRequest> dequeueAccountFlatDatabaseHealingRequestBlocking() {
    return dequeueRequestBlocking(
        List.of(
            pendingAccountRequests,
            pendingStorageRequests,
            pendingLargeStorageRequests,
            pendingTrieNodeRequests,
            pendingStorageFlatDatabaseHealingRequests),
        pendingAccountFlatDatabaseHealingRequests,
        __ -> {});
  }

  public synchronized Task<SnapDataRequest> dequeueStorageFlatDatabaseHealingRequestBlocking() {
    return dequeueRequestBlocking(
        List.of(
            pendingAccountRequests,
            pendingStorageRequests,
            pendingLargeStorageRequests,
            pendingTrieNodeRequests),
        pendingStorageFlatDatabaseHealingRequests,
        __ -> {});
  }

  public SnapsyncMetricsManager getMetricsManager() {
    return metricsManager;
  }

  public void setPivotBlockSelector(final DynamicPivotBlockSelector pivotBlockSelector) {
    this.pivotBlockSelector = pivotBlockSelector;
  }

  public BlockAddedObserver getBlockAddedListener() {
    return addedBlockContext -> {
      if (snapSyncState.isWaitingBlockchain()) {
        // if we receive a new pivot block we can restart the heal
        pivotBlockSelector.check(
            (____, isNewPivotBlock) -> {
              if (isNewPivotBlock) {
                snapSyncState.setWaitingBlockchain(false);
              }
            });
        // if we are close to the head we can also restart the heal and finish snapsync
        if (!pivotBlockSelector.isBlockchainBehind()) {
          snapSyncState.setWaitingBlockchain(false);
        }
        if (!snapSyncState.isWaitingBlockchain()) {
          blockObserverId.ifPresent(blockchain::removeObserver);
          blockObserverId = OptionalLong.empty();
          reloadTrieHeal();
        }
      }
    };
  }
}
