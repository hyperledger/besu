/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.eth.sync.snap2;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/** Formats the figures of the SNAP2 logs: counts, block numbers, shares, rates and durations. */
public final class ProgressFormat {

  private static final Duration LONGEST_ETA = Duration.ofDays(365);

  private ProgressFormat() {}

  /** Returns a count with thousands separators, as in 11,901,107. */
  public static String count(final long value) {
    return String.format(Locale.ROOT, "%,d", value);
  }

  /** Returns a block number, as in #11,901,107. */
  public static String block(final long number) {
    return "#" + count(number);
  }

  /** Returns a compact count, as in 2,140, 82.9K, 1.31M, 82.9M or 1.20B. */
  public static String compact(final long value) {
    final long magnitude = Math.abs(value);
    // each unit stops half a shown digit short of the next one, where rounding would reach it
    if (magnitude < 10_000) {
      return count(value);
    }
    if (magnitude < 1_000_000 - 50) {
      return String.format(Locale.ROOT, "%.1fK", value / 1e3);
    }
    if (magnitude < 10_000_000 - 5_000) {
      return String.format(Locale.ROOT, "%.2fM", value / 1e6);
    }
    if (magnitude < 1_000_000_000 - 50_000) {
      return String.format(Locale.ROOT, "%.1fM", value / 1e6);
    }
    return String.format(Locale.ROOT, "%.2fB", value / 1e9);
  }

  /** Returns a duration in two units at most, as in 45s, 3m 10s, 2h 31m or 1d 2h. */
  public static String duration(final Duration duration) {
    final long seconds = Math.max(0, duration.toSeconds());
    final long days = seconds / 86_400;
    final long hours = seconds % 86_400 / 3_600;
    final long minutes = seconds % 3_600 / 60;
    final long rest = seconds % 60;
    if (days > 0) {
      return days + "d" + (hours > 0 ? " " + hours + "h" : "");
    }
    if (hours > 0) {
      return hours + "h" + (minutes > 0 ? " " + minutes + "m" : "");
    }
    if (minutes > 0) {
      return minutes + "m" + (rest > 0 ? " " + rest + "s" : "");
    }
    return rest + "s";
  }

  /** Returns the share of a total as a percentage, as in 37.4%, 0.0% without a total. */
  public static String percent(final long done, final long total) {
    final double share = total <= 0 ? 0 : Math.max(0, Math.min(1, (double) done / total));
    return String.format(Locale.ROOT, "%.1f%%", 100 * share);
  }

  /** Returns a rate in mebibytes per second, as in 11.8 MiB/s. */
  public static String mibPerSecond(final double bytesPerSecond) {
    return String.format(Locale.ROOT, "%.1f MiB/s", Math.max(0, bytesPerSecond) / (1024 * 1024));
  }

  /**
   * Returns the time left to do what remains at the average rate since the start, as in ~45m or ~2h
   * 31m, or empty without a rate, with nothing left, or beyond a year.
   */
  public static Optional<String> eta(
      final double done, final double remaining, final Duration elapsed) {
    final double perSecond = done / (elapsed.toMillis() / 1000.0);
    if (remaining <= 0 || perSecond <= 0 || !Double.isFinite(perSecond)) {
      return Optional.empty();
    }
    final double seconds = Math.ceil(remaining / perSecond);
    if (seconds > LONGEST_ETA.toSeconds()) {
      return Optional.empty();
    }
    // a minute or more reads without its seconds
    final Duration rounded =
        seconds >= 60
            ? Duration.ofMinutes(Math.round(seconds / 60))
            : Duration.ofSeconds((long) seconds);
    return Optional.of("~" + duration(rounded));
  }
}
