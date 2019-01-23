/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.eth.sync.fastsync;

import static tech.pegasys.pantheon.ethereum.eth.sync.fastsync.FastSyncError.FAST_SYNC_UNAVAILABLE;

import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FastSyncDownloader<C> {
  private static final Logger LOG = LogManager.getLogger();
  private final FastSyncActions<C> fastSyncActions;

  public FastSyncDownloader(final FastSyncActions<C> fastSyncActions) {
    this.fastSyncActions = fastSyncActions;
  }

  public CompletableFuture<FastSyncState> start() {
    LOG.info("Fast sync enabled");
    return fastSyncActions
        .waitForSuitablePeers()
        .thenApply(state -> fastSyncActions.selectPivotBlock())
        .thenCompose(fastSyncActions::downloadPivotBlockHeader)
        .thenCompose(
            state -> {
              LOG.info("Reached end of current fast sync implementation with state {}", state);
              throw new FastSyncException(FAST_SYNC_UNAVAILABLE);
            });
  }
}
