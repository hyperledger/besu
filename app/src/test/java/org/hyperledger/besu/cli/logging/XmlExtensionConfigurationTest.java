/*
 * Copyright contributors to Besu.
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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import jakarta.validation.constraints.NotNull;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.junit.jupiter.api.Test;

class XmlExtensionConfigurationTest {

  @Test
  void shouldConfigureConsoleAppenderToUseStdout() {
    // Create a minimal Log4j XML configuration without console appender
    // so that XmlExtensionConfiguration will create one programmatically
    final ConfigurationSource configSource = getConfigurationSource();

    final LoggerContext loggerContext = LoggerContext.getContext(false);
    final XmlExtensionConfiguration configuration =
        new XmlExtensionConfiguration(loggerContext, configSource);

    // Ensure no custom log4j config is detected for this test
    final String originalProp = System.getProperty("log4j.configurationFile");
    try {
      if (originalProp != null) {
        System.clearProperty("log4j.configurationFile");
      }

      // Trigger configuration - should create console appender programmatically
      configuration.doConfigure();

      // Verify the Console appender was created and uses SYSTEM_OUT
      final ConsoleAppender consoleAppender =
          (ConsoleAppender) configuration.getRootLogger().getAppenders().get("Console");

      assertThat(consoleAppender)
          .as(
              "Console appender should be created programmatically when no custom config is present")
          .isNotNull();
      assertThat(consoleAppender.getTarget())
          .as(
              "Console appender should use SYSTEM_OUT not SYSTEM_ERR for colored output to work properly")
          .isEqualTo(ConsoleAppender.Target.SYSTEM_OUT);

    } finally {
      if (originalProp != null) {
        System.setProperty("log4j.configurationFile", originalProp);
      }
    }
  }

  @NotNull
  private static ConfigurationSource getConfigurationSource() {
    final String xmlConfig =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<Configuration status=\"WARN\">"
            + "  <Loggers>"
            + "    <Root level=\"info\"/>"
            + "  </Loggers>"
            + "</Configuration>";

    final ConfigurationSource configSource;
    try {
      configSource =
          new ConfigurationSource(
              new ByteArrayInputStream(xmlConfig.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new RuntimeException("Failed to create configuration source", e);
    }
    return configSource;
  }

  @Test
  void shouldNotCreateConsoleAppenderWhenCustomConfigPresent() {
    // Set a custom log4j configuration file property to simulate custom config
    final String originalProp = System.getProperty("log4j.configurationFile");

    try {
      System.setProperty("log4j.configurationFile", "custom-log4j2.xml");

      final ConfigurationSource configSource = getConfigurationSource();

      final LoggerContext loggerContext = LoggerContext.getContext(false);
      final XmlExtensionConfiguration configuration =
          new XmlExtensionConfiguration(loggerContext, configSource);

      // Trigger configuration
      configuration.doConfigure();

      // Verify no Console appender was created programmatically
      // (since custom config is detected)
      final ConsoleAppender consoleAppender =
          (ConsoleAppender) configuration.getRootLogger().getAppenders().get("Console");

      // Should be null because custom config is detected and createConsoleAppender returns early
      assertThat(consoleAppender)
          .as("Console appender should not be created when custom log4j config is detected")
          .isNull();

    } finally {
      // Restore original system property
      if (originalProp != null) {
        System.setProperty("log4j.configurationFile", originalProp);
      } else {
        System.clearProperty("log4j.configurationFile");
      }
    }
  }
}
