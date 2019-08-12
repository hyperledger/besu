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
import static org.assertj.core.api.Assertions.contentOf;
import static org.assertj.core.util.Arrays.asList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import tech.pegasys.pantheon.cli.CommandTestAbstract;
import tech.pegasys.pantheon.config.GenesisConfigFile;
import tech.pegasys.pantheon.controller.PantheonController;
import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider;
import tech.pegasys.pantheon.ethereum.core.MiningParametersTestBuilder;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.eth.EthProtocolConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPoolConfiguration;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.testutil.BlockTestUtil;
import tech.pegasys.pantheon.testutil.TestClock;
import tech.pegasys.pantheon.util.BlockExporter;
import tech.pegasys.pantheon.util.BlockImporter;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

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
      "Usage: pantheon blocks export [-hV] [--end-block=<LONG>] --start-block=<LONG>"
          + System.lineSeparator()
          + "                              [--to=<FILE>]"
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

    verify(mockBlockImporter).importBlockchain(pathArgumentCaptor.capture(), any());

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

    verify(mockBlockImporter).importBlockchain(pathArgumentCaptor.capture(), any());

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

    verify(chainImporter, times(1)).importChain(stringArgumentCaptor.capture());
    assertThat(stringArgumentCaptor.getValue()).isEqualTo(fileContent);
  }

  // Export sub-sub-command
  @Test
  public void callingBlockExportSubCommandWithoutStartBlockMustDisplayErrorAndUsage() {
    parseCommand(BLOCK_SUBCOMMAND_NAME, BLOCK_EXPORT_SUBCOMMAND_NAME);
    final String expectedErrorOutputStart = "Missing required option '--start-block=<LONG>'";
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).startsWith(expectedErrorOutputStart);
  }

  @Test
  public void callingBlockExportSubCommandHelpMustDisplayUsage() {
    parseCommand(BLOCK_SUBCOMMAND_NAME, BLOCK_EXPORT_SUBCOMMAND_NAME, "--help");
    assertThat(commandOutput.toString()).startsWith(EXPECTED_BLOCK_EXPORT_USAGE);
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingBlockExportSubCommandWithNegativeStartBlockMustDisplayErrorAndUsage() {
    final String expectedErrorOutputStart = "--start-block must be greater than or equal to zero";
    parseCommand(BLOCK_SUBCOMMAND_NAME, BLOCK_EXPORT_SUBCOMMAND_NAME, "--start-block=-1");
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).startsWith(expectedErrorOutputStart);
  }

  @Test
  public void
      callingBlockExportSubCommandWithEndBlockGreaterThanStartBlockMustDisplayErrorMessage() {
    final String expectedErrorOutputStart = "--end-block must be greater than --start-block";
    parseCommand(
        BLOCK_SUBCOMMAND_NAME, BLOCK_EXPORT_SUBCOMMAND_NAME, "--start-block=2", "--end-block=1");
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).startsWith(expectedErrorOutputStart);
  }

  @Test
  public void callingBlockExportSubCommandWithEndBlockEqualToStartBlockMustDisplayErrorMessage() {
    final String expectedErrorOutputStart = "--end-block must be greater than --start-block";
    parseCommand(
        BLOCK_SUBCOMMAND_NAME, BLOCK_EXPORT_SUBCOMMAND_NAME, "--start-block=2", "--end-block=2");
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).startsWith(expectedErrorOutputStart);
  }

  @Test
  public void callingExportSubCommandWithFilePathMustWriteBlockInThisFile() throws Exception {

    final long startBlock = 0L;
    final long endBlock = 2L;
    final PantheonController<?> pantheonController = initPantheonController();
    final MutableBlockchain blockchain = pantheonController.getProtocolContext().getBlockchain();
    final File outputFile = File.createTempFile("export", "store");

    mockBlockExporter = new BlockExporter();

    doReturn(pantheonController).when(mockControllerBuilder).build();

    parseCommand(
        BLOCK_SUBCOMMAND_NAME,
        BLOCK_EXPORT_SUBCOMMAND_NAME,
        "--start-block=" + startBlock,
        "--end-block=" + endBlock,
        "--to=" + outputFile.getPath());

    final Block blockFromBlockchain =
        blockchain.getBlockByHash(blockchain.getBlockHashByNumber(startBlock).get());

    final Block secondBlockFromBlockchain =
        blockchain.getBlockByHash(blockchain.getBlockHashByNumber(endBlock - 1).get());

    assertThat(contentOf(outputFile))
        .isEqualTo(asList(new Block[] {blockFromBlockchain, secondBlockFromBlockchain}).toString());

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  private PantheonController<?> initPantheonController() throws IOException {
    final BlockImporter blockImporter = new BlockImporter();
    final Path dataDir = folder.newFolder().toPath();
    final Path source = dataDir.resolve("1000.blocks");
    BlockTestUtil.write1000Blocks(source);
    final PantheonController<?> targetController =
        new PantheonController.Builder()
            .fromGenesisConfig(GenesisConfigFile.mainnet())
            .synchronizerConfiguration(SynchronizerConfiguration.builder().build())
            .ethProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
            .storageProvider(new InMemoryStorageProvider())
            .networkId(1)
            .miningParameters(new MiningParametersTestBuilder().enabled(false).build())
            .nodeKeys(SECP256K1.KeyPair.generate())
            .metricsSystem(new NoOpMetricsSystem())
            .privacyParameters(PrivacyParameters.DEFAULT)
            .dataDirectory(dataDir)
            .clock(TestClock.fixed())
            .transactionPoolConfiguration(TransactionPoolConfiguration.builder().build())
            .build();
    blockImporter.importBlockchain(source, targetController);
    return targetController;
  }
}
