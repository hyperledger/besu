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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class CodeValidationSubCommandTest {

  static final String CODE_STOP_ONLY = "0xef0001 010004 020001-0001 040000 00 00800000 00";
  static final String CODE_RETURN_ONLY = "0xef0001 010004 020001-0003 040000 00 00800002 5f5ff3";
  static final String CODE_BAD_MAGIC = "0xefffff 010004 020001-0001 040000 00 00800000 e4";
  static final String CODE_INTERIOR_COMMENTS =
      """
                  0xef0001 010008 020002-0009-0002 040000 00
                  # 7 inputs 1 output,
                  00800004-04010004
                  59-59-59-59-e30001-50-00
                  # No immediate data
                  f8-e4""";
  static final String CODE_MULTIPLE =
      CODE_STOP_ONLY + "\n" + CODE_BAD_MAGIC + "\n" + CODE_RETURN_ONLY + "\n";

  @Test
  void testSingleValidViaInput() {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final ByteArrayInputStream bais = new ByteArrayInputStream(CODE_STOP_ONLY.getBytes(UTF_8));
    EvmToolCommand parentCommand = new EvmToolCommand(bais, new PrintWriter(baos, true, UTF_8));
    final CodeValidateSubCommand codeValidateSubCommand = new CodeValidateSubCommand(parentCommand);
    codeValidateSubCommand.run();
    assertThat(baos.toString(UTF_8)).contains("OK 1/0/0\n");
  }

  @Test
  void testSingleInvalidViaInput() {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final ByteArrayInputStream bais = new ByteArrayInputStream(CODE_BAD_MAGIC.getBytes(UTF_8));
    EvmToolCommand parentCommand = new EvmToolCommand(bais, new PrintWriter(baos, true, UTF_8));
    final CodeValidateSubCommand codeValidateSubCommand = new CodeValidateSubCommand(parentCommand);
    codeValidateSubCommand.run();
    assertThat(baos.toString(UTF_8))
        .contains("err: layout - invalid_magic EOF header byte 1 incorrect\n");
  }

  @Test
  void testMultipleViaInput() {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final ByteArrayInputStream bais = new ByteArrayInputStream(CODE_MULTIPLE.getBytes(UTF_8));
    EvmToolCommand parentCommand = new EvmToolCommand(bais, new PrintWriter(baos, true, UTF_8));
    final CodeValidateSubCommand codeValidateSubCommand = new CodeValidateSubCommand(parentCommand);
    codeValidateSubCommand.run();
    assertThat(baos.toString(UTF_8))
        .contains(
            """
                OK 1/0/0
                err: layout - invalid_magic EOF header byte 1 incorrect
                OK 1/0/0
                """);
  }

  @Test
  void testSingleValidViaCli() {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
    EvmToolCommand parentCommand = new EvmToolCommand(bais, new PrintWriter(baos, true, UTF_8));
    final CodeValidateSubCommand codeValidateSubCommand = new CodeValidateSubCommand(parentCommand);
    final CommandLine cmd = new CommandLine(codeValidateSubCommand);
    cmd.parseArgs(CODE_STOP_ONLY);
    codeValidateSubCommand.run();
    assertThat(baos.toString(UTF_8)).contains("OK 1/0/0\n");
  }

  @Test
  void testSingleInvalidViaCli() {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
    EvmToolCommand parentCommand = new EvmToolCommand(bais, new PrintWriter(baos, true, UTF_8));
    final CodeValidateSubCommand codeValidateSubCommand = new CodeValidateSubCommand(parentCommand);
    final CommandLine cmd = new CommandLine(codeValidateSubCommand);
    cmd.parseArgs(CODE_BAD_MAGIC);
    codeValidateSubCommand.run();
    assertThat(baos.toString(UTF_8))
        .contains("err: layout - invalid_magic EOF header byte 1 incorrect\n");
  }

  @Test
  void testMultipleViaCli() {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
    EvmToolCommand parentCommand = new EvmToolCommand(bais, new PrintWriter(baos, true, UTF_8));
    final CodeValidateSubCommand codeValidateSubCommand = new CodeValidateSubCommand(parentCommand);
    final CommandLine cmd = new CommandLine(codeValidateSubCommand);
    cmd.parseArgs(CODE_STOP_ONLY, CODE_BAD_MAGIC, CODE_RETURN_ONLY);
    codeValidateSubCommand.run();
    assertThat(baos.toString(UTF_8))
        .contains(
            """
                OK 1/0/0
                err: layout - invalid_magic EOF header byte 1 incorrect
                OK 1/0/0
                """);
  }

  @Test
  void testCliEclipsesInput() {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final ByteArrayInputStream bais = new ByteArrayInputStream(CODE_STOP_ONLY.getBytes(UTF_8));
    EvmToolCommand parentCommand = new EvmToolCommand(bais, new PrintWriter(baos, true, UTF_8));
    final CodeValidateSubCommand codeValidateSubCommand = new CodeValidateSubCommand(parentCommand);
    final CommandLine cmd = new CommandLine(codeValidateSubCommand);
    cmd.parseArgs(CODE_RETURN_ONLY);
    codeValidateSubCommand.run();
    assertThat(baos.toString(UTF_8)).contains("OK 1/0/0\n");
  }

  @Test
  void testInteriorCommentsSkipped() {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
    EvmToolCommand parentCommand = new EvmToolCommand(bais, new PrintWriter(baos, true, UTF_8));
    final CodeValidateSubCommand codeValidateSubCommand = new CodeValidateSubCommand(parentCommand);
    final CommandLine cmd = new CommandLine(codeValidateSubCommand);
    cmd.parseArgs(CODE_INTERIOR_COMMENTS);
    codeValidateSubCommand.run();
    assertThat(baos.toString(UTF_8)).contains("OK 2/0/0\n");
  }

  @Test
  void testBlankLinesAndCommentsSkipped() {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final ByteArrayInputStream bais =
        new ByteArrayInputStream(("# comment\n\n#blank line\n\n" + CODE_MULTIPLE).getBytes(UTF_8));
    EvmToolCommand parentCommand = new EvmToolCommand(bais, new PrintWriter(baos, true, UTF_8));
    final CodeValidateSubCommand codeValidateSubCommand = new CodeValidateSubCommand(parentCommand);
    codeValidateSubCommand.run();
    assertThat(baos.toString(UTF_8))
        .isEqualTo(
            """
                OK 1/0/0
                err: layout - invalid_magic EOF header byte 1 incorrect
                OK 1/0/0
                """);
  }
}
