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
package org.hyperledger.besu.cli.options;

import picocli.CommandLine;

/** The Logging format CLI option. */
public class LoggingFormatOption {
  /** Default Constructor. */
  LoggingFormatOption() {}

  /**
   * Create logging format option.
   *
   * @return the logging format option
   */
  public static LoggingFormatOption create() {
    return new LoggingFormatOption();
  }

  @CommandLine.Option(
      names = {"--logging-format"},
      paramLabel = "<LOG FORMAT>",
      description =
          "Logging output format: PLAIN (default), ECS (Elastic Common Schema), "
              + "GCP (Google Cloud Platform), LOGSTASH (Logstash JSON Event Layout V1), "
              + "GELF (Graylog Extended Log Format)")
  private LoggingFormat loggingFormat = LoggingFormat.PLAIN;

  /**
   * Gets the logging format.
   *
   * @return the logging format
   */
  public LoggingFormat getLoggingFormat() {
    return loggingFormat;
  }
}
