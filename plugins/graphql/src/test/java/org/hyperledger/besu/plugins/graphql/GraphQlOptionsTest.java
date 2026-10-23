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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.rpc.RpcConfiguration;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

/**
 * The GraphQL options keep the names, defaults and validation they had as Besu options. Mirrors
 * Besu's former {@code GraphQlOptionsTest}, with the options added to a command the way Besu adds
 * the options of its built-in plugins.
 */
class GraphQlOptionsTest {

  @CommandLine.Command(name = "besu")
  static class TestCommand implements Runnable {
    @Override
    public void run() {}
  }

  private final GraphQLPlugin plugin = new GraphQLPlugin();
  private final ServiceManager.SimpleServiceManager serviceManager =
      new ServiceManager.SimpleServiceManager();
  private CommandLine commandLine;

  @BeforeEach
  void setUp() {
    commandLine = new CommandLine(new TestCommand());
    final PicoCLIOptions picoCLIOptions =
        (namespace, optionObject) ->
            commandLine
                .getCommandSpec()
                .addMixin("Plugin " + namespace, CommandSpec.forAnnotatedObject(optionObject));
    serviceManager.addService(PicoCLIOptions.class, picoCLIOptions);
    plugin.register(serviceManager);
  }

  private void parseCommand(final String... args) {
    commandLine.parseArgs(args);
  }

  @Test
  void graphQLHttpEnabledPropertyDefaultIsFalse() {
    parseCommand();

    assertThat(plugin.isEnabled()).isFalse();
    assertThat(plugin.getConfiguration().isEnabled()).isFalse();
  }

  @Test
  void graphQLHttpEnabledPropertyMustBeUsed() {
    parseCommand("--graphql-http-enabled");

    assertThat(plugin.isEnabled()).isTrue();
    assertThat(plugin.getConfiguration().isEnabled()).isTrue();
  }

  @Test
  void defaultConfigurationIsTheDefaultOfGraphQLConfiguration() {
    parseCommand();

    // equals compares enabled, port, host, CORS domains and hosts allowlist
    assertThat(plugin.getConfiguration()).isEqualTo(GraphQLConfiguration.createDefault());
    assertThat(plugin.getConfiguration().getHttpTimeoutSec()).isEqualTo(30L);
  }

  @Test
  void graphQLHttpHostAndPortOptionsMustBeUsed() {

    final String host = "1.2.3.4";
    final int port = 1234;
    parseCommand(
        "--graphql-http-enabled",
        "--graphql-http-host",
        host,
        "--graphql-http-port",
        String.valueOf(port));

    assertThat(plugin.getConfiguration().getHost()).isEqualTo(host);
    assertThat(plugin.getConfiguration().getPort()).isEqualTo(port);
  }

  @Test
  void graphQLHttpHostMayBeLocalhost() {

    final String host = "localhost";
    parseCommand("--graphql-http-enabled", "--graphql-http-host", host);

    assertThat(plugin.getConfiguration().getHost()).isEqualTo(host);
  }

  @Test
  void graphQLHttpHostMayBeIPv6() {

    final String host = "2600:DB8::8545";
    parseCommand("--graphql-http-enabled", "--graphql-http-host", host);

    assertThat(plugin.getConfiguration().getHost()).isEqualTo(host);
  }

  @Test
  void graphQLHttpHostDefaultsToTheDefaultHostAddressOfTheNode() {
    final RpcConfiguration rpcConfiguration = mock(RpcConfiguration.class);
    when(rpcConfiguration.getDefaultHostAddress()).thenReturn("10.1.2.3");
    when(rpcConfiguration.getConfiguredHostAllowlist()).thenReturn(List.of("ally", "friend"));
    when(rpcConfiguration.getConfiguredRpcHttpTimeoutSec()).thenReturn(42L);
    serviceManager.addService(RpcConfiguration.class, rpcConfiguration);

    parseCommand("--graphql-http-enabled");

    assertThat(plugin.getConfiguration().getHost()).isEqualTo("10.1.2.3");
    assertThat(plugin.getConfiguration().getHostsAllowlist()).containsExactly("ally", "friend");
    assertThat(plugin.getConfiguration().getHttpTimeoutSec()).isEqualTo(42L);
  }

  @Test
  void optionsMayComeFromTheDefaultValueProvider() {
    // the config file and the environment provide values through the default value provider
    commandLine.setDefaultValueProvider(
        argSpec -> {
          if (argSpec instanceof CommandLine.Model.OptionSpec option) {
            return switch (option.longestName()) {
              case "--graphql-http-enabled" -> "true";
              case "--graphql-http-host" -> "6.7.8.9";
              case "--graphql-http-port" -> "6789";
              default -> null;
            };
          }
          return null;
        });

    parseCommand();

    assertThat(plugin.isEnabled()).isTrue();
    assertThat(plugin.getConfiguration().getHost()).isEqualTo("6.7.8.9");
    assertThat(plugin.getConfiguration().getPort()).isEqualTo(6789);
  }

  @Test
  void graphQLHttpCorsOriginsMustBeUsed() {
    parseCommand(
        "--graphql-http-enabled", "--graphql-http-cors-origins", "http://a.com,http://b.com");

    assertThat(plugin.getConfiguration().getCorsAllowedDomains())
        .containsExactly("http://a.com", "http://b.com");
  }

  @Test
  void graphQLTlsOptionsMustBeUsed() {
    parseCommand(
        "--graphql-http-enabled",
        "--graphql-tls-enabled",
        "--graphql-tls-keystore-file",
        "/keystore",
        "--graphql-tls-keystore-password-file",
        "/keystore-password",
        "--graphql-mtls-enabled",
        "--graphql-tls-truststore-file",
        "/truststore",
        "--graphql-tls-truststore-password-file",
        "/truststore-password");

    final GraphQLConfiguration configuration = plugin.getConfiguration();
    assertThat(configuration.isTlsEnabled()).isTrue();
    assertThat(configuration.getTlsKeyStorePath()).isEqualTo("/keystore");
    assertThat(configuration.isMtlsEnabled()).isTrue();
    assertThat(configuration.getTlsTrustStorePath()).isEqualTo("/truststore");
  }

  @Test
  void graphQLMaxBlockRangeDefaultsToFiveThousand() {
    parseCommand("--graphql-http-enabled");

    assertThat(plugin.getConfiguration().getMaxBlockRange()).isEqualTo(5000L);
  }

  @Test
  void graphQLMaxBlockRangeOptionMustBeUsed() {
    parseCommand("--graphql-http-enabled", "--graphql-max-blocks-range", "42");

    assertThat(plugin.getConfiguration().getMaxBlockRange()).isEqualTo(42L);
  }

  @Test
  void graphQLMaxBlockRangeAcceptsZero() {
    parseCommand("--graphql-http-enabled", "--graphql-max-blocks-range", "0");

    assertThat(plugin.getConfiguration().getMaxBlockRange()).isZero();
  }

  @Test
  void graphQLMaxBlockRangeRejectsNegativeValue() {
    parseCommand("--graphql-http-enabled", "--graphql-max-blocks-range", "-1");

    assertThatThrownBy(plugin::validateOptions)
        .isInstanceOfSatisfying(
            CommandLine.ParameterException.class,
            e -> assertThat(e.getCommandLine()).isSameAs(commandLine))
        .hasMessage("--graphql-max-blocks-range must be >= 0 (0 specifies no limit)");
  }

  @Test
  void graphQLMaxBlockRangeRejectsNegativeValueFromDefaultValueProvider() {
    // the config file and the environment provide values through the default value provider
    commandLine.setDefaultValueProvider(
        argSpec ->
            argSpec instanceof CommandLine.Model.OptionSpec option
                    && "--graphql-max-blocks-range".equals(option.longestName())
                ? "-1"
                : null);
    parseCommand("--graphql-http-enabled");

    assertThatThrownBy(plugin::validateOptions)
        .isInstanceOf(CommandLine.ParameterException.class)
        .hasMessage("--graphql-max-blocks-range must be >= 0 (0 specifies no limit)");
  }

  @Test
  void graphQLMaxBlockRangeRejectsNegativeValueEvenWhenDisabled() {
    parseCommand("--graphql-max-blocks-range", "-1");

    assertThatThrownBy(plugin::validateOptions)
        .isInstanceOf(CommandLine.ParameterException.class)
        .hasMessage("--graphql-max-blocks-range must be >= 0 (0 specifies no limit)");
  }

  @Test
  void optionsAreValidatedBeforeExternalServices() {
    parseCommand("--graphql-http-enabled", "--graphql-max-blocks-range", "-1");

    assertThatThrownBy(plugin::beforeExternalServices)
        .isInstanceOf(CommandLine.ParameterException.class)
        .hasMessage("--graphql-max-blocks-range must be >= 0 (0 specifies no limit)");
  }

  @Test
  void optionsAreValidatedAtStartAtTheLatest() {
    parseCommand("--graphql-http-enabled", "--graphql-max-blocks-range", "-1");

    assertThatThrownBy(plugin::start)
        .isInstanceOf(CommandLine.ParameterException.class)
        .hasMessage("--graphql-max-blocks-range must be >= 0 (0 specifies no limit)");
  }

  @Test
  void validOptionsPassValidation() {
    parseCommand("--graphql-http-enabled", "--graphql-max-blocks-range", "0");

    plugin.validateOptions();

    assertThat(plugin.getCliOptions().getCommandLine()).isSameAs(commandLine);
  }

  @Test
  void helpShowsTheGraphQLOptionsSection() {
    final String usage = commandLine.getUsageMessage(CommandLine.Help.Ansi.OFF);

    assertThat(usage).contains("GraphQL Options");
    assertThat(usage)
        .contains(
            "--graphql-http-enabled",
            "--graphql-http-host=<HOST>",
            "--graphql-http-port=<PORT>",
            "--graphql-http-cors-origins",
            "--graphql-tls-enabled",
            "--graphql-tls-keystore-file",
            "--graphql-tls-keystore-password-file",
            "--graphql-mtls-enabled",
            "--graphql-tls-truststore-file",
            "--graphql-tls-truststore-password-file",
            "--graphql-max-blocks-range");
    assertThat(usage).contains("Port for GraphQL HTTP to listen on (default: 8547)");
    assertThat(usage).contains("(default: 5000)");
  }

  @Test
  void optionsThatNeedGraphQLEnabledAreReportedWhenItIsNot() {
    parseCommand("--graphql-http-host", "1.2.3.4", "--graphql-http-port", "1234");
    final Logger logger = mock(Logger.class);

    plugin.getCliOptions().getGraphQlOptions().checkOptionDependencies(logger, commandLine);

    verify(logger)
        .warn(
            "{} has been ignored because {} was not defined on the command line.",
            "--graphql-http-host and --graphql-http-port",
            "--graphql-http-enabled");
  }

  @Test
  void optionsThatNeedTlsEnabledAreReportedWhenItIsNot() {
    parseCommand(
        "--graphql-http-enabled",
        "--graphql-tls-keystore-file",
        "/keystore",
        "--graphql-tls-truststore-file",
        "/truststore");
    final Logger logger = mock(Logger.class);

    plugin.getCliOptions().getGraphQlOptions().checkOptionDependencies(logger, commandLine);

    verify(logger)
        .warn(
            "{} has been ignored because {} was not defined on the command line.",
            "--graphql-tls-keystore-file",
            "--graphql-tls-enabled");
    verify(logger)
        .warn(
            "{} has been ignored because {} was not defined on the command line.",
            "--graphql-tls-truststore-file",
            "--graphql-mtls-enabled");
  }

  @Test
  void noOptionDependencyIsReportedWhenTheOptionsAreConsistent() {
    parseCommand("--graphql-http-enabled", "--graphql-http-port", "1234");
    final Logger logger = mock(Logger.class);

    plugin.getCliOptions().getGraphQlOptions().checkOptionDependencies(logger, commandLine);

    verify(logger, never()).warn(anyString(), anyString(), anyString());
  }
}
