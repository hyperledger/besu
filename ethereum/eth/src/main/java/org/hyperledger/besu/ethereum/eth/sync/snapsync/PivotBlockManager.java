package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncActions;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldDownloadState;
import org.hyperledger.besu.services.tasks.TasksPriorityProvider;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PivotBlockManager<REQUEST extends TasksPriorityProvider> {

  private static final Logger LOG = LoggerFactory.getLogger(PivotBlockManager.class);

  final WorldDownloadState<REQUEST> worldDownloadState;

  final FastSyncActions syncActions;

  final FastSyncState syncState;

  Optional<BlockHeader> lastBlockFound;

  private boolean lock;

  public PivotBlockManager(
      final WorldDownloadState<REQUEST> worldDownloadState,
      final FastSyncActions fastSyncActions,
      final SnapSyncState fastSyncState) {
    this.worldDownloadState = worldDownloadState;
    this.syncActions = fastSyncActions;
    this.syncState = fastSyncState;
    this.lastBlockFound = Optional.empty();
  }

  public void check(final Consumer<BlockHeader> onNewPivotBlock) {
    syncState
        .getPivotBlockNumber()
        .ifPresent(
            blockNumber -> {
              final long currentPivotBlockNumber = syncState.getPivotBlockNumber().orElseThrow();
              final long distanceNextPivotBlock =
                  syncActions.getSyncState().bestChainHeight()
                      - lastBlockFound
                          .map(ProcessableBlockHeader::getNumber)
                          .orElse(currentPivotBlockNumber);
              if (distanceNextPivotBlock > 60) {
                if (lockResettingPivotBlock()) {
                  syncActions
                      .waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE)
                      .thenCompose(syncActions::selectPivotBlock)
                      .thenCompose(syncActions::downloadPivotBlockHeader)
                      .thenAccept(fss -> lastBlockFound = fss.getPivotBlockHeader())
                      .orTimeout(5, TimeUnit.MINUTES)
                      .whenComplete((unused, throwable) -> unlockResettingPivotBlock());
                }
              }
              final long distance =
                  syncActions.getSyncState().bestChainHeight() - currentPivotBlockNumber;
              if (distance > 126) {
                switchToNewPivotBlock(onNewPivotBlock);
              }
            });
  }

  private void switchToNewPivotBlock(final Consumer<BlockHeader> onNewPivotBlock) {
    lastBlockFound.ifPresent(
        blockHeader -> {
          LOG.info(
              "Select new pivot block {} {}", blockHeader.getNumber(), blockHeader.getStateRoot());
          syncState.setCurrentHeader(blockHeader);
          onNewPivotBlock.accept(blockHeader);
          worldDownloadState.requestComplete(true);
          worldDownloadState.notifyTaskAvailable();
        });
  }

  public boolean lockResettingPivotBlock() {
    synchronized (this) {
      if (!lock) {
        lock = true;
        return true;
      }
      return false;
    }
  }

  public void unlockResettingPivotBlock() {
    synchronized (this) {
      lock = false;
    }
  }
}
