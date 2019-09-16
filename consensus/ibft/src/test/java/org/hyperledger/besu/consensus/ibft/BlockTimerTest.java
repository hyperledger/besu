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
package org.hyperledger.besu.consensus.ibft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.ibft.ibftevent.BlockTimerExpiry;
import org.hyperledger.besu.consensus.ibft.ibftevent.IbftEvent;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;

import java.time.Clock;
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
    final BlockTimer timer = new BlockTimer(mockQueue, 15, mockExecutorService, mockClock);
    // Starts with nothing running
    assertThat(timer.isRunning()).isFalse();
    // cancel shouldn't die if there's nothing running
    timer.cancelTimer();
    // there is still nothing running
    assertThat(timer.isRunning()).isFalse();
  }

  @Test
  public void startTimerSchedulesCorrectlyWhenExpiryIsInTheFuture() {
    final long MINIMAL_TIME_BETWEEN_BLOCKS_SECONDS = 15;
    final long NOW_MILLIS = 505_000L;
    final long BLOCK_TIME_STAMP = 500L;
    final long EXPECTED_DELAY = 10_000L;

    final BlockTimer timer =
        new BlockTimer(
            mockQueue, MINIMAL_TIME_BETWEEN_BLOCKS_SECONDS, mockExecutorService, mockClock);

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
  public void aBlockTimerExpiryEventIsAddedToTheQueueOnExpiry() throws InterruptedException {
    final long MINIMAL_TIME_BETWEEN_BLOCKS_SECONDS = 1;
    final long NOW_MILLIS = 300_500L;
    final long BLOCK_TIME_STAMP = 300;
    final long EXPECTED_DELAY = 500;

    when(mockClock.millis()).thenReturn(NOW_MILLIS);

    final BlockHeader header =
        new BlockHeaderTestFixture().timestamp(BLOCK_TIME_STAMP).buildHeader();
    final ConsensusRoundIdentifier round =
        new ConsensusRoundIdentifier(0xFEDBCA9876543210L, 0x12345678);

    final ScheduledFuture<?> mockedFuture = mock(ScheduledFuture.class);
    Mockito.<ScheduledFuture<?>>when(
            mockExecutorService.schedule(any(Runnable.class), anyLong(), any()))
        .thenReturn(mockedFuture);

    final IbftEventQueue eventQueue = new IbftEventQueue(1000);
    final BlockTimer timer =
        new BlockTimer(
            eventQueue, MINIMAL_TIME_BETWEEN_BLOCKS_SECONDS, mockExecutorService, mockClock);
    timer.startTimer(round, header);

    // Verify that the event will not be added to the queue immediately
    assertThat(eventQueue.isEmpty()).isTrue();

    // Verify that a task is sceheduled for EXPECTED_DELAY milliseconds in the future
    ArgumentCaptor<Runnable> expiryTask = ArgumentCaptor.forClass(Runnable.class);
    verify(mockExecutorService, times(1))
        .schedule(expiryTask.capture(), eq(EXPECTED_DELAY), eq(TimeUnit.MILLISECONDS));

    // assert that the task puts a BlockExpired event into the queue
    final Runnable scheduledTask = expiryTask.getValue();
    assertThat(eventQueue.isEmpty()).isTrue();
    scheduledTask.run();
    assertThat(eventQueue.size()).isEqualTo(1);
    final IbftEvent queuedEvent = eventQueue.poll(0, TimeUnit.SECONDS);
    assertThat(queuedEvent).isInstanceOf(BlockTimerExpiry.class);
    assertThat(((BlockTimerExpiry) queuedEvent).getRoundIndentifier())
        .isEqualToComparingFieldByField(round);
  }

  @Test
  public void eventIsImmediatelyAddedToTheQueueIfAbsoluteExpiryIsEqualToNow() {
    final long MINIMAL_TIME_BETWEEN_BLOCKS_SECONDS = 15;
    final long NOW_MILLIS = 515_000L;
    final long BLOCK_TIME_STAMP = 500;

    final BlockTimer timer =
        new BlockTimer(
            mockQueue, MINIMAL_TIME_BETWEEN_BLOCKS_SECONDS, mockExecutorService, mockClock);

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
    final long MINIMAL_TIME_BETWEEN_BLOCKS_SECONDS = 15;
    final long NOW_MILLIS = 520_000L;
    final long BLOCK_TIME_STAMP = 500L;

    final BlockTimer timer =
        new BlockTimer(
            mockQueue, MINIMAL_TIME_BETWEEN_BLOCKS_SECONDS, mockExecutorService, mockClock);

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
    final long MINIMAL_TIME_BETWEEN_BLOCKS_SECONDS = 15;
    final long NOW_MILLIS = 500_000L;
    final long BLOCK_TIME_STAMP = 500L;

    final BlockTimer timer =
        new BlockTimer(
            mockQueue, MINIMAL_TIME_BETWEEN_BLOCKS_SECONDS, mockExecutorService, mockClock);

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
    final long MINIMAL_TIME_BETWEEN_BLOCKS_SECONDS = 15;
    final long NOW_MILLIS = 500_000L;
    final long BLOCK_TIME_STAMP = 500L;

    final BlockTimer timer =
        new BlockTimer(
            mockQueue, MINIMAL_TIME_BETWEEN_BLOCKS_SECONDS, mockExecutorService, mockClock);

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
