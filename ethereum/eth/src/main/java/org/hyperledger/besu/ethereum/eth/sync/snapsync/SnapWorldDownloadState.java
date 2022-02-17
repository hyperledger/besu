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

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncActions;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.worldstate.FastWorldStateDownloader;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.BytecodeRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.StorageRangeDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.TrieNodeDataHealRequest;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldDownloadState;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.services.tasks.InMemoryTaskQueue;
import org.hyperledger.besu.services.tasks.InMemoryTasksPriorityQueues;
import org.hyperledger.besu.services.tasks.Task;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
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
  protected final InMemoryTaskQueue<SnapDataRequest> futureHealRequests = new InMemoryTaskQueue<>();

  private final FastSyncActions fastSyncActions;
  private final SnapSyncState snapSyncState;
  private final FastWorldStateDownloader healProcess;
  private BlockHeader nextPivotBlock;

  public SnapWorldDownloadState(
      final FastSyncActions fastSyncActions,
      final SnapSyncState snapSyncState,
      final InMemoryTasksPriorityQueues<SnapDataRequest> pendingRequests,
      final int maxRequestsWithoutProgress,
      final long minMillisBeforeStalling,
      final FastWorldStateDownloader healProcess,
      final Clock clock) {
    super(pendingRequests, maxRequestsWithoutProgress, minMillisBeforeStalling, clock);
    this.fastSyncActions = fastSyncActions;
    this.snapSyncState = snapSyncState;
    this.healProcess = healProcess;
  }

  @Override
  public synchronized void notifyTaskAvailable() {
    notifyAll();
  }

  @Override
  protected synchronized void markAsStalled(final int maxNodeRequestRetries) {
    if (!snapSyncState.isResettingPivotBlock()) {
      super.markAsStalled(maxNodeRequestRetries);
    }
  }

  @Override
  public synchronized boolean checkCompletion(
      final WorldStateStorage worldStateStorage, final BlockHeader header) {

    if (!internalFuture.isDone()
        && pendingAccountRequests.allTasksCompleted()
        && pendingCodeRequests.allTasksCompleted()
        && pendingStorageRequests.allTasksCompleted()
        && pendingTrieNodeRequests.allTasksCompleted()
        && !snapSyncState.isResettingPivotBlock()) {
      if (!snapSyncState.isHealInProgress()) {
        snapSyncState.notifyStartHeal();
        LOG.info("Starting heal process on state root " + header.getStateRoot());
        Task<SnapDataRequest> remove = futureHealRequests.remove();
        while (remove != null) {
          final SnapDataRequest data = remove.getData();
          enqueueRequest(data);
          System.out.println("Enque " + ((TrieNodeDataHealRequest) data).getTrieNodePath());
          remove = futureHealRequests.remove();
        }
        /*healProcess
        .run(fastSyncActions, snapSyncState)
        .whenComplete(
            (unused, throwable) -> {
              if (throwable == null) {
                internalFuture.complete(null);
              } else {
                internalFuture.completeExceptionally(throwable);
              }
            });*/
      } else if (!snapSyncState.isResettingPivotBlock()) {
        LOG.info("Finished downloading world state from peers");
        internalFuture.complete(null);
        return true;
      }
    }

    return false;
  }

  public synchronized void enqueueFutureHealRequest(final SnapDataRequest request) {
    if (!internalFuture.isDone()) {
      futureHealRequests.add(request);
      System.out.println("NB heal account request " + futureHealRequests.size());
    }
  }

  @Override
  public synchronized void enqueueRequest(final SnapDataRequest request) {
    if (!internalFuture.isDone()) {
      if (request instanceof BytecodeRequest) {
        pendingCodeRequests.add(request);
      } else if (request instanceof StorageRangeDataRequest) {
        if (!((StorageRangeDataRequest) request).getStartKeyHash().equals(RangeManager.MIN_RANGE)) {
          // pendingBigStorageRequests.add(request);
        } else {
          pendingStorageRequests.add(request);
        }
      } else if (request instanceof TrieNodeDataHealRequest) {
        if (!snapSyncState.isHealInProgress()) {
          enqueueFutureHealRequest(request);
        }
        if (snapSyncState.isValidTask(request)) {
          pendingTrieNodeRequests.add(request);
        }
      } else {
        pendingAccountRequests.add(request);
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

  public synchronized Task<SnapDataRequest> dequeueAccountRequestBlocking() {
    while (!internalFuture.isDone()) {
      while (!pendingStorageRequests.allTasksCompleted()
          || !pendingBigStorageRequests.allTasksCompleted()
          || !pendingCodeRequests.allTasksCompleted()) {
        try {
          wait();
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
          return null;
        }
      }
      Task<SnapDataRequest> task = pendingAccountRequests.remove();
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

  public synchronized Task<SnapDataRequest> dequeueBigStorageRequestBlocking() {
    while (!internalFuture.isDone()) {
      while (!pendingStorageRequests.allTasksCompleted()) {
        try {
          wait();
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
          return null;
        }
      }
      Task<SnapDataRequest> task = pendingBigStorageRequests.remove();
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

  public synchronized Task<SnapDataRequest> dequeueStorageRequestBlocking() {
    while (!internalFuture.isDone()) {
      Task<SnapDataRequest> task = pendingStorageRequests.remove();
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

  public synchronized Task<SnapDataRequest> dequeueCodeRequestBlocking() {
    while (!internalFuture.isDone()) {
      while (!pendingStorageRequests.allTasksCompleted()
          || !pendingBigStorageRequests.allTasksCompleted()) {
        try {
          wait();
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
          return null;
        }
      }
      Task<SnapDataRequest> task = pendingCodeRequests.remove();
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

  public synchronized Task<SnapDataRequest> dequeueTrieNodeRequestBlocking() {
    while (!internalFuture.isDone()) {
      while (!pendingStorageRequests.allTasksCompleted()
          || !pendingBigStorageRequests.allTasksCompleted()
          || !pendingAccountRequests.allTasksCompleted()) {
        try {
          wait();
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
          return null;
        }
      }
      Task<SnapDataRequest> task = pendingTrieNodeRequests.remove();
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

  public int getNbRequestsInProgress() {
    return pendingAccountRequests.getNbUnfinishedOutstandingTasks()
        + pendingBigStorageRequests.getNbUnfinishedOutstandingTasks()
        + pendingStorageRequests.getNbUnfinishedOutstandingTasks()
        + pendingCodeRequests.getNbUnfinishedOutstandingTasks();
  }

  public void checkNewPivotBlock(final SnapSyncState currentPivotBlock) {
    final long currentPivotBlockNumber = currentPivotBlock.getPivotBlockNumber().orElseThrow();
    final long distance =
        fastSyncActions.getSyncState().bestChainHeight() - currentPivotBlockNumber;
    System.out.println("Distance " + distance);
    if (distance > 70) {
      final long distanceNextPivotBlock =
          fastSyncActions.getSyncState().bestChainHeight()
              - Optional.ofNullable(nextPivotBlock)
                  .map(ProcessableBlockHeader::getNumber)
                  .orElse(0L);
      if (distanceNextPivotBlock > 60) {
        System.out.println(
            "Distance pivot block "
                + fastSyncActions.getSyncState().bestChainHeight()
                + " "
                + Optional.ofNullable(nextPivotBlock)
                    .map(ProcessableBlockHeader::getNumber)
                    .orElse(0L));
        if (snapSyncState.lockResettingPivotBlock()) {
          fastSyncActions
              .waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE)
              .thenCompose(fastSyncActions::selectPivotBlock)
              .thenCompose(fastSyncActions::downloadPivotBlockHeader)
              .thenAccept(
                  fss ->
                      fss.getPivotBlockHeader()
                          .ifPresent(
                              newPivotBlockHeader -> {
                                LOG.info(
                                    "Found new next pivot block {} {}",
                                    newPivotBlockHeader.getNumber(),
                                    newPivotBlockHeader.getStateRoot());
                                nextPivotBlock = newPivotBlockHeader;
                                snapSyncState.unlockResettingPivotBlock();
                              }))
              .orTimeout(5, TimeUnit.MINUTES)
              .whenComplete(
                  (unused, throwable) -> {
                    snapSyncState.unlockResettingPivotBlock();
                  });
          ;
        }
      }
    }
    if (distance > 126) {
      if (nextPivotBlock != null && currentPivotBlockNumber != nextPivotBlock.getNumber()) {
        LOG.info(
            "Select new pivot block {} {}",
            nextPivotBlock.getNumber(),
            nextPivotBlock.getStateRoot());
        snapSyncState.setCurrentHeader(nextPivotBlock);
      }
      requestComplete(true);
      notifyTaskAvailable();
    }
  }
}
