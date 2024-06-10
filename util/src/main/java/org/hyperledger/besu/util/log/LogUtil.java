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
package org.hyperledger.besu.util.log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Utility class for logging. */
public class LogUtil {
  static ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
  static final String BESU_NAMESPACE = "org.hyperledger.besu";
  static final int MAX_SUMMARY_DEPTH = 20;

  private LogUtil() {}

  /**
   * Throttles logging to a given logger.
   *
   * @param logger logger as a String consumer
   * @param logMessage message to log
   * @param shouldLog AtomicBoolean to track whether the message should be logged
   * @param logRepeatDelay delay in seconds between repeated logs
   */
  public static void throttledLog(
      final Consumer<String> logger,
      final String logMessage,
      final AtomicBoolean shouldLog,
      final int logRepeatDelay) {

    if (shouldLog.compareAndSet(true, false)) {
      logger.accept(logMessage);

      final Runnable runnable = () -> shouldLog.set(true);
      executor.schedule(runnable, logRepeatDelay, TimeUnit.SECONDS);
    }
  }

  /**
   * Summarizes the stack trace of a throwable to the first class in the namespace. Useful for
   * limiting exceptionally deep stack traces to the last relevant point in besu code.
   *
   * @param contextMessage message to prepend to the summary
   * @param throwable exception to summarize
   * @param namespace namespace to summarize to
   * @return summary of the StackTrace
   */
  public static String summarizeStackTrace(
      final String contextMessage, final Throwable throwable, final String namespace) {
    StackTraceElement[] stackTraceElements = throwable.getStackTrace();

    List<String> stackTraceSummary = new ArrayList<>();
    int depth = 0;
    for (StackTraceElement element : stackTraceElements) {
      stackTraceSummary.add(String.format("\tat: %s", element));
      if (element.getClassName().startsWith(namespace) || ++depth >= MAX_SUMMARY_DEPTH) {
        break;
      }
    }

    return String.format(
        "%s\nThrowable summary: %s\n%s",
        contextMessage, throwable, String.join("\n", stackTraceSummary));
  }

  /**
   * Summarizes the stack trace of a throwable to the first besu class in the namespace. Useful for
   * limiting exceptionally deep stack traces to the last relevant point in besu code.
   *
   * @param contextMessage message to prepend to the summary
   * @param throwable exception to summarize
   * @return summary of the StackTrace
   */
  public static String summarizeBesuStackTrace(
      final String contextMessage, final Throwable throwable) {
    return summarizeStackTrace(contextMessage, throwable, BESU_NAMESPACE);
  }
}
