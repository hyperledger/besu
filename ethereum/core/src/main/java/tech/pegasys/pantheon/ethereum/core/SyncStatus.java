package tech.pegasys.pantheon.ethereum.core;

public final class SyncStatus {

  private final long startingBlock;
  private final long currentBlock;
  private final long highestBlock;

  public SyncStatus(final long startingBlock, final long currentBlock, final long highestBlock) {
    this.startingBlock = startingBlock;
    this.currentBlock = currentBlock;
    this.highestBlock = highestBlock;
  }

  public long getStartingBlock() {
    return startingBlock;
  }

  public long getCurrentBlock() {
    return currentBlock;
  }

  public long getHighestBlock() {
    return highestBlock;
  }
}
