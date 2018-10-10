package net.consensys.pantheon.consensus.common;

public class EpochManager {

  private final long epochLengthInBlocks;

  public EpochManager(final long epochLengthInBlocks) {
    this.epochLengthInBlocks = epochLengthInBlocks;
  }

  public boolean isEpochBlock(final long blockNumber) {
    return (blockNumber % epochLengthInBlocks) == 0;
  }

  public long getLastEpochBlock(final long blockNumber) {
    return blockNumber - (blockNumber % epochLengthInBlocks);
  }
}
