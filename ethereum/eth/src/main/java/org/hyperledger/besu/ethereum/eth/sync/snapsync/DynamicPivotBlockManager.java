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

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynamicPivotBlockManager {

  public static final BiConsumer<BlockHeader, Boolean> doNothingOnPivotChange = (___, __) -> {};

  private static final Logger LOG = LoggerFactory.getLogger(DynamicPivotBlockManager.class);

  private final AtomicBoolean isSearchingPivotBlock = new AtomicBoolean(false);
  private final AtomicBoolean isUpdatingPivotBlock = new AtomicBoolean(false);

  private final FastSyncActions syncActions;

  private final FastSyncState syncState;
  private final int pivotBlockWindowValidity;
  private final int pivotBlockDistanceBeforeCaching;

  private Optional<BlockHeader> lastPivotBlockFound;

  public DynamicPivotBlockManager(
      final FastSyncActions fastSyncActions,
      final SnapSyncState fastSyncState,
      final int pivotBlockWindowValidity,
      final int pivotBlockDistanceBeforeCaching) {
    this.syncActions = fastSyncActions;
    this.syncState = fastSyncState;
    this.pivotBlockWindowValidity = pivotBlockWindowValidity;
    this.pivotBlockDistanceBeforeCaching = pivotBlockDistanceBeforeCaching;
    this.lastPivotBlockFound = Optional.empty();
  }

  public void check(final BiConsumer<BlockHeader, Boolean> onNewPivotBlock) {
    syncState
        .getPivotBlockNumber()
        .ifPresent(
            currentPivotBlockNumber -> {
              final long distanceNextPivotBlock =
                  syncActions.getSyncState().bestChainHeight()
                      - lastPivotBlockFound
                          .map(ProcessableBlockHeader::getNumber)
                          .orElse(currentPivotBlockNumber);
              if (distanceNextPivotBlock > pivotBlockDistanceBeforeCaching
                  && isSearchingPivotBlock.compareAndSet(false, true)) {
                syncActions
                    .waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE)
                    .thenCompose(syncActions::selectPivotBlock)
                    .thenCompose(syncActions::downloadPivotBlockHeader)
                    .thenAccept(fss -> lastPivotBlockFound = fss.getPivotBlockHeader())
                    .orTimeout(5, TimeUnit.MINUTES)
                    .whenComplete((unused, throwable) -> isSearchingPivotBlock.set(false));
              }

              final long distance =
                  syncActions.getSyncState().bestChainHeight() - currentPivotBlockNumber;
              if (distance > pivotBlockWindowValidity
                  && isUpdatingPivotBlock.compareAndSet(false, true)) {
                switchToNewPivotBlock(onNewPivotBlock);
                isUpdatingPivotBlock.set(false);
              }
            });
  }

  public void switchToNewPivotBlock(final BiConsumer<BlockHeader, Boolean> onSwitchDone) {
    lastPivotBlockFound.ifPresentOrElse(
        blockHeader -> {
          LOG.info(
              "Select new pivot block {} {}", blockHeader.getNumber(), blockHeader.getStateRoot());
          syncState.setCurrentHeader(blockHeader);
          lastPivotBlockFound = Optional.empty();
          onSwitchDone.accept(blockHeader, true);
        },
        () -> onSwitchDone.accept(syncState.getPivotBlockHeader().orElseThrow(), false));
  }
}
