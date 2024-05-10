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

import java.util.List;

import com.google.common.base.Splitter;
import org.apache.commons.lang3.StringUtils;

/** The Framed log message. */
public class FramedLogMessage {
  private static final int MAX_LINE_LENGTH = 100;

  private FramedLogMessage() {}

  /**
   * Generate log lines as String.
   *
   * @param logLines the log lines
   * @return the string
   */
  public static String generate(final List<String> logLines) {
    final StringBuilder builder = new StringBuilder("\n");
    appendHeader(builder);

    logLines.forEach(
        logLine ->
            Splitter.fixedLength(76)
                .split(logLine)
                .forEach(
                    splitLogLine ->
                        builder.append(
                            String.format(
                                "# %s #\n",
                                StringUtils.rightPad(splitLogLine, MAX_LINE_LENGTH - 4)))));

    appendFooter(builder);

    return builder.toString();
  }

  /**
   * Generate logs as centered string.
   *
   * @param logLines the log lines
   * @return the string
   */
  public static String generateCentered(final List<String> logLines) {
    final StringBuilder builder = new StringBuilder("\n");
    appendHeader(builder);

    logLines.forEach(
        logLine ->
            builder.append(
                String.format("#%s#\n", StringUtils.center(logLine, MAX_LINE_LENGTH - 2))));

    appendFooter(builder);

    return builder.toString();
  }

  private static void appendHeader(final StringBuilder builder) {
    builder.append("#".repeat(MAX_LINE_LENGTH) + "\n").append(emptyLine());
  }

  private static void appendFooter(final StringBuilder builder) {
    builder.append(emptyLine()).append("#".repeat(MAX_LINE_LENGTH));
  }

  private static String emptyLine() {
    return String.format("#%s#\n", StringUtils.center("", MAX_LINE_LENGTH - 2));
  }
}
