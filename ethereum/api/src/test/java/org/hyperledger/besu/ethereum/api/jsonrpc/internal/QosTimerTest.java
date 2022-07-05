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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.concurrent.TimeoutException;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class QosTimerTest {
  static Vertx vertx = Vertx.vertx();

  @Ignore("fails on CI with short timeouts and don't want to slow test suite down with longer ones")
  @Test
  public void shouldExecuteConsecutivelyAtTimeout(final TestContext ctx) {
    final long TEST_QOS_TIMEOUT = 100L;
    final int INVOCATIONS_COUNT = 2;
    final Async async = ctx.async(INVOCATIONS_COUNT);
    final long startTime = System.currentTimeMillis();
    new QosTimer(vertx, TEST_QOS_TIMEOUT, z -> async.countDown());
    async.awaitSuccess();
    final long executionTime = System.currentTimeMillis() - startTime;
    assertThat(executionTime)
        .as("Execution ended ahead of time")
        .isGreaterThanOrEqualTo(TEST_QOS_TIMEOUT * INVOCATIONS_COUNT);
  }

  @Test
  public void shouldExecuteOnceAtTimeout(final TestContext ctx) {
    final long TEST_QOS_TIMEOUT = 75L;
    final Async async = ctx.async();
    final long startTime = System.currentTimeMillis();
    new QosTimer(vertx, TEST_QOS_TIMEOUT, z -> async.countDown());
    async.awaitSuccess();
    final long executionTime = System.currentTimeMillis() - startTime;
    assertThat(executionTime)
        .as("Execution ended ahead of time")
        .isGreaterThanOrEqualTo(TEST_QOS_TIMEOUT);
  }

  @Test
  public void shouldNotExecuteBeforeTimeout(final TestContext ctx) {
    final long TEST_QOS_TIMEOUT = 200L;
    final Async async = ctx.async();
    new QosTimer(vertx, TEST_QOS_TIMEOUT, z -> async.countDown());
    assertThatThrownBy(() -> async.awaitSuccess(50L)).isInstanceOf(TimeoutException.class);
  }

  @Test
  public void shouldNotExecuteWhenReset(final TestContext ctx) {
    final long TEST_QOS_TIMEOUT = 50L;
    final Async async = ctx.async();
    final var timer = new QosTimer(vertx, TEST_QOS_TIMEOUT, z -> async.countDown());
    // reset QoS timer every 25 millis
    vertx.setPeriodic(25L, z -> timer.resetTimer());
    assertThatThrownBy(() -> async.awaitSuccess(200L)).isInstanceOf(TimeoutException.class);
    async.complete();
  }
}
