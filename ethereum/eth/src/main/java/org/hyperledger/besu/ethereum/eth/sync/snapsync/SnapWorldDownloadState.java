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

import static org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapDataRequest.createAccountTrieNodeRequest;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncActions;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldDownloadState;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.services.tasks.Task;
import org.hyperledger.besu.services.tasks.TaskCollection;

import java.time.Clock;
import java.util.OptionalLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class SnapWorldDownloadState extends WorldDownloadState<SnapDataRequest> {
  private static final Logger LOG = LogManager.getLogger();

  private final FastSyncActions fastSyncActions;
  private final SnapSyncState snapSyncState;

  public SnapWorldDownloadState(
      final FastSyncActions fastSyncActions,
      final SnapSyncState snapSyncState,
      final TaskCollection<SnapDataRequest> pendingRequests,
      final int maxRequestsWithoutProgress,
      final long minMillisBeforeStalling,
      final Clock clock) {
    super(pendingRequests, maxRequestsWithoutProgress, minMillisBeforeStalling, clock);
    this.fastSyncActions = fastSyncActions;
    this.snapSyncState = snapSyncState;
  }

  @Override
  public synchronized boolean checkCompletion(
      final WorldStateStorage worldStateStorage, final BlockHeader header) {
    if (!internalFuture.isDone() && pendingRequests.allTasksCompleted()) {
      if (!snapSyncState.isHealInProgress()) {
        LOG.info("Starting heal process on state root " + header.getStateRoot());
        snapSyncState.setSnapHealInProgress(true);
        snapSyncState.setOriginalPivotBlockNumber(OptionalLong.of(header.getNumber()));
        enqueueRequest(createAccountTrieNodeRequest(header.getStateRoot(), Bytes.EMPTY));
      } else if (!snapSyncState.isResettingPivotBlock()) {
        LOG.info("Finished downloading world state from peers");
        internalFuture.complete(null);
        return true;
      }
    }
    return false;
  }

  @Override
  protected synchronized void markAsStalled(final int maxNodeRequestRetries) {
    synchronized (this) {
      if (requestsSinceLastProgress == maxRequestsWithoutProgress) {
        snapSyncState.setResettingPivotBlock(true);
        requestsSinceLastProgress++;
        fastSyncActions
            .selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE)
            .thenCompose(fastSyncActions::downloadPivotBlockHeader)
            .thenAccept(
                fss ->
                    fss.getPivotBlockHeader()
                        .ifPresent(
                            header -> {
                              if (snapSyncState.getPivotBlockNumber().orElse(0)
                                  != header.getNumber()) {
                                LOG.info(
                                    "Select new pivot block {} {}",
                                    header.getNumber(),
                                    header.getStateRoot());
                                snapSyncState.setCurrentHeader(header);
                                requestsSinceLastProgress = 0;
                                if (snapSyncState.isHealInProgress()) {
                                  // restart heal with another pivot block
                                  pendingRequests.clear();
                                  enqueueRequest(
                                      createAccountTrieNodeRequest(
                                          header.getStateRoot(), Bytes.EMPTY));
                                }
                              }
                              snapSyncState.setResettingPivotBlock(false);
                            }));
      }
    }
  }

  @Override
  public synchronized Task<SnapDataRequest> dequeueRequestBlocking() {
    while (!internalFuture.isDone()) {
      final Task<SnapDataRequest> task = pendingRequests.remove();
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
}
