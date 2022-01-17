/*
 * Copyright ConsenSys AG.
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

import static org.hyperledger.besu.util.FutureUtils.exceptionallyCompose;

import org.hyperledger.besu.ethereum.eth.sync.SyncMode;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncActions;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncDownloader;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncStateStorage;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldStateDownloader;
import org.hyperledger.besu.services.tasks.TaskCollection;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SnapSyncDownloader extends FastSyncDownloader<SnapDataRequest> {

  private static final Logger LOG = LogManager.getLogger();

  public SnapSyncDownloader(
      final FastSyncActions fastSyncActions,
      final WorldStateDownloader worldStateDownloader,
      final FastSyncStateStorage fastSyncStateStorage,
      final TaskCollection<SnapDataRequest> taskCollection,
      final Path fastSyncDataDirectory,
      final FastSyncState initialFastSyncState) {
    super(
        fastSyncActions,
        worldStateDownloader,
        fastSyncStateStorage,
        taskCollection,
        fastSyncDataDirectory,
        initialFastSyncState);
  }

  @Override
  protected CompletableFuture<FastSyncState> start(final FastSyncState fastSyncState) {
    if (fastSyncState instanceof SnapSyncState
        && !((SnapSyncState) fastSyncState).isHealInProgress()) {
      LOG.info("Starting snap sync.");
      fastSyncStateStorage.notifyFastSyncStepChanged(SyncMode.X_SNAP);
    } else {
      LOG.info("Starting heal sync.");
      fastSyncStateStorage.notifyFastSyncStepChanged(SyncMode.FAST);
    }
    return exceptionallyCompose(
        fastSyncActions
            .waitForSuitablePeers(fastSyncState)
            .thenCompose(fastSyncActions::selectPivotBlock)
            .thenCompose(fastSyncActions::downloadPivotBlockHeader)
            .thenApply(this::updateMaxTrailingPeers)
            .thenApply(this::storeState)
            .thenCompose(fss -> downloadChainAndWorldState(fastSyncActions, fss)),
        this::handleFailure);
  }

  @Override
  protected FastSyncState storeState(final FastSyncState fastSyncState) {
    initialFastSyncState = fastSyncState;
    fastSyncStateStorage.storeState(fastSyncState);
    return new SnapSyncState(fastSyncState);
  }
}
