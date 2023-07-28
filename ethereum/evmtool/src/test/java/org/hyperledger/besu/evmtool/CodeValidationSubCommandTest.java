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
import java.io.PrintStream;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public class CodeValidationSubCommandTest {

  static final String CODE_STOP_ONLY = "0xef0001 010004 020001-0001 030000 00 00000000 00";
  static final String CODE_RETF_ONLY = "0xef0001 010004 020001-0001 030000 00 00000000 e4";
  static final String CODE_BAD_MAGIC = "0xefffff 010004 020001-0001 030000 00 00000000 e4";
  static final String CODE_INTERIOR_COMMENTS =
      """
                  0xef0001 010008 020002-000c-0002 030000 00
                  # 7 inputs 1 output,
                  00000007-07010007
                  59-59-59-59-59-59-59-e30001-50-e4
                  # No immediate data
                  f1-e4""";
  static final String CODE_MULTIPLE =
      CODE_STOP_ONLY + "\n" + CODE_BAD_MAGIC + "\n" + CODE_RETF_ONLY + "\n";

  @Test
  public void testSingleValidViaInput() {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final ByteArrayInputStream bais = new ByteArrayInputStream(CODE_STOP_ONLY.getBytes(UTF_8));
    final CodeValidateSubCommand codeValidateSubCommand =
        new CodeValidateSubCommand(bais, new PrintStream(baos));
    codeValidateSubCommand.run();
    assertThat(baos.toString(UTF_8)).contains("OK 00\n");
  }

  @Test
  public void testSingleInvalidViaInput() {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final ByteArrayInputStream bais = new ByteArrayInputStream(CODE_BAD_MAGIC.getBytes(UTF_8));
    final CodeValidateSubCommand codeValidateSubCommand =
        new CodeValidateSubCommand(bais, new PrintStream(baos));
    codeValidateSubCommand.run();
    assertThat(baos.toString(UTF_8)).contains("err: layout - EOF header byte 1 incorrect\n");
  }

  @Test
  public void testMultipleViaInput() {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final ByteArrayInputStream bais = new ByteArrayInputStream(CODE_MULTIPLE.getBytes(UTF_8));
    final CodeValidateSubCommand codeValidateSubCommand =
        new CodeValidateSubCommand(bais, new PrintStream(baos));
    codeValidateSubCommand.run();
    assertThat(baos.toString(UTF_8))
        .contains(
            """
                OK 00
                err: layout - EOF header byte 1 incorrect
                OK e4
                """);
  }

  @Test
  public void testSingleValidViaCli() {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
    final CodeValidateSubCommand codeValidateSubCommand =
        new CodeValidateSubCommand(bais, new PrintStream(baos));
    final CommandLine cmd = new CommandLine(codeValidateSubCommand);
    cmd.parseArgs(CODE_STOP_ONLY);
    codeValidateSubCommand.run();
    assertThat(baos.toString(UTF_8)).contains("OK 00\n");
  }

  @Test
  public void testSingleInvalidViaCli() {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
    final CodeValidateSubCommand codeValidateSubCommand =
        new CodeValidateSubCommand(bais, new PrintStream(baos));
    final CommandLine cmd = new CommandLine(codeValidateSubCommand);
    cmd.parseArgs(CODE_BAD_MAGIC);
    codeValidateSubCommand.run();
    assertThat(baos.toString(UTF_8)).contains("err: layout - EOF header byte 1 incorrect\n");
  }

  @Test
  public void testMultipleViaCli() {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
    final CodeValidateSubCommand codeValidateSubCommand =
        new CodeValidateSubCommand(bais, new PrintStream(baos));
    final CommandLine cmd = new CommandLine(codeValidateSubCommand);
    cmd.parseArgs(CODE_STOP_ONLY, CODE_BAD_MAGIC, CODE_RETF_ONLY);
    codeValidateSubCommand.run();
    assertThat(baos.toString(UTF_8))
        .contains(
            """
                OK 00
                err: layout - EOF header byte 1 incorrect
                OK e4
                """);
  }

  @Test
  public void testCliEclipsesInput() {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final ByteArrayInputStream bais = new ByteArrayInputStream(CODE_STOP_ONLY.getBytes(UTF_8));
    final CodeValidateSubCommand codeValidateSubCommand =
        new CodeValidateSubCommand(bais, new PrintStream(baos));
    final CommandLine cmd = new CommandLine(codeValidateSubCommand);
    cmd.parseArgs(CODE_RETF_ONLY);
    codeValidateSubCommand.run();
    assertThat(baos.toString(UTF_8)).contains("OK e4\n");
  }

  @Test
  public void testInteriorCommentsSkipped() {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
    final CodeValidateSubCommand codeValidateSubCommand =
        new CodeValidateSubCommand(bais, new PrintStream(baos));
    final CommandLine cmd = new CommandLine(codeValidateSubCommand);
    cmd.parseArgs(CODE_INTERIOR_COMMENTS);
    codeValidateSubCommand.run();
    assertThat(baos.toString(UTF_8)).contains("OK 59595959595959e3000150e4,f1e4\n");
  }

  @Test
  public void testBlankLinesAndCommentsSkipped() {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final ByteArrayInputStream bais =
        new ByteArrayInputStream(("# comment\n\n#blank line\n\n" + CODE_MULTIPLE).getBytes(UTF_8));
    final CodeValidateSubCommand codeValidateSubCommand =
        new CodeValidateSubCommand(bais, new PrintStream(baos));
    codeValidateSubCommand.run();
    assertThat(baos.toString(UTF_8))
        .isEqualTo(
            """
                OK 00
                err: layout - EOF header byte 1 incorrect
                OK e4
                """);
  }
}
