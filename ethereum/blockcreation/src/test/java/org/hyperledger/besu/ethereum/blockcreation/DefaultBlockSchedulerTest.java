/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.blockcreation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;

import java.time.Clock;

import org.junit.jupiter.api.Test;

public class DefaultBlockSchedulerTest {

  private final long interBlockSeconds = 1L;
  private final long acceptableClockDrift = 10L;
  private final long parentTimeStamp = 500L;

  @Test
  public void canMineBlockOnLimitOfClockDrift() {
    final Clock clock = mock(Clock.class);
    final DefaultBlockScheduler scheduler =
        new DefaultBlockScheduler(interBlockSeconds, acceptableClockDrift, clock);

    // Determine the system time of parent block creation, which means child will occur on
    // the limit of clock drift.
    final long parentBlockSystemTimeCreation = parentTimeStamp - acceptableClockDrift + 1;
    when(clock.millis()).thenReturn(parentBlockSystemTimeCreation * 1000);

    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    final BlockHeader parentBlock = headerBuilder.timestamp(parentTimeStamp).buildHeader();
    final AbstractBlockScheduler.BlockCreationTimeResult result =
        scheduler.getNextTimestamp(parentBlock);

    assertThat(result.timestampForHeader()).isEqualTo(parentTimeStamp + interBlockSeconds);
    assertThat(result.millisecondsUntilValid()).isEqualTo(0);
  }

  @Test
  public void childBlockWithinClockDriftReportsTimeToValidOfZero() {
    final Clock clock = mock(Clock.class);
    final DefaultBlockScheduler scheduler =
        new DefaultBlockScheduler(interBlockSeconds, acceptableClockDrift, clock);

    when(clock.millis()).thenReturn(parentTimeStamp * 1000);

    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    final BlockHeader parentBlock = headerBuilder.timestamp(parentTimeStamp).buildHeader();
    final AbstractBlockScheduler.BlockCreationTimeResult result =
        scheduler.getNextTimestamp(parentBlock);

    assertThat(result.millisecondsUntilValid()).isEqualTo(0);
  }

  @Test
  public void mustWaitForNextBlockIfTooFarAheadOfSystemTime() {
    final Clock clock = mock(Clock.class);
    final DefaultBlockScheduler scheduler =
        new DefaultBlockScheduler(interBlockSeconds, acceptableClockDrift, clock);

    // Set the clock such that the parenttimestamp is on the limit of acceptability
    when(clock.millis()).thenReturn((parentTimeStamp - acceptableClockDrift) * 1000);

    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    final BlockHeader parentBlock = headerBuilder.timestamp(parentTimeStamp).buildHeader();
    final AbstractBlockScheduler.BlockCreationTimeResult result =
        scheduler.getNextTimestamp(parentBlock);

    assertThat(result.millisecondsUntilValid()).isEqualTo(interBlockSeconds * 1000);
  }

  @Test
  public void ifParentTimestampIsBehindCurrentTimeChildUsesCurrentTime() {
    final long secondsSinceEpoch = parentTimeStamp + 5L; // i.e. time is ahead of blockchain

    final Clock clock = mock(Clock.class);
    final DefaultBlockScheduler scheduler =
        new DefaultBlockScheduler(interBlockSeconds, acceptableClockDrift, clock);

    when(clock.millis()).thenReturn(secondsSinceEpoch * 1000);

    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    final BlockHeader parentBlock = headerBuilder.timestamp(parentTimeStamp).buildHeader();
    final AbstractBlockScheduler.BlockCreationTimeResult result =
        scheduler.getNextTimestamp(parentBlock);

    assertThat(result.timestampForHeader()).isEqualTo(secondsSinceEpoch);
  }
}
