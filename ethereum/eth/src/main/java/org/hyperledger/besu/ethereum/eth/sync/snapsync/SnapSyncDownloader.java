/*
 * Copyright contributors to Hyperledger Besu.
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

import org.hyperledger.besu.ethereum.eth.sync.QuickSyncActions;
import org.hyperledger.besu.ethereum.eth.sync.QuickSyncDownloader;
import org.hyperledger.besu.ethereum.eth.sync.QuickSyncState;
import org.hyperledger.besu.ethereum.eth.sync.QuickSyncStateStorage;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldStateDownloader;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator;
import org.hyperledger.besu.metrics.SyncDurationMetrics;
import org.hyperledger.besu.services.tasks.TaskCollection;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class SnapSyncDownloader extends QuickSyncDownloader<SnapDataRequest> {

  public SnapSyncDownloader(
      final QuickSyncActions quickSyncActions,
      final WorldStateStorageCoordinator worldStateStorageCoordinator,
      final WorldStateDownloader worldStateDownloader,
      final QuickSyncStateStorage quickSyncStateStorage,
      final TaskCollection<SnapDataRequest> taskCollection,
      final Path fastSyncDataDirectory,
      final QuickSyncState initialQuickSyncState,
      final SyncDurationMetrics syncDurationMetrics) {
    super(
        quickSyncActions,
        worldStateStorageCoordinator,
        worldStateDownloader,
        quickSyncStateStorage,
        taskCollection,
        fastSyncDataDirectory,
        initialQuickSyncState,
        syncDurationMetrics);
  }

  @Override
  protected CompletableFuture<QuickSyncState> start(final QuickSyncState quickSyncState) {
    LOG.debug("Start snap sync with initial sync state {}", quickSyncState);
    return findPivotBlock(quickSyncState, fss -> downloadChainAndWorldState(quickSyncActions, fss));
  }

  @Override
  protected QuickSyncState storeState(final QuickSyncState quickSyncState) {
    initialQuickSyncState = quickSyncState;
    quickSyncStateStorage.storeState(quickSyncState);
    return new SnapSyncProcessState(quickSyncState);
  }
}
