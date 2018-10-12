package tech.pegasys.pantheon.ethereum.blockcreation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.blockcreation.AbstractBlockScheduler.BlockCreationTimeResult;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.util.time.Clock;

import org.junit.Test;

public class DefaultBlockSchedulerTest {

  private final long interBlockSeconds = 1L;
  private final long acceptableClockDrift = 10L;
  private final long parentTimeStamp = 500L;

  @Test
  public void canMineBlockOnLimitOfClockDrift() {
    Clock clock = mock(Clock.class);
    DefaultBlockScheduler scheduler =
        new DefaultBlockScheduler(interBlockSeconds, acceptableClockDrift, clock);

    // Determine the system time of parent block creation, which means child will occur on
    // the limit of clock drift.
    final long parentBlockSystemTimeCreation = parentTimeStamp - acceptableClockDrift + 1;
    when(clock.millisecondsSinceEpoch()).thenReturn(parentBlockSystemTimeCreation * 1000);

    BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    BlockHeader parentBlock = headerBuilder.timestamp(parentTimeStamp).buildHeader();
    BlockCreationTimeResult result = scheduler.getNextTimestamp(parentBlock);

    assertThat(result.getTimestampForHeader()).isEqualTo(parentTimeStamp + interBlockSeconds);
    assertThat(result.getMillisecondsUntilValid()).isEqualTo(0);
  }

  @Test
  public void childBlockWithinClockDriftReportsTimeToValidOfZero() {
    Clock clock = mock(Clock.class);
    DefaultBlockScheduler scheduler =
        new DefaultBlockScheduler(interBlockSeconds, acceptableClockDrift, clock);

    when(clock.millisecondsSinceEpoch()).thenReturn(parentTimeStamp * 1000);

    BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    BlockHeader parentBlock = headerBuilder.timestamp(parentTimeStamp).buildHeader();
    BlockCreationTimeResult result = scheduler.getNextTimestamp(parentBlock);

    assertThat(result.getMillisecondsUntilValid()).isEqualTo(0);
  }

  @Test
  public void mustWaitForNextBlockIfTooFarAheadOfSystemTime() {
    final Clock clock = mock(Clock.class);
    final DefaultBlockScheduler scheduler =
        new DefaultBlockScheduler(interBlockSeconds, acceptableClockDrift, clock);

    // Set the clock such that the parenttimestamp is on the limit of acceptability
    when(clock.millisecondsSinceEpoch())
        .thenReturn((parentTimeStamp - acceptableClockDrift) * 1000);

    BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    BlockHeader parentBlock = headerBuilder.timestamp(parentTimeStamp).buildHeader();
    BlockCreationTimeResult result = scheduler.getNextTimestamp(parentBlock);

    assertThat(result.getMillisecondsUntilValid()).isEqualTo(interBlockSeconds * 1000);
  }

  @Test
  public void ifParentTimestampIsBehindCurrentTimeChildUsesCurrentTime() {
    final long secondsSinceEpoch = parentTimeStamp + 5L; // i.e. time is ahead of blockchain

    Clock clock = mock(Clock.class);
    DefaultBlockScheduler scheduler =
        new DefaultBlockScheduler(interBlockSeconds, acceptableClockDrift, clock);

    when(clock.millisecondsSinceEpoch()).thenReturn(secondsSinceEpoch * 1000);

    BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    BlockHeader parentBlock = headerBuilder.timestamp(parentTimeStamp).buildHeader();
    BlockCreationTimeResult result = scheduler.getNextTimestamp(parentBlock);

    assertThat(result.getTimestampForHeader()).isEqualTo(secondsSinceEpoch);
  }
}
