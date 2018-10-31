/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.consensus.ibft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.consensus.ibft.ibftevent.BlockTimerExpiry;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class BlockTimerTest {
  private ScheduledExecutorService mockExecutorService;
  private IbftEventQueue mockQueue;
  private Clock mockClock;

  @Before
  public void initialise() {
    mockExecutorService = mock(ScheduledExecutorService.class);
    mockQueue = mock(IbftEventQueue.class);
    mockClock = mock(Clock.class);
  }

  @Test
  public void cancelTimerCancelsWhenNoTimer() {
    final BlockTimer timer = new BlockTimer(mockQueue, 15_000, mockExecutorService, mockClock);
    // Starts with nothing running
    assertThat(timer.isRunning()).isFalse();
    // cancel shouldn't die if there's nothing running
    timer.cancelTimer();
    // there is still nothing running
    assertThat(timer.isRunning()).isFalse();
  }

  @Test
  public void startTimerSchedulesCorrectlyWhenExpiryIsInTheFuture() {
    final long MINIMAL_TIME_BETWEEN_BLOCKS_MILLIS = 15_000;
    final long NOW_MILLIS = 505_000L;
    final long BLOCK_TIME_STAMP = 500L;
    final long EXPECTED_DELAY = 10_000L;

    final BlockTimer timer =
        new BlockTimer(
            mockQueue, MINIMAL_TIME_BETWEEN_BLOCKS_MILLIS, mockExecutorService, mockClock);

    when(mockClock.millis()).thenReturn(NOW_MILLIS);

    final BlockHeader header =
        new BlockHeaderTestFixture().timestamp(BLOCK_TIME_STAMP).buildHeader();
    final ConsensusRoundIdentifier round =
        new ConsensusRoundIdentifier(0xFEDBCA9876543210L, 0x12345678);

    final ScheduledFuture<?> mockedFuture = mock(ScheduledFuture.class);
    Mockito.<ScheduledFuture<?>>when(
            mockExecutorService.schedule(any(Runnable.class), anyLong(), any()))
        .thenReturn(mockedFuture);

    timer.startTimer(round, header);
    verify(mockExecutorService)
        .schedule(any(Runnable.class), eq(EXPECTED_DELAY), eq(TimeUnit.MILLISECONDS));
  }

  @Test
  public void aBlockTimerExpiryEventIsAddedToTheQueueOnExpiry() {

    final long MINIMAL_TIME_BETWEEN_BLOCKS_MILLIS = 1_000;
    final long NOW_MILLIS = 300_500L;
    final long BLOCK_TIME_STAMP = 300;
    final long EXPECTED_DELAY = 500;

    when(mockClock.millis()).thenReturn(NOW_MILLIS);

    final BlockHeader header =
        new BlockHeaderTestFixture().timestamp(BLOCK_TIME_STAMP).buildHeader();
    final ConsensusRoundIdentifier round =
        new ConsensusRoundIdentifier(0xFEDBCA9876543210L, 0x12345678);

    final BlockTimer timer =
        new BlockTimer(
            mockQueue,
            MINIMAL_TIME_BETWEEN_BLOCKS_MILLIS,
            Executors.newSingleThreadScheduledExecutor(),
            mockClock);
    timer.startTimer(round, header);

    // Verify that the event will not be added to the queue immediately
    verify(mockQueue, never()).add(any());

    await()
        .atMost(EXPECTED_DELAY + 200, TimeUnit.MILLISECONDS)
        .atLeast(EXPECTED_DELAY - 200, TimeUnit.MILLISECONDS)
        .until(timer::isRunning, equalTo(false));

    final ArgumentCaptor<IbftEvent> ibftEventCaptor = ArgumentCaptor.forClass(IbftEvent.class);
    verify(mockQueue).add(ibftEventCaptor.capture());

    assertThat(ibftEventCaptor.getValue() instanceof BlockTimerExpiry).isTrue();
    assertThat(((BlockTimerExpiry) ibftEventCaptor.getValue()).getRoundIndentifier())
        .isEqualToComparingFieldByField(round);
  }

  @Test
  public void eventIsImmediatelyAddedToTheQueueIfAbsoluteExpiryIsEqualToNow() {
    final long MINIMAL_TIME_BETWEEN_BLOCKS_MILLIS = 15_000;
    final long NOW_MILLIS = 515_000L;
    final long BLOCK_TIME_STAMP = 500;

    final BlockTimer timer =
        new BlockTimer(
            mockQueue, MINIMAL_TIME_BETWEEN_BLOCKS_MILLIS, mockExecutorService, mockClock);

    when(mockClock.millis()).thenReturn(NOW_MILLIS);

    final BlockHeader header =
        new BlockHeaderTestFixture().timestamp(BLOCK_TIME_STAMP).buildHeader();
    final ConsensusRoundIdentifier round =
        new ConsensusRoundIdentifier(0xFEDBCA9876543210L, 0x12345678);

    timer.startTimer(round, header);
    verify(mockExecutorService, never()).schedule(any(Runnable.class), anyLong(), any());

    final ArgumentCaptor<IbftEvent> ibftEventCaptor = ArgumentCaptor.forClass(IbftEvent.class);
    verify(mockQueue).add(ibftEventCaptor.capture());

    assertThat(ibftEventCaptor.getValue() instanceof BlockTimerExpiry).isTrue();
    assertThat(((BlockTimerExpiry) ibftEventCaptor.getValue()).getRoundIndentifier())
        .isEqualToComparingFieldByField(round);
  }

  @Test
  public void eventIsImmediatelyAddedToTheQueueIfAbsoluteExpiryIsInThePast() {
    final long MINIMAL_TIME_BETWEEN_BLOCKS_MILLIS = 15_000;
    final long NOW_MILLIS = 520_000L;
    final long BLOCK_TIME_STAMP = 500L;

    final BlockTimer timer =
        new BlockTimer(
            mockQueue, MINIMAL_TIME_BETWEEN_BLOCKS_MILLIS, mockExecutorService, mockClock);

    when(mockClock.millis()).thenReturn(NOW_MILLIS);

    final BlockHeader header =
        new BlockHeaderTestFixture().timestamp(BLOCK_TIME_STAMP).buildHeader();
    final ConsensusRoundIdentifier round =
        new ConsensusRoundIdentifier(0xFEDBCA9876543210L, 0x12345678);

    timer.startTimer(round, header);
    verify(mockExecutorService, never()).schedule(any(Runnable.class), anyLong(), any());

    final ArgumentCaptor<IbftEvent> ibftEventCaptor = ArgumentCaptor.forClass(IbftEvent.class);
    verify(mockQueue).add(ibftEventCaptor.capture());

    assertThat(ibftEventCaptor.getValue() instanceof BlockTimerExpiry).isTrue();
    assertThat(((BlockTimerExpiry) ibftEventCaptor.getValue()).getRoundIndentifier())
        .isEqualToComparingFieldByField(round);
  }

  @Test
  public void startTimerCancelsExistingTimer() {
    final long MINIMAL_TIME_BETWEEN_BLOCKS_MILLIS = 15_0000;
    final long NOW_MILLIS = 500_000L;
    final long BLOCK_TIME_STAMP = 500L;

    final BlockTimer timer =
        new BlockTimer(
            mockQueue, MINIMAL_TIME_BETWEEN_BLOCKS_MILLIS, mockExecutorService, mockClock);

    when(mockClock.millis()).thenReturn(NOW_MILLIS);

    final BlockHeader header =
        new BlockHeaderTestFixture().timestamp(BLOCK_TIME_STAMP).buildHeader();
    final ConsensusRoundIdentifier round =
        new ConsensusRoundIdentifier(0xFEDBCA9876543210L, 0x12345678);
    final ScheduledFuture<?> mockedFuture = mock(ScheduledFuture.class);

    Mockito.<ScheduledFuture<?>>when(
            mockExecutorService.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS)))
        .thenReturn(mockedFuture);
    timer.startTimer(round, header);
    verify(mockedFuture, times(0)).cancel(false);
    timer.startTimer(round, header);
    verify(mockedFuture, times(1)).cancel(false);
  }

  @Test
  public void runningFollowsTheStateOfTheTimer() {
    final long MINIMAL_TIME_BETWEEN_BLOCKS_MILLIS = 15_0000;
    final long NOW_MILLIS = 500_000L;
    final long BLOCK_TIME_STAMP = 500L;

    final BlockTimer timer =
        new BlockTimer(
            mockQueue, MINIMAL_TIME_BETWEEN_BLOCKS_MILLIS, mockExecutorService, mockClock);

    when(mockClock.millis()).thenReturn(NOW_MILLIS);

    final BlockHeader header =
        new BlockHeaderTestFixture().timestamp(BLOCK_TIME_STAMP).buildHeader();
    final ConsensusRoundIdentifier round =
        new ConsensusRoundIdentifier(0xFEDBCA9876543210L, 0x12345678);

    final ScheduledFuture<?> mockedFuture = mock(ScheduledFuture.class);
    Mockito.<ScheduledFuture<?>>when(
            mockExecutorService.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS)))
        .thenReturn(mockedFuture);
    timer.startTimer(round, header);
    when(mockedFuture.isDone()).thenReturn(false);
    assertThat(timer.isRunning()).isTrue();
    when(mockedFuture.isDone()).thenReturn(true);
    assertThat(timer.isRunning()).isFalse();
  }
}
