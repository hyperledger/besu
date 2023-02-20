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

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/** The Log4j2 configurator util. */
public class JulConfiguratorUtil {

  private JulConfiguratorUtil() {}

  /**
   * Sets all levels.
   *
   * @param parentLogger the parent logger
   * @param levelName the level
   */
  public static void setAllLevels(final String parentLogger, final String levelName) {
    if (levelName == null) {
      Logger.getLogger(parentLogger).setUseParentHandlers(true);
    } else {
      Level level = Level.parse(levelName);
      Handler[] handlers = Logger.getLogger(parentLogger).getHandlers();
      for (Handler handler : handlers) {
        handler.setLevel(level);
      }
    }
  }
}
