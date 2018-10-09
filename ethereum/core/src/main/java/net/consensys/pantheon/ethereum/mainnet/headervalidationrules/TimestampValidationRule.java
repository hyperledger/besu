package net.consensys.pantheon.ethereum.mainnet.headervalidationrules;

import static com.google.common.base.Preconditions.checkArgument;

import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.mainnet.DetachedBlockHeaderValidationRule;

import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Responsible for ensuring the timestamp of a block is newer than its parent, but also that it has
 * a timestamp not more than "acceptableClockDriftSeconds' into the future.
 */
public class TimestampValidationRule implements DetachedBlockHeaderValidationRule {

  private final Logger LOGGER = LogManager.getLogger(TimestampValidationRule.class);
  private final long acceptableClockDriftSeconds;
  private final long minimumSecondsSinceParent;

  public TimestampValidationRule(
      final long acceptableClockDriftSeconds, final long minimumSecondsSinceParent) {
    checkArgument(minimumSecondsSinceParent >= 0, "minimumSecondsSinceParent must be positive");
    this.acceptableClockDriftSeconds = acceptableClockDriftSeconds;
    this.minimumSecondsSinceParent = minimumSecondsSinceParent;
  }

  @Override
  public boolean validate(final BlockHeader header, final BlockHeader parent) {
    return validateTimestamp(header.getTimestamp(), parent.getTimestamp());
  }

  private boolean validateTimestamp(final long timestamp, final long parentTimestamp) {
    boolean result = validateHeaderSufficientlyAheadOfParent(timestamp, parentTimestamp);
    result &= validateHeaderNotAheadOfCurrentSystemTime(timestamp);

    return result;
  }

  private boolean validateHeaderSufficientlyAheadOfParent(
      final long timestamp, final long parentTimestamp) {
    if ((timestamp - minimumSecondsSinceParent) < parentTimestamp) {
      LOGGER.trace(
          "Invalid block header: timestamp {} is not sufficiently newer than parent timestamp {}",
          timestamp,
          parentTimestamp);
      return false;
    }

    return true;
  }

  private boolean validateHeaderNotAheadOfCurrentSystemTime(final long timestamp) {
    final long timestampMargin =
        TimeUnit.SECONDS.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            + acceptableClockDriftSeconds;
    if (Long.compareUnsigned(timestamp, timestampMargin) > 0) {
      LOGGER.trace(
          "Invalid block header: timestamp {} is greater than the timestamp margin {}",
          timestamp,
          timestampMargin);
      return false;
    }
    return true;
  }
}
