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
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.AccountHealRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.AccountRangeDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.BytecodeRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.StorageRangeDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldDownloadState;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.services.tasks.InMemoryTaskQueue;
import org.hyperledger.besu.services.tasks.InMemoryTasksPriorityQueues;
import org.hyperledger.besu.services.tasks.Task;
import org.hyperledger.besu.services.tasks.TaskCollection;

import java.time.Clock;
import java.util.Collections;
import java.util.List;
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
  protected final InMemoryTaskQueue<SnapDataRequest> pendingAccountHealRequests =
      new InMemoryTaskQueue<>();

  private final SnapSyncState snapSyncState;

  public SnapWorldDownloadState(
      final SnapSyncState snapSyncState,
      final InMemoryTasksPriorityQueues<SnapDataRequest> pendingRequests,
      final int maxRequestsWithoutProgress,
      final long minMillisBeforeStalling,
      final Clock clock) {
    super(pendingRequests, maxRequestsWithoutProgress, minMillisBeforeStalling, clock);
    this.snapSyncState = snapSyncState;
  }

  @Override
  public synchronized void notifyTaskAvailable() {
    notifyAll();
  }

  @Override
  protected synchronized void markAsStalled(final int maxNodeRequestRetries) {
    /*if (!snapSyncState.isResettingPivotBlock()) {
      super.markAsStalled(maxNodeRequestRetries);
    }*/
  }

  @Override
  public synchronized boolean checkCompletion(
      final WorldStateStorage worldStateStorage, final BlockHeader header) {

    if (!internalFuture.isDone()
        && pendingAccountRequests.allTasksCompleted()
        && pendingCodeRequests.allTasksCompleted()
        && pendingStorageRequests.allTasksCompleted()
        && pendingBigStorageRequests.allTasksCompleted()
        && pendingTrieNodeRequests.allTasksCompleted()) {
      if (!snapSyncState.isHealInProgress()) {
        snapSyncState.setHealStatus(true);
        enqueueRequest(
            createAccountTrieNodeDataRequest(
                snapSyncState.getPivotBlockHeader().orElseThrow().getStateRoot(), Bytes.EMPTY));
      } else {

        final WorldStateStorage.Updater updater = worldStateStorage.updater();
        updater.saveWorldState(header.getHash(), header.getStateRoot(), rootNodeData);
        updater.commit();

        LOG.info("Finished downloading world state from peers");
        internalFuture.complete(null);
        return true;
      }
    }

    return false;
  }

  public synchronized void enqueueFutureHealRequest(final SnapDataRequest request) {
    if (!internalFuture.isDone()) {
      pendingAccountHealRequests.add(request);
      System.out.println("NB heal account request " + pendingAccountHealRequests.size());
    }
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
      } else if (request instanceof AccountHealRequest) {
        pendingAccountHealRequests.add(request);
      } else {
        pendingTrieNodeRequests.add(request);
      }
      notifyAll();
    }
  }

  @Override
  public synchronized void enqueueRequests(final Stream<SnapDataRequest> requests) {
    if (!internalFuture.isDone()) {
      requests.forEach(this::enqueueRequest);
    }
  }

  public synchronized Task<SnapDataRequest> dequeueRequestBlocking(
      final List<InMemoryTaskQueue<SnapDataRequest>> queueDependencies,
      final TaskCollection<SnapDataRequest> queue) {
    while (!internalFuture.isDone()) {
      while (queueDependencies.stream()
          .map(InMemoryTaskQueue::allTasksCompleted)
          .anyMatch(Predicate.isEqual(false))) {
        try {
          wait();
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
          return null;
        }
      }
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
        List.of(pendingStorageRequests, pendingCodeRequests), pendingAccountRequests);
  }

  public synchronized Task<SnapDataRequest> dequeueBigStorageRequestBlocking() {
    return dequeueRequestBlocking(Collections.emptyList(), pendingBigStorageRequests);
  }

  public synchronized Task<SnapDataRequest> dequeueStorageRequestBlocking() {
    return dequeueRequestBlocking(Collections.emptyList(), pendingStorageRequests);
  }

  public synchronized Task<SnapDataRequest> dequeueCodeRequestBlocking() {
    return dequeueRequestBlocking(List.of(pendingStorageRequests), pendingCodeRequests);
  }

  public synchronized Task<SnapDataRequest> dequeueTrieNodeRequestBlocking() {
    return dequeueRequestBlocking(
        List.of(pendingAccountRequests, pendingStorageRequests, pendingBigStorageRequests),
        pendingTrieNodeRequests);
  }

  public void clearTrieNodes() {
    pendingTrieNodeRequests.clearInternalQueues();
    pendingCodeRequests.clearInternalQueue();
    snapSyncState.setHealStatus(false);
  }
}
