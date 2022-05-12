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
package org.hyperledger.besu.cli.operator;

import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.createTempDirectory;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.contentOf;
import static org.hyperledger.besu.cli.operator.OperatorSubCommandTest.Cmd.cmd;

import org.hyperledger.besu.BesuInfo;
import org.hyperledger.besu.cli.CommandTestAbstract;
import org.hyperledger.besu.cli.subcommands.operator.OperatorSubCommand;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.crypto.SECP256R1;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.json.JsonObject;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

@RunWith(MockitoJUnitRunner.class)
public class OperatorSubCommandTest extends CommandTestAbstract {

  private static final String EXPECTED_OPERATOR_USAGE =
      "Usage: besu operator [-hV] [COMMAND]"
          + System.lineSeparator()
          + "Operator related actions such as generating configuration and caches."
          + System.lineSeparator()
          + "  -h, --help      Show this help message and exit."
          + System.lineSeparator()
          + "  -V, --version   Print version information and exit."
          + System.lineSeparator()
          + "Commands:"
          + System.lineSeparator()
          + "  generate-blockchain-config  Generates node keypairs and genesis file with RLP"
          + System.lineSeparator()
          + "                                encoded extra data."
          + System.lineSeparator()
          + "  generate-log-bloom-cache    Generate cached values of block log bloom filters.";

  private Path tmpOutputDirectoryPath;

  @Before
  public void init() throws IOException {
    SignatureAlgorithmFactory.resetInstance();
    tmpOutputDirectoryPath = createTempDirectory(format("output-%d", currentTimeMillis()));
  }

  @Test
  public void operatorSubCommandExistAndHaveSubCommands() {
    final CommandSpec spec = parseCommand().getSpec();
    assertThat(spec.subcommands()).containsKeys(OperatorSubCommand.COMMAND_NAME);
    assertThat(spec.subcommands().get(OperatorSubCommand.COMMAND_NAME).getSubcommands())
        .containsKeys(OperatorSubCommand.GENERATE_BLOCKCHAIN_CONFIG_SUBCOMMAND_NAME);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingOperatorSubCommandWithoutSubSubcommandMustDisplayUsage() {
    parseCommand(OperatorSubCommand.COMMAND_NAME);
    assertThat(commandOutput.toString(UTF_8)).startsWith(EXPECTED_OPERATOR_USAGE);
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingOperatorCommandHelpMustDisplayUsage() {
    parseCommand(OperatorSubCommand.COMMAND_NAME, "--help");
    assertThat(commandOutput.toString(UTF_8)).startsWith(EXPECTED_OPERATOR_USAGE);
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingOperatorCommandVersionMustDisplayVersion() {
    parseCommand(OperatorSubCommand.COMMAND_NAME, "--version");
    assertThat(commandOutput.toString(UTF_8)).isEqualToIgnoringWhitespace(BesuInfo.version());
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingBackupStateCommandVersionMustDisplayVersion() {
    parseCommand("x-backup-state", "--version");
    assertThat(commandOutput.toString(UTF_8)).isEqualToIgnoringWhitespace(BesuInfo.version());
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingGenerateBlockchainConfigCommandVersionMustDisplayVersion() {
    parseCommand("generate-blockchain-config", "--version");
    assertThat(commandOutput.toString(UTF_8)).isEqualToIgnoringWhitespace(BesuInfo.version());
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingGenerateLogBloomCacheCommandVersionMustDisplayVersion() {
    parseCommand("generate-log-bloom-cache", "--version");
    assertThat(commandOutput.toString(UTF_8)).isEqualToIgnoringWhitespace(BesuInfo.version());
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingRestoreStateCommandVersionMustDisplayVersion() {
    parseCommand("x-restore-state", "--version");
    assertThat(commandOutput.toString(UTF_8)).isEqualToIgnoringWhitespace(BesuInfo.version());
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void generateBlockchainConfigMustGenerateKeysWhenGenerateIsTrue() throws IOException {
    runCmdAndCheckOutput(
        cmd(),
        "/operator/config_generate_keys.json",
        tmpOutputDirectoryPath,
        "genesis.json",
        true,
        asList("key.pub", "key.priv"),
        Optional.of(new SECP256K1()));
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
        asList("key.pub", "key.priv"),
        Optional.of(new SECP256K1()));
  }

  @Test
  public void publicKeyFileNameShouldBeEqualToOption() throws IOException {
    runCmdAndCheckOutput(
        cmd("--public-key-file-name", "pub.test"),
        "/operator/config_generate_keys.json",
        tmpOutputDirectoryPath,
        "genesis.json",
        true,
        asList("pub.test", "key.priv"),
        Optional.of(new SECP256K1()));
  }

  @Test
  public void privateKeyFileNameShouldBeEqualToOption() throws IOException {
    runCmdAndCheckOutput(
        cmd("--private-key-file-name", "priv.test"),
        "/operator/config_generate_keys.json",
        tmpOutputDirectoryPath,
        "genesis.json",
        true,
        asList("key.pub", "priv.test"),
        Optional.of(new SECP256K1()));
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

  @Test
  public void shouldFailIfOutputDirectoryNonEmpty() throws IOException {
    assertThatThrownBy(
            () ->
                runCmdAndCheckOutput(
                    cmd(),
                    "/operator/config_generate_keys.json",
                    FileSystems.getDefault().getPath("."),
                    "genesis.json",
                    true,
                    asList("key.pub", "key.priv")))
        .isInstanceOf(CommandLine.ExecutionException.class);
  }

  @Test
  public void shouldFailIfInvalidEcCurveIsSet() {
    assertThatThrownBy(
            () ->
                runCmdAndCheckOutput(
                    cmd(),
                    "/operator/config_generate_keys_ec_invalid.json",
                    tmpOutputDirectoryPath,
                    "genesis.json",
                    true,
                    asList("key.pub", "priv.test")))
        .isInstanceOf(CommandLine.ExecutionException.class);
  }

  @Test
  public void shouldGenerateSECP256R1KeysWhenSetAsEcCurve() throws IOException {
    runCmdAndCheckOutput(
        cmd(),
        "/operator/config_generate_keys_secp256r1.json",
        tmpOutputDirectoryPath,
        "genesis.json",
        true,
        asList("key.pub", "key.priv"),
        Optional.of(new SECP256R1()));
  }

  @Test
  public void shouldFailIfImportedKeysAreFromDifferentEllipticCurve() {
    assertThatThrownBy(
            () ->
                runCmdAndCheckOutput(
                    cmd(),
                    "/operator/config_import_keys_secp256r1_invalid_keys.json",
                    tmpOutputDirectoryPath,
                    "genesis.json",
                    true,
                    asList("key.pub", "key.priv")))
        .isInstanceOf(CommandLine.ExecutionException.class)
        .hasMessageEndingWith(
            "0xb295c4242fb40c6e8ac7b831c916846050f191adc560b8098ba6ad513079571ec1be6e5e1a715857a13a91963097962e048c36c5863014b59e8f67ed3f667680 is not a valid public key for elliptic curve secp256r1");
  }

  @Test
  public void shouldFailIfNoConfigSection() {
    assertThatThrownBy(
            () ->
                runCmdAndCheckOutput(
                    cmd(),
                    "/operator/config_no_config_section.json",
                    tmpOutputDirectoryPath,
                    "genesis.json",
                    true,
                    asList("key.pub", "key.priv"),
                    Optional.of(new SECP256K1())))
        .isInstanceOf(CommandLine.ExecutionException.class)
        .hasMessageEndingWith("Missing config section in config file");
  }

  @Test
  public void shouldImportSecp256R1Keys() throws IOException {
    runCmdAndCheckOutput(
        cmd(),
        "/operator/config_import_keys_secp256r1.json",
        tmpOutputDirectoryPath,
        "genesis.json",
        false,
        singletonList("key.pub"));
  }

  @Test
  public void shouldCreateIbft2ExtraData() throws IOException {
    runCmdAndCheckOutput(
        cmd(),
        "/operator/config_import_keys.json",
        tmpOutputDirectoryPath,
        "genesis.json",
        false,
        singletonList("key.pub"),
        Optional.empty(),
        Optional.of(
            "0xf853a00000000000000000000000000000000000000000000000000000000000000000ea94d5feb0fc5a54a89f97aeb34c3df15397c19f6dd294d6a9a4c886eb008ac307abdc1f38745c1dd13a88808400000000c0"));
  }

  @Test
  public void shouldCreateQbftExtraData() throws IOException {
    runCmdAndCheckOutput(
        cmd(),
        "/operator/config_import_keys_qbft.json",
        tmpOutputDirectoryPath,
        "genesis.json",
        false,
        singletonList("key.pub"),
        Optional.empty(),
        Optional.of(
            "0xf84fa00000000000000000000000000000000000000000000000000000000000000000ea94d5feb0fc5a54a89f97aeb34c3df15397c19f6dd294d6a9a4c886eb008ac307abdc1f38745c1dd13a88c080c0"));
  }

  private void runCmdAndCheckOutput(
      final Cmd cmd,
      final String configFile,
      final Path outputDirectoryPath,
      final String genesisFileName,
      final boolean generate,
      final Collection<String> expectedKeyFiles)
      throws IOException {
    runCmdAndCheckOutput(
        cmd,
        configFile,
        outputDirectoryPath,
        genesisFileName,
        generate,
        expectedKeyFiles,
        Optional.empty(),
        Optional.empty());
  }

  private void runCmdAndCheckOutput(
      final Cmd cmd,
      final String configFile,
      final Path outputDirectoryPath,
      final String genesisFileName,
      final boolean generate,
      final Collection<String> expectedKeyFiles,
      final Optional<SignatureAlgorithm> signatureAlgorithm)
      throws IOException {
    runCmdAndCheckOutput(
        cmd,
        configFile,
        outputDirectoryPath,
        genesisFileName,
        generate,
        expectedKeyFiles,
        signatureAlgorithm,
        Optional.empty());
  }

  private void runCmdAndCheckOutput(
      final Cmd cmd,
      final String configFile,
      final Path outputDirectoryPath,
      final String genesisFileName,
      final boolean generate,
      final Collection<String> expectedKeyFiles,
      final Optional<SignatureAlgorithm> signatureAlgorithm,
      final Optional<String> expectedExtraData)
      throws IOException {
    final URL configFilePath = this.getClass().getResource(configFile);
    parseCommand(
        cmd(
                OperatorSubCommand.COMMAND_NAME,
                OperatorSubCommand.GENERATE_BLOCKCHAIN_CONFIG_SUBCOMMAND_NAME,
                "--config-file",
                configFilePath.getPath(),
                "--to",
                outputDirectoryPath.toString())
            .args(cmd.argsArray())
            .argsArray());
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

    final Path outputGenesisExpectedPath = outputDirectoryPath.resolve(genesisFileName);
    final File outputGenesisFile = new File(outputGenesisExpectedPath.toUri());
    assertThat(outputGenesisFile).withFailMessage("Output genesis file must exist.").exists();
    final String genesisString = contentOf(outputGenesisFile, UTF_8);
    final JsonObject genesisContent = new JsonObject(genesisString);
    assertThat(genesisContent.containsKey("extraData")).isTrue();
    expectedExtraData.ifPresent(
        extraData -> assertThat(genesisContent.getString("extraData")).isEqualTo(extraData));

    final Path expectedKeysPath = outputDirectoryPath.resolve("keys");
    final File keysDirectory = new File(expectedKeysPath.toUri());
    assertThat(keysDirectory).exists();
    final File[] nodesKeysFolders = keysDirectory.listFiles();
    assertThat(nodesKeysFolders).isNotNull();
    if (generate) {
      final JsonFactory jsonFactory = new JsonFactory();
      final JsonParser jp = jsonFactory.createParser(configFilePath);
      jp.setCodec(new ObjectMapper());
      final JsonNode jsonNode = jp.readValueAsTree();
      final int nodeCount = jsonNode.get("blockchain").get("nodes").get("count").asInt();
      assertThat(nodeCount).isEqualTo(nodesKeysFolders.length);
    }

    for (File nodeFolder : nodesKeysFolders) {
      assertThat(nodeFolder.list()).containsAll(expectedKeyFiles);

      if (signatureAlgorithm.isPresent()) {
        checkPublicKey(nodeFolder, signatureAlgorithm.get());
      }
    }
  }

  private void checkPublicKey(final File dir, final SignatureAlgorithm signatureAlgorithm)
      throws IOException {
    String publicKeyHex = readPubFile(dir);
    String privateKeyHex = readPrivFile(dir);

    SECPPrivateKey privateKey =
        signatureAlgorithm.createPrivateKey(Bytes32.fromHexString(privateKeyHex));
    SECPPublicKey expectedPublicKey = signatureAlgorithm.createPublicKey(privateKey);

    assertThat(publicKeyHex).isEqualTo(expectedPublicKey.getEncodedBytes().toHexString());
  }

  private String readPubFile(final File dir) throws IOException {
    FilenameFilter pubFilter = (folder, name) -> name.contains("pub");

    return readFile(dir, pubFilter);
  }

  private String readPrivFile(final File dir) throws IOException {
    FilenameFilter privFilter = (folder, name) -> name.contains("priv");

    return readFile(dir, privFilter);
  }

  private String readFile(final File dir, final FilenameFilter fileFilter) throws IOException {
    File[] files = dir.listFiles(fileFilter);

    assertThat(files).isNotNull();
    assertThat(files.length).isEqualTo(1);

    return Files.readString(Path.of(files[0].getAbsolutePath()));
  }

  static class Cmd {
    private final List<String> args;

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
      final String[] wrapper = new String[] {};
      return args.toArray(wrapper);
    }
  }
}
