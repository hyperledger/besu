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

import java.util.Set;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/** The Logging level CLI option. */
public class LoggingLevelOption {

  /**
   * Create logging level option.
   *
   * @return the logging level option
   */
  public static LoggingLevelOption create() {
    return new LoggingLevelOption();
  }

  private static final Set<String> ACCEPTED_VALUES =
      Set.of("OFF", "ERROR", "WARN", "INFO", "DEBUG", "TRACE", "ALL");
  /** The Picocli CommandSpec. Visible for testing. Injected by Picocli framework at runtime. */
  @Spec CommandSpec spec;

  private String logLevel;

  /**
   * Sets log level.
   *
   * @param logLevel the log level
   */
  @CommandLine.Option(
      names = {"--logging", "-l"},
      paramLabel = "<LOG VERBOSITY LEVEL>",
      description = "Logging verbosity levels: OFF, ERROR, WARN, INFO, DEBUG, TRACE, ALL")
  public void setLogLevel(final String logLevel) {
    if ("FATAL".equalsIgnoreCase(logLevel)) {
      System.out.println("FATAL level is deprecated");
      this.logLevel = "ERROR";
    } else if (ACCEPTED_VALUES.contains(logLevel.toUpperCase())) {
      this.logLevel = logLevel.toUpperCase();
    } else {
      throw new CommandLine.ParameterException(
          spec.commandLine(), "Unknown logging value: " + logLevel);
    }
  }

  /**
   * Gets log level.
   *
   * @return the log level
   */
  public String getLogLevel() {
    return logLevel;
  }
}
