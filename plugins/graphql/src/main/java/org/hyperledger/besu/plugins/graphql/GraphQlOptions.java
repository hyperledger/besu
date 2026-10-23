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
package org.hyperledger.besu.plugins.graphql;

import static java.util.Arrays.asList;
import static org.hyperledger.besu.plugins.graphql.GraphQLConfiguration.DEFAULT_GRAPHQL_HTTP_PORT;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import picocli.CommandLine;

/**
 * Handles configuration options for the GraphQL HTTP service in Besu. The options have the same
 * names, defaults and descriptions as when the GraphQL service was part of Besu's core.
 */
public class GraphQlOptions {

  private static final String MANDATORY_HOST_FORMAT_HELP = "<HOST>";
  private static final String MANDATORY_PORT_FORMAT_HELP = "<PORT>";

  private static final String DEPENDENCY_WARNING_MSG =
      "{} has been ignored because {} was not defined on the command line.";

  static final String MAX_BLOCKS_RANGE_ERROR =
      "--graphql-max-blocks-range must be >= 0 (0 specifies no limit)";

  @CommandLine.Option(
      names = {"--graphql-http-enabled"},
      description = "Set to start the GraphQL HTTP service (default: ${DEFAULT-VALUE})")
  private final Boolean isGraphQLHttpEnabled = false;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @CommandLine.Option(
      names = {"--graphql-http-host"},
      paramLabel = MANDATORY_HOST_FORMAT_HELP,
      description = "Host for GraphQL HTTP to listen on (default: ${DEFAULT-VALUE})")
  private String graphQLHttpHost;

  @CommandLine.Option(
      names = {"--graphql-http-port"},
      paramLabel = MANDATORY_PORT_FORMAT_HELP,
      description = "Port for GraphQL HTTP to listen on (default: ${DEFAULT-VALUE})")
  private final Integer graphQLHttpPort = DEFAULT_GRAPHQL_HTTP_PORT;

  @CommandLine.Option(
      names = {"--graphql-http-cors-origins"},
      description = "Comma separated origin domain URLs for CORS validation (default: none)")
  private final CorsAllowedOriginsProperty graphQLHttpCorsAllowedOrigins =
      new CorsAllowedOriginsProperty();

  @CommandLine.Option(
      names = {"--graphql-tls-enabled"},
      description = "Enable TLS for GraphQL HTTP service")
  private Boolean graphqlTlsEnabled = false;

  @CommandLine.Option(
      names = {"--graphql-tls-keystore-file"},
      description = "Path to the TLS keystore file for GraphQL HTTP service")
  private String graphqlTlsKeystoreFile;

  @CommandLine.Option(
      names = {"--graphql-tls-keystore-password-file"},
      description = "Path to the file containing the password for the TLS keystore")
  private String graphqlTlsKeystorePasswordFile;

  @CommandLine.Option(
      names = {"--graphql-mtls-enabled"},
      description = "Enable mTLS for GraphQL HTTP service")
  private Boolean graphqlMtlsEnabled = false;

  @CommandLine.Option(
      names = {"--graphql-tls-truststore-file"},
      description = "Path to the TLS truststore file for GraphQL HTTP service")
  private String graphqlTlsTruststoreFile;

  @CommandLine.Option(
      names = {"--graphql-tls-truststore-password-file"},
      description = "Path to the file containing the password for the TLS truststore")
  private String graphqlTlsTruststorePasswordFile;

  @CommandLine.Option(
      names = {"--graphql-max-blocks-range"},
      description =
          "Specifies the maximum number of blocks that can be retrieved by a single GraphQL "
              + "blocks(from,to) query. Must be >=0. 0 specifies no limit (default: ${DEFAULT-VALUE})")
  private final Long graphQLMaxBlockRange = GraphQLConfiguration.DEFAULT_MAX_BLOCK_RANGE;

  /** Default constructor */
  public GraphQlOptions() {}

  /**
   * Validates the GraphQL HTTP options, the same way as Besu did before the GraphQL service became
   * a plugin: a negative maximum block range is rejected, and the options that have no effect
   * because the option they depend on is not set are logged.
   *
   * @param logger Logger instance
   * @param commandLine CommandLine instance
   * @throws CommandLine.ParameterException if the maximum block range is negative
   */
  public void validate(final Logger logger, final CommandLine commandLine) {
    if (isMaxBlockRangeInvalid()) {
      throw new CommandLine.ParameterException(commandLine, MAX_BLOCKS_RANGE_ERROR);
    }
    checkOptionDependencies(logger, commandLine);
  }

  /**
   * Validates the option values when the command line they belong to is not known: a negative
   * maximum block range is rejected with the same message as {@link #validate(Logger,
   * CommandLine)}. The option dependencies cannot be checked without the command line.
   *
   * @throws IllegalArgumentException if the maximum block range is negative
   */
  public void validateWithoutCommandLine() {
    if (isMaxBlockRangeInvalid()) {
      throw new IllegalArgumentException(MAX_BLOCKS_RANGE_ERROR);
    }
  }

  private boolean isMaxBlockRangeInvalid() {
    return graphQLMaxBlockRange < 0;
  }

  /**
   * Logs a warning for the options that have no effect because the option they depend on is not
   * set, with the same messages as Besu's own option checks.
   *
   * @param logger Logger instance
   * @param commandLine CommandLine instance
   */
  public void checkOptionDependencies(final Logger logger, final CommandLine commandLine) {
    checkOptionDependencies(
        logger,
        commandLine,
        "--graphql-http-enabled",
        !isGraphQLHttpEnabled,
        asList(
            "--graphql-http-cors-origins",
            "--graphql-http-host",
            "--graphql-http-port",
            "--graphql-tls-enabled"));

    checkOptionDependencies(
        logger,
        commandLine,
        "--graphql-tls-enabled",
        !graphqlTlsEnabled,
        asList(
            "--graphql-tls-keystore-file",
            "--graphql-tls-keystore-password-file",
            "--graphql-mtls-enabled"));

    checkOptionDependencies(
        logger,
        commandLine,
        "--graphql-mtls-enabled",
        !graphqlMtlsEnabled,
        asList("--graphql-tls-truststore-file", "--graphql-tls-truststore-password-file"));
  }

  // Same as Besu's CommandLineUtils.checkOptionDependencies.
  private static void checkOptionDependencies(
      final Logger logger,
      final CommandLine commandLine,
      final String mainOptionName,
      final boolean isMainOptionCondition,
      final List<String> dependentOptionsNames) {
    if (isMainOptionCondition) {
      final String affectedOptions = getAffectedOptions(commandLine, dependentOptionsNames);

      if (!affectedOptions.isEmpty()) {
        logger.warn(DEPENDENCY_WARNING_MSG, affectedOptions, mainOptionName);
      }
    }
  }

  private static String getAffectedOptions(
      final CommandLine commandLine, final List<String> dependentOptionsNames) {
    return commandLine.getCommandSpec().options().stream()
        .filter(option -> Arrays.stream(option.names()).anyMatch(dependentOptionsNames::contains))
        .filter(GraphQlOptions::isOptionSet)
        .map(option -> option.names()[0])
        .collect(
            Collectors.collectingAndThen(
                Collectors.toList(), GraphQlOptions::joiningWithLastDelimiter));
  }

  private static boolean isOptionSet(final CommandLine.Model.OptionSpec option) {
    final CommandLine commandLine = option.command().commandLine();
    try {
      return !option.stringValues().isEmpty()
          || !Strings.isNullOrEmpty(commandLine.getDefaultValueProvider().defaultValue(option));
    } catch (final Exception e) {
      return false;
    }
  }

  private static String joiningWithLastDelimiter(final List<String> list) {
    final int last = list.size() - 1;
    if (last < 1) return String.join(", ", list);
    return String.join(" and ", String.join(", ", list.subList(0, last)), list.get(last));
  }

  /**
   * Creates a GraphQLConfiguration based on the provided options.
   *
   * @param hostsAllowlist List of hosts allowed
   * @param defaultHostAddress Default host address
   * @param timeoutSec Timeout in seconds
   * @return A GraphQLConfiguration instance
   */
  public GraphQLConfiguration graphQLConfiguration(
      final List<String> hostsAllowlist, final String defaultHostAddress, final Long timeoutSec) {
    final GraphQLConfiguration graphQLConfiguration = GraphQLConfiguration.createDefault();
    graphQLConfiguration.setEnabled(isGraphQLHttpEnabled);
    graphQLConfiguration.setHost(getGraphQLHttpHost(defaultHostAddress));
    graphQLConfiguration.setPort(graphQLHttpPort);
    graphQLConfiguration.setHostsAllowlist(hostsAllowlist);
    graphQLConfiguration.setCorsAllowedDomains(graphQLHttpCorsAllowedOrigins);
    graphQLConfiguration.setHttpTimeoutSec(timeoutSec);
    graphQLConfiguration.setTlsEnabled(graphqlTlsEnabled);
    graphQLConfiguration.setTlsKeyStorePath(graphqlTlsKeystoreFile);
    graphQLConfiguration.setTlsKeyStorePasswordFile(graphqlTlsKeystorePasswordFile);
    graphQLConfiguration.setMtlsEnabled(graphqlMtlsEnabled);
    graphQLConfiguration.setTlsTrustStorePath(graphqlTlsTruststoreFile);
    graphQLConfiguration.setTlsTrustStorePasswordFile(graphqlTlsTruststorePasswordFile);
    graphQLConfiguration.setMaxBlockRange(graphQLMaxBlockRange);

    return graphQLConfiguration;
  }

  /**
   * Checks if GraphQL over HTTP is enabled.
   *
   * @return true if enabled, false otherwise
   */
  public Boolean isGraphQLHttpEnabled() {
    return isGraphQLHttpEnabled;
  }

  /**
   * Returns the host for GraphQL over HTTP.
   *
   * @param defaultHostAddress the host to use when none is configured
   * @return the configured host, or the default host if none is configured
   */
  public String getGraphQLHttpHost(final String defaultHostAddress) {
    return Strings.isNullOrEmpty(graphQLHttpHost) ? defaultHostAddress : graphQLHttpHost;
  }

  /**
   * Returns the port for GraphQL over HTTP.
   *
   * @return The port number
   */
  public Integer getGraphQLHttpPort() {
    return graphQLHttpPort;
  }

  /**
   * Returns the maximum number of blocks a single range query may span.
   *
   * @return the maximum block range, 0 for no limit
   */
  public Long getGraphQLMaxBlockRange() {
    return graphQLMaxBlockRange;
  }
}
