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
package org.hyperledger.besu.cli.subcommands.blocks;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.BesuInfo;
import org.hyperledger.besu.cli.CommandTestAbstract;
import org.hyperledger.besu.controller.BesuController;

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
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import picocli.CommandLine.Model.CommandSpec;

@RunWith(MockitoJUnitRunner.class)
public class BlocksSubCommandTest extends CommandTestAbstract {

  @Rule public final TemporaryFolder folder = new TemporaryFolder();

  private static final String EXPECTED_BLOCK_USAGE =
      "Usage: besu blocks [-hV] [COMMAND]"
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
      "Usage: besu blocks import [-hV] [--run] [--skip-pow-validation-enabled]\n"
          + "                          [--end-block=<LONG>] [--format=<format>]\n"
          + "                          [--start-block=<LONG>] [--start-time=<startTime>]\n"
          + "                          [--from[=<FILE>...]]... [<FILE>...]\n"
          + "This command imports blocks from a file into the database.\n"
          + "      [<FILE>...]            Files containing blocks to import.\n"
          + "      --end-block=<LONG>     The ending index of the block list to import\n"
          + "                               (exclusive).  If not specified all blocks after\n"
          + "                               the start block will be imported.\n"
          + "      --format=<format>      The type of data to be imported, possible values\n"
          + "                               are: RLP, JSON (default: RLP).\n"
          + "      --from[=<FILE>...]     File containing blocks to import.\n"
          + "  -h, --help                 Show this help message and exit.\n"
          + "      --run                  Start besu after importing.\n"
          + "      --skip-pow-validation-enabled\n"
          + "                             Skip proof of work validation when importing.\n"
          + "      --start-block=<LONG>   The starting index of the block, or block list to\n"
          + "                               import.  If not specified all blocks before the\n"
          + "                               end block will be imported\n"
          + "      --start-time=<startTime>\n"
          + "                             The timestamp in seconds of the first block for\n"
          + "                               JSON imports. Subsequent blocks will be 1 second\n"
          + "                               later. (default: current time)\n"
          + "  -V, --version              Print version information and exit.\n";

  private static final String EXPECTED_BLOCK_EXPORT_USAGE =
      "Usage: besu blocks export [-hV] [--end-block=<LONG>] [--start-block=<LONG>]"
          + System.lineSeparator()
          + "                          --to=<FILE>"
          + System.lineSeparator()
          + "This command exports a specific block, or list of blocks from storage."
          + System.lineSeparator()
          + "      --end-block=<LONG>     The ending index of the block list to export"
          + System.lineSeparator()
          + "                               (exclusive). If not specified a single block"
          + System.lineSeparator()
          + "                               will be exported."
          + System.lineSeparator()
          + "  -h, --help                 Show this help message and exit."
          + System.lineSeparator()
          + "      --start-block=<LONG>   The starting index of the block, or block list to"
          + System.lineSeparator()
          + "                               export."
          + System.lineSeparator()
          + "      --to=<FILE>            File to write the block list to."
          + System.lineSeparator()
          + "  -V, --version              Print version information and exit."
          + System.lineSeparator();

  private static final String BLOCK_SUBCOMMAND_NAME = "blocks";
  private static final String BLOCK_IMPORT_SUBCOMMAND_NAME = "import";
  private static final String BLOCK_EXPORT_SUBCOMMAND_NAME = "export";

  // Block sub-command
  @Test
  public void blockSubCommandExistsAndHasSubCommands() {
    final CommandSpec spec = parseCommand().getSpec();
    assertThat(spec.subcommands()).containsKeys(BLOCK_SUBCOMMAND_NAME);
    assertThat(spec.subcommands().get(BLOCK_SUBCOMMAND_NAME).getSubcommands())
        .containsKeys(BLOCK_IMPORT_SUBCOMMAND_NAME);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingBlockSubCommandWithoutSubSubcommandMustDisplayUsage() {
    parseCommand(BLOCK_SUBCOMMAND_NAME);
    assertThat(commandOutput.toString(UTF_8)).startsWith(EXPECTED_BLOCK_USAGE);
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingBlockSubCommandHelpMustDisplayUsage() {
    parseCommand(BLOCK_SUBCOMMAND_NAME, "--help");
    assertThat(commandOutput.toString(UTF_8)).startsWith(EXPECTED_BLOCK_USAGE);
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingBlockSubCommandVersionMustDisplayVersion() {
    parseCommand(BLOCK_SUBCOMMAND_NAME, "--version");
    assertThat(commandOutput.toString(UTF_8)).isEqualToIgnoringWhitespace(BesuInfo.version());
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  // Import sub-sub-command
  @Test
  public void callingBlockImportSubCommandWithoutPathMustDisplayErrorAndUsage() {
    parseCommand(BLOCK_SUBCOMMAND_NAME, BLOCK_IMPORT_SUBCOMMAND_NAME);
    final String expectedErrorOutputStart = "No files specified to import.";
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).startsWith(expectedErrorOutputStart);
  }

  @Test
  public void callingBlockImportSubCommandWithJSONAndSkipPOWFails() {
    parseCommand(
        BLOCK_SUBCOMMAND_NAME,
        BLOCK_IMPORT_SUBCOMMAND_NAME,
        "--format",
        "JSON",
        "--skip-pow-validation-enabled",
        "blocks.file");
    final String expectedErrorOutputStart = "Can't skip";
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).startsWith(expectedErrorOutputStart);
  }

  @Test
  public void callingBlockImportSubCommandHelpMustDisplayUsage() {
    parseCommand(BLOCK_SUBCOMMAND_NAME, BLOCK_IMPORT_SUBCOMMAND_NAME, "--help");
    assertThat(commandOutput.toString(UTF_8)).isEqualTo(EXPECTED_BLOCK_IMPORT_USAGE);
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingBlockImportSubCommandVersionMustDisplayVersion() {
    parseCommand(BLOCK_SUBCOMMAND_NAME, BLOCK_IMPORT_SUBCOMMAND_NAME, "--version");
    assertThat(commandOutput.toString(UTF_8)).isEqualToIgnoringWhitespace(BesuInfo.version());
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingBlockImportSubCommandWithPathMustImportBlocksWithThisPath() throws Exception {
    final File fileToImport = temp.newFile("blocks.file");
    parseCommand(
        BLOCK_SUBCOMMAND_NAME, BLOCK_IMPORT_SUBCOMMAND_NAME, "--from", fileToImport.getPath());

    verify(rlpBlockImporter)
        .importBlockchain(pathArgumentCaptor.capture(), any(), anyBoolean(), anyLong(), anyLong());

    assertThat(pathArgumentCaptor.getValue()).isEqualByComparingTo(fileToImport.toPath());

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
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

    verify(rlpBlockImporter)
        .importBlockchain(pathArgumentCaptor.capture(), any(), anyBoolean(), anyLong(), anyLong());

    assertThat(pathArgumentCaptor.getValue()).isEqualByComparingTo(fileToImport.toPath());

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void blocksImport_rlpFormatMultiple() throws Exception {
    final File fileToImport = temp.newFile("blocks.file");
    final File file2ToImport = temp.newFile("blocks2.file");
    final File file3ToImport = temp.newFile("blocks3.file");
    parseCommand(
        BLOCK_SUBCOMMAND_NAME,
        BLOCK_IMPORT_SUBCOMMAND_NAME,
        "--format",
        "RLP",
        fileToImport.getPath(),
        file2ToImport.getPath(),
        file3ToImport.getPath());

    verify(rlpBlockImporter, times(3))
        .importBlockchain(pathArgumentCaptor.capture(), any(), anyBoolean(), anyLong(), anyLong());

    assertThat(pathArgumentCaptor.getAllValues())
        .containsExactlyInAnyOrder(
            fileToImport.toPath(), file2ToImport.toPath(), file3ToImport.toPath());

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
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

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

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
    final String expectedErrorOutputStart = "Missing required option: '--to=<FILE>'";
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).startsWith(expectedErrorOutputStart);

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
            + File.separator
            + BesuController.DATABASE_PATH;
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).startsWith(expectedErrorOutputStart);

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
            + File.separator
            + BesuController.DATABASE_PATH;
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).startsWith(expectedErrorOutputStart);

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
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

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
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

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
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

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
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

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
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Parameter --end-block (1) must be greater start block (10)");
    assertThat(commandOutput.toString(UTF_8)).isEmpty();

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
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Parameter --end-block (10) must be greater start block (10)");
    assertThat(commandOutput.toString(UTF_8)).isEmpty();

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
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Parameter --start-block (-1) must be greater than or equal to zero");
    assertThat(commandOutput.toString(UTF_8)).isEmpty();

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
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Parameter --end-block (-1) must be greater than or equal to zero");
    assertThat(commandOutput.toString(UTF_8)).isEmpty();

    verify(rlpBlockExporter, never()).exportBlocks(any(), any(), any());
  }

  @Test
  public void callingBlockExportSubCommandHelpMustDisplayUsage() {
    parseCommand(BLOCK_SUBCOMMAND_NAME, BLOCK_EXPORT_SUBCOMMAND_NAME, "--help");
    assertThat(commandOutput.toString(UTF_8)).startsWith(EXPECTED_BLOCK_EXPORT_USAGE);
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingBlockExportSubCommandVersionMustDisplayVersion() {
    parseCommand(BLOCK_SUBCOMMAND_NAME, BLOCK_EXPORT_SUBCOMMAND_NAME, "--version");
    assertThat(commandOutput.toString(UTF_8)).isEqualToIgnoringWhitespace(BesuInfo.version());
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  private void createDbDirectory(final boolean createDataFiles) throws IOException {
    final File dbDir = folder.newFolder(BesuController.DATABASE_PATH);
    if (createDataFiles) {
      final Path dataFilePath = Paths.get(dbDir.getAbsolutePath(), "0000001.sst");
      final boolean success = new File(dataFilePath.toString()).createNewFile();
      assertThat(success).isTrue();
    }
  }
}
