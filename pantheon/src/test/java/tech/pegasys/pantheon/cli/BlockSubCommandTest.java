/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.io.File;

import org.junit.Test;
import picocli.CommandLine.Model.CommandSpec;

public class BlockSubCommandTest extends CommandTestAbstract {

  private static final String EXPECTED_BLOCK_USAGE =
      "Usage: pantheon blocks [-hV] [COMMAND]"
          + System.lineSeparator()
          + "This command provides blocks related actions."
          + System.lineSeparator()
          + "  -h, --help      Show this help message and exit."
          + System.lineSeparator()
          + "  -V, --version   Print version information and exit."
          + System.lineSeparator()
          + "Commands:"
          + System.lineSeparator()
          + "  import  This command imports blocks from a file into the database."
          + System.lineSeparator();

  private static final String EXPECTED_BLOCK_IMPORT_USAGE =
      "Usage: pantheon blocks import [-hV] --from=<FILE>"
          + System.lineSeparator()
          + "This command imports blocks from a file into the database."
          + System.lineSeparator()
          + "      --from=<FILE>   File containing blocks to import"
          + System.lineSeparator()
          + "  -h, --help          Show this help message and exit."
          + System.lineSeparator()
          + "  -V, --version       Print version information and exit."
          + System.lineSeparator();

  private static final String BLOCK_SUBCOMMAND_NAME = "blocks";
  private static final String BLOCK_IMPORT_SUBCOMMAND_NAME = "import";

  // Block sub-command
  @Test
  public void blockSubCommandExistAnbHaveSubCommands() {
    CommandSpec spec = parseCommand().getSpec();
    assertThat(spec.subcommands()).containsKeys(BLOCK_SUBCOMMAND_NAME);
    assertThat(spec.subcommands().get(BLOCK_SUBCOMMAND_NAME).getSubcommands())
        .containsKeys(BLOCK_IMPORT_SUBCOMMAND_NAME);
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingBlockSubCommandWithoutSubSubcommandMustDisplayUsage() {
    parseCommand(BLOCK_SUBCOMMAND_NAME);
    assertThat(commandOutput.toString()).startsWith(EXPECTED_BLOCK_USAGE);
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingBlockSubCommandHelpMustDisplayUsage() {
    parseCommand(BLOCK_SUBCOMMAND_NAME, "--help");
    assertThat(commandOutput.toString()).startsWith(EXPECTED_BLOCK_USAGE);
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  // Import sub-sub-command
  @Test
  public void callingBlockImportSubCommandWithoutPathMustDisplayErrorAndUsage() {
    parseCommand(BLOCK_SUBCOMMAND_NAME, BLOCK_IMPORT_SUBCOMMAND_NAME);
    final String expectedErrorOutputStart = "Missing required option '--from=<FILE>'";
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).startsWith(expectedErrorOutputStart);
  }

  @Test
  public void callingBlockImportSubCommandHelpMustDisplayUsage() {
    parseCommand(BLOCK_SUBCOMMAND_NAME, BLOCK_IMPORT_SUBCOMMAND_NAME, "--help");
    assertThat(commandOutput.toString()).startsWith(EXPECTED_BLOCK_IMPORT_USAGE);
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingBlockImportSubCommandWithPathMustImportBlocksWithThisPath() throws Exception {
    File fileToImport = temp.newFile("blocks.file");
    parseCommand(
        BLOCK_SUBCOMMAND_NAME, BLOCK_IMPORT_SUBCOMMAND_NAME, "--from", fileToImport.getPath());

    verify(mockBlockImporter).importBlockchain(pathArgumentCaptor.capture(), any());

    assertThat(pathArgumentCaptor.getValue()).isEqualByComparingTo(fileToImport.toPath());

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }
}
