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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static picocli.CommandLine.defaultExceptionHandler;

import org.hyperledger.besu.cli.util.CommandLineUtils;

import java.util.ArrayList;
import java.util.Arrays;

import org.apache.logging.log4j.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.RunLast;

@RunWith(MockitoJUnitRunner.class)
public class CommandLineUtilsTest {
  @Mock Logger mockLogger;

  @Command(description = "This command is for testing.", name = "testcommand")
  private abstract static class AbstractTestCommand implements Runnable {

    final Logger logger;
    final CommandLine commandLine;

    AbstractTestCommand(final Logger logger) {
      this.logger = logger;
      commandLine = new CommandLine(this);
    }

    // Completely disables p2p within Besu.
    @Option(
        names = {"--option-enabled"},
        arity = "1")
    final Boolean optionEnabled = true;

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

  /**
   * Check logger calls
   *
   * <p>Here we check the calls to logger and not the result of the log line as we don't test the
   * logger itself but the fact that we call it.
   *
   * @param dependentOptions the string representing the list of dependent options names
   * @param mainOption the main option name
   */
  private void verifyOptionsConstraintLoggerCall(
      final Logger logger, final String dependentOptions, final String mainOption) {

    final ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);

    verify(logger)
        .warn(
            stringArgumentCaptor.capture(),
            stringArgumentCaptor.capture(),
            stringArgumentCaptor.capture());
    assertThat(stringArgumentCaptor.getAllValues().get(0))
        .isEqualTo("{} will have no effect unless {} is defined on the command line.");
    assertThat(stringArgumentCaptor.getAllValues().get(1)).isEqualTo(dependentOptions);
    assertThat(stringArgumentCaptor.getAllValues().get(2)).isEqualTo(mainOption);
  }
}
