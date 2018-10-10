package net.consensys.pantheon.ethereum.blockcreation;

import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.util.time.Clock;

public abstract class AbstractBlockScheduler {

  protected final Clock clock;

  public AbstractBlockScheduler(final Clock clock) {
    this.clock = clock;
  }

  public long waitUntilNextBlockCanBeMined(final BlockHeader parentHeader)
      throws InterruptedException {
    final BlockCreationTimeResult result = getNextTimestamp(parentHeader);

    Thread.sleep(result.millisecondsUntilValid);

    return result.timestampForHeader;
  }

  public abstract BlockCreationTimeResult getNextTimestamp(final BlockHeader parentHeader);

  public static class BlockCreationTimeResult {

    private final long timestampForHeader;
    private final long millisecondsUntilValid;

    public BlockCreationTimeResult(
        final long timestampForHeader, final long millisecondsUntilValid) {
      this.timestampForHeader = timestampForHeader;
      this.millisecondsUntilValid = millisecondsUntilValid;
    }

    public long getTimestampForHeader() {
      return timestampForHeader;
    }

    public long getMillisecondsUntilValid() {
      return millisecondsUntilValid;
    }
  }
}
