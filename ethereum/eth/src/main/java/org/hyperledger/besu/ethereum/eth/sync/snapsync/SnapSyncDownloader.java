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

import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncActions;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncDownloader;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncStateStorage;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldStateDownloader;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnapSyncDownloader extends FastSyncDownloader {

  private static final Logger LOG = LoggerFactory.getLogger(SnapSyncDownloader.class);

  private final SnapContextLoader snapContextLoader;

  public SnapSyncDownloader(
      final FastSyncActions fastSyncActions,
      final WorldStateStorage worldStateStorage,
      final SnapContextLoader snapContextLoader,
      final WorldStateDownloader worldStateDownloader,
      final FastSyncStateStorage fastSyncStateStorage,
      final Path fastSyncDataDirectory,
      final FastSyncState initialFastSyncState) {
    super(
        fastSyncActions,
        worldStateStorage,
        worldStateDownloader,
        fastSyncStateStorage,
        fastSyncDataDirectory,
        initialFastSyncState);
    this.snapContextLoader = snapContextLoader;
  }

  @Override
  protected CompletableFuture<FastSyncState> start(final FastSyncState fastSyncState) {
    LOG.info("Starting sync.");
    return findPivotBlock(fastSyncState, fss -> downloadChainAndWorldState(fastSyncActions, fss));
  }

  @Override
  protected CompletableFuture<FastSyncState> handleFailure(final Throwable error) {
    snapContextLoader.clear();
    return super.handleFailure(error);
  }

  @Override
  protected FastSyncState storeState(final FastSyncState fastSyncState) {
    initialFastSyncState = fastSyncState;
    fastSyncStateStorage.storeState(fastSyncState);
    return new SnapSyncState(fastSyncState);
  }
}
