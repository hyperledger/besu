package net.consensys.pantheon.ethereum.p2p.discovery.internal;

import java.util.function.UnaryOperator;

/**
 * A function to calculate the next retry delay based on the previous one. In the future, this could
 * be enhanced to take in the number of retries so far, so the function can take a decision to abort
 * if too many retries have been attempted.
 */
interface RetryDelayFunction extends UnaryOperator<Long> {

  static RetryDelayFunction linear(final double multiplier, final long min, final long max) {
    return (prev) -> Math.min(Math.max((long) Math.ceil(prev * multiplier), min), max);
  }
}
