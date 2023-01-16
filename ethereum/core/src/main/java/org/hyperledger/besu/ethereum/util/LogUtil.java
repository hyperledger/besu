/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class LogUtil {
  static ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

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
}
