package tech.pegasys.pantheon.ethereum.eth.sync.state;

import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;

public final class FastSyncState {
  private long fastSyncTargetBlockNumber = -1;

  private final SynchronizerConfiguration config;

  public FastSyncState(final SynchronizerConfiguration config) {
    this.config = config;
  }

  /**
   * Registers the chain height that we're trying to sync to.
   *
   * @param blockNumber the height of the chain we are syncing to.
   */
  public void setFastSyncChainTarget(final long blockNumber) {
    fastSyncTargetBlockNumber = blockNumber;
  }

  /** @return the block number at which we switch from fast sync to full sync */
  public long pivot() {
    return Math.max(fastSyncTargetBlockNumber - config.fastSyncPivotDistance(), 0);
  }
}
