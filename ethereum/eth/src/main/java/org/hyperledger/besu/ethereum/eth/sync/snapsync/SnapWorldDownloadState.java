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
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncActions;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.worldstate.FastWorldStateDownloader;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldDownloadState;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.services.tasks.InMemoryTasksPriorityQueues;

import java.time.Clock;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SnapWorldDownloadState extends WorldDownloadState<SnapDataRequest> {
  private static final Logger LOG = LogManager.getLogger();

  private final FastSyncActions fastSyncActions;
  private final SnapSyncState snapSyncState;
  private final FastWorldStateDownloader healProcess;

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
  protected synchronized void markAsStalled(final int maxNodeRequestRetries) {
    if (!snapSyncState.isResettingPivotBlock()) {
      super.markAsStalled(maxNodeRequestRetries);
    }
  }

  @Override
  public synchronized boolean checkCompletion(
      final WorldStateStorage worldStateStorage, final BlockHeader header) {

    if (!internalFuture.isDone() && pendingRequests.allTasksCompleted()) {
      if (!snapSyncState.isHealInProgress()) {
        LOG.info("Starting heal process on state root " + header.getStateRoot());
        healProcess
            .run(fastSyncActions, snapSyncState)
            .whenComplete(
                (unused, throwable) -> {
                  if (throwable == null) {
                    internalFuture.complete(null);
                  } else {
                    internalFuture.completeExceptionally(throwable);
                  }
                });
      } else if (!snapSyncState.isResettingPivotBlock()) {
        LOG.info("Finished downloading world state from peers");
        internalFuture.complete(null);
        return true;
      }
    }

    return false;
  }

  public void checkNewPivotBlock(final SnapSyncState currentPivotBlock) {
    final long currentPivotBlockNumber = currentPivotBlock.getPivotBlockNumber().orElseThrow();
    final long distance =
        fastSyncActions.getSyncState().bestChainHeight() - currentPivotBlockNumber;
    if (distance > 126) {
      if (snapSyncState.lockResettingPivotBlock()) {
        fastSyncActions
            .selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE)
            .thenCompose(fastSyncActions::downloadPivotBlockHeader)
            .thenAccept(
                fss ->
                    fss.getPivotBlockHeader()
                        .ifPresent(
                            newPivotBlockHeader -> {
                              if (currentPivotBlockNumber != newPivotBlockHeader.getNumber()) {
                                LOG.info(
                                    "Select new pivot block {} {}",
                                    newPivotBlockHeader.getNumber(),
                                    newPivotBlockHeader.getStateRoot());
                                snapSyncState.setCurrentHeader(newPivotBlockHeader);
                              }
                              requestComplete(true);
                              snapSyncState.unlockResettingPivotBlock();
                            }))
            .orTimeout(10, TimeUnit.MINUTES)
            .whenComplete(
                (unused, throwable) -> {
                  snapSyncState.unlockResettingPivotBlock();
                });
      }
    }
  }
}
