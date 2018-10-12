package tech.pegasys.pantheon.ethereum.blockcreation;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.util.time.Clock;

import java.util.concurrent.TimeUnit;

import com.google.common.annotations.VisibleForTesting;

public class DefaultBlockScheduler extends AbstractBlockScheduler {

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
    final long now = TimeUnit.SECONDS.convert(msSinceEpoch, TimeUnit.MILLISECONDS);
    final long parentTimestamp = parentHeader.getTimestamp();

    final long nextHeaderTimestamp = Long.max(parentTimestamp + minimumSecondsSinceParent, now);

    final long earliestBlockTransmissionTime = nextHeaderTimestamp - acceptableClockDriftSeconds;
    final long msUntilBlocKTransmission = (earliestBlockTransmissionTime * 1000) - msSinceEpoch;

    return new BlockCreationTimeResult(nextHeaderTimestamp, Math.max(0, msUntilBlocKTransmission));
  }
}
