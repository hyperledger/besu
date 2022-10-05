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
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncActions;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynamicPivotBlockManager {

  private static final Duration DEFAULT_CHECK_INTERVAL = Duration.ofSeconds(60);
  public static final BiConsumer<BlockHeader, Boolean> doNothingOnPivotChange = (___, __) -> {};

  private static final Logger LOG = LoggerFactory.getLogger(DynamicPivotBlockManager.class);

  private final AtomicBoolean isTimeToCheckAgain = new AtomicBoolean(true);

  private final EthContext ethContext;
  private final FastSyncActions syncActions;

  private final SnapSyncState syncState;
  private final int pivotBlockWindowValidity;
  private final int pivotBlockDistanceBeforeCaching;

  private Optional<BlockHeader> lastPivotBlockFound;

  public DynamicPivotBlockManager(
      final EthContext ethContext,
      final FastSyncActions fastSyncActions,
      final SnapSyncState fastSyncState,
      final int pivotBlockWindowValidity,
      final int pivotBlockDistanceBeforeCaching) {
    this.ethContext = ethContext;
    this.syncActions = fastSyncActions;
    this.syncState = fastSyncState;
    this.pivotBlockWindowValidity = pivotBlockWindowValidity;
    this.pivotBlockDistanceBeforeCaching = pivotBlockDistanceBeforeCaching;
    this.lastPivotBlockFound = Optional.empty();
  }

  public void check(final BiConsumer<BlockHeader, Boolean> onNewPivotBlock) {
    if (isTimeToCheckAgain.compareAndSet(true, false)) {
      AtomicBoolean delayNextCheck = new AtomicBoolean(false);

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
                              switchToNewPivotBlock(onNewPivotBlock);
                            }
                            // delay next check only if we are successful
                            delayNextCheck.set(true);
                          })
                      .get();
                } catch (InterruptedException | ExecutionException e) {
                  LOG.debug("Exception while searching for new pivot", e);
                }
              });

      scheduleNextCheck(delayNextCheck.get());
    }
  }

  private CompletableFuture<Void> downloadNewPivotBlock(final FastSyncState fss) {
    return syncActions
        .downloadPivotBlockHeader(fss)
        .thenAccept(
            fssWithHeader -> {
              lastPivotBlockFound = fssWithHeader.getPivotBlockHeader();
              debugLambda(LOG, "Found new pivot block {}", this::logLastPivotBlockFound);
            })
        .orTimeout(5, TimeUnit.MINUTES);
  }

  private boolean isSamePivotBlock(final FastSyncState fss) {
    return lastPivotBlockFound.isPresent()
        && fss.hasPivotBlockHash()
        && lastPivotBlockFound.get().getHash().equals(fss.getPivotBlockHash().get());
  }

  private void scheduleNextCheck(final boolean delayNextCheck) {
    if (delayNextCheck) {
      ethContext
          .getScheduler()
          .scheduleFutureTask(
              () -> {
                LOG.debug("Is time to check the pivot again");
                isTimeToCheckAgain.set(true);
              },
              DEFAULT_CHECK_INTERVAL);
    } else {
      isTimeToCheckAgain.set(true);
    }
  }

  public void switchToNewPivotBlock(final BiConsumer<BlockHeader, Boolean> onSwitchDone) {
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
          onSwitchDone.accept(blockHeader, true);
        },
        () -> onSwitchDone.accept(syncState.getPivotBlockHeader().orElseThrow(), false));
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
}
