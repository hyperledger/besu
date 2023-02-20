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

public interface LogConfigurator {

  static void setLevel(final String parentLogger, final String level) {
    try {
      Log4j2ConfiguratorUtil.setAllLevels(parentLogger, level);
    } catch (Throwable t) {
      // This is expected when Log4j support is not in the classpath, so ignore
    }
    try {
      JulConfiguratorUtil.setAllLevels(parentLogger, level);
    } catch (Throwable t) {
      // This is expected when java.util.logging support is not in the classpath, so ignore
    }
  }

  static void reconfigure() {
    try {
      Log4j2ConfiguratorUtil.reconfigure();
    } catch (Throwable t) {
      // This is expected when Log4j support is not in the classpath, so ignore
    }
  }

  static void shutdown() {
    try {
      Log4j2ConfiguratorUtil.shutdown();
    } catch (Throwable t) {
      // This is expected when Log4j is not in the classpath, so ignore
    }
  }
}
