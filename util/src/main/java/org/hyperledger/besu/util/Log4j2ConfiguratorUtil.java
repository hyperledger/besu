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
package org.hyperledger.besu.util;

import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.util.Strings;
import org.apache.logging.slf4j.Log4jLoggerFactory;
import org.slf4j.LoggerFactory;

public class Log4j2ConfiguratorUtil {

  private Log4j2ConfiguratorUtil() {}

  public static void setAllLevels(final String parentLogger, final Level level) {
    // 1) get logger config
    // 2) if exact match, use it, if not, create it.
    // 3) set level on logger config
    // 4) update child logger configs with level
    // 5) update loggers
    final LoggerContext loggerContext = getLoggerContext();
    final Configuration config = loggerContext.getConfiguration();
    boolean set = setLevel(parentLogger, level, config);
    for (final Map.Entry<String, LoggerConfig> entry : config.getLoggers().entrySet()) {
      if (entry.getKey().startsWith(parentLogger)) {
        set |= setLevel(entry.getValue(), level);
      }
    }
    if (set) {
      loggerContext.updateLoggers();
    }
  }

  public static void setLevelDebug(final String loggerName) {
    setLevel(loggerName, Level.DEBUG);
  }

  public static void setLevel(final String loggerName, final Level level) {
    final LoggerContext loggerContext = getLoggerContext();
    if (Strings.isEmpty(loggerName)) {
      setRootLevel(loggerContext, level);
    } else if (setLevel(loggerName, level, loggerContext.getConfiguration())) {
      loggerContext.updateLoggers();
    }
  }

  private static boolean setLevel(
      final String loggerName, final Level level, final Configuration config) {
    boolean set;
    LoggerConfig loggerConfig = config.getLoggerConfig(loggerName);
    if (!loggerName.equals(loggerConfig.getName())) {
      loggerConfig = new LoggerConfig(loggerName, level, true);
      config.addLogger(loggerName, loggerConfig);
      loggerConfig.setLevel(level);
      set = true;
    } else {
      set = setLevel(loggerConfig, level);
    }
    return set;
  }

  private static boolean setLevel(final LoggerConfig loggerConfig, final Level level) {
    final boolean set = !loggerConfig.getLevel().equals(level);
    if (set) {
      loggerConfig.setLevel(level);
    }
    return set;
  }

  private static void setRootLevel(final LoggerContext loggerContext, final Level level) {
    final LoggerConfig loggerConfig = loggerContext.getConfiguration().getRootLogger();
    if (!loggerConfig.getLevel().equals(level)) {
      loggerConfig.setLevel(level);
      loggerContext.updateLoggers();
    }
  }

  public static void reconfigure() {
    getLoggerContext().reconfigure();
  }

  private static LoggerContext getLoggerContext() {
    final Set<org.apache.logging.log4j.spi.LoggerContext> loggerContexts =
        ((Log4jLoggerFactory) LoggerFactory.getILoggerFactory()).getLoggerContexts();
    return (LoggerContext) loggerContexts.iterator().next();
  }

  public static void shutdown() {
    getLoggerContext().terminate();
  }
}
