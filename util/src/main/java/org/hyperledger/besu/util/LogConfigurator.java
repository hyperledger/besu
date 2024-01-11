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

import java.util.NoSuchElementException;

import org.slf4j.LoggerFactory;

/** The library independent logger configurator util. */
@SuppressWarnings("CatchAndPrintStackTrace")
public interface LogConfigurator {

  /**
   * Sets level to specified logger.
   *
   * @param parentLogger the logger name
   * @param level the level
   */
  static void setLevel(final String parentLogger, final String level) {
    try {
      // ensure we have at least one log context, to load configs
      LoggerFactory.getLogger(LogConfigurator.class);
      Log4j2ConfiguratorUtil.setAllLevels(parentLogger, level);
    } catch (NoClassDefFoundError | ClassCastException | NoSuchElementException e) {
      // This is expected when Log4j support is not in the classpath, so ignore
    }
  }

  /** Reconfigure. */
  static void reconfigure() {
    try {
      Log4j2ConfiguratorUtil.reconfigure();
    } catch (NoClassDefFoundError | ClassCastException | NoSuchElementException e) {
      // This is expected when Log4j support is not in the classpath, so ignore
    }
  }

  /** Shutdown. */
  static void shutdown() {
    try {
      Log4j2ConfiguratorUtil.shutdown();
    } catch (NoClassDefFoundError | ClassCastException | NoSuchElementException e) {
      // This is expected when Log4j support is not in the classpath, so ignore
    }
  }
}
