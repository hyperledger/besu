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
package org.hyperledger.besu.evmtool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.evmtool.exception.UnsupportedForkException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class StateTestSubCommandTest {

  @Test
  void shouldDetectUnsupportedFork() {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    EvmToolCommand parentCommand =
        new EvmToolCommand(System.in, new PrintWriter(baos, true, UTF_8));
    final StateTestSubCommand stateTestSubCommand = new StateTestSubCommand(parentCommand);
    final CommandLine cmd = new CommandLine(stateTestSubCommand);
    cmd.parseArgs(
        StateTestSubCommandTest.class.getResource("unsupported-fork-state-test.json").getPath());
    assertThatThrownBy(stateTestSubCommand::run)
        .hasMessageContaining("Fork 'UnknownFork' not supported")
        .isInstanceOf(UnsupportedForkException.class);
  }

  @Test
  void shouldWorkWithValidStateTest() {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    EvmToolCommand parentCommand =
        new EvmToolCommand(System.in, new PrintWriter(baos, true, UTF_8));
    final StateTestSubCommand stateTestSubCommand = new StateTestSubCommand(parentCommand);
    final CommandLine cmd = new CommandLine(stateTestSubCommand);
    cmd.parseArgs(StateTestSubCommandTest.class.getResource("valid-state-test.json").getPath());
    stateTestSubCommand.run();
  }

  @Test
  void shouldWorkWithValidAccessListStateTest() {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    EvmToolCommand parentCommand =
        new EvmToolCommand(System.in, new PrintWriter(baos, true, UTF_8));
    final StateTestSubCommand stateTestSubCommand = new StateTestSubCommand(parentCommand);
    final CommandLine cmd = new CommandLine(stateTestSubCommand);
    cmd.parseArgs(StateTestSubCommandTest.class.getResource("access-list.json").getPath());
    stateTestSubCommand.run();
  }

  @Test
  void noJsonTracer() {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    EvmToolCommand parentCommand =
        new EvmToolCommand(System.in, new PrintWriter(baos, true, UTF_8));
    CommandLine parentCmd = new CommandLine(parentCommand);
    parentCmd.parseArgs("--json=false");
    final StateTestSubCommand stateTestSubCommand = new StateTestSubCommand(parentCommand);
    final CommandLine cmd = new CommandLine(stateTestSubCommand);
    cmd.parseArgs(StateTestSubCommandTest.class.getResource("access-list.json").getPath());
    stateTestSubCommand.run();
    assertThat(baos.toString(UTF_8)).doesNotContain("\"pc\"");
  }

  @Test
  void testsInvalidTransactions() {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final ByteArrayInputStream bais =
        new ByteArrayInputStream(
            StateTestSubCommandTest.class
                .getResource("HighGasPrice.json")
                .getPath()
                .getBytes(UTF_8));
    final StateTestSubCommand stateTestSubCommand =
        new StateTestSubCommand(new EvmToolCommand(bais, new PrintWriter(baos, true, UTF_8)));
    stateTestSubCommand.run();
    assertThat(baos.toString(UTF_8)).contains("Upfront gas cost cannot exceed 2^256 Wei");
  }

  @Test
  void shouldStreamTests() {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final ByteArrayInputStream bais =
        new ByteArrayInputStream(
            StateTestSubCommandTest.class
                .getResource("access-list.json")
                .getPath()
                .getBytes(UTF_8));
    final StateTestSubCommand stateTestSubCommand =
        new StateTestSubCommand(new EvmToolCommand(bais, new PrintWriter(baos, true, UTF_8)));
    stateTestSubCommand.run();
    assertThat(baos.toString(UTF_8)).contains("\"pass\":true");
  }

  @Test
  void failStreamMissingFile() {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final ByteArrayInputStream bais =
        new ByteArrayInputStream("./file-dose-not-exist.json".getBytes(UTF_8));
    final StateTestSubCommand stateTestSubCommand =
        new StateTestSubCommand(new EvmToolCommand(bais, new PrintWriter(baos, true, UTF_8)));
    stateTestSubCommand.run();
    assertThat(baos.toString(UTF_8)).contains("File not found: ./file-dose-not-exist.json");
  }

  @Test
  void failStreamBadFile() {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final ByteArrayInputStream bais =
        new ByteArrayInputStream(
            StateTestSubCommandTest.class.getResource("bogus-test.json").getPath().getBytes(UTF_8));
    final StateTestSubCommand stateTestSubCommand =
        new StateTestSubCommand(new EvmToolCommand(bais, new PrintWriter(baos, true, UTF_8)));
    stateTestSubCommand.run();
    assertThat(baos.toString(UTF_8)).contains("File content error: ");
  }
}
