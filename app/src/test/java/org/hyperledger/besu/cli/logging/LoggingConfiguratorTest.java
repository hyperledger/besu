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
package org.hyperledger.besu.cli.logging;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.cli.options.LoggingFormat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LoggingConfiguratorTest {

  @AfterEach
  void resetConfiguration() {
    LoggerContext.getContext(false).reconfigure();
  }

  private Configuration applyAndGetConfig(
      final String logLevel, final LoggingFormat format, final boolean colorEnabled) {
    LoggingConfigurator.configureLogging(logLevel, format, colorEnabled);
    return LoggerContext.getContext(false).getConfiguration();
  }

  // --- Console Appender: Format Selection ---

  @Test
  void plainFormatCreatesPatternLayout() {
    final Configuration config = applyAndGetConfig("INFO", LoggingFormat.PLAIN, false);
    final Appender console = config.getAppenders().get("Console");

    assertThat(console).isNotNull();
    assertThat(console.getLayout()).isInstanceOf(PatternLayout.class);
    assertThat(((PatternLayout) console.getLayout()).getConversionPattern()).contains("%msgc");
  }

  @ParameterizedTest
  @EnumSource(
      value = LoggingFormat.class,
      names = {"ECS", "GCP", "LOGSTASH", "GELF"})
  void jsonFormatsCreateJsonTemplateLayout(final LoggingFormat format) {
    final Configuration config = applyAndGetConfig("INFO", format, false);
    final Appender console = config.getAppenders().get("Console");

    assertThat(console).isNotNull();
    assertThat(console.getLayout().getClass().getSimpleName()).isEqualTo("JsonTemplateLayout");
  }

  @Test
  void splunkWithoutEnvVarsFallsBackToConsoleWithWarning() {
    Assumptions.assumeTrue(
        System.getenv("SPLUNK_URL") == null || System.getenv("SPLUNK_URL").isEmpty(),
        "Skipping: SPLUNK_URL environment variable is set");

    final PrintStream originalErr = System.err;
    final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    System.setErr(new PrintStream(errContent));
    try {
      final Configuration config = applyAndGetConfig("INFO", LoggingFormat.SPLUNK, false);
      final Appender console = config.getAppenders().get("Console");

      assertThat(console).isNotNull();
      assertThat(console.getLayout()).isInstanceOf(PatternLayout.class);
      assertThat(errContent.toString(UTF_8))
          .contains("Splunk logging requested but required environment variables are missing");
    } finally {
      System.setErr(originalErr);
    }
  }

  @Test
  void nullFormatDefaultsToPatternLayout() {
    final Configuration config = applyAndGetConfig("INFO", null, false);
    final Appender console = config.getAppenders().get("Console");

    assertThat(console).isNotNull();
    assertThat(console.getLayout()).isInstanceOf(PatternLayout.class);
  }

  // --- Console Appender: Color Settings ---

  @Test
  void colorEnabledCreatesPatternLayoutWithoutError() {
    final Configuration config = applyAndGetConfig("INFO", LoggingFormat.PLAIN, true);
    final Appender console = config.getAppenders().get("Console");

    assertThat(console).isNotNull();
    assertThat(console.getLayout()).isInstanceOf(PatternLayout.class);
  }

  @Test
  void colorDisabledCreatesPatternLayoutWithoutError() {
    final Configuration config = applyAndGetConfig("INFO", LoggingFormat.PLAIN, false);
    final Appender console = config.getAppenders().get("Console");

    assertThat(console).isNotNull();
    assertThat(console.getLayout()).isInstanceOf(PatternLayout.class);
  }

  // --- Log Level ---

  @Test
  void explicitDebugLevelIsApplied() {
    final Configuration config = applyAndGetConfig("DEBUG", LoggingFormat.PLAIN, false);
    assertThat(config.getRootLogger().getLevel()).isEqualTo(Level.DEBUG);
  }

  @Test
  void nullLogLevelDefaultsToInfo() {
    Assumptions.assumeTrue(
        System.getenv("LOG_LEVEL") == null || System.getenv("LOG_LEVEL").isEmpty(),
        "Skipping: LOG_LEVEL environment variable is set");

    final Configuration config = applyAndGetConfig(null, LoggingFormat.PLAIN, false);
    assertThat(config.getRootLogger().getLevel()).isEqualTo(Level.INFO);
  }

  @Test
  void emptyLogLevelDefaultsToInfo() {
    Assumptions.assumeTrue(
        System.getenv("LOG_LEVEL") == null || System.getenv("LOG_LEVEL").isEmpty(),
        "Skipping: LOG_LEVEL environment variable is set");

    final Configuration config = applyAndGetConfig("", LoggingFormat.PLAIN, false);
    assertThat(config.getRootLogger().getLevel()).isEqualTo(Level.INFO);
  }

  // --- Logger Filters ---

  @Test
  void allLoggerFiltersAreCreated() {
    final Configuration config = applyAndGetConfig("INFO", LoggingFormat.PLAIN, false);
    final Map<String, LoggerConfig> loggers = config.getLoggers();

    // StatusLogger suppressed at OFF level
    assertThat(loggers).containsKey("org.apache.logging.log4j.status.StatusLogger");
    assertThat(loggers.get("org.apache.logging.log4j.status.StatusLogger").getLevel())
        .isEqualTo(Level.OFF);

    // 3 DNS-related RegexFilter loggers
    assertThat(loggers).containsKey("org.apache.tuweni.discovery.DNSTimerTask");
    assertThat(loggers).containsKey("org.apache.tuweni.discovery.DNSResolver");
    assertThat(loggers).containsKey("io.vertx.core.dns.DnsException");

    // MarkerFilter for invalid transaction removal
    assertThat(loggers).containsKey("org.hyperledger.besu.ethereum.eth.transactions");

    // RegexFilter for B3 propagation
    assertThat(loggers)
        .containsKey(
            "io.opentelemetry.extension.trace.propagation"
                + ".B3PropagatorExtractorMultipleHeaders");

    // StackTraceMatchFilter for Bonsai worldstate
    assertThat(loggers)
        .containsKey(
            "org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage"
                + ".BonsaiSnapshotWorldStateKeyValueStorage");
  }
}
