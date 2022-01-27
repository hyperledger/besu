package org.hyperledger.besu.util;

import java.util.Arrays;
import java.util.function.Supplier;

import org.slf4j.Logger;

/**
 * Static helper class to shim SLF4J with lambda parameter suppliers until the final release of
 * SLF4J 2.0.
 */
public class Slf4jLambdaHelper {

  public static void debugLambda(
      final Logger log, final String message, final Supplier<?>... params) {
    if (log.isDebugEnabled()) {
      log.debug(message, Arrays.stream(params).map(Supplier::get).toArray());
    }
  }

  public static void traceLambda(
      final Logger log, final String message, final Supplier<?>... params) {
    if (log.isTraceEnabled()) {
      log.trace(message, Arrays.stream(params).map(Supplier::get).toArray());
    }
  }

  public static void warnLambda(
      final Logger log, final String message, final Supplier<?>... params) {
    if (log.isWarnEnabled()) {
      log.warn(message, Arrays.stream(params).map(Supplier::get).toArray());
    }
  }
}
