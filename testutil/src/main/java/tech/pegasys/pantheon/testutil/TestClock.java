/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.testutil;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalUnit;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;

public class TestClock extends Clock {

  private static final Supplier<Clock> fixedNow =
      Suppliers.memoize(() -> Clock.fixed(Instant.now(), ZoneOffset.UTC));
  private Instant now = fixedNow.get().instant();

  public static Clock fixed() {
    return fixedNow.get();
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(final ZoneId zone) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public Instant instant() {
    return now;
  }

  public void stepMillis(final long millis) {
    now = now.plusMillis(millis);
  }

  public void step(final long a, final TemporalUnit unit) {
    now = now.plus(a, unit);
  }
}
