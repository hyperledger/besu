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

import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncActions;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncDownloader;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncStateStorage;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldStateDownloader;
import org.hyperledger.besu.services.tasks.TaskCollection;

import java.nio.file.Path;

public class SnapSyncDownloader extends FastSyncDownloader<SnapDataRequest> {

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
  protected FastSyncState storeState(final FastSyncState state) {
    initialFastSyncState = state;
    fastSyncStateStorage.storeState(state);
    return new SnapSyncState(state);
  }
}
