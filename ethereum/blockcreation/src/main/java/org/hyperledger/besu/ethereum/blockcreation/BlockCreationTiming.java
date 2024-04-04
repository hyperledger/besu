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
    for(final var entry: subTiming.timing.entrySet()) {
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
    final var sb = new StringBuilder("Started at " + startedAt + ", ");

    var prevDuration = Duration.ZERO;
    for (final var entry : timing.entrySet()) {
      sb.append(entry.getKey())
          .append("=")
          .append(entry.getValue().minus(prevDuration))
          .append(", ");
      prevDuration = entry.getValue();
    }
    sb.delete(sb.length() - 2, sb.length());

    return sb.toString();
  }
}
