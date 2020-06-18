/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.cli.logging;

import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;
import org.apache.logging.log4j.core.layout.PatternLayout;

public class XmlExtensionConfiguration extends XmlConfiguration {
  public XmlExtensionConfiguration(
      final LoggerContext loggerContext, final ConfigurationSource configSource) {
    super(loggerContext, configSource);
  }

  @Override
  protected void doConfigure() {
    super.doConfigure();

    final boolean color = true;
    final String pattern =
        color
            ? "%style{%d{yyyy-MM-dd HH:mm:ss.SSSZZZ} | }{DIM}%highlight{%t | %-5level | %c{1} | %msg%n}{TRACE=normal}"
            : "%d{yyyy-MM-dd HH:mm:ss.SSSZZZ} | %t | %-5level | %c{1} | %msg%n";
    final PatternLayout patternLayout =
        PatternLayout.newBuilder()
            .withConfiguration(this)
            .withNoConsoleNoAnsi(true)
            .withPattern(pattern)
            .build();
    final ConsoleAppender consoleAppender =
        ConsoleAppender.newBuilder().setName("Console").setLayout(patternLayout).build();
    consoleAppender.start();
    this.getRootLogger().addAppender(consoleAppender, getRootLogger().getLevel(), null);
  }
}
