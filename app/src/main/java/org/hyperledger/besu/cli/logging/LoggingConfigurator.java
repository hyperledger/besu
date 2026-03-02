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

import org.hyperledger.besu.cli.options.LoggingFormat;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.api.LayoutComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.RootLoggerComponentBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;

/** Programmatic Log4j2 configuration for Besu. */
public final class LoggingConfigurator {

  private LoggingConfigurator() {}

  private static final String DEFAULT_PATTERN =
      "%style{%d{yyyy-MM-dd HH:mm:ss.SSSZZZ}}{DIM} %style{|}{DIM} %style{%t}{DIM} %style{|}{DIM} "
          + "%highlight{%-5level}{TRACE=normal} %style{|}{DIM} %style{%c{1}}{DIM} %style{|}{DIM} "
          + "%highlight{%msgc%n%throwable}{TRACE=normal}";

  /**
   * Configure logging programmatically based on CLI options.
   *
   * @param logLevel the log level from CLI (e.g., "INFO", "DEBUG")
   * @param loggingFormat the logging format from CLI
   * @param colorEnabled whether ANSI colors should be enabled
   */
  public static void configureLogging(
      final String logLevel, final LoggingFormat loggingFormat, final boolean colorEnabled) {

    // Build new configuration
    final ConfigurationBuilder<BuiltConfiguration> builder =
        ConfigurationBuilderFactory.newConfigurationBuilder();

    builder.setStatusLevel(Level.ERROR);
    builder.setConfigurationName("BesuProgrammaticConfig");
    // Ensure custom Log4j2 plugins (%msgc, StackTraceMatchFilter) are discoverable
    builder.setPackages("org.hyperledger.besu.util.log4j.plugin");

    // Determine which appender to use: --logging-format=SPLUNK or LOGGER=Splunk env var
    final boolean requestedSplunk =
        (loggingFormat != null && loggingFormat.isSplunkFormat())
            || "splunk".equalsIgnoreCase(System.getenv("LOGGER"));
    boolean usingSplunk = false;

    if (requestedSplunk) {
      // Validate Splunk configuration and add appender
      if (isSplunkConfigValid()) {
        addSplunkAppender(builder);
        usingSplunk = true;
      } else {
        // Fall back to console logging if Splunk configuration is invalid
        System.err.println(
            "WARNING: Splunk logging requested but required environment variables are missing. "
                + "Required: SPLUNK_URL, SPLUNK_TOKEN. Falling back to console logging.");
        addConsoleAppender(builder, loggingFormat, colorEnabled);
      }
    } else {
      // Add Console appender with selected format
      addConsoleAppender(builder, loggingFormat, colorEnabled);
    }

    // Add specialized logger filters
    addLoggerFilters(builder);

    // Create root logger
    final Level level = parseLevel(logLevel);
    final RootLoggerComponentBuilder rootLogger = builder.newRootLogger(level);
    rootLogger.add(builder.newAppenderRef(usingSplunk ? "Splunk" : "Console"));
    builder.add(rootLogger);

    // Build and apply configuration
    final BuiltConfiguration config = builder.build();
    config.initialize();
    config.start();

    final LoggerContext context = LoggerContext.getContext(false);
    context.setConfiguration(config);
    context.updateLoggers();
  }

  private static void addConsoleAppender(
      final ConfigurationBuilder<BuiltConfiguration> builder,
      final LoggingFormat loggingFormat,
      final boolean colorEnabled) {

    final LayoutComponentBuilder layoutBuilder;

    if (loggingFormat != null && loggingFormat.isJsonFormat()) {
      // JSON layout for structured logging
      layoutBuilder =
          builder
              .newLayout("JsonTemplateLayout")
              .addAttribute("eventTemplateUri", loggingFormat.getEventTemplateUri());
    } else {
      // Pattern layout for plain text logging
      layoutBuilder =
          builder
              .newLayout("PatternLayout")
              .addAttribute("pattern", DEFAULT_PATTERN)
              .addAttribute("disableAnsi", !colorEnabled)
              .addAttribute("noConsoleNoAnsi", !colorEnabled);
    }

    final AppenderComponentBuilder consoleAppender =
        builder
            .newAppender("Console", "Console")
            .addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT)
            .add(layoutBuilder);

    builder.add(consoleAppender);
  }

  private static void addSplunkAppender(final ConfigurationBuilder<BuiltConfiguration> builder) {
    // Splunk configuration from environment variables
    final String splunkUrl = System.getenv("SPLUNK_URL");
    final String splunkToken = System.getenv("SPLUNK_TOKEN");
    final String splunkIndex = System.getenv("SPLUNK_INDEX");
    final String splunkSource = getEnvOrDefault("SPLUNK_SOURCE", "besu");
    final String splunkSourcetype = getEnvOrDefault("SPLUNK_SOURCETYPE", "besu");
    final String host = getHost();
    final String batchSizeBytes = getEnvOrDefault("SPLUNK_BATCH_SIZE_BYTES", "65536");
    final String batchSizeCount = getEnvOrDefault("SPLUNK_BATCH_SIZE_COUNT", "1000");
    final String batchInterval = getEnvOrDefault("SPLUNK_BATCH_INTERVAL", "500");
    final String skipTlsVerify = getEnvOrDefault("SPLUNK_SKIPTLSVERIFY", "false");
    final String messageFormat = getEnvOrDefault("SPLUNK_MESSAGE_FORMAT", "text");

    final LayoutComponentBuilder patternLayout =
        builder.newLayout("PatternLayout").addAttribute("pattern", "%msg");

    final AppenderComponentBuilder splunkAppender =
        builder
            .newAppender("Splunk", "splunkhttp")
            .addAttribute("url", splunkUrl)
            .addAttribute("token", splunkToken)
            .addAttribute("host", host)
            .addAttribute("source", splunkSource)
            .addAttribute("sourcetype", splunkSourcetype)
            .addAttribute("messageFormat", messageFormat)
            .addAttribute("batch_size_bytes", batchSizeBytes)
            .addAttribute("batch_size_count", batchSizeCount)
            .addAttribute("batch_interval", batchInterval)
            .addAttribute("disableCertificateValidation", skipTlsVerify)
            .add(patternLayout);

    // Only add index if specified (otherwise Splunk uses default index for the token)
    if (splunkIndex != null && !splunkIndex.isEmpty()) {
      splunkAppender.addAttribute("index", splunkIndex);
    }

    builder.add(splunkAppender);
  }

  private static void addLoggerFilters(final ConfigurationBuilder<BuiltConfiguration> builder) {
    // Disable Log4j2 internal status logger
    builder.add(builder.newLogger("org.apache.logging.log4j.status.StatusLogger", Level.OFF));

    // DNS timer task filter - suppress "Refreshing DNS records with ..." messages
    // Uses root logger level, filter only suppresses matching messages
    builder.add(
        builder
            .newLogger("org.apache.tuweni.discovery.DNSTimerTask")
            .add(
                builder
                    .newFilter("RegexFilter", "DENY", "NEUTRAL")
                    .addAttribute("regex", "Refreshing DNS records with .*")));

    // DNS resolver filter - suppress "DNS query error with ..." messages
    builder.add(
        builder
            .newLogger("org.apache.tuweni.discovery.DNSResolver")
            .add(
                builder
                    .newFilter("RegexFilter", "DENY", "NEUTRAL")
                    .addAttribute("regex", "DNS query error with .*")));

    // Vertx DNS exception filter - suppress "DNS query error occurred:..." messages
    builder.add(
        builder
            .newLogger("io.vertx.core.dns.DnsException")
            .add(
                builder
                    .newFilter("RegexFilter", "DENY", "NEUTRAL")
                    .addAttribute("regex", "DNS query error occurred:.*")));

    // Invalid transaction removal marker filter
    builder.add(
        builder
            .newLogger("org.hyperledger.besu.ethereum.eth.transactions")
            .add(
                builder
                    .newFilter("MarkerFilter", "DENY", "NEUTRAL")
                    .addAttribute("marker", "INVALID_TX_REMOVED")));

    // OpenTelemetry B3 propagation filter
    builder.add(
        builder
            .newLogger(
                "io.opentelemetry.extension.trace.propagation.B3PropagatorExtractorMultipleHeaders")
            .add(
                builder
                    .newFilter("RegexFilter", "DENY", "NEUTRAL")
                    .addAttribute("regex", "Invalid TraceId in B3 header:.*")));

    // Bonsai worldstate stack trace filter
    builder.add(
        builder
            .newLogger(
                "org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiSnapshotWorldStateKeyValueStorage")
            .add(
                builder
                    .newFilter("StackTraceMatchFilter", "DENY", "NEUTRAL")
                    .addAttribute("stackContains", "BlockTransactionSelector")
                    .addAttribute("messageEquals", "Attempting to access closed worldstate")));
  }

  private static Level parseLevel(final String logLevel) {
    if (logLevel == null || logLevel.isEmpty()) {
      // Fall back to LOG_LEVEL env var for backward compatibility with pre-programmatic config
      final String envLogLevel = System.getenv("LOG_LEVEL");
      if (envLogLevel != null && !envLogLevel.isEmpty()) {
        return Level.toLevel(envLogLevel.toUpperCase(java.util.Locale.ROOT), Level.INFO);
      }
      return Level.INFO;
    }
    return Level.toLevel(logLevel.toUpperCase(java.util.Locale.ROOT), Level.INFO);
  }

  private static boolean isSplunkConfigValid() {
    // Check required Splunk environment variables (only URL and TOKEN are required)
    final String splunkUrl = System.getenv("SPLUNK_URL");
    final String splunkToken = System.getenv("SPLUNK_TOKEN");

    return splunkUrl != null
        && !splunkUrl.isEmpty()
        && splunkToken != null
        && !splunkToken.isEmpty();
  }

  private static String getEnvOrDefault(final String envVar, final String defaultValue) {
    final String value = System.getenv(envVar);
    return (value == null || value.isBlank()) ? defaultValue : value;
  }

  private static String getHost() {
    String host = System.getenv("HOST");
    if (host == null) {
      // Fall back to hostname (in Docker, hostname defaults to the container ID)
      try {
        host = java.net.InetAddress.getLocalHost().getHostName();
      } catch (java.net.UnknownHostException e) {
        host = "localhost";
      }
    }
    return host;
  }
}
