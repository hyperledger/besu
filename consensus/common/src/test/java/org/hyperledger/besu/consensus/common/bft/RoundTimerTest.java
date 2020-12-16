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
package org.hyperledger.besu.consensus.common.bft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.common.bft.events.RoundExpiry;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class RoundTimerTest {
  private BftExecutors bftExecutors;
  private BftEventQueue queue;
  private RoundTimer timer;

  @Before
  public void initialise() {
    bftExecutors = mock(BftExecutors.class);
    queue = new BftEventQueue(1000);
    timer = new RoundTimer(queue, 1, bftExecutors);
  }

  @Test
  public void cancelTimerCancelsWhenNoTimer() {
    // Starts with nothing running
    assertThat(timer.isRunning()).isFalse();
    // cancel shouldn't die if there's nothing running
    timer.cancelTimer();
    // there is still nothing running
    assertThat(timer.isRunning()).isFalse();
  }

  @Test
  public void startTimerSchedulesTimerCorrectlyForRound0() {
    checkTimerForRound(0, 1000);
  }

  @Test
  public void startTimerSchedulesTimerCorrectlyForRound1() {
    checkTimerForRound(1, 2000);
  }

  @Test
  public void startTimerSchedulesTimerCorrectlyForRound2() {
    checkTimerForRound(2, 4000);
  }

  @Test
  public void startTimerSchedulesTimerCorrectlyForRound3() {
    checkTimerForRound(3, 8000);
  }

  private void checkTimerForRound(final int roundNumber, final long timeout) {
    // Start a new timer for round
    final ConsensusRoundIdentifier round = new ConsensusRoundIdentifier(1, roundNumber);
    final ScheduledFuture<?> mockedFuture = mock(ScheduledFuture.class);
    Mockito.<ScheduledFuture<?>>when(
            bftExecutors.scheduleTask(any(Runnable.class), eq(timeout), eq(TimeUnit.MILLISECONDS)))
        .thenReturn(mockedFuture);
    timer.startTimer(round);
    verify(bftExecutors).scheduleTask(any(Runnable.class), eq(timeout), eq(TimeUnit.MILLISECONDS));
  }

  @Test
  public void checkRunnableSubmittedDistributesCorrectEvent() throws InterruptedException {
    final ConsensusRoundIdentifier round = new ConsensusRoundIdentifier(1, 5);
    final ScheduledFuture<?> mockedFuture = mock(ScheduledFuture.class);
    final ArgumentCaptor<Runnable> expiryRunnable = ArgumentCaptor.forClass(Runnable.class);
    Mockito.<ScheduledFuture<?>>when(
            bftExecutors.scheduleTask(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS)))
        .thenReturn(mockedFuture);
    timer.startTimer(round);
    verify(bftExecutors)
        .scheduleTask(expiryRunnable.capture(), anyLong(), eq(TimeUnit.MILLISECONDS));
    assertThat(queue.poll(0, TimeUnit.MICROSECONDS)).isNull();
    expiryRunnable.getValue().run();
    assertThat(queue.poll(0, TimeUnit.MICROSECONDS)).isEqualTo(new RoundExpiry(round));
  }

  @Test
  public void startTimerCancelsExistingTimer() {
    final ConsensusRoundIdentifier round = new ConsensusRoundIdentifier(1, 0);
    final ScheduledFuture<?> mockedFuture = mock(ScheduledFuture.class);
    Mockito.<ScheduledFuture<?>>when(
            bftExecutors.scheduleTask(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS)))
        .thenReturn(mockedFuture);
    timer.startTimer(round);
    verify(mockedFuture, times(0)).cancel(false);
    timer.startTimer(round);
    verify(mockedFuture, times(1)).cancel(false);
  }

  @Test
  public void runningFollowsTheStateOfTheTimer() {
    final ConsensusRoundIdentifier round = new ConsensusRoundIdentifier(1, 0);
    final ScheduledFuture<?> mockedFuture = mock(ScheduledFuture.class);
    Mockito.<ScheduledFuture<?>>when(
            bftExecutors.scheduleTask(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS)))
        .thenReturn(mockedFuture);
    timer.startTimer(round);
    when(mockedFuture.isDone()).thenReturn(false);
    assertThat(timer.isRunning()).isTrue();
    when(mockedFuture.isDone()).thenReturn(true);
    assertThat(timer.isRunning()).isFalse();
  }
}
