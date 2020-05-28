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
package org.hyperledger.besu.ethereum.p2p.discovery.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class IndirectVertxTimerUtil implements TimerUtil {

  private static final Logger LOG = LogManager.getLogger();

  private final ScheduledExecutorService secheduledExecutor =
      Executors.newSingleThreadScheduledExecutor();
  private final AtomicLong nextId = new AtomicLong(0);
  private final Map<Long, ScheduledFuture<?>> timers = new HashMap<>();
  private final Vertx vertx;

  public IndirectVertxTimerUtil(final Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public long setPeriodic(final long delayInMs, final TimerHandler handler) {
    final long id = nextId.get();
    timers.put(
        id,
        secheduledExecutor.scheduleAtFixedRate(
            () -> vertx.executeBlocking(e -> handler.handle(), r -> {}),
            delayInMs,
            delayInMs,
            TimeUnit.MILLISECONDS));
    return id;
  }

  @Override
  public long setTimer(final long delayInMs, final TimerHandler handler) {
    LOG.debug("calling VertxTimerUtil.setTimer {} delayInMs {} handler", delayInMs, handler);
    final long id = nextId.get();
    timers.put(
        id,
        secheduledExecutor.schedule(
            () -> vertx.executeBlocking(e -> handler.handle(), r -> timers.remove(id)),
            delayInMs,
            TimeUnit.MILLISECONDS));
    return id;
  }

  @Override
  public void cancelTimer(final long timerId) {
    final ScheduledFuture<?> timer = timers.remove(timerId);
    if (timer != null) {
      timer.cancel(true);
    }
  }
}
