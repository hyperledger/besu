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
package org.hyperledger.besu.ethereum.retesteth;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

public class RetestethClock extends Clock {

  private Optional<Instant> fixedInstant;
  private final Clock delegateClock;

  RetestethClock() {
    this(Clock.systemUTC());
  }

  private RetestethClock(final Clock delegateClock) {
    fixedInstant = Optional.empty();
    this.delegateClock = delegateClock;
  }

  @Override
  public ZoneId getZone() {
    return delegateClock.getZone();
  }

  @Override
  public Clock withZone(final ZoneId zone) {
    final RetestethClock zonedClock = new RetestethClock(delegateClock.withZone(zone));
    zonedClock.fixedInstant = fixedInstant;
    return zonedClock;
  }

  @Override
  public Instant instant() {
    return fixedInstant.orElseGet(delegateClock::instant);
  }

  public void resetTime(final long time) {
    fixedInstant = Optional.of(Instant.ofEpochSecond(time));
  }

  public void advanceSeconds(final long seconds) {
    fixedInstant = Optional.of(Instant.ofEpochSecond(instant().getEpochSecond() + seconds));
  }
}
