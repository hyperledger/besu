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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.cli.CommandTestAbstract;
import org.hyperledger.besu.plugins.graphql.GraphQLConfiguration;
import org.hyperledger.besu.plugins.graphql.GraphQLPlugin;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

@ExtendWith(MockitoExtension.class)
public class GraphQlOptionsTest extends CommandTestAbstract {
  @Test
  public void graphQLHttpEnabledPropertyMustBeUsed() {
    final TestBesuCommand besuCommand = parseCommand("--graphql-http-enabled");

    verify(mockRunnerBuilder).build();
    final GraphQLConfiguration graphQLConfiguration =
        besuCommand.getGraphQLPlugin().getConfiguration();

    assertThat(graphQLConfiguration.isEnabled()).isTrue();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void graphQLHttpHostAndPortOptionsMustBeUsed() {

    final String host = "1.2.3.4";
    final int port = 1234;
    final TestBesuCommand besuCommand =
        parseCommand(
            "--graphql-http-enabled",
            "--graphql-http-host",
            host,
            "--graphql-http-port",
            String.valueOf(port));

    verify(mockRunnerBuilder).build();
    final GraphQLConfiguration graphQLConfiguration =
        besuCommand.getGraphQLPlugin().getConfiguration();

    assertThat(graphQLConfiguration.getHost()).isEqualTo(host);
    assertThat(graphQLConfiguration.getPort()).isEqualTo(port);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void graphQLHttpHostMayBeLocalhost() {

    final String host = "localhost";
    final TestBesuCommand besuCommand =
        parseCommand("--graphql-http-enabled", "--graphql-http-host", host);

    verify(mockRunnerBuilder).build();
    final GraphQLConfiguration graphQLConfiguration =
        besuCommand.getGraphQLPlugin().getConfiguration();

    assertThat(graphQLConfiguration.getHost()).isEqualTo(host);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void graphQLHttpHostMayBeIPv6() {

    final String host = "2600:DB8::8545";
    final TestBesuCommand besuCommand =
        parseCommand("--graphql-http-enabled", "--graphql-http-host", host);

    verify(mockRunnerBuilder).build();
    final GraphQLConfiguration graphQLConfiguration =
        besuCommand.getGraphQLPlugin().getConfiguration();

    assertThat(graphQLConfiguration.getHost()).isEqualTo(host);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void graphQLMaxBlockRangeDefaultsToFiveThousand() {
    final TestBesuCommand besuCommand = parseCommand("--graphql-http-enabled");

    verify(mockRunnerBuilder).build();
    final GraphQLConfiguration graphQLConfiguration =
        besuCommand.getGraphQLPlugin().getConfiguration();

    assertThat(graphQLConfiguration.getMaxBlockRange()).isEqualTo(5000L);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void graphQLMaxBlockRangeOptionMustBeUsed() {
    final TestBesuCommand besuCommand =
        parseCommand("--graphql-http-enabled", "--graphql-max-blocks-range", "42");

    verify(mockRunnerBuilder).build();
    final GraphQLConfiguration graphQLConfiguration =
        besuCommand.getGraphQLPlugin().getConfiguration();

    assertThat(graphQLConfiguration.getMaxBlockRange()).isEqualTo(42L);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void graphQLMaxBlockRangeRejectsNegativeValue() {
    parseCommand("--graphql-http-enabled", "--graphql-max-blocks-range", "-1");

    Mockito.verifyNoInteractions(mockRunnerBuilder);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("--graphql-max-blocks-range must be >= 0 (0 specifies no limit)");
  }

  @Test
  public void graphQLOptionsMustBeReadFromConfigFile() throws IOException {
    final Path toml =
        createTempFile(
            "toml",
            "graphql-http-enabled=true\n"
                + "graphql-http-host=\"5.6.7.8\"\n"
                + "graphql-http-port=5678\n"
                + "graphql-max-blocks-range=7\n");
    final TestBesuCommand besuCommand = parseCommand("--config-file", toml.toString());

    verify(mockRunnerBuilder).build();
    final GraphQLConfiguration graphQLConfiguration =
        besuCommand.getGraphQLPlugin().getConfiguration();
    assertThat(graphQLConfiguration.isEnabled()).isTrue();
    assertThat(graphQLConfiguration.getHost()).isEqualTo("5.6.7.8");
    assertThat(graphQLConfiguration.getPort()).isEqualTo(5678);
    assertThat(graphQLConfiguration.getMaxBlockRange()).isEqualTo(7L);

    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void graphQLMaxBlockRangeRejectsNegativeValueFromConfigFile() throws IOException {
    final Path toml =
        createTempFile("toml", "graphql-http-enabled=true\ngraphql-max-blocks-range=-1\n");
    parseCommand("--config-file", toml.toString());

    Mockito.verifyNoInteractions(mockRunnerBuilder);
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("--graphql-max-blocks-range must be >= 0 (0 specifies no limit)");
  }

  @Test
  public void graphQLMaxBlockRangeRejectsNegativeValueWithoutCommandLine() {
    // options parsed outside Besu's command, so the plugin cannot find the command line to report
    // a ParameterException on
    final GraphQLPlugin plugin = new GraphQLPlugin();
    new CommandLine(plugin.getCliOptions()).parseArgs("--graphql-max-blocks-range=-1");

    assertThatThrownBy(() -> plugin.validateOptions(mockLogger))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("--graphql-max-blocks-range must be >= 0 (0 specifies no limit)");
    // a failed validation does not count: the value is still rejected on the next call
    assertThatThrownBy(plugin::validateOptions)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("--graphql-max-blocks-range must be >= 0 (0 specifies no limit)");
  }

  @Test
  public void graphQLValidMaxBlockRangeIsAcceptedWithoutCommandLine() {
    final GraphQLPlugin plugin = new GraphQLPlugin();
    new CommandLine(plugin.getCliOptions()).parseArgs("--graphql-max-blocks-range=0");

    plugin.validateOptions(mockLogger);

    Mockito.verifyNoInteractions(mockLogger);
  }
}
