/*
 * Copyright contributors to Hyperledger Besu.
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

import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.QosTimer;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
public class EngineQosTimerTest {
  private EngineQosTimer engineQosTimer;
  private Vertx vertx;
  private VertxTestContext testContext;

  @BeforeEach
  public void setUp() throws Exception {
    vertx = Vertx.vertx();
    testContext = new VertxTestContext();
    engineQosTimer = new EngineQosTimer(vertx);
  }

  @AfterEach
  public void cleanUp() {
    vertx.close();
  }

  @Test
  public void shouldNotWarnWhenCalledWithinTimeout() {
    final long TEST_QOS_TIMEOUT = 75L;
    final var spyEngineQosTimer = spy(engineQosTimer);
    final var spyTimer =
        spy(new QosTimer(vertx, TEST_QOS_TIMEOUT, z -> spyEngineQosTimer.logTimeoutWarning()));
    spyTimer.resetTimer();
    doReturn(spyTimer).when(spyEngineQosTimer).getQosTimer();

    // call executionEngineCalled() 50 milliseconds hence to reset our QoS timer
    vertx.setTimer(50L, z -> spyEngineQosTimer.executionEngineCalled());

    vertx.setTimer(
        100L,
        z -> {
          try {
            verify(spyTimer, atLeast(2)).resetTimer();
            // should not warn
            verify(spyEngineQosTimer, never()).logTimeoutWarning();
            testContext.completeNow();
          } catch (Exception ex) {
            testContext.failNow(ex);
          }
        });
  }

  @Test
  public void shouldWarnWhenNotCalledWithinTimeout() {
    final long TEST_QOS_TIMEOUT = 75L;
    final var spyEngineQosTimer = spy(engineQosTimer);
    final var spyTimer =
        spy(new QosTimer(vertx, TEST_QOS_TIMEOUT, z -> spyEngineQosTimer.logTimeoutWarning()));
    spyTimer.resetTimer();
    doReturn(spyTimer).when(spyEngineQosTimer).getQosTimer();

    vertx.setTimer(
        100L,
        z -> {
          try {
            verify(spyTimer, atLeastOnce()).resetTimer();
            // should warn
            verify(spyEngineQosTimer, atLeastOnce()).logTimeoutWarning();
            testContext.completeNow();
          } catch (Exception ex) {
            testContext.failNow(ex);
          }
        });
  }
}
