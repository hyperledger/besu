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
package tech.pegasys.pantheon.cli.subcommands.blocks;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import tech.pegasys.pantheon.cli.CommandTestAbstract;
import tech.pegasys.pantheon.controller.PantheonController;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import picocli.CommandLine.Model.CommandSpec;

public class BlocksSubCommandTest extends CommandTestAbstract {

  @Rule public final TemporaryFolder folder = new TemporaryFolder();

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
          //      "Usage: pantheon blocks import [-hV] [--format=<format>] --from=<FILE>"
          + System.lineSeparator()
          + "This command imports blocks from a file into the database."
          // Hide format for while JSON option is under development
          //          + System.lineSeparator()
          //          + "      --format=<format>   The type of data to be imported, possible values
          // are: RLP,\n"
          //          + "                            JSON (default: RLP)."
          + System.lineSeparator()
          + "      --from=<FILE>   File containing blocks to import."
          + System.lineSeparator()
          + "  -h, --help          Show this help message and exit."
          + System.lineSeparator()
          + "  -V, --version       Print version information and exit."
          + System.lineSeparator();

  private static final String EXPECTED_BLOCK_EXPORT_USAGE =
      "Usage: pantheon blocks export [-hV] [--end-block=<LONG>] [--start-block=<LONG>]"
          + System.lineSeparator()
          + "                              --to=<FILE>"
          + System.lineSeparator()
          + "This command export a specific block from storage"
          + System.lineSeparator()
          + "      --end-block=<LONG>     the ending index of the block list to export"
          + System.lineSeparator()
          + "                               (exclusive), if not specified a single block will be"
          + System.lineSeparator()
          + "                               export"
          + System.lineSeparator()
          + "      --start-block=<LONG>   the starting index of the block list to export"
          + System.lineSeparator()
          + "                               (inclusive)"
          + System.lineSeparator()
          + "      --to=<FILE>            File to write the block list instead of standard output"
          + System.lineSeparator()
          + "  -h, --help                 Show this help message and exit."
          + System.lineSeparator()
          + "  -V, --version              Print version information and exit."
          + System.lineSeparator();

  private static final String BLOCK_SUBCOMMAND_NAME = "blocks";
  private static final String BLOCK_IMPORT_SUBCOMMAND_NAME = "import";
  private static final String BLOCK_EXPORT_SUBCOMMAND_NAME = "export";

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
    assertThat(commandOutput.toString()).isEqualToIgnoringWhitespace(EXPECTED_BLOCK_IMPORT_USAGE);
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingBlockImportSubCommandWithPathMustImportBlocksWithThisPath() throws Exception {
    final File fileToImport = temp.newFile("blocks.file");
    parseCommand(
        BLOCK_SUBCOMMAND_NAME, BLOCK_IMPORT_SUBCOMMAND_NAME, "--from", fileToImport.getPath());

    verify(rlpBlockImporter).importBlockchain(pathArgumentCaptor.capture(), any());

    assertThat(pathArgumentCaptor.getValue()).isEqualByComparingTo(fileToImport.toPath());

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void blocksImport_rlpFormat() throws Exception {
    final File fileToImport = temp.newFile("blocks.file");
    parseCommand(
        BLOCK_SUBCOMMAND_NAME,
        BLOCK_IMPORT_SUBCOMMAND_NAME,
        "--format",
        "RLP",
        "--from",
        fileToImport.getPath());

    verify(rlpBlockImporter).importBlockchain(pathArgumentCaptor.capture(), any());

    assertThat(pathArgumentCaptor.getValue()).isEqualByComparingTo(fileToImport.toPath());

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void blocksImport_jsonFormat() throws Exception {
    final String fileContent = "test";
    final File fileToImport = temp.newFile("blocks.file");
    final Writer fileWriter = Files.newBufferedWriter(fileToImport.toPath(), UTF_8);
    fileWriter.write(fileContent);
    fileWriter.close();

    parseCommand(
        BLOCK_SUBCOMMAND_NAME,
        BLOCK_IMPORT_SUBCOMMAND_NAME,
        "--format",
        "JSON",
        "--from",
        fileToImport.getPath());

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();

    verify(jsonBlockImporter, times(1)).importChain(stringArgumentCaptor.capture());
    assertThat(stringArgumentCaptor.getValue()).isEqualTo(fileContent);
  }

  // Export sub-sub-command
  @Test
  public void blocksExport_missingFileParam() throws IOException {
    createDbDirectory(true);
    parseCommand(
        "--data-path=" + folder.getRoot().getAbsolutePath(),
        BLOCK_SUBCOMMAND_NAME,
        BLOCK_EXPORT_SUBCOMMAND_NAME);
    final String expectedErrorOutputStart = "Missing required option '--to=<FILE>'";
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).startsWith(expectedErrorOutputStart);

    verify(rlpBlockExporter, never()).exportBlocks(any(), any(), any());
  }

  @Test
  public void blocksExport_noDbDirectory() throws IOException {
    final File outputFile = folder.newFile("blocks.bin");
    parseCommand(
        "--data-path=" + folder.getRoot().getAbsolutePath(),
        BLOCK_SUBCOMMAND_NAME,
        BLOCK_EXPORT_SUBCOMMAND_NAME,
        "--to",
        outputFile.getPath());
    final String expectedErrorOutputStart =
        "Chain is empty.  Unable to export blocks from specified data directory: "
            + folder.getRoot().getAbsolutePath()
            + "/"
            + PantheonController.DATABASE_PATH;
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).startsWith(expectedErrorOutputStart);

    verify(rlpBlockExporter, never()).exportBlocks(any(), any(), any());
  }

  @Test
  public void blocksExport_emptyDbDirectory() throws IOException {
    createDbDirectory(false);
    final File outputFile = folder.newFile("blocks.bin");
    parseCommand(
        "--data-path=" + folder.getRoot().getAbsolutePath(),
        BLOCK_SUBCOMMAND_NAME,
        BLOCK_EXPORT_SUBCOMMAND_NAME,
        "--to",
        outputFile.getPath());
    final String expectedErrorOutputStart =
        "Chain is empty.  Unable to export blocks from specified data directory: "
            + folder.getRoot().getAbsolutePath()
            + "/"
            + PantheonController.DATABASE_PATH;
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).startsWith(expectedErrorOutputStart);

    verify(rlpBlockExporter, never()).exportBlocks(any(), any(), any());
  }

  @Test
  public void blocksExport_noStartOrEnd() throws IOException {
    createDbDirectory(true);
    final File outputFile = folder.newFile("blocks.bin");
    parseCommand(
        "--data-path=" + folder.getRoot().getAbsolutePath(),
        BLOCK_SUBCOMMAND_NAME,
        BLOCK_EXPORT_SUBCOMMAND_NAME,
        "--to",
        outputFile.getPath());
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();

    verify(rlpBlockExporter, times(1)).exportBlocks(outputFile, Optional.empty(), Optional.empty());
  }

  @Test
  public void blocksExport_withStartAndNoEnd() throws IOException {
    createDbDirectory(true);
    final File outputFile = folder.newFile("blocks.bin");
    parseCommand(
        "--data-path=" + folder.getRoot().getAbsolutePath(),
        BLOCK_SUBCOMMAND_NAME,
        BLOCK_EXPORT_SUBCOMMAND_NAME,
        "--to",
        outputFile.getPath(),
        "--start-block=1");
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();

    verify(rlpBlockExporter, times(1)).exportBlocks(outputFile, Optional.of(1L), Optional.empty());
  }

  @Test
  public void blocksExport_withEndAndNoStart() throws IOException {
    createDbDirectory(true);
    final File outputFile = folder.newFile("blocks.bin");
    parseCommand(
        "--data-path=" + folder.getRoot().getAbsolutePath(),
        BLOCK_SUBCOMMAND_NAME,
        BLOCK_EXPORT_SUBCOMMAND_NAME,
        "--to",
        outputFile.getPath(),
        "--end-block=10");
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();

    verify(rlpBlockExporter, times(1)).exportBlocks(outputFile, Optional.empty(), Optional.of(10L));
  }

  @Test
  public void blocksExport_withStartAndEnd() throws IOException {
    createDbDirectory(true);
    final File outputFile = folder.newFile("blocks.bin");
    parseCommand(
        "--data-path=" + folder.getRoot().getAbsolutePath(),
        BLOCK_SUBCOMMAND_NAME,
        BLOCK_EXPORT_SUBCOMMAND_NAME,
        "--to",
        outputFile.getPath(),
        "--start-block=1",
        "--end-block=10");
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();

    verify(rlpBlockExporter, times(1)).exportBlocks(outputFile, Optional.of(1L), Optional.of(10L));
  }

  @Test
  public void blocksExport_withOutOfOrderStartAndEnd() throws IOException {
    createDbDirectory(true);
    final File outputFile = folder.newFile("blocks.bin");
    parseCommand(
        "--data-path=" + folder.getRoot().getAbsolutePath(),
        BLOCK_SUBCOMMAND_NAME,
        BLOCK_EXPORT_SUBCOMMAND_NAME,
        "--to",
        outputFile.getPath(),
        "--start-block=10",
        "--end-block=1");
    assertThat(commandErrorOutput.toString())
        .contains("Parameter --end-block (1) must be greater start block (10)");
    assertThat(commandOutput.toString()).isEmpty();

    verify(rlpBlockExporter, never()).exportBlocks(any(), any(), any());
  }

  @Test
  public void blocksExport_withEmptyRange() throws IOException {
    createDbDirectory(true);
    final File outputFile = folder.newFile("blocks.bin");
    parseCommand(
        "--data-path=" + folder.getRoot().getAbsolutePath(),
        BLOCK_SUBCOMMAND_NAME,
        BLOCK_EXPORT_SUBCOMMAND_NAME,
        "--to",
        outputFile.getPath(),
        "--start-block=10",
        "--end-block=10");
    assertThat(commandErrorOutput.toString())
        .contains("Parameter --end-block (10) must be greater start block (10)");
    assertThat(commandOutput.toString()).isEmpty();

    verify(rlpBlockExporter, never()).exportBlocks(any(), any(), any());
  }

  @Test
  public void blocksExport_withInvalidStart() throws IOException {
    createDbDirectory(true);
    final File outputFile = folder.newFile("blocks.bin");
    parseCommand(
        "--data-path=" + folder.getRoot().getAbsolutePath(),
        BLOCK_SUBCOMMAND_NAME,
        BLOCK_EXPORT_SUBCOMMAND_NAME,
        "--to",
        outputFile.getPath(),
        "--start-block=-1");
    assertThat(commandErrorOutput.toString())
        .contains("Parameter --start-block (-1) must be greater than or equal to zero");
    assertThat(commandOutput.toString()).isEmpty();

    verify(rlpBlockExporter, never()).exportBlocks(any(), any(), any());
  }

  @Test
  public void blocksExport_withInvalidEnd() throws IOException {
    createDbDirectory(true);
    final File outputFile = folder.newFile("blocks.bin");
    parseCommand(
        "--data-path=" + folder.getRoot().getAbsolutePath(),
        BLOCK_SUBCOMMAND_NAME,
        BLOCK_EXPORT_SUBCOMMAND_NAME,
        "--to",
        outputFile.getPath(),
        "--end-block=-1");
    assertThat(commandErrorOutput.toString())
        .contains("Parameter --end-block (-1) must be greater than or equal to zero");
    assertThat(commandOutput.toString()).isEmpty();

    verify(rlpBlockExporter, never()).exportBlocks(any(), any(), any());
  }

  @Test
  public void callingBlockExportSubCommandHelpMustDisplayUsage() {
    parseCommand(BLOCK_SUBCOMMAND_NAME, BLOCK_EXPORT_SUBCOMMAND_NAME, "--help");
    assertThat(commandOutput.toString()).startsWith(EXPECTED_BLOCK_EXPORT_USAGE);
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  private void createDbDirectory(final boolean createDataFiles) throws IOException {
    File dbDir = folder.newFolder(PantheonController.DATABASE_PATH);
    if (createDataFiles) {
      Path dataFilePath = Paths.get(dbDir.getAbsolutePath(), "0000001.sst");
      final boolean success = new File(dataFilePath.toString()).createNewFile();
      assertThat(success).isTrue();
    }
  }
}
