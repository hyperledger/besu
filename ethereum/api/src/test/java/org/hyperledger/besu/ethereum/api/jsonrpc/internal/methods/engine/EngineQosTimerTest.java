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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.QosTimer;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class EngineQosTimerTest {
  private EngineQosTimer engineQosTimer;

  private static final Vertx vertx = Vertx.vertx();

  @Before
  public void setUp() throws Exception {
    engineQosTimer = new EngineQosTimer(vertx);
  }

  @Test
  public void shouldNotWarnWhenCalledWithinTimeout(final TestContext ctx) {
    final long TEST_QOS_TIMEOUT = 75L;
    final Async async = ctx.async();
    final var spyEngineQosTimer = spy(engineQosTimer);
    final var spyTimer =
        spy(new QosTimer(vertx, TEST_QOS_TIMEOUT, z -> spyEngineQosTimer.logTimeoutWarning()));
    spyTimer.resetTimer();
    when(spyEngineQosTimer.getQosTimer()).thenReturn(spyTimer);

    // call executionEngineCalled() 50 milliseconds hence to reset our QoS timer
    vertx.setTimer(50L, z -> spyEngineQosTimer.executionEngineCalled());

    vertx.setTimer(
        100L,
        z -> {
          try {
            // once on construction, once on call:
            verify(spyTimer, times(2)).resetTimer();
          } catch (Exception ex) {
            ctx.fail(ex);
          }
          // should not warn
          verify(spyEngineQosTimer, never()).logTimeoutWarning();
          async.complete();
        });
  }

  @Test
  public void shouldWarnWhenNotCalledWithinTimeout(final TestContext ctx) {
    final long TEST_QOS_TIMEOUT = 75L;
    final Async async = ctx.async();
    final var spyEngineQosTimer = spy(engineQosTimer);
    final var spyTimer =
        spy(new QosTimer(vertx, TEST_QOS_TIMEOUT, z -> spyEngineQosTimer.logTimeoutWarning()));
    spyTimer.resetTimer();
    when(spyEngineQosTimer.getQosTimer()).thenReturn(spyTimer);

    vertx.setTimer(
        100L,
        z -> {
          try {
            // once on construction:
            verify(spyTimer, times(1)).resetTimer();
          } catch (Exception ex) {
            ctx.fail(ex);
          }
          // should warn
          verify(spyEngineQosTimer, times(1)).logTimeoutWarning();
          async.complete();
        });
  }
}
