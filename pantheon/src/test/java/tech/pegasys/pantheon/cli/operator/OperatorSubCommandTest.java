/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.cli.operator;

import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.createTempDirectory;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.contentOf;
import static tech.pegasys.pantheon.cli.operator.OperatorSubCommandTest.Cmd.cmd;
import static tech.pegasys.pantheon.cli.subcommands.operator.OperatorSubCommand.COMMAND_NAME;
import static tech.pegasys.pantheon.cli.subcommands.operator.OperatorSubCommand.GENERATE_BLOCKCHAIN_CONFIG_SUBCOMMAND_NAME;

import tech.pegasys.pantheon.cli.CommandTestAbstract;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.json.JsonObject;
import org.junit.Before;
import org.junit.Test;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

public class OperatorSubCommandTest extends CommandTestAbstract {

  private static final String EXPECTED_OPERATOR_USAGE =
      "Usage: pantheon operator [-hV] [COMMAND]"
          + System.lineSeparator()
          + "This command provides operator related actions."
          + System.lineSeparator()
          + "  -h, --help      Show this help message and exit."
          + System.lineSeparator()
          + "  -V, --version   Print version information and exit."
          + System.lineSeparator()
          + "Commands:"
          + System.lineSeparator()
          + "  generate-blockchain-config  This command generates node keypairs, genesis"
          + System.lineSeparator()
          + "                                file (with RLP encoded IBFT 2.0 extra data).";

  private Path tmpOutputDirectoryPath;

  @Before
  public void init() throws IOException {
    tmpOutputDirectoryPath = createTempDirectory(format("output-%d", currentTimeMillis()));
  }

  @Test
  public void operatorSubCommandExistAndHaveSubCommands() {
    final CommandSpec spec = parseCommand().getSpec();
    assertThat(spec.subcommands()).containsKeys(COMMAND_NAME);
    assertThat(spec.subcommands().get(COMMAND_NAME).getSubcommands())
        .containsKeys(GENERATE_BLOCKCHAIN_CONFIG_SUBCOMMAND_NAME);
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingOperatorSubCommandWithoutSubSubcommandMustDisplayUsage() {
    parseCommand(COMMAND_NAME);
    assertThat(commandOutput.toString()).startsWith(EXPECTED_OPERATOR_USAGE);
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingOperatorCommandHelpMustDisplayUsage() {
    parseCommand(COMMAND_NAME, "--help");
    assertThat(commandOutput.toString()).startsWith(EXPECTED_OPERATOR_USAGE);
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void generateBlockchainConfigMustGenerateKeysWhenGenerateIsTrue() throws IOException {
    runCmdAndCheckOutput(
        cmd(),
        "/operator/config_generate_keys.json",
        tmpOutputDirectoryPath,
        "genesis.json",
        true,
        asList("key.pub", "key.priv"));
  }

  @Test
  public void generateBlockchainConfigMustImportKeysWhenGenerateIsFalse() throws IOException {
    runCmdAndCheckOutput(
        cmd(),
        "/operator/config_import_keys.json",
        tmpOutputDirectoryPath,
        "genesis.json",
        false,
        singletonList("key.pub"));
  }

  @Test
  public void genesisFileNameShouldBeEqualToOption() throws IOException {
    runCmdAndCheckOutput(
        cmd("--genesis-file-name", "option.json"),
        "/operator/config_generate_keys.json",
        tmpOutputDirectoryPath,
        "option.json",
        true,
        asList("key.pub", "key.priv"));
  }

  @Test
  public void publicKeyFileNameShouldBeEqualToOption() throws IOException {
    runCmdAndCheckOutput(
        cmd("--public-key-file-name", "pub.test"),
        "/operator/config_generate_keys.json",
        tmpOutputDirectoryPath,
        "genesis.json",
        true,
        asList("pub.test", "key.priv"));
  }

  @Test
  public void privateKeyFileNameShouldBeEqualToOption() throws IOException {
    runCmdAndCheckOutput(
        cmd("--private-key-file-name", "priv.test"),
        "/operator/config_generate_keys.json",
        tmpOutputDirectoryPath,
        "genesis.json",
        true,
        asList("key.pub", "priv.test"));
  }

  @Test
  public void shouldFailIfDuplicateFiles() {
    assertThatThrownBy(
            () ->
                runCmdAndCheckOutput(
                    cmd(
                        "--private-key-file-name",
                        "dup.test",
                        "--public-key-file-name",
                        "dup.test"),
                    "/operator/config_generate_keys.json",
                    tmpOutputDirectoryPath,
                    "genesis.json",
                    true,
                    asList("key.pub", "priv.test")))
        .isInstanceOf(CommandLine.ExecutionException.class);
  }

  @Test
  public void shouldFailIfPublicKeysAreWrongType() {
    assertThatThrownBy(
            () ->
                runCmdAndCheckOutput(
                    cmd(),
                    "/operator/config_import_keys_invalid_keys.json",
                    tmpOutputDirectoryPath,
                    "genesis.json",
                    false,
                    singletonList("key.pub")))
        .isInstanceOf(CommandLine.ExecutionException.class);
  }

  @Test(expected = CommandLine.ExecutionException.class)
  public void shouldFailIfOutputDirectoryNonEmpty() throws IOException {
    runCmdAndCheckOutput(
        cmd(),
        "/operator/config_generate_keys.json",
        FileSystems.getDefault().getPath("."),
        "genesis.json",
        true,
        asList("key.pub", "key.priv"));
  }

  private void runCmdAndCheckOutput(
      final Cmd cmd,
      final String configFile,
      final Path outputDirectoryPath,
      final String genesisFileName,
      final boolean generate,
      final Collection<String> expectedKeyFiles)
      throws IOException {
    final URL configFilePath = this.getClass().getResource(configFile);
    parseCommand(
        cmd(
                COMMAND_NAME,
                GENERATE_BLOCKCHAIN_CONFIG_SUBCOMMAND_NAME,
                "--config-file",
                configFilePath.getPath(),
                "--to",
                outputDirectoryPath.toString())
            .args(cmd.argsArray())
            .argsArray());
    assertThat(commandErrorOutput.toString()).isEmpty();

    final Path outputGenesisExpectedPath = outputDirectoryPath.resolve(genesisFileName);
    final File outputGenesisFile = new File(outputGenesisExpectedPath.toUri());
    assertThat(outputGenesisFile).withFailMessage("Output genesis file must exist.").exists();
    final String genesisString = contentOf(outputGenesisFile, UTF_8);
    final JsonObject genesisContent = new JsonObject(genesisString);
    assertThat(genesisContent.containsKey("extraData")).isTrue();

    final Path expectedKeysPath = outputDirectoryPath.resolve("keys");
    final File keysDirectory = new File(expectedKeysPath.toUri());
    assertThat(keysDirectory).exists();
    final File[] nodesKeysFolders = keysDirectory.listFiles();
    assert nodesKeysFolders != null;
    if (generate) {
      final JsonFactory jsonFactory = new JsonFactory();
      final JsonParser jp = jsonFactory.createParser(configFilePath);
      jp.setCodec(new ObjectMapper());
      final JsonNode jsonNode = jp.readValueAsTree();
      final int nodeCount = jsonNode.get("blockchain").get("nodes").get("count").asInt();
      assertThat(nodeCount).isEqualTo(nodesKeysFolders.length);
    }
    final Stream<File> nodesKeysFoldersStream = stream(nodesKeysFolders);

    nodesKeysFoldersStream.forEach(
        nodeFolder -> assertThat(nodeFolder.list()).containsAll(expectedKeyFiles));
  }

  static class Cmd {
    private List<String> args;

    private Cmd(final List<String> args) {
      this.args = args;
    }

    static Cmd cmd(final String... args) {
      return new Cmd(new ArrayList<>(asList(args)));
    }

    Cmd args(final String... args) {
      this.args.addAll(asList(args));
      return this;
    }

    String[] argsArray() {
      String[] wrapper = new String[] {};
      return args.toArray(wrapper);
    }
  }
}
