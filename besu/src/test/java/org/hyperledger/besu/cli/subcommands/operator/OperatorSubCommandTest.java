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
package org.hyperledger.besu.cli.subcommands.operator;

import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.createTempDirectory;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.contentOf;
import static org.hyperledger.besu.cli.subcommands.operator.OperatorSubCommandTest.Cmd.cmd;

import org.hyperledger.besu.BesuInfo;
import org.hyperledger.besu.cli.CommandTestAbstract;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine.Model.CommandSpec;

@ExtendWith(MockitoExtension.class)
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
          + "  generate-blockchain-config  Generate node keypairs and genesis file with RLP"
          + System.lineSeparator()
          + "                                encoded extra data."
          + System.lineSeparator()
          + "  generate-log-bloom-cache    Generate cached values of block log bloom filters.";

  private Path tmpOutputDirectoryPath;

  @BeforeEach
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
    String[] args =
        new String[] {"--private-key-file-name", "dup.test", "--public-key-file-name", "dup.test"};
    runCmdAndAssertErrorMsg(
        "/operator/config_generate_keys.json",
        tmpOutputDirectoryPath.toString(),
        args,
        "Output file paths must be unique.");
  }

  @Test
  public void shouldFailIfPublicKeysAreWrongType() {
    runCmdAndAssertErrorMsg(
        "/operator/config_import_keys_invalid_keys.json", "Invalid key json of type: OBJECT");
  }

  @Test
  public void shouldFailIfOutputDirectoryNonEmpty() throws IOException {
    runCmdAndAssertErrorMsg(
        "/operator/config_generate_keys.json",
        FileSystems.getDefault().getPath(".").toString(),
        new String[0],
        "Output directory already exists.");
  }

  @Test
  public void shouldFailIfInvalidEcCurveIsSet() {
    runCmdAndAssertErrorMsg(
        "/operator/config_generate_keys_ec_invalid.json",
        "Invalid parameter for ecCurve in genesis config: abcd is not in the list of valid elliptic curves [secp256k1, secp256r1]");
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
    runCmdAndAssertErrorMsg(
        "/operator/config_import_keys_secp256r1_invalid_keys.json",
        "0xb295c4242fb40c6e8ac7b831c916846050f191adc560b8098ba6ad513079571ec1be6e5e1a715857a13a91963097962e048c36c5863014b59e8f67ed3f667680 is not a valid public key for elliptic curve secp256r1");
  }

  @Test
  public void shouldFailIfNoConfigSection() {
    runCmdAndAssertErrorMsg(
        "/operator/config_no_config_section.json", "Missing config section in config file");
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
        List.of(
            new Field(
                "extraData",
                "0xf853a00000000000000000000000000000000000000000000000000000000000000000ea94d5feb0fc5a54a89f97aeb34c3df15397c19f6dd294d6a9a4c886eb008ac307abdc1f38745c1dd13a88808400000000c0")));
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
        List.of(
            new Field(
                "extraData",
                "0xf84fa00000000000000000000000000000000000000000000000000000000000000000ea94d5feb0fc5a54a89f97aeb34c3df15397c19f6dd294d6a9a4c886eb008ac307abdc1f38745c1dd13a88c080c0")));
  }

  @Test
  public void generatedGenesisFileShouldContainAllOriginalFieldsExcludingExtraData()
      throws IOException {
    final JsonObject alloc =
        new JsonObject(
            """
    {
      "24defc2d149861d3d245749b81fe0e6b28e04f31": {
        "balance": "0x446c3b15f9926687d2c40534fdb564000000000000"
      },
      "2a813d7db3de19b07f92268b6d4125ed295cbe00": {
        "balance": "0x446c3b15f9926687d2c40534fdb542000000000000"
      }
    }""");
    final List<Field> fields =
        List.of(
            new Field("nonce", "0x0"),
            new Field("timestamp", "0x5b3c3d18"),
            new Field("gasUsed", "0x0"),
            new Field(
                "parentHash", "0x0000000000000000000000000000000000000000000000000000000000000000"),
            new Field("gasLimit", "0x47b760"),
            new Field("difficulty", "0x1"),
            new Field(
                "mixHash", "0x63746963616c2062797a616e74696e65206661756c7420746f6c6572616e6365"),
            new Field("coinbase", "0x0000000000000000000000000000000000000000"),
            new Field("alloc", alloc.getMap().toString()));

    runCmdAndCheckOutput(
        cmd(),
        "/operator/config_generate_keys.json",
        tmpOutputDirectoryPath,
        "genesis.json",
        false,
        singletonList("key.pub"),
        Optional.empty(),
        fields);
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
        List.of());
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
        List.of());
  }

  private record Field(String key, String value) {}

  private void runCmdAndCheckOutput(
      final Cmd cmd,
      final String configFile,
      final Path outputDirectoryPath,
      final String genesisFileName,
      final boolean generate,
      final Collection<String> expectedKeyFiles,
      final Optional<SignatureAlgorithm> signatureAlgorithm,
      final List<Field> expectedFields)
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

    expectedFields.forEach(
        field -> assertThat(genesisContent.getString(field.key)).isEqualTo(field.value));

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

  private void runCmdAndAssertErrorMsg(final String configPath, final String expectedErrorMessage) {
    runCmdAndAssertErrorMsg(
        configPath, tmpOutputDirectoryPath.toString(), new String[0], expectedErrorMessage);
  }

  private void runCmdAndAssertErrorMsg(
      final String configPath,
      final String outPutDirectory,
      final String[] args,
      final String expectedErrorMessage) {
    final URL configFilePath = this.getClass().getResource(configPath);
    parseCommand(
        cmd(
                OperatorSubCommand.COMMAND_NAME,
                OperatorSubCommand.GENERATE_BLOCKCHAIN_CONFIG_SUBCOMMAND_NAME,
                "--config-file",
                configFilePath.getPath(),
                "--to",
                outPutDirectory)
            .args(cmd(args).argsArray())
            .argsArray());

    assertThat(commandErrorOutput.toString(UTF_8).trim()).endsWith(expectedErrorMessage);
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
