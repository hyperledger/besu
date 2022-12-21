package org.hyperledger.besu.ethereum.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class LogUtil {
  public static void throttledLog(
      final Consumer<String> logger,
      final String logMessage,
      final AtomicBoolean shouldLog,
      final int logRepeatDelay) {
    if (shouldLog.compareAndSet(true, false)) {
      logger.accept(logMessage);
      final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
      final Runnable runnable =
          () -> {
            shouldLog.set(true);
            executor.shutdown();
          };
      executor.schedule(runnable, logRepeatDelay, TimeUnit.SECONDS);
    }
  }
}
