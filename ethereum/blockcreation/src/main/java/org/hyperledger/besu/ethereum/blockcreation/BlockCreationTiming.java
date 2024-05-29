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
package org.hyperledger.besu.ethereum.blockcreation;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.common.base.Stopwatch;

public class BlockCreationTiming {
  private final Map<String, Duration> timing = new LinkedHashMap<>();
  private final Stopwatch stopwatch;
  private final Instant startedAt = Instant.now();

  public BlockCreationTiming() {
    this.stopwatch = Stopwatch.createStarted();
  }

  public void register(final String step) {
    timing.put(step, stopwatch.elapsed());
  }

  public void registerAll(final BlockCreationTiming subTiming) {
    final var offset = Duration.between(startedAt, subTiming.startedAt);
    for (final var entry : subTiming.timing.entrySet()) {
      timing.put(entry.getKey(), offset.plus(entry.getValue()));
    }
  }

  public Duration end(final String step) {
    final var elapsed = stopwatch.stop().elapsed();
    timing.put(step, elapsed);
    return elapsed;
  }

  @Override
  public String toString() {
    final var sb = new StringBuilder("started at " + startedAt + ", ");

    var prevDuration = Duration.ZERO;
    for (final var entry : timing.entrySet()) {
      sb.append(entry.getKey())
          .append("=")
          .append(entry.getValue().minus(prevDuration).toMillis())
          .append("ms, ");
      prevDuration = entry.getValue();
    }
    sb.delete(sb.length() - 2, sb.length());

    return sb.toString();
  }
}
