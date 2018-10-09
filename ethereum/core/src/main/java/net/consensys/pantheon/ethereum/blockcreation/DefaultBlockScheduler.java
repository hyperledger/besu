package net.consensys.pantheon.ethereum.blockcreation;

import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.util.time.Clock;

import java.util.concurrent.TimeUnit;

import com.google.common.annotations.VisibleForTesting;

public class DefaultBlockScheduler extends BaseBlockScheduler {

  private final long acceptableClockDriftSeconds;
  private final long minimumSecondsSinceParent;

  public DefaultBlockScheduler(
      final long minimumSecondsSinceParent,
      final long acceptableClockDriftSeconds,
      final Clock clock) {
    super(clock);
    this.acceptableClockDriftSeconds = acceptableClockDriftSeconds;
    this.minimumSecondsSinceParent = minimumSecondsSinceParent;
  }

  @Override
  @VisibleForTesting
  public BlockCreationTimeResult getNextTimestamp(final BlockHeader parentHeader) {
    final long msSinceEpoch = clock.millisecondsSinceEpoch();
    final long secondsSinceEpoch = TimeUnit.SECONDS.convert(msSinceEpoch, TimeUnit.MILLISECONDS);
    final long parentTimestamp = parentHeader.getTimestamp();

    final long nextHeaderTimestamp =
        Long.max(parentTimestamp + minimumSecondsSinceParent, secondsSinceEpoch);

    final long millisecondsUntilHeaderTimeStampIsValid =
        (nextHeaderTimestamp * 1000) - (msSinceEpoch + (acceptableClockDriftSeconds * 1000));

    return new BlockCreationTimeResult(
        nextHeaderTimestamp, Math.max(0, millisecondsUntilHeaderTimeStampIsValid));
  }
}
