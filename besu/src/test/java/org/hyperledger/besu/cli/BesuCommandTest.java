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
package org.hyperledger.besu.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.hyperledger.besu.cli.config.NetworkName.CLASSIC;
import static org.hyperledger.besu.cli.config.NetworkName.DEV;
import static org.hyperledger.besu.cli.config.NetworkName.EXPERIMENTAL_EIPS;
import static org.hyperledger.besu.cli.config.NetworkName.FUTURE_EIPS;
import static org.hyperledger.besu.cli.config.NetworkName.GOERLI;
import static org.hyperledger.besu.cli.config.NetworkName.HOLESKY;
import static org.hyperledger.besu.cli.config.NetworkName.MAINNET;
import static org.hyperledger.besu.cli.config.NetworkName.MORDOR;
import static org.hyperledger.besu.cli.config.NetworkName.SEPOLIA;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis.ENGINE;
import static org.hyperledger.besu.ethereum.p2p.config.DefaultDiscoveryConfiguration.GOERLI_BOOTSTRAP_NODES;
import static org.hyperledger.besu.ethereum.p2p.config.DefaultDiscoveryConfiguration.GOERLI_DISCOVERY_URL;
import static org.hyperledger.besu.ethereum.p2p.config.DefaultDiscoveryConfiguration.MAINNET_BOOTSTRAP_NODES;
import static org.hyperledger.besu.ethereum.p2p.config.DefaultDiscoveryConfiguration.MAINNET_DISCOVERY_URL;
import static org.hyperledger.besu.ethereum.worldstate.DataStorageFormat.BONSAI;
import static org.hyperledger.besu.nat.kubernetes.KubernetesNatManager.DEFAULT_BESU_SERVICE_NAME_FILTER;
import static org.junit.Assume.assumeThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.BesuInfo;
import org.hyperledger.besu.cli.config.EthNetworkConfig;
import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.MergeConfigOptions;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.api.ApiConfiguration;
import org.hyperledger.besu.ethereum.api.ImmutableApiConfiguration;
import org.hyperledger.besu.ethereum.api.graphql.GraphQLConfiguration;
import org.hyperledger.besu.ethereum.api.handlers.TimeoutOptions;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.sync.SyncMode;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.trie.forest.pruner.PrunerConfiguration;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.evm.precompile.AbstractAltBnPrecompiledContract;
import org.hyperledger.besu.evm.precompile.KZGPointEvalPrecompiledContract;
import org.hyperledger.besu.metrics.StandardMetricCategory;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;
import org.hyperledger.besu.nat.NatMethod;
import org.hyperledger.besu.pki.config.PkiKeyStoreConfiguration;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.plugin.services.privacy.PrivateMarkerTransactionFactory;
import org.hyperledger.besu.util.number.Fraction;
import org.hyperledger.besu.util.number.Percentage;
import org.hyperledger.besu.util.number.PositiveNumber;
import org.hyperledger.besu.util.platform.PlatformDetector;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.io.Resources;
import io.vertx.core.json.JsonObject;
import org.apache.commons.io.FileUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.toml.Toml;
import org.apache.tuweni.toml.TomlParseResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

@ExtendWith(MockitoExtension.class)
public class BesuCommandTest extends CommandTestAbstract {

  private static final String ENCLAVE_URI = "http://1.2.3.4:5555";
  private static final String ENCLAVE_PUBLIC_KEY = "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";
  private static final String VALID_NODE_ID =
      "6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0";
  private static final JsonRpcConfiguration DEFAULT_JSON_RPC_CONFIGURATION;
  private static final GraphQLConfiguration DEFAULT_GRAPH_QL_CONFIGURATION;
  private static final WebSocketConfiguration DEFAULT_WEB_SOCKET_CONFIGURATION;
  private static final MetricsConfiguration DEFAULT_METRICS_CONFIGURATION;
  private static final ApiConfiguration DEFAULT_API_CONFIGURATION;

  private static final int GENESIS_CONFIG_TEST_CHAINID = 3141592;
  private static final JsonObject GENESIS_VALID_JSON =
      (new JsonObject())
          .put("config", (new JsonObject()).put("chainId", GENESIS_CONFIG_TEST_CHAINID));
  private static final JsonObject GENESIS_INVALID_DATA =
      (new JsonObject()).put("config", new JsonObject());
  private static final JsonObject INVALID_GENESIS_EC_CURVE =
      (new JsonObject()).put("config", new JsonObject().put("ecCurve", "abcd"));
  private static final JsonObject VALID_GENESIS_EC_CURVE =
      (new JsonObject()).put("config", new JsonObject().put("ecCurve", "secp256k1"));
  private static final String ENCLAVE_PUBLIC_KEY_PATH =
      BesuCommand.class.getResource("/orion_publickey.pub").getPath();

  private static final String[] VALID_ENODE_STRINGS = {
    "enode://" + VALID_NODE_ID + "@192.168.0.1:4567",
    "enode://" + VALID_NODE_ID + "@192.168.0.2:4567",
    "enode://" + VALID_NODE_ID + "@192.168.0.3:4567"
  };
  private static final String DNS_DISCOVERY_URL =
      "enrtree://AM5FCQLWIZX2QFPNJAP7VUERCCRNGRHWZG3YYHIUV7BVDQ5FDPRT2@nodes.example.org";
  private static final JsonObject VALID_GENESIS_WITH_DISCOVERY_OPTIONS =
      new JsonObject()
          .put(
              "config",
              new JsonObject()
                  .put(
                      "discovery",
                      new JsonObject()
                          .put("bootnodes", List.of(VALID_ENODE_STRINGS))
                          .put("dns", DNS_DISCOVERY_URL)));

  private static final JsonObject GENESIS_WITH_DATA_BLOBS_ENABLED =
      new JsonObject().put("config", new JsonObject().put("cancunTime", 1L));

  private static final JsonObject GENESIS_WITH_ZERO_BASE_FEE_MARKET =
      new JsonObject().put("config", new JsonObject().put("zeroBaseFee", true));

  static {
    DEFAULT_JSON_RPC_CONFIGURATION = JsonRpcConfiguration.createDefault();
    DEFAULT_GRAPH_QL_CONFIGURATION = GraphQLConfiguration.createDefault();
    DEFAULT_WEB_SOCKET_CONFIGURATION = WebSocketConfiguration.createDefault();
    DEFAULT_METRICS_CONFIGURATION = MetricsConfiguration.builder().build();
    DEFAULT_API_CONFIGURATION = ImmutableApiConfiguration.builder().build();
  }

  @BeforeEach
  public void setup() {
    try {
      // optimistically tear down a potential previous loaded trusted setup
      KZGPointEvalPrecompiledContract.tearDown();
    } catch (Throwable ignore) {
      // and ignore errors in case no trusted setup was already loaded
    }

    MergeConfigOptions.setMergeEnabled(false);
  }

  @AfterEach
  public void tearDown() {

    MergeConfigOptions.setMergeEnabled(false);
  }

  @Test
  public void callingHelpSubCommandMustDisplayUsage() {
    parseCommand("--help");
    final String expectedOutputStart = String.format("Usage:%n%nbesu [OPTIONS] [COMMAND]");
    assertThat(commandOutput.toString(UTF_8)).startsWith(expectedOutputStart);
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingHelpDisplaysDefaultRpcApisCorrectly() {
    parseCommand("--help");
    assertThat(commandOutput.toString(UTF_8)).contains("default: [ETH, NET, WEB3]");
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingVersionDisplayBesuInfoVersion() {
    parseCommand("--version");
    assertThat(commandOutput.toString(UTF_8)).isEqualToIgnoringWhitespace(BesuInfo.version());
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  // Testing default values
  @Test
  public void callingBesuCommandWithoutOptionsMustSyncWithDefaultValues() {
    parseCommand();

    final int maxPeers = 25;

    final ArgumentCaptor<EthNetworkConfig> ethNetworkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);
    verify(mockRunnerBuilder).discovery(eq(true));
    verify(mockRunnerBuilder)
        .ethNetworkConfig(
            new EthNetworkConfig(
                EthNetworkConfig.jsonConfig(MAINNET),
                MAINNET.getNetworkId(),
                MAINNET_BOOTSTRAP_NODES,
                MAINNET_DISCOVERY_URL));
    verify(mockRunnerBuilder).p2pAdvertisedHost(eq("127.0.0.1"));
    verify(mockRunnerBuilder).p2pListenPort(eq(30303));
    verify(mockRunnerBuilder).jsonRpcConfiguration(eq(DEFAULT_JSON_RPC_CONFIGURATION));
    verify(mockRunnerBuilder).graphQLConfiguration(eq(DEFAULT_GRAPH_QL_CONFIGURATION));
    verify(mockRunnerBuilder).webSocketConfiguration(eq(DEFAULT_WEB_SOCKET_CONFIGURATION));
    verify(mockRunnerBuilder).metricsConfiguration(eq(DEFAULT_METRICS_CONFIGURATION));
    verify(mockRunnerBuilder).ethNetworkConfig(ethNetworkArg.capture());
    verify(mockRunnerBuilder).autoLogBloomCaching(eq(true));
    verify(mockRunnerBuilder).apiConfiguration(DEFAULT_API_CONFIGURATION);
    verify(mockRunnerBuilder).build();

    verify(mockControllerBuilderFactory)
        .fromEthNetworkConfig(ethNetworkArg.capture(), any(), any());
    final ArgumentCaptor<MiningParameters> miningArg =
        ArgumentCaptor.forClass(MiningParameters.class);
    verify(mockControllerBuilder).synchronizerConfiguration(syncConfigurationCaptor.capture());
    verify(mockControllerBuilder).dataDirectory(isNotNull());
    verify(mockControllerBuilder).miningParameters(miningArg.capture());
    verify(mockControllerBuilder).nodeKey(isNotNull());
    verify(mockControllerBuilder).storageProvider(storageProviderArgumentCaptor.capture());
    verify(mockControllerBuilder).gasLimitCalculator(eq(GasLimitCalculator.constant()));
    verify(mockControllerBuilder).maxPeers(eq(maxPeers));
    verify(mockControllerBuilder).maxRemotelyInitiatedPeers(eq((int) Math.floor(0.6 * maxPeers)));
    verify(mockControllerBuilder).build();

    assertThat(storageProviderArgumentCaptor.getValue()).isNotNull();
    assertThat(syncConfigurationCaptor.getValue().getSyncMode()).isEqualTo(SyncMode.SNAP);
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
    assertThat(miningArg.getValue().getCoinbase()).isEqualTo(Optional.empty());
    assertThat(miningArg.getValue().getMinTransactionGasPrice()).isEqualTo(Wei.of(1000));
    assertThat(miningArg.getValue().getExtraData()).isEqualTo(Bytes.EMPTY);
    assertThat(ethNetworkArg.getValue().getNetworkId()).isEqualTo(1);
    assertThat(ethNetworkArg.getValue().getBootNodes()).isEqualTo(MAINNET_BOOTSTRAP_NODES);
  }

  // Testing each option
  @Test
  public void callingWithConfigOptionButNoConfigFileShouldDisplayHelp() {
    parseCommand("--config-file");

    final String expectedOutputStart =
        "Missing required parameter for option '--config-file' (<FILE>)";
    assertThat(commandErrorOutput.toString(UTF_8)).startsWith(expectedOutputStart);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingWithConfigOptionButNonExistingFileShouldDisplayHelp() throws IOException {
    final Path tempConfigFilePath = createTempFile("an-invalid-file-name-without-extension", "");
    parseCommand("--config-file", tempConfigFilePath.toString());

    final String expectedOutputStart = "Unable to read TOML configuration file";
    assertThat(commandErrorOutput.toString(UTF_8)).startsWith(expectedOutputStart);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingWithConfigOptionButTomlFileNotFoundShouldDisplayHelp() {
    String invalidFile = "./an-invalid-file-name-sdsd87sjhqoi34io23.toml";
    parseCommand("--config-file", invalidFile);

    final String expectedOutputStart =
        String.format("Unable to read TOML configuration, file not found: %s", invalidFile);
    assertThat(commandErrorOutput.toString(UTF_8)).startsWith(expectedOutputStart);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingWithConfigOptionButInvalidContentTomlFileShouldDisplayHelp() throws Exception {
    // We write a config file to prevent an invalid file in resource folder to raise errors in
    // code checks (CI + IDE)
    final Path tempConfigFile = createTempFile("invalid_config.toml", ".");

    parseCommand("--config-file", tempConfigFile.toString());

    final String expectedOutputStart =
        "Invalid TOML configuration: org.apache.tuweni.toml.TomlParseError: Unexpected '.', expected a-z, A-Z, 0-9, ', \", a table key, "
            + "a newline, or end-of-input (line 1, column 1)";
    assertThat(commandErrorOutput.toString(UTF_8)).startsWith(expectedOutputStart);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingWithNoBootnodesConfig() throws Exception {
    final URL configFile = this.getClass().getResource("/no_bootnodes.toml");
    final Path toml = createTempFile("toml", Resources.toString(configFile, UTF_8));

    parseCommand("--config-file", toml.toAbsolutePath().toString());

    verify(mockRunnerBuilder).ethNetworkConfig(ethNetworkConfigArgumentCaptor.capture());
    assertThat(ethNetworkConfigArgumentCaptor.getValue().getBootNodes()).isEmpty();

    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingWithConfigOptionButInvalidValueTomlFileShouldDisplayHelp() throws Exception {
    // We write a config file to prevent an invalid file in resource folder to raise errors in
    // code checks (CI + IDE)
    final Path tempConfigFile = createTempFile("invalid_config.toml", "tester===========.......");
    parseCommand("--config-file", tempConfigFile.toString());

    final String expectedOutputStart =
        "Invalid TOML configuration: org.apache.tuweni.toml.TomlParseError: Unexpected '=', expected ', \", ''', \"\"\", a number, "
            + "a boolean, a date/time, an array, or a table (line 1, column 8)";
    assertThat(commandErrorOutput.toString(UTF_8)).startsWith(expectedOutputStart);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void tomlThatConfiguresEverythingExceptPermissioningToml() throws IOException {
    // Load a TOML that configures literally everything (except permissioning TOML config)
    final URL configFile = this.getClass().getResource("/everything_config.toml");
    final Path toml = createTempFile("toml", Resources.toByteArray(configFile));

    // Parse it.
    final CommandLine.Model.CommandSpec spec = parseCommand("--config-file", toml.toString()).spec;
    final TomlParseResult tomlResult = Toml.parse(toml);

    // Verify we configured everything
    final HashSet<CommandLine.Model.OptionSpec> options = new HashSet<>(spec.options());

    // Except for meta-options
    options.remove(spec.optionsMap().get("--config-file"));
    options.remove(spec.optionsMap().get("--help"));
    options.remove(spec.optionsMap().get("--version"));

    for (final String tomlKey : tomlResult.keySet()) {
      final CommandLine.Model.OptionSpec optionSpec = spec.optionsMap().get("--" + tomlKey);
      assertThat(optionSpec)
          .describedAs("Option '%s' should be a configurable option.", tomlKey)
          .isNotNull();
      // Verify TOML stores it by the appropriate type
      if (optionSpec.type().equals(Boolean.class)) {
        tomlResult.getBoolean(tomlKey);
      } else if (optionSpec.isMultiValue() || optionSpec.arity().max > 1) {
        tomlResult.getArray(tomlKey);
      } else if (optionSpec.type().equals(Double.class)) {
        tomlResult.getDouble(tomlKey);
      } else if (optionSpec.type().equals(Float.class)) {
        tomlResult.getDouble(tomlKey);
      } else if (Number.class.isAssignableFrom(optionSpec.type())) {
        tomlResult.getLong(tomlKey);
      } else if (Wei.class.isAssignableFrom(optionSpec.type())) {
        tomlResult.getLong(tomlKey);
      } else if (Fraction.class.isAssignableFrom(optionSpec.type())) {
        tomlResult.getDouble(tomlKey);
      } else if (Percentage.class.isAssignableFrom(optionSpec.type())) {
        tomlResult.getLong(tomlKey);
      } else if (PositiveNumber.class.isAssignableFrom(optionSpec.type())) {
        tomlResult.getLong(tomlKey);
      } else {
        tomlResult.getString(tomlKey);
      }
      options.remove(optionSpec);
    }
    assertThat(
            options.stream()
                .filter(optionSpec -> !optionSpec.hidden())
                .map(CommandLine.Model.OptionSpec::longestName))
        .isEmpty();
  }

  @Test
  public void nodekeyOptionMustBeUsed() throws Exception {
    final File file = new File("./specific/enclavePrivateKey");

    parseCommand("--node-private-key-file", file.getPath());

    verify(mockControllerBuilder).dataDirectory(isNotNull());
    verify(mockControllerBuilder).nodeKey(isNotNull());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void dataDirOptionMustBeUsed() {
    final Path path = Paths.get(".");

    parseCommand("--data-path", path.toString());

    verify(mockControllerBuilder).dataDirectory(pathArgumentCaptor.capture());
    verify(mockControllerBuilder).nodeKey(isNotNull());
    verify(mockControllerBuilder).build();

    assertThat(pathArgumentCaptor.getValue()).isEqualByComparingTo(path.toAbsolutePath());

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void genesisPathOptionMustBeUsed() throws Exception {
    final Path genesisFile = createFakeGenesisFile(GENESIS_VALID_JSON);
    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    parseCommand("--genesis-file", genesisFile.toString());

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue().getGenesisConfig())
        .isEqualTo(encodeJsonGenesis(GENESIS_VALID_JSON));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void testGenesisPathEthOptions() throws Exception {
    final Path genesisFile = createFakeGenesisFile(GENESIS_VALID_JSON);

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    parseCommand("--genesis-file", genesisFile.toString());

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any(), any());
    verify(mockControllerBuilder).build();

    final EthNetworkConfig config = networkArg.getValue();
    assertThat(config.getBootNodes()).isEmpty();
    assertThat(config.getDnsDiscoveryUrl()).isNull();
    assertThat(config.getNetworkId()).isEqualTo(BigInteger.valueOf(3141592));
  }

  @Test
  public void testGenesisPathMainnetEthConfig() throws Exception {
    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    parseCommand("--network", "mainnet");

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any(), any());
    verify(mockControllerBuilder).build();

    final EthNetworkConfig config = networkArg.getValue();
    assertThat(config.getBootNodes()).isEqualTo(MAINNET_BOOTSTRAP_NODES);
    assertThat(config.getDnsDiscoveryUrl()).isEqualTo(MAINNET_DISCOVERY_URL);
    assertThat(config.getNetworkId()).isEqualTo(BigInteger.valueOf(1));

    verify(mockLogger, never()).warn(contains("Mainnet is deprecated and will be shutdown"));
  }

  @Test
  public void testGenesisPathGoerliEthConfig() {
    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    parseCommand("--network", "goerli");

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any(), any());
    verify(mockControllerBuilder).build();

    final EthNetworkConfig config = networkArg.getValue();
    assertThat(config.getBootNodes()).isEqualTo(GOERLI_BOOTSTRAP_NODES);
    assertThat(config.getDnsDiscoveryUrl()).isEqualTo(GOERLI_DISCOVERY_URL);
    assertThat(config.getNetworkId()).isEqualTo(BigInteger.valueOf(5));
  }

  @Test
  public void testGenesisPathFutureEipsEthConfig() {
    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    parseCommand("--network", "future_eips");

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any(), any());
    verify(mockControllerBuilder).build();

    final EthNetworkConfig config = networkArg.getValue();
    assertThat(config.getBootNodes()).isEmpty();
    assertThat(config.getDnsDiscoveryUrl()).isNull();
    assertThat(config.getNetworkId()).isEqualTo(BigInteger.valueOf(2022));
  }

  @Test
  public void testGenesisPathExperimentalEipsEthConfig() {
    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    parseCommand("--network", "experimental_eips");

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any(), any());
    verify(mockControllerBuilder).build();

    final EthNetworkConfig config = networkArg.getValue();
    assertThat(config.getBootNodes()).isEmpty();
    assertThat(config.getDnsDiscoveryUrl()).isNull();
    assertThat(config.getNetworkId()).isEqualTo(BigInteger.valueOf(2023));
  }

  @Test
  public void genesisAndNetworkMustNotBeUsedTogether() throws Exception {
    final Path genesisFile = createFakeGenesisFile(GENESIS_VALID_JSON);

    parseCommand("--genesis-file", genesisFile.toString(), "--network", "mainnet");

    Mockito.verifyNoInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith("--network option and --genesis-file option can't be used at the same time.");
  }

  @Test
  public void nonExistentGenesisGivesError() throws Exception {
    final String nonExistentGenesis = "non-existent-genesis.json";
    parseCommand("--genesis-file", nonExistentGenesis);

    Mockito.verifyNoInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).startsWith("Unable to load genesis file");
    assertThat(commandErrorOutput.toString(UTF_8)).contains(nonExistentGenesis);
  }

  @Test
  public void testDnsDiscoveryUrlEthConfig() throws Exception {
    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    parseCommand(
        "--discovery-dns-url",
        "enrtree://AM5FCQLWIZX2QFPNJAP7VUERCCRNGRHWZG3YYHIUV7BVDQ5FDPRT2@nodes.example.org");

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any(), any());
    verify(mockControllerBuilder).build();

    final EthNetworkConfig config = networkArg.getValue();
    assertThat(config.getDnsDiscoveryUrl())
        .isEqualTo(
            "enrtree://AM5FCQLWIZX2QFPNJAP7VUERCCRNGRHWZG3YYHIUV7BVDQ5FDPRT2@nodes.example.org");
  }

  @Test
  public void testDnsDiscoveryUrlOverridesNetworkEthConfig() throws Exception {
    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    parseCommand(
        "--network",
        "dev",
        "--discovery-dns-url",
        "enrtree://AM5FCQLWIZX2QFPNJAP7VUERCCRNGRHWZG3YYHIUV7BVDQ5FDPRT2@nodes.example.org");

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any(), any());
    verify(mockControllerBuilder).build();

    final EthNetworkConfig config = networkArg.getValue();
    assertThat(config.getDnsDiscoveryUrl())
        .isEqualTo(
            "enrtree://AM5FCQLWIZX2QFPNJAP7VUERCCRNGRHWZG3YYHIUV7BVDQ5FDPRT2@nodes.example.org");
  }

  @Test
  public void defaultNetworkIdAndBootnodesForCustomNetworkOptions() throws Exception {
    final Path genesisFile = createFakeGenesisFile(GENESIS_VALID_JSON);

    parseCommand("--genesis-file", genesisFile.toString());

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue().getGenesisConfig())
        .isEqualTo(encodeJsonGenesis(GENESIS_VALID_JSON));
    assertThat(networkArg.getValue().getBootNodes()).isEmpty();
    assertThat(networkArg.getValue().getNetworkId()).isEqualTo(GENESIS_CONFIG_TEST_CHAINID);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void defaultNetworkIdForInvalidGenesisMustBeMainnetNetworkId() throws Exception {
    final Path genesisFile = createFakeGenesisFile(GENESIS_INVALID_DATA);

    parseCommand("--genesis-file", genesisFile.toString());

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue().getGenesisConfig())
        .isEqualTo(encodeJsonGenesis(GENESIS_INVALID_DATA));

    //    assertThat(networkArg.getValue().getNetworkId())
    //        .isEqualTo(EthNetworkConfig.getNetworkConfig(MAINNET).getNetworkId());

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void predefinedNetworkIdsMustBeEqualToChainIds() {
    // check the network id against the one in mainnet genesis config
    // it implies that EthNetworkConfig.mainnet().getNetworkId() returns a value equals to the chain
    // id
    // in this network genesis file.

    final GenesisConfigFile genesisConfigFile =
        GenesisConfigFile.fromConfig(EthNetworkConfig.getNetworkConfig(MAINNET).getGenesisConfig());
    assertThat(genesisConfigFile.getConfigOptions().getChainId().isPresent()).isTrue();
    assertThat(genesisConfigFile.getConfigOptions().getChainId().get())
        .isEqualTo(EthNetworkConfig.getNetworkConfig(MAINNET).getNetworkId());
  }

  @Test
  public void identityValueTrueMustBeUsed() {
    parseCommand("--identity", "test");

    verify(mockRunnerBuilder).identityString(eq(Optional.of("test")));
    verify(mockRunnerBuilder).build();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void p2pEnabledOptionValueTrueMustBeUsed() {
    parseCommand("--p2p-enabled", "true");

    verify(mockRunnerBuilder).p2pEnabled(eq(true));
    verify(mockRunnerBuilder).build();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void p2pEnabledOptionValueFalseMustBeUsed() {
    parseCommand("--p2p-enabled", "false");

    verify(mockRunnerBuilder).p2pEnabled(eq(false));
    verify(mockRunnerBuilder).build();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void p2pOptionsRequiresServiceToBeEnabled() {
    final String[] nodes = {
      "6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0"
    };

    parseCommand(
        "--p2p-enabled",
        "false",
        "--bootnodes",
        String.join(",", VALID_ENODE_STRINGS),
        "--discovery-enabled",
        "false",
        "--max-peers",
        "42",
        "--remote-connections-max-percentage",
        "50",
        "--banned-node-id",
        String.join(",", nodes),
        "--banned-node-ids",
        String.join(",", nodes));

    verifyOptionsConstraintLoggerCall(
        "--p2p-enabled",
        "--discovery-enabled",
        "--bootnodes",
        "--max-peers",
        "--banned-node-ids",
        "--remote-connections-max-percentage");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void p2pOptionsRequiresServiceToBeEnabledToml() throws IOException {
    final String[] nodes = {
      "6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0"
    };

    final Path toml =
        createTempFile(
            "toml",
            "p2p-enabled=false\n"
                + "bootnodes=[\""
                + String.join("\",\"", VALID_ENODE_STRINGS)
                + "\"]\n"
                + "discovery-enabled=false\n"
                + "max-peers=42\n"
                + "remote-connections-max-percentage=50\n"
                + "banned-node-id=[\""
                + String.join(",", nodes)
                + "\"]\n"
                + "banned-node-ids=[\""
                + String.join(",", nodes)
                + "\"]\n");

    parseCommand("--config-file", toml.toString());

    verifyOptionsConstraintLoggerCall(
        "--p2p-enabled",
        "--discovery-enabled",
        "--bootnodes",
        "--max-peers",
        "--banned-node-ids",
        "--remote-connections-max-percentage");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void discoveryOptionValueTrueMustBeUsed() {
    parseCommand("--discovery-enabled", "true");

    verify(mockRunnerBuilder).discovery(eq(true));
    verify(mockRunnerBuilder).build();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void discoveryOptionValueFalseMustBeUsed() {
    parseCommand("--discovery-enabled", "false");

    verify(mockRunnerBuilder).discovery(eq(false));
    verify(mockRunnerBuilder).build();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void loadDiscoveryOptionsFromGenesisFile() throws IOException {
    final Path genesisFile = createFakeGenesisFile(VALID_GENESIS_WITH_DISCOVERY_OPTIONS);
    parseCommand("--genesis-file", genesisFile.toString());

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any(), any());
    verify(mockControllerBuilder).build();

    final EthNetworkConfig config = networkArg.getValue();
    assertThat(config.getDnsDiscoveryUrl()).isEqualTo(DNS_DISCOVERY_URL);

    assertThat(config.getBootNodes())
        .extracting(bootnode -> bootnode.toURI().toString())
        .containsExactly(VALID_ENODE_STRINGS);
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void discoveryDnsUrlCliArgTakesPrecedenceOverGenesisFile() throws IOException {
    final Path genesisFile = createFakeGenesisFile(VALID_GENESIS_WITH_DISCOVERY_OPTIONS);
    final String discoveryDnsUrlCliArg =
        "enrtree://XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX@nodes.example.org";
    parseCommand(
        "--genesis-file", genesisFile.toString(), "--discovery-dns-url", discoveryDnsUrlCliArg);

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);
    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any(), any());
    verify(mockControllerBuilder).build();

    final EthNetworkConfig config = networkArg.getValue();
    assertThat(config.getDnsDiscoveryUrl()).isEqualTo(discoveryDnsUrlCliArg);
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void bootnodesUrlCliArgTakesPrecedenceOverGenesisFile() throws IOException {
    final Path genesisFile = createFakeGenesisFile(VALID_GENESIS_WITH_DISCOVERY_OPTIONS);
    final URI bootnode =
        URI.create(
            "enode://d2567893371ea5a6fa6371d483891ed0d129e79a8fc74d6df95a00a6545444cd4a6960bbffe0b4e2edcf35135271de57ee559c0909236bbc2074346ef2b5b47c@127.0.0.1:30304");

    parseCommand("--genesis-file", genesisFile.toString(), "--bootnodes", bootnode.toString());

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);
    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any(), any());
    verify(mockControllerBuilder).build();

    final EthNetworkConfig config = networkArg.getValue();
    assertThat(config.getBootNodes()).extracting(EnodeURL::toURI).containsExactly(bootnode);

    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingWithBootnodesOptionButNoValueMustPassEmptyBootnodeList() {
    parseCommand("--bootnodes");

    verify(mockRunnerBuilder).ethNetworkConfig(ethNetworkConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(ethNetworkConfigArgumentCaptor.getValue().getBootNodes()).isEmpty();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingWithValidBootnodeMustSucceed() {
    parseCommand(
        "--bootnodes",
        "enode://d2567893371ea5a6fa6371d483891ed0d129e79a8fc74d6df95a00a6545444cd4a6960bbffe0b4e2edcf35135271de57ee559c0909236bbc2074346ef2b5b47c@127.0.0.1:30304");
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingWithValidBootnodeButDiscoveryDisabledMustDisplayWarning() {
    parseCommand(
        "--bootnodes",
        "enode://d2567893371ea5a6fa6371d483891ed0d129e79a8fc74d6df95a00a6545444cd4a6960bbffe0b4e2edcf35135271de57ee559c0909236bbc2074346ef2b5b47c@127.0.0.1:30304",
        "--discovery-enabled",
        "false");
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    verify(mockRunnerBuilder).build();
    verify(mockLogger, atLeast(1)).warn("Discovery disabled: bootnodes will be ignored.");
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingWithInvalidBootnodeMustDisplayError() {
    parseCommand("--bootnodes", "invalid_enode_url");
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    final String expectedErrorOutputStart =
        "Invalid enode URL syntax 'invalid_enode_url'. Enode URL should have the following format "
            + "'enode://<node_id>@<ip>:<listening_port>[?discport=<discovery_port>]'.";
    assertThat(commandErrorOutput.toString(UTF_8)).startsWith(expectedErrorOutputStart);
  }

  @Test
  public void callingWithBootnodeThatHasDiscoveryDisabledMustDisplayError() {
    final String validBootnode =
        "enode://d2567893371ea5a6fa6371d483891ed0d129e79a8fc74d6df95a00a6545444cd4a6960bbffe0b4e2edcf35135271de57ee559c0909236bbc2074346ef2b5b47c@127.0.0.1:30304";
    final String invalidBootnode =
        "enode://02567893371ea5a6fa6371d483891ed0d129e79a8fc74d6df95a00a6545444cd4a6960bbffe0b4e2edcf35135271de57ee559c0909236bbc2074346ef2b5b47c@127.0.0.1:30303?discport=0";
    final String bootnodesValue = validBootnode + "," + invalidBootnode;
    parseCommand("--bootnodes", bootnodesValue);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    final String expectedErrorOutputStart =
        "Bootnodes must have discovery enabled. Invalid bootnodes: " + invalidBootnode + ".";
    assertThat(commandErrorOutput.toString(UTF_8)).startsWith(expectedErrorOutputStart);
  }

  // This test ensures non regression on https://pegasys1.atlassian.net/browse/PAN-2387
  @Test
  public void callingWithInvalidBootnodeAndEqualSignMustDisplayError() {
    parseCommand("--bootnodes=invalid_enode_url");
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    final String expectedErrorOutputStart =
        "Invalid enode URL syntax 'invalid_enode_url'. Enode URL should have the following format "
            + "'enode://<node_id>@<ip>:<listening_port>[?discport=<discovery_port>]'.";
    assertThat(commandErrorOutput.toString(UTF_8)).startsWith(expectedErrorOutputStart);
  }

  @Test
  public void bootnodesOptionMustBeUsed() {
    parseCommand("--bootnodes", String.join(",", VALID_ENODE_STRINGS));

    verify(mockRunnerBuilder).ethNetworkConfig(ethNetworkConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(ethNetworkConfigArgumentCaptor.getValue().getBootNodes())
        .isEqualTo(
            Stream.of(VALID_ENODE_STRINGS)
                .map(EnodeURLImpl::fromString)
                .collect(Collectors.toList()));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void bannedNodeIdsOptionMustBeUsed() {
    final Bytes[] nodes = {
      Bytes.fromHexString(
          "6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0"),
      Bytes.fromHexString(
          "7f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0"),
      Bytes.fromHexString(
          "0x8f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0")
    };

    final String nodeIdsArg =
        Arrays.stream(nodes).map(Bytes::toShortHexString).collect(Collectors.joining(","));
    parseCommand("--banned-node-ids", nodeIdsArg);

    verify(mockRunnerBuilder).bannedNodeIds(bytesCollectionCollector.capture());
    verify(mockRunnerBuilder).build();

    assertThat(bytesCollectionCollector.getValue().toArray()).isEqualTo(nodes);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingWithBannedNodeidsOptionButNoValueMustDisplayError() {
    parseCommand("--banned-node-ids");
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    final String expectedErrorOutputStart =
        "Missing required parameter for option '--banned-node-ids' at index 0 (<NODEID>)";
    assertThat(commandErrorOutput.toString(UTF_8)).startsWith(expectedErrorOutputStart);
  }

  @Test
  public void callingWithBannedNodeidsOptionWithInvalidValuesMustDisplayError() {
    parseCommand("--banned-node-ids", "0x10,20,30");
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    final String expectedErrorOutputStart =
        "Invalid ids supplied to '--banned-node-ids'. Expected 64 bytes in 0x10";
    assertThat(commandErrorOutput.toString(UTF_8)).startsWith(expectedErrorOutputStart);
  }

  @Test
  public void p2pHostAndPortOptionsAreRespected() {

    final String host = "1.2.3.4";
    final int port = 1234;
    parseCommand("--p2p-host", host, "--p2p-port", String.valueOf(port));

    verify(mockRunnerBuilder).p2pAdvertisedHost(stringArgumentCaptor.capture());
    verify(mockRunnerBuilder).p2pListenPort(intArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(stringArgumentCaptor.getValue()).isEqualTo(host);
    assertThat(intArgumentCaptor.getValue()).isEqualTo(port);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void p2pInterfaceOptionIsRespected() {

    final String ip = "1.2.3.4";
    parseCommand("--p2p-interface", ip);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

    verify(mockRunnerBuilder).p2pListenInterface(stringArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(stringArgumentCaptor.getValue()).isEqualTo(ip);
  }

  @Test
  public void p2pHostMayBeLocalhost() {

    final String host = "localhost";
    parseCommand("--p2p-host", host);

    verify(mockRunnerBuilder).p2pAdvertisedHost(stringArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(stringArgumentCaptor.getValue()).isEqualTo(host);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void p2pHostMayBeIPv6() {

    final String host = "2600:DB8::8545";
    parseCommand("--p2p-host", host);

    verify(mockRunnerBuilder).p2pAdvertisedHost(stringArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(stringArgumentCaptor.getValue()).isEqualTo(host);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void maxpeersOptionMustBeUsed() {

    final int maxPeers = 123;
    parseCommand("--max-peers", String.valueOf(maxPeers));

    verify(mockControllerBuilder).maxPeers(intArgumentCaptor.capture());
    verify(mockControllerBuilder).build();

    assertThat(intArgumentCaptor.getValue()).isEqualTo(maxPeers);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void p2pPeerUpperBound_without_p2pPeerLowerBound_shouldSetMaxPeers() {

    final int maxPeers = 23;
    parseCommand("--p2p-peer-upper-bound", String.valueOf(maxPeers));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

    verify(mockControllerBuilder).maxPeers(intArgumentCaptor.capture());
    assertThat(intArgumentCaptor.getValue()).isEqualTo(maxPeers);

    verify(mockRunnerBuilder).build();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void remoteConnectionsPercentageOptionMustBeUsed() {

    final int remoteConnectionsPercentage = 12;
    parseCommand(
        "--remote-connections-limit-enabled=true",
        "--remote-connections-max-percentage",
        String.valueOf(remoteConnectionsPercentage));

    verify(mockControllerBuilder).maxRemotelyInitiatedPeers(intArgumentCaptor.capture());
    verify(mockControllerBuilder).build();

    assertThat(intArgumentCaptor.getValue())
        .isEqualTo(
            (int) Math.floor(25 * Fraction.fromPercentage(remoteConnectionsPercentage).getValue()));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void remoteConnectionsPercentageWithInvalidFormatMustFail() {

    parseCommand(
        "--remote-connections-limit-enabled", "--remote-connections-max-percentage", "invalid");
    Mockito.verifyNoInteractions(mockRunnerBuilder);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains(
            "Invalid value for option '--remote-connections-max-percentage'",
            "should be a number between 0 and 100 inclusive");
  }

  @Test
  public void remoteConnectionsPercentageWithOutOfRangeMustFail() {

    parseCommand(
        "--remote-connections-limit-enabled", "--remote-connections-max-percentage", "150");
    Mockito.verifyNoInteractions(mockRunnerBuilder);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains(
            "Invalid value for option '--remote-connections-max-percentage'",
            "should be a number between 0 and 100 inclusive");
  }

  @Test
  public void syncMode_fast() {
    parseCommand("--sync-mode", "FAST");
    verify(mockControllerBuilder).synchronizerConfiguration(syncConfigurationCaptor.capture());

    final SynchronizerConfiguration syncConfig = syncConfigurationCaptor.getValue();
    assertThat(syncConfig.getSyncMode()).isEqualTo(SyncMode.FAST);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void syncMode_full() {
    parseCommand("--sync-mode", "FULL");
    verify(mockControllerBuilder).synchronizerConfiguration(syncConfigurationCaptor.capture());

    final SynchronizerConfiguration syncConfig = syncConfigurationCaptor.getValue();
    assertThat(syncConfig.getSyncMode()).isEqualTo(SyncMode.FULL);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void syncMode_invalid() {
    parseCommand("--sync-mode", "bogus");
    Mockito.verifyNoInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains(
            "Invalid value for option '--sync-mode': expected one of [FULL, FAST, SNAP, CHECKPOINT, X_SNAP, X_CHECKPOINT] (case-insensitive) but was 'bogus'");
  }

  @Test
  public void syncMode_full_by_default_for_dev() {
    parseCommand("--network", "dev");
    verify(mockControllerBuilder).synchronizerConfiguration(syncConfigurationCaptor.capture());

    final SynchronizerConfiguration syncConfig = syncConfigurationCaptor.getValue();
    assertThat(syncConfig.getSyncMode()).isEqualTo(SyncMode.FULL);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void storage_bonsai_by_default() {
    parseCommand();
    verify(mockControllerBuilder)
        .dataStorageConfiguration(dataStorageConfigurationArgumentCaptor.capture());

    final DataStorageConfiguration dataStorageConfig =
        dataStorageConfigurationArgumentCaptor.getValue();
    assertThat(dataStorageConfig.getDataStorageFormat()).isEqualTo(BONSAI);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void syncMode_snap_by_default() {
    parseCommand();
    verify(mockControllerBuilder).synchronizerConfiguration(syncConfigurationCaptor.capture());

    final SynchronizerConfiguration syncConfig = syncConfigurationCaptor.getValue();
    assertThat(syncConfig.getSyncMode()).isEqualTo(SyncMode.SNAP);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void helpShouldDisplayFastSyncOptions() {
    parseCommand("--help");

    Mockito.verifyNoInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString(UTF_8)).contains("--fast-sync-min-peers");
    // whitelist is now a hidden option
    assertThat(commandOutput.toString(UTF_8)).doesNotContain("whitelist");
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void checkValidDefaultFastSyncMinPeers() {
    parseCommand("--sync-mode", "FAST");
    verify(mockControllerBuilder).synchronizerConfiguration(syncConfigurationCaptor.capture());

    final SynchronizerConfiguration syncConfig = syncConfigurationCaptor.getValue();
    assertThat(syncConfig.getSyncMode()).isEqualTo(SyncMode.FAST);
    assertThat(syncConfig.getFastSyncMinimumPeerCount()).isEqualTo(5);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void parsesValidFastSyncMinPeersOption() {
    parseCommand("--sync-mode", "FAST", "--fast-sync-min-peers", "11");
    verify(mockControllerBuilder).synchronizerConfiguration(syncConfigurationCaptor.capture());

    final SynchronizerConfiguration syncConfig = syncConfigurationCaptor.getValue();
    assertThat(syncConfig.getSyncMode()).isEqualTo(SyncMode.FAST);
    assertThat(syncConfig.getFastSyncMinimumPeerCount()).isEqualTo(11);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void parsesValidSnapSyncMinPeersOption() {
    parseCommand("--sync-mode", "SNAP", "--sync-min-peers", "11");
    verify(mockControllerBuilder).synchronizerConfiguration(syncConfigurationCaptor.capture());

    final SynchronizerConfiguration syncConfig = syncConfigurationCaptor.getValue();
    assertThat(syncConfig.getSyncMode()).isEqualTo(SyncMode.SNAP);
    assertThat(syncConfig.getFastSyncMinimumPeerCount()).isEqualTo(11);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void parsesValidSyncMinPeersOption() {
    parseCommand("--sync-mode", "FAST", "--sync-min-peers", "11");
    verify(mockControllerBuilder).synchronizerConfiguration(syncConfigurationCaptor.capture());

    final SynchronizerConfiguration syncConfig = syncConfigurationCaptor.getValue();
    assertThat(syncConfig.getSyncMode()).isEqualTo(SyncMode.FAST);
    assertThat(syncConfig.getFastSyncMinimumPeerCount()).isEqualTo(11);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void parsesInvalidFastSyncMinPeersOptionWrongFormatShouldFail() {

    parseCommand("--sync-mode", "FAST", "--fast-sync-min-peers", "ten");
    Mockito.verifyNoInteractions(mockRunnerBuilder);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Invalid value for option '--fast-sync-min-peers': 'ten' is not an int");
  }

  @Test
  public void natMethodOptionIsParsedCorrectly() {

    parseCommand("--nat-method", "NONE");
    verify(mockRunnerBuilder).natMethod(eq(NatMethod.NONE));

    parseCommand("--nat-method", "UPNP");
    verify(mockRunnerBuilder).natMethod(eq(NatMethod.UPNP));

    parseCommand("--nat-method", "UPNPP2PONLY");
    verify(mockRunnerBuilder).natMethod(eq(NatMethod.UPNPP2PONLY));

    parseCommand("--nat-method", "AUTO");
    verify(mockRunnerBuilder).natMethod(eq(NatMethod.AUTO));

    parseCommand("--nat-method", "DOCKER");
    verify(mockRunnerBuilder).natMethod(eq(NatMethod.DOCKER));

    parseCommand("--nat-method", "KUBERNETES");
    verify(mockRunnerBuilder).natMethod(eq(NatMethod.KUBERNETES));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void parsesInvalidNatMethodOptionsShouldFail() {

    parseCommand("--nat-method", "invalid");
    Mockito.verifyNoInteractions(mockRunnerBuilder);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains(
            "Invalid value for option '--nat-method': expected one of [UPNP, UPNPP2PONLY, DOCKER, KUBERNETES, AUTO, NONE] (case-insensitive) but was 'invalid'");
  }

  @Test
  public void ethStatsOptionIsParsedCorrectly() {
    final String url = "besu-node:secret@host:443";
    parseCommand("--ethstats", url);
    verify(mockRunnerBuilder).ethstatsOptions(ethstatsOptionsArgumentCaptor.capture());
    assertThat(ethstatsOptionsArgumentCaptor.getValue().getEthstatsUrl()).isEqualTo(url);
  }

  @Test
  public void ethStatsContactOptionIsParsedCorrectly() {
    final String contact = "contact@mail.net";
    parseCommand("--ethstats", "besu-node:secret@host:443", "--ethstats-contact", contact);
    verify(mockRunnerBuilder).ethstatsOptions(ethstatsOptionsArgumentCaptor.capture());
    assertThat(ethstatsOptionsArgumentCaptor.getValue().getEthstatsContact()).isEqualTo(contact);
  }

  @Test
  public void ethStatsContactOptionCannotBeUsedWithoutEthStatsServerProvided() {
    parseCommand("--ethstats-contact", "besu-updated");
    Mockito.verifyNoInteractions(mockRunnerBuilder);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains(
            "The `--ethstats-contact` requires ethstats server URL to be provided. Either remove --ethstats-contact or provide a URL (via --ethstats=nodename:secret@host:port)");
  }

  @Test
  public void parsesValidBonsaiTrieLimitBackLayersOption() {
    parseCommand("--data-storage-format", "BONSAI", "--bonsai-historical-block-limit", "11");
    verify(mockControllerBuilder)
        .dataStorageConfiguration(dataStorageConfigurationArgumentCaptor.capture());

    final DataStorageConfiguration dataStorageConfiguration =
        dataStorageConfigurationArgumentCaptor.getValue();
    assertThat(dataStorageConfiguration.getDataStorageFormat()).isEqualTo(BONSAI);
    assertThat(dataStorageConfiguration.getBonsaiMaxLayersToLoad()).isEqualTo(11);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void parsesInvalidBonsaiTrieLimitBackLayersOption() {

    parseCommand("--data-storage-format", "BONSAI", "--bonsai-maximum-back-layers-to-load", "ten");

    Mockito.verifyNoInteractions(mockRunnerBuilder);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains(
            "Invalid value for option '--bonsai-maximum-back-layers-to-load': 'ten' is not a long");
  }

  @Test
  public void dnsEnabledOptionIsParsedCorrectly() {
    final TestBesuCommand besuCommand = parseCommand("--Xdns-enabled", "true");

    assertThat(besuCommand.getEnodeDnsConfiguration().dnsEnabled()).isTrue();
    assertThat(besuCommand.getEnodeDnsConfiguration().updateEnabled()).isFalse();
  }

  @Test
  public void versionCompatibilityProtectionTrueOptionIsParsedCorrectly() {
    final TestBesuCommand besuCommand = parseCommand("--version-compatibility-protection", "true");

    assertThat(besuCommand.getVersionCompatibilityProtection()).isTrue();
  }

  @Test
  public void dnsUpdateEnabledOptionIsParsedCorrectly() {
    final TestBesuCommand besuCommand =
        parseCommand("--Xdns-enabled", "true", "--Xdns-update-enabled", "true");

    assertThat(besuCommand.getEnodeDnsConfiguration().dnsEnabled()).isTrue();
    assertThat(besuCommand.getEnodeDnsConfiguration().updateEnabled()).isTrue();
  }

  @Test
  public void dnsUpdateEnabledOptionCannotBeUsedWithoutDnsEnabled() {
    parseCommand("--Xdns-update-enabled", "true");
    Mockito.verifyNoInteractions(mockRunnerBuilder);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains(
            "The `--Xdns-update-enabled` requires dns to be enabled. Either remove --Xdns-update-enabled or specify dns is enabled (--Xdns-enabled)");
  }

  @Test
  public void helpShouldDisplayNatMethodInfo() {
    parseCommand("--help");

    Mockito.verifyNoInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString(UTF_8)).contains("--nat-method");
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void natMethodPropertyDefaultIsAuto() {
    parseCommand();

    verify(mockRunnerBuilder).natMethod(eq(NatMethod.AUTO));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void natManagerPodNamePropertyDefaultIsBesu() {
    parseCommand();

    verify(mockRunnerBuilder).natManagerServiceName(eq(DEFAULT_BESU_SERVICE_NAME_FILTER));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void natManagerPodNamePropertyIsCorrectlyUpdated() {
    final String podName = "besu-updated";
    parseCommand("--Xnat-kube-service-name", podName);

    verify(mockRunnerBuilder).natManagerServiceName(eq(podName));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void natManagerPodNameCannotBeUsedWithNatDockerMethod() {
    parseCommand("--nat-method", "DOCKER", "--Xnat-kube-service-name", "besu-updated");
    Mockito.verifyNoInteractions(mockRunnerBuilder);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains(
            "The `--Xnat-kube-service-name` parameter is only used in kubernetes mode. Either remove --Xnat-kube-service-name or select the KUBERNETES mode (via --nat--method=KUBERNETES)");
  }

  @Test
  public void natManagerPodNameCannotBeUsedWithNatNoneMethod() {
    parseCommand("--nat-method", "NONE", "--Xnat-kube-service-name", "besu-updated");
    Mockito.verifyNoInteractions(mockRunnerBuilder);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains(
            "The `--Xnat-kube-service-name` parameter is only used in kubernetes mode. Either remove --Xnat-kube-service-name or select the KUBERNETES mode (via --nat--method=KUBERNETES)");
  }

  @Test
  public void natMethodFallbackEnabledPropertyIsCorrectlyUpdatedWithKubernetes() {

    parseCommand("--nat-method", "KUBERNETES", "--Xnat-method-fallback-enabled", "false");
    verify(mockRunnerBuilder).natMethodFallbackEnabled(eq(false));
    parseCommand("--nat-method", "KUBERNETES", "--Xnat-method-fallback-enabled", "true");
    verify(mockRunnerBuilder).natMethodFallbackEnabled(eq(true));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void natMethodFallbackEnabledPropertyIsCorrectlyUpdatedWithDocker() {

    parseCommand("--nat-method", "DOCKER", "--Xnat-method-fallback-enabled", "false");
    verify(mockRunnerBuilder).natMethodFallbackEnabled(eq(false));
    parseCommand("--nat-method", "DOCKER", "--Xnat-method-fallback-enabled", "true");
    verify(mockRunnerBuilder).natMethodFallbackEnabled(eq(true));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void natMethodFallbackEnabledPropertyIsCorrectlyUpdatedWithUpnp() {

    parseCommand("--nat-method", "UPNP", "--Xnat-method-fallback-enabled", "false");
    verify(mockRunnerBuilder).natMethodFallbackEnabled(eq(false));
    parseCommand("--nat-method", "UPNP", "--Xnat-method-fallback-enabled", "true");
    verify(mockRunnerBuilder).natMethodFallbackEnabled(eq(true));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void natMethodFallbackEnabledCannotBeUsedWithAutoMethod() {
    parseCommand("--nat-method", "AUTO", "--Xnat-method-fallback-enabled", "false");
    Mockito.verifyNoInteractions(mockRunnerBuilder);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains(
            "The `--Xnat-method-fallback-enabled` parameter cannot be used in AUTO mode. Either remove --Xnat-method-fallback-enabled or select another mode (via --nat--method=XXXX)");
  }

  @Test
  public void rpcHttpEnabledPropertyDefaultIsFalse() {
    parseCommand();

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().isEnabled()).isFalse();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void graphQLHttpEnabledPropertyDefaultIsFalse() {
    parseCommand();

    verify(mockRunnerBuilder).graphQLConfiguration(graphQLConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(graphQLConfigArgumentCaptor.getValue().isEnabled()).isFalse();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void rpcApisSupportsEngine() {
    parseCommand("--rpc-http-api", "ENGINE", "--rpc-http-enabled");

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getRpcApis())
        .containsExactlyInAnyOrder(ENGINE.name());

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void engineApiAuthOptions() {
    // TODO: once we have mainnet TTD, we can remove the TTD override parameter here
    // https://github.com/hyperledger/besu/issues/3874
    parseCommand(
        "--override-genesis-config",
        "terminalTotalDifficulty=1337",
        "--rpc-http-enabled",
        "--engine-jwt-secret",
        "/tmp/fakeKey.hex");
    verify(mockRunnerBuilder).engineJsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    assertThat(jsonRpcConfigArgumentCaptor.getValue().isAuthenticationEnabled()).isTrue();
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void engineApiDisableAuthOptions() {
    // TODO: once we have mainnet TTD, we can remove the TTD override parameter here
    // https://github.com/hyperledger/besu/issues/3874
    parseCommand(
        "--override-genesis-config",
        "terminalTotalDifficulty=1337",
        "--rpc-http-enabled",
        "--engine-jwt-disabled",
        "--engine-jwt-secret",
        "/tmp/fakeKey.hex");
    verify(mockRunnerBuilder).engineJsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    assertThat(jsonRpcConfigArgumentCaptor.getValue().isAuthenticationEnabled()).isFalse();
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void privacyTlsOptionsRequiresTlsToBeEnabled() {
    when(storageService.getByName("rocksdb-privacy"))
        .thenReturn(Optional.of(rocksDBSPrivacyStorageFactory));
    final URL configFile = this.getClass().getResource("/orion_publickey.pub");
    final String coinbaseStr = String.format("%040x", 1);

    parseCommand(
        "--privacy-enabled",
        "--miner-enabled",
        "--miner-coinbase=" + coinbaseStr,
        "--min-gas-price",
        "0",
        "--privacy-url",
        ENCLAVE_URI,
        "--privacy-public-key-file",
        configFile.getPath(),
        "--privacy-tls-keystore-file",
        "/Users/me/key");

    verifyOptionsConstraintLoggerCall("--privacy-tls-enabled", "--privacy-tls-keystore-file");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void privacyTlsOptionsRequiresTlsToBeEnabledToml() throws IOException {
    when(storageService.getByName("rocksdb-privacy"))
        .thenReturn(Optional.of(rocksDBSPrivacyStorageFactory));
    final URL configFile = this.getClass().getResource("/orion_publickey.pub");
    final String coinbaseStr = String.format("%040x", 1);

    final Path toml =
        createTempFile(
            "toml",
            "privacy-enabled=true\n"
                + "miner-enabled=true\n"
                + "miner-coinbase=\""
                + coinbaseStr
                + "\"\n"
                + "min-gas-price=0\n"
                + "privacy-url=\""
                + ENCLAVE_URI
                + "\"\n"
                + "privacy-public-key-file=\""
                + configFile.getPath()
                + "\"\n"
                + "privacy-tls-keystore-file=\"/Users/me/key\"");

    parseCommand("--config-file", toml.toString());

    verifyOptionsConstraintLoggerCall("--privacy-tls-enabled", "--privacy-tls-keystore-file");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void privacyTlsOptionsRequiresPrivacyToBeEnabled() {
    parseCommand("--privacy-tls-enabled", "--privacy-tls-keystore-file", "/Users/me/key");

    verifyOptionsConstraintLoggerCall("--privacy-enabled", "--privacy-tls-enabled");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void privacyTlsOptionsRequiresPrivacyToBeEnabledToml() throws IOException {
    final Path toml =
        createTempFile(
            "toml", "privacy-tls-enabled=true\n" + "privacy-tls-keystore-file=\"/Users/me/key\"");

    parseCommand("--config-file", toml.toString());

    verifyOptionsConstraintLoggerCall("--privacy-enabled", "--privacy-tls-enabled");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  /** test deprecated CLI option * */
  @Deprecated
  @Test
  public void rpcHttpHostWhitelistAcceptsSingleArgument() {
    parseCommand("--host-whitelist", "a");

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist().size()).isEqualTo(1);
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist()).contains("a");
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist())
        .doesNotContain("localhost");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void rpcHttpHostAllowlistAcceptsSingleArgument() {
    parseCommand("--host-allowlist", "a");

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist().size()).isEqualTo(1);
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist()).contains("a");
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist())
        .doesNotContain("localhost");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void rpcHttpHostAllowlistAcceptsMultipleArguments() {
    parseCommand("--host-allowlist", "a,b");

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist().size()).isEqualTo(2);
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist()).contains("a", "b");
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist())
        .doesNotContain("*", "localhost");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void rpcHttpHostAllowlistAcceptsDoubleComma() {
    parseCommand("--host-allowlist", "a,,b");

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist().size()).isEqualTo(2);
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist()).contains("a", "b");
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist())
        .doesNotContain("*", "localhost");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Deprecated
  @Test
  public void rpcHttpHostWhitelistAllowlistAcceptsMultipleFlags() {
    parseCommand("--host-whitelist=a", "--host-allowlist=b");

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist().size()).isEqualTo(2);
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist()).contains("a", "b");
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist())
        .doesNotContain("*", "localhost");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void rpcHttpHostAllowlistAcceptsMultipleFlags() {
    parseCommand("--host-allowlist=a", "--host-allowlist=b");

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist().size()).isEqualTo(2);
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist()).contains("a", "b");
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist())
        .doesNotContain("*", "localhost");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void rpcHttpHostAllowlistStarWithAnotherHostnameMustFail() {
    final String[] origins = {"friend", "*"};
    parseCommand("--host-allowlist", String.join(",", origins));

    Mockito.verifyNoInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Values '*' or 'all' can't be used with other hostnames");
  }

  @Test
  public void rpcHttpHostAllowlistStarWithAnotherHostnameMustFailStarFirst() {
    final String[] origins = {"*", "friend"};
    parseCommand("--host-allowlist", String.join(",", origins));

    Mockito.verifyNoInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Values '*' or 'all' can't be used with other hostnames");
  }

  @Test
  public void rpcHttpHostAllowlistAllWithAnotherHostnameMustFail() {
    final String[] origins = {"friend", "all"};
    parseCommand("--host-allowlist", String.join(",", origins));

    Mockito.verifyNoInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Values '*' or 'all' can't be used with other hostnames");
  }

  @Test
  public void rpcHttpHostAllowlistWithNoneMustBuildEmptyList() {
    final String[] origins = {"none"};
    parseCommand("--host-allowlist", String.join(",", origins));

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist()).isEmpty();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void rpcHttpHostAllowlistNoneWithAnotherDomainMustFail() {
    final String[] origins = {"http://domain1.com", "none"};
    parseCommand("--host-allowlist", String.join(",", origins));

    Mockito.verifyNoInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Value 'none' can't be used with other hostnames");
  }

  @Test
  public void rpcHttpHostAllowlistNoneWithAnotherDomainMustFailNoneFirst() {
    final String[] origins = {"none", "http://domain1.com"};
    parseCommand("--host-allowlist", String.join(",", origins));

    Mockito.verifyNoInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Value 'none' can't be used with other hostnames");
  }

  @Test
  public void rpcHttpHostAllowlistEmptyValueFails() {
    parseCommand("--host-allowlist=");

    Mockito.verifyNoInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Hostname cannot be empty string or null string.");
  }

  @Test
  public void metricsEnabledPropertyDefaultIsFalse() {
    parseCommand();

    verify(mockRunnerBuilder).metricsConfiguration(metricsConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(metricsConfigArgumentCaptor.getValue().isEnabled()).isFalse();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void metricsEnabledPropertyMustBeUsed() {
    parseCommand("--metrics-enabled");

    verify(mockRunnerBuilder).metricsConfiguration(metricsConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(metricsConfigArgumentCaptor.getValue().isEnabled()).isTrue();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void metricsPushOptionsRequiresPushToBeEnabled() {
    parseCommand(
        "--metrics-push-host",
        "0.0.0.0",
        "--metrics-push-port",
        "1234",
        "--metrics-push-interval",
        "2",
        "--metrics-push-prometheus-job",
        "job-name");

    verifyOptionsConstraintLoggerCall(
        "--metrics-push-enabled",
        "--metrics-push-host",
        "--metrics-push-port",
        "--metrics-push-interval",
        "--metrics-push-prometheus-job");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void metricsPushOptionsRequiresPushToBeEnabledToml() throws IOException {
    final Path toml =
        createTempFile(
            "toml",
            "metrics-push-host=\"0.0.0.0\"\n"
                + "metrics-push-port=1234\n"
                + "metrics-push-interval=2\n"
                + "metrics-push-prometheus-job=\"job-name\"\n");

    parseCommand("--config-file", toml.toString());

    verifyOptionsConstraintLoggerCall(
        "--metrics-push-enabled",
        "--metrics-push-host",
        "--metrics-push-port",
        "--metrics-push-interval",
        "--metrics-push-prometheus-job");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void metricsOptionsRequiresPullMetricsToBeEnabled() {
    parseCommand("--metrics-host", "0.0.0.0", "--metrics-port", "1234");

    verifyOptionsConstraintLoggerCall("--metrics-enabled", "--metrics-host", "--metrics-port");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void metricsOptionsRequiresPullMetricsToBeEnabledToml() throws IOException {
    final Path toml = createTempFile("toml", "metrics-host=\"0.0.0.0\"\n" + "metrics-port=1234\n");

    parseCommand("--config-file", toml.toString());

    verifyOptionsConstraintLoggerCall("--metrics-enabled", "--metrics-host", "--metrics-port");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void metricsHostAndPortOptionMustBeUsed() {
    final String host = "1.2.3.4";
    final int port = 1234;
    parseCommand(
        "--metrics-enabled", "--metrics-host", host, "--metrics-port", String.valueOf(port));

    verify(mockRunnerBuilder).metricsConfiguration(metricsConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(metricsConfigArgumentCaptor.getValue().getHost()).isEqualTo(host);
    assertThat(metricsConfigArgumentCaptor.getValue().getPort()).isEqualTo(port);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void metricsHostMayBeLocalhost() {
    final String host = "localhost";
    parseCommand("--metrics-enabled", "--metrics-host", host);

    verify(mockRunnerBuilder).metricsConfiguration(metricsConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(metricsConfigArgumentCaptor.getValue().getHost()).isEqualTo(host);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void metricsHostMayBeIPv6() {
    final String host = "2600:DB8::8545";
    parseCommand("--metrics-enabled", "--metrics-host", host);

    verify(mockRunnerBuilder).metricsConfiguration(metricsConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(metricsConfigArgumentCaptor.getValue().getHost()).isEqualTo(host);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void metricsCategoryPropertyMustBeUsed() {
    parseCommand("--metrics-enabled", "--metrics-category", StandardMetricCategory.JVM.toString());

    verify(mockRunnerBuilder).metricsConfiguration(metricsConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(metricsConfigArgumentCaptor.getValue().getMetricCategories())
        .containsExactly(StandardMetricCategory.JVM);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void metricsPushEnabledPropertyMustBeUsed() {
    parseCommand("--metrics-push-enabled");

    verify(mockRunnerBuilder).metricsConfiguration(metricsConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(metricsConfigArgumentCaptor.getValue().isPushEnabled()).isTrue();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void metricsPushHostAndPushPortOptionMustBeUsed() {
    final String host = "1.2.3.4";
    final int port = 1234;
    parseCommand(
        "--metrics-push-enabled",
        "--metrics-push-host",
        host,
        "--metrics-push-port",
        String.valueOf(port));

    verify(mockRunnerBuilder).metricsConfiguration(metricsConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(metricsConfigArgumentCaptor.getValue().getPushHost()).isEqualTo(host);
    assertThat(metricsConfigArgumentCaptor.getValue().getPushPort()).isEqualTo(port);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void metricsPushHostMayBeLocalhost() {
    final String host = "localhost";
    parseCommand("--metrics-push-enabled", "--metrics-push-host", host);

    verify(mockRunnerBuilder).metricsConfiguration(metricsConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(metricsConfigArgumentCaptor.getValue().getPushHost()).isEqualTo(host);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void metricsPushIntervalMustBeUsed() {
    parseCommand("--metrics-push-enabled", "--metrics-push-interval", "42");

    verify(mockRunnerBuilder).metricsConfiguration(metricsConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(metricsConfigArgumentCaptor.getValue().getPushInterval()).isEqualTo(42);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void metricsPrometheusJobMustBeUsed() {
    parseCommand("--metrics-push-enabled", "--metrics-push-prometheus-job", "besu-command-test");

    verify(mockRunnerBuilder).metricsConfiguration(metricsConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(metricsConfigArgumentCaptor.getValue().getPrometheusJob())
        .isEqualTo("besu-command-test");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void metricsAndMetricsPushMustNotBeUsedTogether() {
    parseCommand("--metrics-enabled", "--metrics-push-enabled");

    Mockito.verifyNoInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith("--metrics-enabled option and --metrics-push-enabled option can't be used");
  }

  @Test
  public void colorCanBeEnabledOrDisabledExplicitly() {
    Stream.of(true, false)
        .forEach(
            bool -> {
              parseCommand("--color-enabled", bool.toString());
              assertThat(BesuCommand.getColorEnabled()).contains(bool);
            });
  }

  @Disabled
  public void pruningIsEnabledIfSyncModeIsFast() {
    parseCommand("--sync-mode", "FAST");

    verify(mockControllerBuilder).isPruningEnabled(true);
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Disabled
  public void pruningIsDisabledIfSyncModeIsFull() {
    parseCommand("--sync-mode", "FULL");

    verify(mockControllerBuilder).isPruningEnabled(false);
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void pruningEnabledExplicitly() {
    parseCommand("--pruning-enabled", "--sync-mode=FULL");

    verify(mockControllerBuilder).isPruningEnabled(true);
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Disabled
  public void pruningDisabledExplicitly() {
    parseCommand("--pruning-enabled=false", "--sync-mode=FAST");

    verify(mockControllerBuilder).isPruningEnabled(false);
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void pruningDisabledByDefault() {
    parseCommand();

    verify(mockControllerBuilder).isPruningEnabled(false);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void pruningParametersAreCaptured() throws Exception {
    parseCommand(
        "--pruning-enabled", "--pruning-blocks-retained=15", "--pruning-block-confirmations=4");

    final ArgumentCaptor<PrunerConfiguration> pruningArg =
        ArgumentCaptor.forClass(PrunerConfiguration.class);

    verify(mockControllerBuilder).pruningConfiguration(pruningArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
    assertThat(pruningArg.getValue().getBlocksRetained()).isEqualTo(15);
    assertThat(pruningArg.getValue().getBlockConfirmations()).isEqualTo(4);
  }

  @Test
  public void pruningLogsDeprecationWarningWithForest() {
    parseCommand("--pruning-enabled", "--data-storage-format=FOREST");

    verify(mockControllerBuilder).isPruningEnabled(true);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
    verify(mockLogger)
        .warn(
            contains(
                "Forest pruning is deprecated and will be removed soon."
                    + " To save disk space consider switching to Bonsai data storage format."));
  }

  @Test
  public void pruningLogsIgnoredWarningWithBonsai() {
    parseCommand("--pruning-enabled", "--data-storage-format=BONSAI");

    verify(mockControllerBuilder).isPruningEnabled(true);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
    verify(mockLogger).warn(contains("Forest pruning is ignored with Bonsai data storage format."));
  }

  @Test
  public void devModeOptionMustBeUsed() throws Exception {
    parseCommand("--network", "dev");

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue()).isEqualTo(EthNetworkConfig.getNetworkConfig(DEV));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void goerliValuesAreUsed() {
    parseCommand("--network", "goerli");

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue()).isEqualTo(EthNetworkConfig.getNetworkConfig(GOERLI));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

    verify(mockLogger, never()).warn(contains("Goerli is deprecated and will be shutdown"));
  }

  @Test
  public void futureEipsValuesAreUsed() {
    parseCommand("--network", "future_eips");

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue()).isEqualTo(EthNetworkConfig.getNetworkConfig(FUTURE_EIPS));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void experimentalEipsValuesAreUsed() {
    parseCommand("--network", "experimental_eips");

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue())
        .isEqualTo(EthNetworkConfig.getNetworkConfig(EXPERIMENTAL_EIPS));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void sepoliaValuesAreUsed() {
    parseCommand("--network", "sepolia");

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue()).isEqualTo(EthNetworkConfig.getNetworkConfig(SEPOLIA));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

    verify(mockLogger, never()).warn(contains("Sepolia is deprecated and will be shutdown"));
  }

  @Test
  public void holeskyValuesAreUsed() {
    parseCommand("--network", "holesky");

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue()).isEqualTo(EthNetworkConfig.getNetworkConfig(HOLESKY));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

    verify(mockLogger, never()).warn(contains("Holesky is deprecated and will be shutdown"));
  }

  @Test
  public void classicValuesAreUsed() throws Exception {
    parseCommand("--network", "classic");

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue()).isEqualTo(EthNetworkConfig.getNetworkConfig(CLASSIC));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void mordorValuesAreUsed() throws Exception {
    parseCommand("--network", "mordor");

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue()).isEqualTo(EthNetworkConfig.getNetworkConfig(MORDOR));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void goerliValuesCanBeOverridden() throws Exception {
    networkValuesCanBeOverridden("goerli");
  }

  @Test
  public void futureEipsValuesCanBeOverridden() throws Exception {
    networkValuesCanBeOverridden("future_eips");
  }

  @Test
  public void experimentalEipsValuesCanBeOverridden() throws Exception {
    networkValuesCanBeOverridden("experimental_eips");
  }

  @Test
  public void devValuesCanBeOverridden() throws Exception {
    networkValuesCanBeOverridden("dev");
  }

  @Test
  public void classicValuesCanBeOverridden() throws Exception {
    networkValuesCanBeOverridden("classic");
  }

  @Test
  public void mordorValuesCanBeOverridden() throws Exception {
    networkValuesCanBeOverridden("mordor");
  }

  private void networkValuesCanBeOverridden(final String network) throws Exception {
    parseCommand(
        "--network",
        network,
        "--network-id",
        "1234567",
        "--bootnodes",
        String.join(",", VALID_ENODE_STRINGS));

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue().getBootNodes())
        .isEqualTo(
            Stream.of(VALID_ENODE_STRINGS)
                .map(EnodeURLImpl::fromString)
                .collect(Collectors.toList()));
    assertThat(networkArg.getValue().getNetworkId()).isEqualTo(1234567);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void fullCLIOptionsShown() {
    parseCommand("--help");

    Mockito.verifyNoInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString(UTF_8)).contains("--config-file");
    assertThat(commandOutput.toString(UTF_8)).contains("--data-path");
    assertThat(commandOutput.toString(UTF_8)).contains("--genesis-file");
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void mustUseEnclaveUriAndOptions() {
    final URL configFile = this.getClass().getResource("/orion_publickey.pub");

    parseCommand(
        "--privacy-enabled",
        "--privacy-url",
        ENCLAVE_URI,
        "--privacy-public-key-file",
        configFile.getPath(),
        "--min-gas-price",
        "0");

    final ArgumentCaptor<PrivacyParameters> enclaveArg =
        ArgumentCaptor.forClass(PrivacyParameters.class);

    verify(mockControllerBuilder).privacyParameters(enclaveArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(enclaveArg.getValue().isEnabled()).isEqualTo(true);
    assertThat(enclaveArg.getValue().getEnclaveUri()).isEqualTo(URI.create(ENCLAVE_URI));
    assertThat(enclaveArg.getValue().getPrivacyUserId()).isEqualTo(ENCLAVE_PUBLIC_KEY);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void privacyOptionsRequiresServiceToBeEnabled() {

    final File file = new File("./specific/enclavePublicKey");
    file.deleteOnExit();

    parseCommand("--privacy-url", ENCLAVE_URI, "--privacy-public-key-file", file.toString());

    verifyMultiOptionsConstraintLoggerCall(
        "--privacy-url and/or --privacy-public-key-file ignored because none of --privacy-enabled was defined.");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void privacyWithoutPrivacyPublicKeyFails() {
    parseCommand("--privacy-enabled", "--privacy-url", ENCLAVE_URI);

    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith("Please specify Enclave public key file path to enable privacy");
  }

  @Test
  public void mustVerifyPrivacyIsDisabled() {
    parseCommand();

    final ArgumentCaptor<PrivacyParameters> enclaveArg =
        ArgumentCaptor.forClass(PrivacyParameters.class);

    verify(mockControllerBuilder).privacyParameters(enclaveArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
    assertThat(enclaveArg.getValue().isEnabled()).isEqualTo(false);
  }

  @Test
  public void privacyMultiTenancyIsConfiguredWhenConfiguredWithNecessaryOptions() {
    parseCommand(
        "--privacy-enabled",
        "--rpc-http-authentication-enabled",
        "--privacy-multi-tenancy-enabled",
        "--rpc-http-authentication-jwt-public-key-file",
        "/non/existent/file",
        "--min-gas-price",
        "0");

    final ArgumentCaptor<PrivacyParameters> privacyParametersArgumentCaptor =
        ArgumentCaptor.forClass(PrivacyParameters.class);

    verify(mockControllerBuilder).privacyParameters(privacyParametersArgumentCaptor.capture());
    verify(mockControllerBuilder).build();

    assertThat(privacyParametersArgumentCaptor.getValue().isMultiTenancyEnabled()).isTrue();
  }

  @Test
  public void privacyMultiTenancyWithoutAuthenticationFails() {
    parseCommand(
        "--privacy-enabled",
        "--privacy-multi-tenancy-enabled",
        "--rpc-http-authentication-jwt-public-key-file",
        "/non/existent/file");

    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith(
            "Privacy multi-tenancy requires either http authentication to be enabled or WebSocket authentication to be enabled");
  }

  @Test
  public void privacyMultiTenancyWithPrivacyPublicKeyFileFails() {
    parseCommand(
        "--privacy-enabled",
        "--rpc-http-authentication-enabled",
        "--privacy-multi-tenancy-enabled",
        "--rpc-http-authentication-jwt-public-key-file",
        "/non/existent/file",
        "--privacy-public-key-file",
        ENCLAVE_PUBLIC_KEY_PATH);

    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith("Privacy multi-tenancy and privacy public key cannot be used together");
  }

  @Test
  public void flexiblePrivacyGroupEnabledFlagDefaultValueIsFalse() {
    parseCommand(
        "--privacy-enabled",
        "--privacy-public-key-file",
        ENCLAVE_PUBLIC_KEY_PATH,
        "--min-gas-price",
        "0");

    final ArgumentCaptor<PrivacyParameters> privacyParametersArgumentCaptor =
        ArgumentCaptor.forClass(PrivacyParameters.class);

    verify(mockControllerBuilder).privacyParameters(privacyParametersArgumentCaptor.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

    final PrivacyParameters privacyParameters = privacyParametersArgumentCaptor.getValue();
    assertThat(privacyParameters.isFlexiblePrivacyGroupsEnabled()).isEqualTo(false);
  }

  @Test
  public void flexiblePrivacyGroupEnabledFlagValueIsSet() {
    parseCommand(
        "--privacy-enabled",
        "--privacy-public-key-file",
        ENCLAVE_PUBLIC_KEY_PATH,
        "--privacy-flexible-groups-enabled",
        "--min-gas-price",
        "0");

    final ArgumentCaptor<PrivacyParameters> privacyParametersArgumentCaptor =
        ArgumentCaptor.forClass(PrivacyParameters.class);

    verify(mockControllerBuilder).privacyParameters(privacyParametersArgumentCaptor.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

    final PrivacyParameters privacyParameters = privacyParametersArgumentCaptor.getValue();
    assertThat(privacyParameters.isFlexiblePrivacyGroupsEnabled()).isEqualTo(true);
  }

  @Test
  public void privateMarkerTransactionSigningKeyFileRequiredIfMinGasPriceNonZero() {
    parseCommand(
        "--privacy-enabled",
        "--privacy-public-key-file",
        ENCLAVE_PUBLIC_KEY_PATH,
        "--min-gas-price",
        "1");

    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith(
            "Not a free gas network. --privacy-marker-transaction-signing-key-file must be specified");
  }

  @Test
  public void
      privateMarkerTransactionSigningKeyFileNotRequiredIfMinGasPriceNonZeroAndUsingPluginPrivateMarkerTransactionFactory() {

    when(privacyPluginService.getPrivateMarkerTransactionFactory())
        .thenReturn(mock(PrivateMarkerTransactionFactory.class));

    parseCommand(
        "--privacy-enabled",
        "--privacy-public-key-file",
        ENCLAVE_PUBLIC_KEY_PATH,
        "--min-gas-price",
        "1");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void
      privateMarkerTransactionSigningKeyFileNotCanNotBeUsedWithPluginPrivateMarkerTransactionFactory()
          throws IOException {
    when(privacyPluginService.getPrivateMarkerTransactionFactory())
        .thenReturn(mock(PrivateMarkerTransactionFactory.class));
    final Path toml =
        createTempFile(
            "key",
            "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63".getBytes(UTF_8));

    parseCommand(
        "--privacy-enabled",
        "--privacy-public-key-file",
        ENCLAVE_PUBLIC_KEY_PATH,
        "--privacy-marker-transaction-signing-key-file",
        toml.toString());

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    //    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith(
            "--privacy-marker-transaction-signing-key-file can not be used in conjunction with a plugin that specifies");
  }

  @Test
  public void mustProvidePayloadWhenPrivacyPluginEnabled() {
    parseCommand("--privacy-enabled", "--Xprivacy-plugin-enabled", "--min-gas-price", "0");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith(
            "No Payload Provider has been provided. You must register one when enabling privacy plugin!");
  }

  @Test
  public void canNotUseFlexiblePrivacyWhenPrivacyPluginEnabled() {
    parseCommand(
        "--privacy-enabled",
        "--Xprivacy-plugin-enabled",
        "--min-gas-price",
        "0",
        "--privacy-flexible-groups-enabled");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith(
            "No Payload Provider has been provided. You must register one when enabling privacy plugin!");
  }

  /**
   * Check logger calls
   *
   * <p>Here we check the calls to logger and not the result of the log line as we don't test the
   * logger itself but the fact that we call it.
   *
   * @param stringToLog the string that is logged
   */
  private void verifyMultiOptionsConstraintLoggerCall(final String stringToLog) {
    verify(mockLogger, atLeast(1)).warn(stringToLog);
  }

  @Test
  public void privacyWithFastSyncMustError() {
    parseCommand("--sync-mode=FAST", "--privacy-enabled");

    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Fast sync cannot be enabled with privacy.");
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void privacyWithSnapSyncMustError() {
    parseCommand("--sync-mode=SNAP", "--privacy-enabled");

    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Snap sync cannot be enabled with privacy.");
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void privacyWithCheckpointSyncMustError() {
    parseCommand("--sync-mode=CHECKPOINT", "--privacy-enabled");

    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Checkpoint sync cannot be enabled with privacy.");
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void privacyWithPruningMustError() {
    parseCommand("--pruning-enabled", "--privacy-enabled");

    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Pruning cannot be enabled with privacy.");
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void tomlThatHasInvalidOptions() throws IOException {
    final URL configFile = this.getClass().getResource("/complete_config.toml");
    // update genesis file path, "similar" valid option and add invalid options
    final Path genesisFile = createFakeGenesisFile(GENESIS_VALID_JSON);
    final String updatedConfig =
        Resources.toString(configFile, UTF_8)
                .replace("/opt/besu/genesis.json", escapeTomlString(genesisFile.toString()))
                .replace("rpc-http-api", "rpc-http-apis")
            + System.lineSeparator()
            + "invalid_option=true"
            + System.lineSeparator()
            + "invalid_option2=true";

    final Path toml = createTempFile("toml", updatedConfig.getBytes(UTF_8));

    // Parse it.
    parseCommand("--config-file", toml.toString());

    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Unknown options in TOML configuration file: invalid_option, invalid_option2");
  }

  @Test
  public void requiredBlocksSetWhenSpecified() {
    final long blockNumber = 8675309L;
    final String hash = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";

    parseCommand("--required-block=" + blockNumber + "=" + hash);

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Map<Long, Hash>> requiredBlocksArg = ArgumentCaptor.forClass(Map.class);

    verify(mockControllerBuilder).requiredBlocks(requiredBlocksArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

    assertThat(requiredBlocksArg.getValue()).containsOnlyKeys(blockNumber);
    assertThat(requiredBlocksArg.getValue())
        .containsEntry(blockNumber, Hash.fromHexStringLenient(hash));
  }

  @Test
  public void requiredBlocksEmptyWhenNotSpecified() {
    parseCommand();

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Map<Long, Hash>> requiredBlocksArg = ArgumentCaptor.forClass(Map.class);

    verify(mockControllerBuilder).requiredBlocks(requiredBlocksArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

    assertThat(requiredBlocksArg.getValue()).isEmpty();
  }

  @Test
  public void requiredBlocksMulpleBlocksOneArg() {
    final long block1 = 8675309L;
    final long block2 = 5551212L;
    final String hash1 = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";
    final String hash2 = "0x0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    parseCommand("--required-block=" + block1 + "=" + hash1 + "," + block2 + "=" + hash2);

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Map<Long, Hash>> requiredBlocksArg = ArgumentCaptor.forClass(Map.class);

    verify(mockControllerBuilder).requiredBlocks(requiredBlocksArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

    assertThat(requiredBlocksArg.getValue()).containsOnlyKeys(block1, block2);
    assertThat(requiredBlocksArg.getValue())
        .containsEntry(block1, Hash.fromHexStringLenient(hash1));
    assertThat(requiredBlocksArg.getValue())
        .containsEntry(block2, Hash.fromHexStringLenient(hash2));
  }

  @Test
  public void requiredBlocksMultipleBlocksTwoArgs() {
    final long block1 = 8675309L;
    final long block2 = 5551212L;
    final String hash1 = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";
    final String hash2 = "0x0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    parseCommand(
        "--required-block=" + block1 + "=" + hash1, "--required-block=" + block2 + "=" + hash2);

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Map<Long, Hash>> requiredBlocksArg = ArgumentCaptor.forClass(Map.class);

    verify(mockControllerBuilder).requiredBlocks(requiredBlocksArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

    assertThat(requiredBlocksArg.getValue()).containsOnlyKeys(block1, block2);
    assertThat(requiredBlocksArg.getValue())
        .containsEntry(block1, Hash.fromHexStringLenient(hash1));
    assertThat(requiredBlocksArg.getValue())
        .containsEntry(block2, Hash.fromHexStringLenient(hash2));
  }

  @Test
  public void privHttpApisWithPrivacyDisabledLogsWarning() {
    parseCommand("--privacy-enabled=false", "--rpc-http-api", "PRIV", "--rpc-http-enabled");

    verify(mockRunnerBuilder).build();
    verify(mockLogger)
        .warn("Privacy is disabled. Cannot use EEA/PRIV API methods when not using Privacy.");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void privWsApisWithPrivacyDisabledLogsWarning() {
    parseCommand("--privacy-enabled=false", "--rpc-ws-api", "PRIV", "--rpc-ws-enabled");

    verify(mockRunnerBuilder).build();
    verify(mockLogger)
        .warn("Privacy is disabled. Cannot use EEA/PRIV API methods when not using Privacy.");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void eeaHttpApisWithPrivacyDisabledLogsWarning() {
    parseCommand("--privacy-enabled=false", "--rpc-http-api", "EEA", "--rpc-http-enabled");

    verify(mockRunnerBuilder).build();
    verify(mockLogger)
        .warn("Privacy is disabled. Cannot use EEA/PRIV API methods when not using Privacy.");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void eeaWsApisWithPrivacyDisabledLogsWarning() {
    parseCommand("--privacy-enabled=false", "--rpc-ws-api", "EEA", "--rpc-ws-enabled");

    verify(mockRunnerBuilder).build();
    verify(mockLogger)
        .warn("Privacy is disabled. Cannot use EEA/PRIV API methods when not using Privacy.");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void privEnclaveKeyFileDoesNotExist() {
    assumeThat(
        "Ignored if system language is not English",
        System.getProperty("user.language"),
        startsWith("en"));
    parseCommand("--privacy-enabled=true", "--privacy-public-key-file", "/non/existent/file");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith("Problem with privacy-public-key-file");
    assertThat(commandErrorOutput.toString(UTF_8)).contains("No such file");
  }

  @Test
  public void privEnclaveKeyFileInvalidContentTooShort() throws IOException {
    final Path file = createTempFile("privacy.key", "lkjashdfiluhwelrk");
    parseCommand("--privacy-enabled=true", "--privacy-public-key-file", file.toString());

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith("Contents of privacy-public-key-file invalid");
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Last unit does not have enough valid bits");
  }

  @Test
  public void privEnclaveKeyFileInvalidContentNotValidBase64() throws IOException {
    final Path file = createTempFile("privacy.key", "l*jashdfillk9ashdfillkjashdfillkjashdfilrtg=");
    parseCommand("--privacy-enabled=true", "--privacy-public-key-file", file.toString());

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith("Contents of privacy-public-key-file invalid");
    assertThat(commandErrorOutput.toString(UTF_8)).contains("Illegal base64 character");
  }

  @Test
  public void logLevelHasNullAsDefaultValue() {
    final TestBesuCommand command = parseCommand();

    assertThat(command.getLogLevel()).isNull();
  }

  @Test
  public void logLevelIsSetByLoggingOption() {
    final TestBesuCommand command = parseCommand("--logging", "WARN");

    assertThat(command.getLogLevel()).isEqualTo("WARN");
  }

  @Test
  public void assertThatDefaultHttpTimeoutSecondsWorks() {
    parseCommand();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHttpTimeoutSec())
        .isEqualTo(TimeoutOptions.defaultOptions().getTimeoutSeconds());
  }

  @Test
  public void assertThatHttpTimeoutSecondsWorks() {
    parseCommand("--Xhttp-timeout-seconds=513");
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHttpTimeoutSec()).isEqualTo(513);
  }

  @Test
  public void assertThatInvalidHttpTimeoutSecondsFails() {
    parseCommand("--Xhttp-timeout-seconds=abc");
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Invalid value for option", "--Xhttp-timeout-seconds", "abc", "is not a long");
  }

  @Test
  public void assertThatDefaultWsTimeoutSecondsWorks() {
    parseCommand();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
    verify(mockRunnerBuilder).webSocketConfiguration(wsRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();
    assertThat(wsRpcConfigArgumentCaptor.getValue().getTimeoutSec())
        .isEqualTo(TimeoutOptions.defaultOptions().getTimeoutSeconds());
  }

  @Test
  public void assertThatWsTimeoutSecondsWorks() {
    parseCommand("--Xws-timeout-seconds=11112018");
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
    verify(mockRunnerBuilder).webSocketConfiguration(wsRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();
    assertThat(wsRpcConfigArgumentCaptor.getValue().getTimeoutSec()).isEqualTo(11112018);
  }

  @Test
  public void assertThatInvalidWsTimeoutSecondsFails() {
    parseCommand("--Xws-timeout-seconds=abc");
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Invalid value for option", "--Xws-timeout-seconds", "abc", "is not a long");
  }

  @Test
  public void assertThatDuplicatePortSpecifiedFails() {
    parseCommand(
        "--p2p-port=9",
        "--rpc-http-enabled",
        "--rpc-http-port=10",
        "--rpc-ws-port=10",
        "--rpc-ws-enabled");
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Port number '10' has been specified multiple times.");
  }

  @Test
  public void assertThatDuplicatePortZeroSucceeds() {
    parseCommand("--p2p-port=0", "--rpc-http-port=0", "--rpc-ws-port=0");
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void assertThatNoPortsSpecifiedSucceeds() {
    parseCommand();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void assertThatAllPortsSpecifiedSucceeds() {
    parseCommand(
        "--p2p-port=9",
        "--graphql-http-port=10",
        "--rpc-http-port=11",
        "--rpc-ws-port=12",
        "--metrics-port=13",
        "--metrics-push-port=14",
        "--miner-stratum-port=15");
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void compatibilityEth64ForkIdEnabledMustBeUsed() {
    parseCommand("--compatibility-eth64-forkid-enabled");
    verify(mockControllerBuilder)
        .ethProtocolConfiguration(ethProtocolConfigurationArgumentCaptor.capture());
    assertThat(ethProtocolConfigurationArgumentCaptor.getValue().isLegacyEth64ForkIdEnabled())
        .isTrue();
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void compatibilityEth64ForkIdNotEnabledMustBeUsed() {
    parseCommand("--compatibility-eth64-forkid-enabled=false");
    verify(mockControllerBuilder)
        .ethProtocolConfiguration(ethProtocolConfigurationArgumentCaptor.capture());
    assertThat(ethProtocolConfigurationArgumentCaptor.getValue().isLegacyEth64ForkIdEnabled())
        .isFalse();
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void assertThatCompatibilityEth64ForkIdIsNotEnabledByDefault() {
    parseCommand();
    verify(mockControllerBuilder)
        .ethProtocolConfiguration(ethProtocolConfigurationArgumentCaptor.capture());
    assertThat(ethProtocolConfigurationArgumentCaptor.getValue().isLegacyEth64ForkIdEnabled())
        .isFalse();
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void assertThatCompatibilityEth64ForkIdIsPresentInHelpMessage() {
    parseCommand("--help");
    assertThat(commandOutput.toString(UTF_8))
        .contains(
            "--compatibility-eth64-forkid-enabled",
            "Enable the legacy Eth/64 fork id. (default: false)");
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void assertThatCheckPortClashRejectsAsExpectedForEngineApi() throws Exception {
    // use WS port for HTTP
    final int port = 8545;
    parseCommand(
        "--rpc-http-enabled",
        "--rpc-http-port",
        String.valueOf(port),
        "--engine-rpc-port",
        String.valueOf(port));
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains(
            "Port number '8545' has been specified multiple times. Please review the supplied configuration.");
  }

  @Test
  public void staticNodesFileOptionValueAbsentMessage() {
    parseCommand("--static-nodes-file");
    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith("Missing required parameter for option");
  }

  @Test
  public void staticNodesFilesOptionInvalidJSONFormatError() throws IOException {
    final Path tempfile =
        createTempFile(
            "static-nodes-badformat.json",
            "\"enode://c0b0e1151971f8a22dc2493c622317c8706c731f6fcf46d93104ef"
                + "3a08f21f7750b5d5e17f311091f732c9f917b02e1ae6d39f076903779fd1e7"
                + "e7e6cd2fcef6@192.168.1.25:30303\"\n]");
    parseCommand("--static-nodes-file", tempfile.toString());
    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith("Failed to decode:Cannot deserialize ");
  }

  @Test
  public void staticNodesFileOptionFileDoesNotExistMessage() {
    parseCommand("--static-nodes-file", "this-file-does-not-exist-at-all.json");
    assertThat(commandErrorOutput.toString(UTF_8)).contains("Static nodes file", "does not exist");
  }

  @Test
  public void staticNodesFileOptionValidParamenter() throws IOException {
    final Path staticNodeTempFile =
        createTempFile(
            "static-nodes-goodformat.json",
            "[\n"
                + "\"enode://c0b0e1151971f8a22dc2493c622317c8706c731f6fcf46d93104ef"
                + "3a08f21f7750b5d5e17f311091f732c9f917b02e1ae6d39f076903779fd1e7"
                + "e7e6cd2fcef6@192.168.1.25:30303\"\n]");
    parseCommand("--static-nodes-file", staticNodeTempFile.toString());
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void invalidEcCurveFailsWithErrorMessage() throws IOException {
    SignatureAlgorithmFactory.resetInstance();
    final Path genesisFile = createFakeGenesisFile(INVALID_GENESIS_EC_CURVE);

    parseCommand("--genesis-file", genesisFile.toString());
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains(
            "Invalid genesis file configuration for ecCurve. "
                + "abcd is not in the list of valid elliptic curves [secp256k1, secp256r1]");
  }

  @Test
  public void validEcCurveSucceeds() throws IOException {
    SignatureAlgorithmFactory.resetInstance();
    final Path genesisFile = createFakeGenesisFile(VALID_GENESIS_EC_CURVE);

    parseCommand("--genesis-file", genesisFile.toString());
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void nativeSecp256IsDisabled() {
    SignatureAlgorithmFactory.resetInstance();
    parseCommand("--Xsecp256k1-native-enabled", "false");

    verify(mockLogger).info("Using the Java implementation of the signature algorithm");
    assertThat(SignatureAlgorithmFactory.getInstance().isNative()).isFalse();
  }

  @Test
  public void nativeAltBn128IsDisabled() {
    // it is necessary to reset it, because the tested variable
    // is static and it will stay true if it has been set in another test
    // AbstractAltBnPrecompiledContract.resetNative();

    parseCommand("--Xaltbn128-native-enabled", "false");

    verify(mockLogger).info("Using the Java implementation of alt bn128");
    assertThat(AbstractAltBnPrecompiledContract.isNative()).isFalse();
  }

  @Test
  public void nativeLibrariesAreEnabledByDefault() {
    parseCommand();

    assertThat(SignatureAlgorithmFactory.getInstance().isNative()).isTrue();
    verify(mockLogger).info("Using the native implementation of the signature algorithm");

    assertThat(AbstractAltBnPrecompiledContract.isNative()).isTrue();
    verify(mockLogger).info("Using the native implementation of alt bn128");
  }

  @Test
  public void pkiBlockCreationIsDisabledByDefault() {
    parseCommand();

    verifyNoInteractions(mockPkiBlockCreationConfigProvider);
  }

  @Test
  public void pkiBlockCreationKeyStoreFileRequired() {
    parseCommand(
        "--Xpki-block-creation-enabled",
        "--Xpki-block-creation-keystore-password-file",
        "/tmp/pwd");

    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("KeyStore file is required when PKI Block Creation is enabled");
  }

  @Test
  public void pkiBlockCreationPasswordFileRequired() {
    parseCommand(
        "--Xpki-block-creation-enabled", "--Xpki-block-creation-keystore-file", "/tmp/keystore");

    assertThat(commandErrorOutput.toString(UTF_8))
        .contains(
            "File containing password to unlock keystore is required when PKI Block Creation is enabled");
  }

  @Test
  public void pkiBlockCreationFullConfig(final @TempDir Path pkiTempFolder) throws Exception {
    // Create temp file with password
    final File pwdFile = pkiTempFolder.resolve("pwd").toFile();
    FileUtils.writeStringToFile(pwdFile, "foo", UTF_8);

    parseCommand(
        "--Xpki-block-creation-enabled",
        "--Xpki-block-creation-keystore-type",
        "JKS",
        "--Xpki-block-creation-keystore-file",
        "/tmp/keystore",
        "--Xpki-block-creation-keystore-password-file",
        pwdFile.getAbsolutePath(),
        "--Xpki-block-creation-keystore-certificate-alias",
        "anAlias",
        "--Xpki-block-creation-truststore-type",
        "JKS",
        "--Xpki-block-creation-truststore-file",
        "/tmp/truststore",
        "--Xpki-block-creation-truststore-password-file",
        pwdFile.getAbsolutePath(),
        "--Xpki-block-creation-crl-file",
        "/tmp/crl");

    final PkiKeyStoreConfiguration pkiKeyStoreConfig =
        pkiKeyStoreConfigurationArgumentCaptor.getValue();

    assertThat(pkiKeyStoreConfig).isNotNull();
    assertThat(pkiKeyStoreConfig.getKeyStoreType()).isEqualTo("JKS");
    assertThat(pkiKeyStoreConfig.getKeyStorePath()).isEqualTo(Path.of("/tmp/keystore"));
    assertThat(pkiKeyStoreConfig.getKeyStorePassword()).isEqualTo("foo");
    assertThat(pkiKeyStoreConfig.getCertificateAlias()).isEqualTo("anAlias");
    assertThat(pkiKeyStoreConfig.getTrustStoreType()).isEqualTo("JKS");
    assertThat(pkiKeyStoreConfig.getTrustStorePath()).isEqualTo(Path.of("/tmp/truststore"));
    assertThat(pkiKeyStoreConfig.getTrustStorePassword()).isEqualTo("foo");
    assertThat(pkiKeyStoreConfig.getCrlFilePath()).hasValue(Path.of("/tmp/crl"));
  }

  @Test
  public void logsWarningWhenFailToLoadJemalloc() {
    assumeThat(PlatformDetector.getOSType(), is("linux"));
    setEnvironmentVariable("BESU_USING_JEMALLOC", "true");
    parseCommand();
    verify(mockLogger)
        .warn(
            eq(
                "BESU_USING_JEMALLOC is present but we failed to load jemalloc library to get the version"),
            any(Throwable.class));
  }

  @Test
  public void logsSuggestInstallingJemallocWhenEnvVarNotPresent() {
    assumeThat(PlatformDetector.getOSType(), is("linux"));
    parseCommand();
    verify(mockLogger)
        .info("jemalloc library not found, memory usage may be reduced by installing it");
  }

  @Test
  public void logWarnIfFastSyncMinPeersUsedWithFullSync() {
    parseCommand("--sync-mode", "FULL", "--fast-sync-min-peers", "1");
    verify(mockLogger).warn("--sync-min-peers is ignored in FULL sync-mode");
  }

  @Test
  public void presentRequiredOptionShouldPass() {
    parseCommandWithRequiredOption("--accept-terms-and-conditions", "true");
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void missingRequiredOptionShouldFail() {
    parseCommandWithRequiredOption();
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith(
            "Missing required option: '--accept-terms-and-conditions=<acceptTermsAndConditions>'");
  }

  @Test
  public void havingRequiredOptionInEnvVarShouldFail() {
    setEnvironmentVariable("BESU_ACCEPT_TERMS_AND_CONDITIONS", "true");
    parseCommandWithRequiredOption();
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith(
            "Missing required option: '--accept-terms-and-conditions=<acceptTermsAndConditions>'");
  }

  @Test
  public void havingRequiredOptionInConfigShouldFail() throws IOException {
    final Path toml = createTempFile("toml", "accept-terms-and-conditions=true\n");
    parseCommandWithRequiredOption("--config-file", toml.toString());
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith(
            "Missing required option: '--accept-terms-and-conditions=<acceptTermsAndConditions>'");
  }

  @Test
  public void checkpointPostMergeShouldFailWhenGenesisHasNoTTD() throws IOException {
    final String configText =
        Resources.toString(
            Resources.getResource("invalid_post_merge_near_head_checkpoint.json"),
            StandardCharsets.UTF_8);
    final Path genesisFile = createFakeGenesisFile(new JsonObject(configText));

    parseCommand(
        "--genesis-file",
        genesisFile.toString(),
        "--sync-mode",
        "CHECKPOINT",
        "--Xcheckpoint-post-merge-enabled");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("PoS checkpoint sync requires TTD in the genesis file");
  }

  @Test
  public void checkpointPostMergeShouldFailWhenGenesisUsesCheckpointFromPreMerge() {
    // using the default genesis which has a checkpoint sync block prior to the merge
    parseCommand("--sync-mode", "CHECKPOINT", "--Xcheckpoint-post-merge-enabled");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains(
            "PoS checkpoint sync requires a block with total difficulty greater or equal than the TTD");
  }

  @Test
  public void checkpointPostMergeShouldFailWhenSyncModeIsNotCheckpoint() {

    parseCommand("--sync-mode", "SNAP", "--Xcheckpoint-post-merge-enabled");
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("--Xcheckpoint-post-merge-enabled can only be used with CHECKPOINT sync-mode");
  }

  @Test
  public void checkpointPostMergeWithPostMergeBlockSucceeds() throws IOException {
    final String configText =
        Resources.toString(
            Resources.getResource("valid_post_merge_near_head_checkpoint.json"),
            StandardCharsets.UTF_8);
    final Path genesisFile = createFakeGenesisFile(new JsonObject(configText));

    parseCommand(
        "--genesis-file",
        genesisFile.toString(),
        "--sync-mode",
        "CHECKPOINT",
        "--Xcheckpoint-post-merge-enabled");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void checkpointPostMergeWithPostMergeBlockTDEqualsTTDSucceeds() throws IOException {
    final String configText =
        Resources.toString(
            Resources.getResource("valid_pos_checkpoint_pos_TD_equals_TTD.json"),
            StandardCharsets.UTF_8);
    final Path genesisFile = createFakeGenesisFile(new JsonObject(configText));

    parseCommand(
        "--genesis-file",
        genesisFile.toString(),
        "--sync-mode",
        "CHECKPOINT",
        "--Xcheckpoint-post-merge-enabled");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void checkpointMergeAtGenesisWithGenesisBlockDifficultyZeroFails() throws IOException {
    final String configText =
        Resources.toString(
            Resources.getResource("invalid_post_merge_merge_at_genesis.json"),
            StandardCharsets.UTF_8);
    final Path genesisFile = createFakeGenesisFile(new JsonObject(configText));

    parseCommand(
        "--genesis-file",
        genesisFile.toString(),
        "--sync-mode",
        "CHECKPOINT",
        "--Xcheckpoint-post-merge-enabled");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains(
            "PoS checkpoint sync can't be used with TTD = 0 and checkpoint totalDifficulty = 0");
  }

  @Test
  public void kzgTrustedSetupFileRequiresDataBlobEnabledNetwork() throws IOException {
    final Path genesisFileWithoutBlobs =
        createFakeGenesisFile(new JsonObject().put("config", new JsonObject()));
    parseCommand(
        "--genesis-file",
        genesisFileWithoutBlobs.toString(),
        "--kzg-trusted-setup",
        "/etc/besu/kzg-trusted-setup.txt");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("--kzg-trusted-setup can only be specified on networks with data blobs enabled");
  }

  @Test
  public void kzgTrustedSetupFileLoadedWithCustomGenesisFile()
      throws IOException, URISyntaxException {
    final Path testSetupAbsolutePath =
        Path.of(BesuCommandTest.class.getResource("/trusted_setup.txt").toURI());
    final Path genesisFileWithBlobs = createFakeGenesisFile(GENESIS_WITH_DATA_BLOBS_ENABLED);
    parseCommand(
        "--genesis-file",
        genesisFileWithBlobs.toString(),
        "--kzg-trusted-setup",
        testSetupAbsolutePath.toString());

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void txpoolDefaultSaveFileRelativeToDataPath() throws IOException {
    final Path dataDir = Files.createTempDirectory("data-dir");
    parseCommand("--data-path", dataDir.toString(), "--tx-pool-enable-save-restore", "true");
    verify(mockControllerBuilder)
        .transactionPoolConfiguration(transactionPoolConfigCaptor.capture());

    assertThat(transactionPoolConfigCaptor.getValue().getSaveFile())
        .isEqualTo(dataDir.resolve(TransactionPoolConfiguration.DEFAULT_SAVE_FILE_NAME).toFile());

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void txpoolCustomSaveFileRelativeToDataPath() throws IOException {
    final Path dataDir = Files.createTempDirectory("data-dir");
    dataDir.toFile().deleteOnExit();
    final File saveFile = Files.createTempFile(dataDir, "txpool", "save").toFile();
    saveFile.deleteOnExit();
    parseCommand(
        "--data-path",
        dataDir.toString(),
        "--tx-pool-enable-save-restore",
        "true",
        "--tx-pool-save-file",
        saveFile.getName());
    verify(mockControllerBuilder)
        .transactionPoolConfiguration(transactionPoolConfigCaptor.capture());

    final File configuredSaveFile = transactionPoolConfigCaptor.getValue().getSaveFile();
    assertThat(configuredSaveFile).isEqualTo(saveFile);
    assertThat(configuredSaveFile.toPath().getParent()).isEqualTo(dataDir);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void txpoolSaveFileAbsolutePathOutsideDataPath() throws IOException {
    final Path dataDir = Files.createTempDirectory("data-dir");
    dataDir.toFile().deleteOnExit();
    final File saveFile = File.createTempFile("txpool", "dump");
    saveFile.deleteOnExit();
    parseCommand(
        "--data-path",
        dataDir.toString(),
        "--tx-pool-enable-save-restore",
        "true",
        "--tx-pool-save-file",
        saveFile.getAbsolutePath());
    verify(mockControllerBuilder)
        .transactionPoolConfiguration(transactionPoolConfigCaptor.capture());

    final File configuredSaveFile = transactionPoolConfigCaptor.getValue().getSaveFile();
    assertThat(configuredSaveFile).isEqualTo(saveFile);
    assertThat(configuredSaveFile.toPath().getParent()).isNotEqualTo(dataDir);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void txpoolForcePriceBumpToZeroWhenZeroBaseFeeMarket() throws IOException {
    final Path genesisFile = createFakeGenesisFile(GENESIS_WITH_ZERO_BASE_FEE_MARKET);
    parseCommand("--genesis-file", genesisFile.toString());
    verify(mockControllerBuilder)
        .transactionPoolConfiguration(transactionPoolConfigCaptor.capture());

    final Percentage priceBump = transactionPoolConfigCaptor.getValue().getPriceBump();
    assertThat(priceBump).isEqualTo(Percentage.ZERO);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void txpoolPriceBumpOptionIncompatibleWithZeroWhenZeroBaseFeeMarket() throws IOException {
    final Path genesisFile = createFakeGenesisFile(GENESIS_WITH_ZERO_BASE_FEE_MARKET);
    parseCommand("--genesis-file", genesisFile.toString(), "--tx-pool-price-bump", "5");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Price bump option is not compatible with zero base fee market");
  }

  @Test
  public void txpoolForcePriceBumpToZeroWhenMinGasPriceZero() {
    parseCommand("--min-gas-price", "0");
    verify(mockControllerBuilder)
        .transactionPoolConfiguration(transactionPoolConfigCaptor.capture());

    final Percentage priceBump = transactionPoolConfigCaptor.getValue().getPriceBump();
    assertThat(priceBump).isEqualTo(Percentage.ZERO);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void txpoolPriceBumpKeepItsValueIfSetEvenWhenMinGasPriceZero() {
    parseCommand("--min-gas-price", "0", "--tx-pool-price-bump", "1");
    verify(mockControllerBuilder)
        .transactionPoolConfiguration(transactionPoolConfigCaptor.capture());

    final Percentage priceBump = transactionPoolConfigCaptor.getValue().getPriceBump();
    assertThat(priceBump).isEqualTo(Percentage.fromInt(1));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void txpoolWhenNotSetForceTxPoolMinGasPriceToZeroWhenMinGasPriceZero() {
    parseCommand("--min-gas-price", "0");
    verify(mockControllerBuilder)
        .transactionPoolConfiguration(transactionPoolConfigCaptor.capture());

    final Wei txPoolMinGasPrice = transactionPoolConfigCaptor.getValue().getMinGasPrice();
    assertThat(txPoolMinGasPrice).isEqualTo(Wei.ZERO);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
    verify(mockLogger, atLeast(1))
        .warn(
            contains(
                "Forcing tx-pool-min-gas-price=0, since it cannot be greater than the value of min-gas-price"));
  }

  @Test
  public void txpoolTxPoolMinGasPriceMustNotBeGreaterThanMinGasPriceZero() {
    parseCommand("--min-gas-price", "100", "--tx-pool-min-gas-price", "101");
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("tx-pool-min-gas-price cannot be greater than the value of min-gas-price");
  }

  @Test
  public void snapsyncHealingOptionShouldBeDisabledByDefault() {
    final TestBesuCommand besuCommand = parseCommand();
    assertThat(besuCommand.unstableSynchronizerOptions.isSnapsyncFlatDbHealingEnabled()).isFalse();
  }

  @Test
  public void snapsyncHealingOptionShouldWork() {
    final TestBesuCommand besuCommand =
        parseCommand("--Xsnapsync-synchronizer-flat-db-healing-enabled", "true");
    assertThat(besuCommand.unstableSynchronizerOptions.isSnapsyncFlatDbHealingEnabled()).isTrue();
  }

  @Test
  public void snapsyncForHealingFeaturesShouldFailWhenHealingIsNotEnabled() {
    parseCommand("--Xsnapsync-synchronizer-flat-account-healed-count-per-request", "100");
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains(
            "--Xsnapsync-synchronizer-flat option can only be used when -Xsnapsync-synchronizer-flat-db-healing-enabled is true");

    parseCommand("--Xsnapsync-synchronizer-flat-slot-healed-count-per-request", "100");
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains(
            "--Xsnapsync-synchronizer-flat option can only be used when -Xsnapsync-synchronizer-flat-db-healing-enabled is true");
  }

  @Test
  public void cacheLastBlocksOptionShouldWork() {
    int numberOfBlocksToCache = 512;
    parseCommand("--cache-last-blocks", String.valueOf(numberOfBlocksToCache));
    verify(mockControllerBuilder).cacheLastBlocks(intArgumentCaptor.capture());
    verify(mockControllerBuilder).build();

    assertThat(intArgumentCaptor.getValue()).isEqualTo(numberOfBlocksToCache);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }
}
