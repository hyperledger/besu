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
package org.hyperledger.besu.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.cli.util.CommandLineUtils.DEPENDENCY_WARNING_MSG;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static picocli.CommandLine.defaultExceptionHandler;

import org.hyperledger.besu.cli.util.CommandLineUtils;
import org.hyperledger.besu.cli.util.EnvironmentVariableDefaultProvider;
import org.hyperledger.besu.cli.util.TomlConfigFileDefaultProvider;
import org.hyperledger.besu.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.RunLast;

@RunWith(MockitoJUnitRunner.class)
public class CommandLineUtilsTest {
  @SuppressWarnings("PrivateStaticFinalLoggers") // @Mocks are inited by JUnit
  @Mock
  Logger mockLogger;

  @Command(description = "This command is for testing.", name = "testcommand")
  private abstract static class AbstractTestCommand implements Runnable {

    // inner class is needed for testing, so field can't be static
    @SuppressWarnings("PrivateStaticFinalLoggers")
    final Logger logger;

    final CommandLine commandLine;

    final Map<String, String> environment = new HashMap<>();

    AbstractTestCommand(final Logger logger) {
      this.logger = logger;
      commandLine = new CommandLine(this);
      commandLine.setDefaultValueProvider(new EnvironmentVariableDefaultProvider(environment));
    }

    // Completely disables p2p within Besu.
    @Option(
        names = {"--option-enabled"},
        arity = "1")
    final Boolean optionEnabled = true;

    @Option(
        names = {"--other-option-enabled"},
        arity = "1")
    final Boolean otherOptionEnabled = true;

    @Option(names = {"--option2"})
    final Integer option2 = 2;

    @Option(names = {"--option3"})
    final Integer option3 = 3;

    @Option(names = {"--option4"})
    final Integer option4 = 4;
  }

  private static class TestCommandWithDeps extends AbstractTestCommand {

    TestCommandWithDeps(final Logger logger) {
      super(logger);
    }

    @Override
    public void run() {
      // Check that mining options are able top work or send an error
      CommandLineUtils.checkOptionDependencies(
          logger,
          commandLine,
          "--option-enabled",
          !optionEnabled,
          Arrays.asList("--option2", "--option3"));
    }
  }

  private static class TestCommandWithoutDeps extends AbstractTestCommand {

    TestCommandWithoutDeps(final Logger logger) {
      super(logger);
    }

    @Override
    public void run() {
      // Check that mining options are able top work or send an error
      CommandLineUtils.checkOptionDependencies(
          logger, commandLine, "--option-enabled", !optionEnabled, new ArrayList<>());
    }
  }

  private static class TestMultiCommandWithDeps extends AbstractTestCommand {
    TestMultiCommandWithDeps(final Logger logger) {
      super(logger);
    }

    @Override
    public void run() {
      CommandLineUtils.checkMultiOptionDependencies(
          logger,
          commandLine,
          "--option2 and/or --option3 ignored because none of --option-enabled or --other-option-enabled was defined.",
          List.of(!optionEnabled, !otherOptionEnabled),
          Arrays.asList("--option2", "--option3"));
    }
  }

  @Test
  public void optionsAreNotExpected() {
    final AbstractTestCommand testCommand = new TestCommandWithDeps(mockLogger);
    testCommand.commandLine.parseWithHandlers(
        new RunLast(),
        defaultExceptionHandler(),
        "--option-enabled",
        "false",
        "--option2",
        "20",
        "--option3",
        "30",
        "--option4",
        "40");
    verifyOptionsConstraintLoggerCall(mockLogger, "--option2 and --option3", "--option-enabled");

    assertThat(testCommand.optionEnabled).isFalse();
    assertThat(testCommand.option2).isEqualTo(20);
    assertThat(testCommand.option3).isEqualTo(30);
    assertThat(testCommand.option4).isEqualTo(40);
  }

  @Test
  public void optionIsNotExpected() {
    final AbstractTestCommand testCommand = new TestCommandWithDeps(mockLogger);
    testCommand.commandLine.parseWithHandlers(
        new RunLast(),
        defaultExceptionHandler(),
        "--option-enabled",
        "false",
        "--option2",
        "20",
        "--option4",
        "40");
    verifyOptionsConstraintLoggerCall(mockLogger, "--option2", "--option-enabled");

    assertThat(testCommand.optionEnabled).isFalse();
    assertThat(testCommand.option2).isEqualTo(20);
    assertThat(testCommand.option3).isEqualTo(3);
    assertThat(testCommand.option4).isEqualTo(40);
  }

  @Test
  public void optionsAreExpected() {
    final AbstractTestCommand testCommand = new TestCommandWithDeps(mockLogger);
    testCommand.commandLine.parseWithHandlers(
        new RunLast(),
        defaultExceptionHandler(),
        "--option2",
        "20",
        "--option3",
        "30",
        "--option4",
        "40");
    verifyNoMoreInteractions(mockLogger);
    assertThat(testCommand.optionEnabled).isTrue();
    assertThat(testCommand.option2).isEqualTo(20);
    assertThat(testCommand.option3).isEqualTo(30);
    assertThat(testCommand.option4).isEqualTo(40);
  }

  @Test
  public void noDependencies() {
    final AbstractTestCommand testCommand = new TestCommandWithoutDeps(mockLogger);
    testCommand.commandLine.parseWithHandlers(
        new RunLast(),
        defaultExceptionHandler(),
        "--option-enabled",
        "false",
        "--option2",
        "20",
        "--option3",
        "30",
        "--option4",
        "40");
    verifyNoMoreInteractions(mockLogger);
    assertThat(testCommand.optionEnabled).isFalse();
    assertThat(testCommand.option2).isEqualTo(20);
    assertThat(testCommand.option3).isEqualTo(30);
    assertThat(testCommand.option4).isEqualTo(40);
  }

  @Test
  public void multipleMainOptions() {
    final AbstractTestCommand testCommand = new TestMultiCommandWithDeps(mockLogger);
    testCommand.commandLine.parseWithHandlers(
        new RunLast(),
        defaultExceptionHandler(),
        "--option-enabled",
        "false",
        "--other-option-enabled",
        "false",
        "--option2",
        "20");
    verifyMultiOptionsConstraintLoggerCall(
        mockLogger,
        "--option2 and/or --option3 ignored because none of --option-enabled or --other-option-enabled was defined.");

    assertThat(testCommand.optionEnabled).isFalse();
    assertThat(testCommand.otherOptionEnabled).isFalse();
    assertThat(testCommand.option2).isEqualTo(20);
  }

  @Test
  public void multipleMainOptionsToml() throws IOException {
    final Path toml = Files.createTempFile("toml", "");
    Files.write(
        toml,
        ("option-enabled=false\n" + "other-option-enabled=false\n" + "option2=30")
            .getBytes(StandardCharsets.UTF_8));
    toml.toFile().deleteOnExit();

    final AbstractTestCommand testCommand = new TestMultiCommandWithDeps(mockLogger);
    testCommand.commandLine.setDefaultValueProvider(
        new TomlConfigFileDefaultProvider(testCommand.commandLine, toml.toFile()));
    testCommand.commandLine.parseWithHandlers(new RunLast(), defaultExceptionHandler());

    verifyMultiOptionsConstraintLoggerCall(
        mockLogger,
        "--option2 and/or --option3 ignored because none of --option-enabled or --other-option-enabled was defined.");

    assertThat(testCommand.optionEnabled).isFalse();
    assertThat(testCommand.otherOptionEnabled).isFalse();
    assertThat(testCommand.option2).isEqualTo(30);
  }

  @Test
  public void multipleMainOptionsEnv() {
    final AbstractTestCommand testCommand = new TestMultiCommandWithDeps(mockLogger);
    testCommand.environment.put("BESU_OPTION_ENABLED", "false");
    testCommand.environment.put("BESU_OTHER_OPTION_ENABLED", "false");
    testCommand.environment.put("BESU_OPTION2", "40");

    testCommand.commandLine.parseWithHandlers(new RunLast(), defaultExceptionHandler());

    verifyMultiOptionsConstraintLoggerCall(
        mockLogger,
        "--option2 and/or --option3 ignored because none of --option-enabled or --other-option-enabled was defined.");

    assertThat(testCommand.optionEnabled).isFalse();
    assertThat(testCommand.otherOptionEnabled).isFalse();
    assertThat(testCommand.option2).isEqualTo(40);
  }

  /**
   * Check logger calls
   *
   * <p>Here we check the calls to logger and not the result of the log line as we don't test the
   * logger itself but the fact that we call it.
   *
   * @param dependentOptions the string representing the list of dependent options names
   * @param mainOptions the main option name
   */
  private void verifyOptionsConstraintLoggerCall(
      final Logger logger, final String dependentOptions, final String... mainOptions) {
    verifyCall(logger, dependentOptions, DEPENDENCY_WARNING_MSG, mainOptions);
  }

  private void verifyCall(
      final Logger logger,
      final String dependentOptions,
      final String dependencyWarningMsg,
      final String... mainOptions) {

    final ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);

    verify(logger)
        .warn(
            stringArgumentCaptor.capture(),
            stringArgumentCaptor.capture(),
            stringArgumentCaptor.capture());
    assertThat(stringArgumentCaptor.getAllValues().get(0)).isEqualTo(dependencyWarningMsg);
    assertThat(stringArgumentCaptor.getAllValues().get(1)).isEqualTo(dependentOptions);

    final String joinedMainOptions =
        StringUtils.joiningWithLastDelimiter(", ", " or ").apply(Arrays.asList(mainOptions));
    assertThat(stringArgumentCaptor.getAllValues().get(2)).isEqualTo(joinedMainOptions);
  }

  /**
   * Check logger calls, where multiple main options have been specified
   *
   * <p>Here we check the calls to logger and not the result of the log line as we don't test the
   * logger itself but the fact that we call it.
   *
   * @param stringToLog the string representing the list of dependent options names
   */
  private void verifyMultiOptionsConstraintLoggerCall(
      final Logger logger, final String stringToLog) {

    verify(logger).warn(stringToLog);
  }
}
