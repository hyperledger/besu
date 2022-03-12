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
package org.hyperledger.besu.util;

import java.util.concurrent.atomic.AtomicInteger;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class QosTimerTest {
  static Vertx vertx = Vertx.vertx();

  @Test
  public void shouldExecuteConsecutivelyAtTimeout(final TestContext ctx) {
    final long TEST_QOS_TIMEOUT = 100L;
    final Async async = ctx.async();
    final AtomicInteger execCount = new AtomicInteger(0);
    new QosTimer(TEST_QOS_TIMEOUT, z -> execCount.incrementAndGet());

    vertx.setTimer(
        250L,
        z -> {
          ctx.assertEquals(2, execCount.get());
          async.complete();
        });
  }

  @Test
  public void shouldExecuteOnceAtTimeout(final TestContext ctx) {
    final long TEST_QOS_TIMEOUT = 75L;
    final Async async = ctx.async();
    final AtomicInteger execCount = new AtomicInteger(0);
    new QosTimer(TEST_QOS_TIMEOUT, z -> execCount.incrementAndGet());

    vertx.setTimer(
        100L,
        z -> {
          ctx.assertEquals(1, execCount.get());
          async.complete();
        });
  }

  @Test
  public void shouldNotExecuteBeforeTimeout(final TestContext ctx) {
    final long TEST_QOS_TIMEOUT = 200L;
    final Async async = ctx.async();
    final AtomicInteger execCount = new AtomicInteger(0);
    new QosTimer(TEST_QOS_TIMEOUT, z -> execCount.incrementAndGet());

    vertx.setTimer(
        50L,
        z -> {
          ctx.assertEquals(0, execCount.get());
          async.complete();
        });
  }

  @Test
  public void shouldNotExecuteWhenReset(final TestContext ctx) {
    final long TEST_QOS_TIMEOUT = 50L;
    final Async async = ctx.async();
    final AtomicInteger execCount = new AtomicInteger(0);
    final var timer = new QosTimer(TEST_QOS_TIMEOUT, z -> execCount.incrementAndGet());

    // reset QoS timer every 25 millis
    vertx.setPeriodic(25L, z -> timer.resetTimer());

    vertx.setTimer(
        200L,
        z -> {
          ctx.assertEquals(0, execCount.get());
          async.complete();
        });
  }
}
