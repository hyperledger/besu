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

import java.net.URL;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.core.joran.spi.JoranException;
import org.slf4j.LoggerFactory;

public class LogbackConfiguratorUtil {

  private LogbackConfiguratorUtil() {}

  public static void setAllLevels(final String parentLogger, final Level level) {
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    for (Logger logger : loggerContext.getLoggerList()) {
      if (logger.getName().startsWith(parentLogger)) {
        logger.setLevel(level);
      }
    }
  }

  public static void setLevelDebug(final String loggerName) {
    setLevel(loggerName, Level.DEBUG);
  }

  public static void setLevel(final String loggerName, final Level level) {
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    if (loggerName == null || loggerName.isBlank()) {
      loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(level);
    } else {
      loggerContext.getLogger(loggerName).setLevel(level);
    }
  }

  public static void reconfigure() throws JoranException {
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    ContextInitializer ci = new ContextInitializer(loggerContext);
    URL url = ci.findURLOfDefaultConfigurationFile(true);
    loggerContext.reset();
    ci.configureByResource(url);
  }

  public static void shutdown() {
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    loggerContext.stop();
  }
}
