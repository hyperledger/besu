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
package org.hyperledger.besu.cli.options.stable;

import org.apache.logging.log4j.Level;
import picocli.CommandLine;

public class LoggingLevelOption {

  public static LoggingLevelOption create() {
    return new LoggingLevelOption();
  }

  private Level logLevel;

  @CommandLine.Option(
      names = {"--logging", "-l"},
      paramLabel = "<LOG VERBOSITY LEVEL>",
      description = "Logging verbosity levels: OFF, ERROR, WARN, INFO, DEBUG, TRACE, ALL")
  public void setLogLevel(final Level logLevel) {
    if (Level.FATAL.equals(logLevel)) {
      System.out.println("FATAL level is deprecated");
      this.logLevel = Level.ERROR;
    } else {
      this.logLevel = logLevel;
    }
  }

  public Level getLogLevel() {
    return logLevel;
  }
}
