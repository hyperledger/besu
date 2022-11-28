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

import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncActions;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynamicPivotBlockManager {

  private static final Logger LOG = LoggerFactory.getLogger(DynamicPivotBlockManager.class);
  private final FastSyncActions syncActions;

  private final SnapSyncState syncState;
  private final int pivotBlockWindowValidity;
  private final int pivotBlockDistanceBeforeCaching;
  private Optional<BlockHeader> lastPivotBlockFound;

  private final List<DynamicPivotBlockObserver> dynamicPivotBlockObservers;

  private final ScheduledExecutorService scheduler;
  private ScheduledFuture<?> pivotBlockChecker;

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
    this.dynamicPivotBlockObservers = new ArrayList<>();
    scheduler = Executors.newSingleThreadScheduledExecutor();
  }

  public void start() {
    if (pivotBlockChecker == null) {
      pivotBlockChecker =
          scheduler.scheduleWithFixedDelay(this::searchPivotBlock, 60, 60, TimeUnit.SECONDS);
    }
  }

  public void stop() {
    if (pivotBlockChecker != null && !pivotBlockChecker.isCancelled()) {
      pivotBlockChecker.cancel(true);
      scheduler.shutdown();
      pivotBlockChecker = null;
      dynamicPivotBlockObservers.clear();
    }
  }

  public void addObserver(final DynamicPivotBlockObserver dynamicPivotBlockManager) {
    dynamicPivotBlockObservers.add(dynamicPivotBlockManager);
  }

  private void notifyObservers() {
    dynamicPivotBlockObservers.forEach(DynamicPivotBlockObserver::onNewPivotBlock);
  }

  public void searchPivotBlock() {
    syncState
        .getPivotBlockNumber()
        .ifPresent(
            currentPivotBlockNumber -> {
              final long bestChainHeight = syncActions.getBestChainHeight();
              final long distanceNextPivotBlock =
                  bestChainHeight
                      - lastPivotBlockFound
                          .map(ProcessableBlockHeader::getNumber)
                          .orElse(currentPivotBlockNumber);

              final CompletableFuture<Void> searchForNewPivot;

              if (distanceNextPivotBlock > pivotBlockDistanceBeforeCaching) {
                debugLambda(
                    LOG,
                    "Searching for a new pivot: current pivot {} best chain height {} distance next pivot {} last pivot block found {}",
                    () -> currentPivotBlockNumber,
                    () -> bestChainHeight,
                    () -> distanceNextPivotBlock,
                    this::logLastPivotBlockFound);

                searchForNewPivot =
                    CompletableFuture.completedFuture(FastSyncState.EMPTY_SYNC_STATE)
                        .thenCompose(syncActions::selectPivotBlock)
                        .thenCompose(
                            fss -> {
                              if (isSamePivotBlock(fss)) {
                                debugLambda(
                                    LOG,
                                    "New pivot {} is equal to last found {}, nothing to do",
                                    fss::getPivotBlockHash,
                                    this::logLastPivotBlockFound);
                                return CompletableFuture.completedFuture(null);
                              }
                              return downloadNewPivotBlock(fss);
                            })
                        .whenComplete(
                            (unused, throwable) -> {
                              if (throwable != null) {
                                LOG.debug("Error while searching for a new pivot", throwable);
                              }
                            });
              } else {
                searchForNewPivot = CompletableFuture.completedFuture(null);
              }

              try {
                searchForNewPivot
                    .thenRun(
                        () -> {
                          final long distance = bestChainHeight - currentPivotBlockNumber;
                          if (distance > pivotBlockWindowValidity) {
                            debugLambda(
                                LOG,
                                "Switch to new pivot: current pivot {} is distant {} from current best chain height {} last pivot block found {}",
                                () -> currentPivotBlockNumber,
                                () -> distance,
                                () -> bestChainHeight,
                                this::logLastPivotBlockFound);
                            switchToNewPivotBlock();
                          }
                        })
                    .get();
              } catch (InterruptedException | ExecutionException e) {
                LOG.debug("Exception while searching for new pivot", e);
              }
            });
  }

  private CompletableFuture<Void> downloadNewPivotBlock(final FastSyncState fss) {
    return syncActions
        .downloadPivotBlockHeader(fss)
        .thenAccept(
            fssWithHeader -> {
              lastPivotBlockFound = fssWithHeader.getPivotBlockHeader();
              debugLambda(LOG, "Found new pivot block {}", this::logLastPivotBlockFound);
            })
        .orTimeout(1, TimeUnit.MINUTES);
  }

  private boolean isSamePivotBlock(final FastSyncState fss) {
    return lastPivotBlockFound.isPresent()
        && fss.hasPivotBlockHash()
        && lastPivotBlockFound.get().getHash().equals(fss.getPivotBlockHash().get());
  }

  public void switchToNewPivotBlock() {
    lastPivotBlockFound.ifPresentOrElse(
        blockHeader -> {
          if (syncState.getPivotBlockHeader().filter(blockHeader::equals).isEmpty()) {
            debugLambda(
                LOG,
                "Setting new pivot block {} with state root {}",
                blockHeader::toLogString,
                blockHeader.getStateRoot()::toString);
            syncState.setCurrentHeader(blockHeader);
            lastPivotBlockFound = Optional.empty();
          }
          notifyObservers();
        },
        this::notifyObservers);
  }

  public boolean isBlockchainBehind() {
    return syncState
        .getPivotBlockHeader()
        .map(pivot -> syncActions.isBlockchainBehind(pivot.getNumber()))
        .orElse(false);
  }

  private String logLastPivotBlockFound() {
    return lastPivotBlockFound.map(BlockHeader::toLogString).orElse("empty");
  }

  public interface DynamicPivotBlockObserver {
    void onNewPivotBlock();
  }
}
