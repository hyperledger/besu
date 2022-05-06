/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.metrics;

import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.plugin.services.metrics.Counter;

import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RunnableTimedCounterTest {

  @Mock Counter backedCounter;

  @Test
  public void shouldNotRunTaskIfIntervalNotElapsed() {

    RunnableTimedCounter rtc =
        new RunnableTimedCounter(
            backedCounter, () -> fail("Must not be called"), 1L, TimeUnit.MINUTES);

    rtc.inc();
    verify(backedCounter).inc(1L);
  }

  @Test
  public void shouldRunTaskIfIntervalElapsed() throws InterruptedException {

    Runnable task = mock(Runnable.class);

    RunnableTimedCounter rtc =
        new RunnableTimedCounter(backedCounter, task, 1L, TimeUnit.MICROSECONDS);

    Thread.sleep(1L);

    rtc.inc();

    verify(backedCounter).inc(1L);
    verify(task).run();
  }
}
