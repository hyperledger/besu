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
import static org.hyperledger.besu.cli.config.NetworkName.CLASSIC;
import static org.hyperledger.besu.cli.config.NetworkName.DEV;
import static org.hyperledger.besu.cli.config.NetworkName.EPHEMERY;
import static org.hyperledger.besu.cli.config.NetworkName.EXPERIMENTAL_EIPS;
import static org.hyperledger.besu.cli.config.NetworkName.FUTURE_EIPS;
import static org.hyperledger.besu.cli.config.NetworkName.HOLESKY;
import static org.hyperledger.besu.cli.config.NetworkName.LUKSO;
import static org.hyperledger.besu.cli.config.NetworkName.MAINNET;
import static org.hyperledger.besu.cli.config.NetworkName.MORDOR;
import static org.hyperledger.besu.cli.config.NetworkName.SEPOLIA;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis.ENGINE;
import static org.hyperledger.besu.ethereum.p2p.config.DefaultDiscoveryConfiguration.MAINNET_BOOTSTRAP_NODES;
import static org.hyperledger.besu.ethereum.p2p.config.DefaultDiscoveryConfiguration.MAINNET_DISCOVERY_URL;
import static org.hyperledger.besu.ethereum.p2p.config.DefaultDiscoveryConfiguration.SEPOLIA_BOOTSTRAP_NODES;
import static org.hyperledger.besu.ethereum.p2p.config.DefaultDiscoveryConfiguration.SEPOLIA_DISCOVERY_URL;
import static org.hyperledger.besu.plugin.services.storage.DataStorageFormat.BONSAI;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.hyperledger.besu.BesuInfo;
import org.hyperledger.besu.cli.config.EthNetworkConfig;
import org.hyperledger.besu.cli.config.NetworkName;
import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.config.MergeConfiguration;
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
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.SyncMode;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.evm.precompile.AbstractAltBnPrecompiledContract;
import org.hyperledger.besu.evm.precompile.KZGPointEvalPrecompiledContract;
import org.hyperledger.besu.metrics.StandardMetricCategory;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
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
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.io.Resources;
import io.vertx.core.json.JsonObject;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.toml.Toml;
import org.apache.tuweni.toml.TomlParseResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

@ExtendWith(MockitoExtension.class)
public class BesuCommandTest extends CommandTestAbstract {

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

    MergeConfiguration.setMergeEnabled(false);
  }

  @AfterEach
  public void tearDown() {

    MergeConfiguration.setMergeEnabled(false);
  }

  @Test
  public void testGenesisOverrideOptions() throws Exception {
    parseCommand("--override-genesis-config", "shanghaiTime=123");

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    final EthNetworkConfig config = networkArg.getValue();
    // mainnet defaults
    assertThat(config.networkId()).isEqualTo(BigInteger.valueOf(1));

    // assert that shanghaiTime override is applied
    final GenesisConfig actualGenesisConfig = (config.genesisConfig());
    assertThat(actualGenesisConfig).isNotNull();
    assertThat(actualGenesisConfig.getConfigOptions().getShanghaiTime()).isNotEmpty();
    assertThat(actualGenesisConfig.getConfigOptions().getShanghaiTime().getAsLong()).isEqualTo(123);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void testGenesisOverrideOptionsWithCustomGenesis() throws Exception {
    final Path genesisFile = createFakeGenesisFile(GENESIS_VALID_JSON);

    parseCommand(
        "--genesis-file", genesisFile.toString(), "--override-genesis-config", "shanghaiTime=123");

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    final EthNetworkConfig config = networkArg.getValue();
    assertThat(config.bootNodes()).isEmpty();
    assertThat(config.dnsDiscoveryUrl()).isNull();
    assertThat(config.networkId()).isEqualTo(BigInteger.valueOf(3141592));

    // then assert that the shanghaiTime is applied
    final GenesisConfig actualGenesisConfig = (config.genesisConfig());
    assertThat(actualGenesisConfig).isNotNull();
    assertThat(actualGenesisConfig.getConfigOptions().getShanghaiTime()).isNotEmpty();
    assertThat(actualGenesisConfig.getConfigOptions().getShanghaiTime().getAsLong()).isEqualTo(123);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
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
    verify(mockRunnerBuilder).discoveryEnabled(eq(true));
    verify(mockRunnerBuilder)
        .ethNetworkConfig(
            new EthNetworkConfig(
                GenesisConfig.fromResource(MAINNET.getGenesisFile()),
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

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(ethNetworkArg.capture(), any());
    final ArgumentCaptor<MiningConfiguration> miningArg =
        ArgumentCaptor.forClass(MiningConfiguration.class);
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
    assertThat(ethNetworkArg.getValue().networkId()).isEqualTo(1);
    assertThat(ethNetworkArg.getValue().bootNodes()).isEqualTo(MAINNET_BOOTSTRAP_NODES);
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

    final String expectedOutputStart = "Unable to read from empty TOML configuration file.";
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
    assertThat(ethNetworkConfigArgumentCaptor.getValue().bootNodes()).isEmpty();

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
    options.remove(spec.optionsMap().get("--print-paths-and-exit"));

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
  public void dataDirOptionMustBeUsed(final @TempDir Path path) {

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

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue().genesisConfig())
        .isEqualTo(GenesisConfig.fromConfig(encodeJsonGenesis(GENESIS_VALID_JSON)));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void testGenesisPathEthOptions() throws Exception {
    final Path genesisFile = createFakeGenesisFile(GENESIS_VALID_JSON);

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    parseCommand("--genesis-file", genesisFile.toString());

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    final EthNetworkConfig config = networkArg.getValue();
    assertThat(config.bootNodes()).isEmpty();
    assertThat(config.dnsDiscoveryUrl()).isNull();
    assertThat(config.networkId()).isEqualTo(BigInteger.valueOf(3141592));
  }

  @Test
  public void testGenesisPathMainnetEthConfig() {
    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    parseCommand("--network", "mainnet");

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    final EthNetworkConfig config = networkArg.getValue();
    assertThat(config.bootNodes()).isEqualTo(MAINNET_BOOTSTRAP_NODES);
    assertThat(config.dnsDiscoveryUrl()).isEqualTo(MAINNET_DISCOVERY_URL);
    assertThat(config.networkId()).isEqualTo(BigInteger.valueOf(1));

    verify(mockLogger, never()).warn(contains("Mainnet is deprecated and will be shutdown"));
  }

  @Test
  public void testGenesisPathSepoliaEthConfig() {
    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    parseCommand("--network", "sepolia");

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    final EthNetworkConfig config = networkArg.getValue();
    assertThat(config.bootNodes()).isEqualTo(SEPOLIA_BOOTSTRAP_NODES);
    assertThat(config.dnsDiscoveryUrl()).isEqualTo(SEPOLIA_DISCOVERY_URL);
    assertThat(config.networkId()).isEqualTo(BigInteger.valueOf(11155111));
  }

  @Test
  public void testGenesisPathFutureEipsEthConfig() {
    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    parseCommand("--network", "future_eips");

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    final EthNetworkConfig config = networkArg.getValue();
    assertThat(config.bootNodes()).isEmpty();
    assertThat(config.dnsDiscoveryUrl()).isNull();
    assertThat(config.networkId()).isEqualTo(BigInteger.valueOf(2022));
  }

  @Test
  public void testGenesisPathExperimentalEipsEthConfig() {
    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    parseCommand("--network", "experimental_eips");

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    final EthNetworkConfig config = networkArg.getValue();
    assertThat(config.bootNodes()).isEmpty();
    assertThat(config.dnsDiscoveryUrl()).isNull();
    assertThat(config.networkId()).isEqualTo(BigInteger.valueOf(2023));
  }

  @Test
  public void genesisAndNetworkMustNotBeUsedTogether() throws Exception {
    final Path genesisFile = createFakeGenesisFile(GENESIS_VALID_JSON);

    parseCommand("--genesis-file", genesisFile.toString(), "--network", "mainnet");

    verifyNoInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith("--network option and --genesis-file option can't be used at the same time.");
  }

  @Test
  public void nonExistentGenesisGivesError() {
    final String nonExistentGenesis = "non-existent-genesis.json";
    parseCommand("--genesis-file", nonExistentGenesis);

    verifyNoInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).startsWith("Unable to load genesis file");
    assertThat(commandErrorOutput.toString(UTF_8)).contains(nonExistentGenesis);
  }

  @Test
  public void testDnsDiscoveryUrlEthConfig() {
    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    parseCommand(
        "--discovery-dns-url",
        "enrtree://AM5FCQLWIZX2QFPNJAP7VUERCCRNGRHWZG3YYHIUV7BVDQ5FDPRT2@nodes.example.org");

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    final EthNetworkConfig config = networkArg.getValue();
    assertThat(config.dnsDiscoveryUrl())
        .isEqualTo(
            "enrtree://AM5FCQLWIZX2QFPNJAP7VUERCCRNGRHWZG3YYHIUV7BVDQ5FDPRT2@nodes.example.org");
  }

  @Test
  public void testDnsDiscoveryUrlOverridesNetworkEthConfig() {
    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    parseCommand(
        "--network",
        "dev",
        "--discovery-dns-url",
        "enrtree://AM5FCQLWIZX2QFPNJAP7VUERCCRNGRHWZG3YYHIUV7BVDQ5FDPRT2@nodes.example.org");

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    final EthNetworkConfig config = networkArg.getValue();
    assertThat(config.dnsDiscoveryUrl())
        .isEqualTo(
            "enrtree://AM5FCQLWIZX2QFPNJAP7VUERCCRNGRHWZG3YYHIUV7BVDQ5FDPRT2@nodes.example.org");
  }

  @Test
  public void defaultNetworkIdAndBootnodesForCustomNetworkOptions() throws Exception {
    final Path genesisFile = createFakeGenesisFile(GENESIS_VALID_JSON);

    parseCommand("--genesis-file", genesisFile.toString());

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue().genesisConfig())
        .isEqualTo(GenesisConfig.fromConfig(encodeJsonGenesis(GENESIS_VALID_JSON)));
    assertThat(networkArg.getValue().bootNodes()).isEmpty();
    assertThat(networkArg.getValue().networkId()).isEqualTo(GENESIS_CONFIG_TEST_CHAINID);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void defaultNetworkIdForInvalidGenesisMustBeMainnetNetworkId() throws Exception {
    final Path genesisFile = createFakeGenesisFile(GENESIS_INVALID_DATA);

    parseCommand("--genesis-file", genesisFile.toString());

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue().genesisConfig())
        .isEqualTo(GenesisConfig.fromConfig(encodeJsonGenesis(GENESIS_INVALID_DATA)));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void predefinedNetworkIdsMustBeEqualToChainIds() {
    // check the network id against the one in mainnet genesis config
    // it implies that EthNetworkConfig.mainnet().getNetworkId() returns a value equals to the chain
    // id
    // in this network genesis file.

    final var genesisConfig =
        EthNetworkConfig.getNetworkConfig(MAINNET).genesisConfig().getConfigOptions();
    assertThat(genesisConfig.getChainId().isPresent()).isTrue();
    assertThat(genesisConfig.getChainId().get())
        .isEqualTo(EthNetworkConfig.getNetworkConfig(MAINNET).networkId());
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

    verify(mockRunnerBuilder).discoveryEnabled(eq(true));
    verify(mockRunnerBuilder).build();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void discoveryOptionValueFalseMustBeUsed() {
    parseCommand("--discovery-enabled", "false");

    verify(mockRunnerBuilder).discoveryEnabled(eq(false));
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

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    final EthNetworkConfig config = networkArg.getValue();
    assertThat(config.dnsDiscoveryUrl()).isEqualTo(DNS_DISCOVERY_URL);

    assertThat(config.bootNodes())
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
    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    final EthNetworkConfig config = networkArg.getValue();
    assertThat(config.dnsDiscoveryUrl()).isEqualTo(discoveryDnsUrlCliArg);
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
    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    final EthNetworkConfig config = networkArg.getValue();
    assertThat(config.bootNodes()).extracting(EnodeURL::toURI).containsExactly(bootnode);

    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void poaDiscoveryRetryBootnodesValueTrueMustBeUsed() {
    parseCommand("--poa-discovery-retry-bootnodes", "true");

    verify(mockRunnerBuilder).poaDiscoveryRetryBootnodes(eq(true));
    verify(mockRunnerBuilder).build();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void poaDiscoveryRetryBootnodesValueFalseMustBeUsed() {
    parseCommand("--poa-discovery-retry-bootnodes", "false");

    verify(mockRunnerBuilder).poaDiscoveryRetryBootnodes(eq(false));
    verify(mockRunnerBuilder).build();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingWithBootnodesOptionButNoValueMustPassEmptyBootnodeList() {
    parseCommand("--bootnodes");

    verify(mockRunnerBuilder).ethNetworkConfig(ethNetworkConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(ethNetworkConfigArgumentCaptor.getValue().bootNodes()).isEmpty();

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

    assertThat(ethNetworkConfigArgumentCaptor.getValue().bootNodes())
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
    verifyNoInteractions(mockRunnerBuilder);
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
    verifyNoInteractions(mockRunnerBuilder);
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
  public void syncMode_full_requires_bonsaiLimitTrieLogsToBeDisabled() {
    parseCommand("--sync-mode", "FULL", "--bonsai-limit-trie-logs-enabled=false");
    verify(mockControllerBuilder).synchronizerConfiguration(syncConfigurationCaptor.capture());

    final SynchronizerConfiguration syncConfig = syncConfigurationCaptor.getValue();
    assertThat(syncConfig.getSyncMode()).isEqualTo(SyncMode.FULL);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void syncMode_invalid() {
    parseCommand("--sync-mode", "bogus");
    verifyNoInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains(
            "Invalid value for option '--sync-mode': expected one of [FULL, FAST, SNAP, CHECKPOINT] (case-insensitive) but was 'bogus'");
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

    verifyNoInteractions(mockRunnerBuilder);

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
    assertThat(syncConfig.getSyncMinimumPeerCount()).isEqualTo(5);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void parsesValidFastSyncMinPeersOption() {
    parseCommand("--sync-mode", "FAST", "--fast-sync-min-peers", "11");
    verify(mockControllerBuilder).synchronizerConfiguration(syncConfigurationCaptor.capture());

    final SynchronizerConfiguration syncConfig = syncConfigurationCaptor.getValue();
    assertThat(syncConfig.getSyncMode()).isEqualTo(SyncMode.FAST);
    assertThat(syncConfig.getSyncMinimumPeerCount()).isEqualTo(11);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void parsesValidSnapSyncMinPeersOption() {
    parseCommand("--sync-mode", "SNAP", "--sync-min-peers", "11");
    verify(mockControllerBuilder).synchronizerConfiguration(syncConfigurationCaptor.capture());

    final SynchronizerConfiguration syncConfig = syncConfigurationCaptor.getValue();
    assertThat(syncConfig.getSyncMode()).isEqualTo(SyncMode.SNAP);
    assertThat(syncConfig.getSyncMinimumPeerCount()).isEqualTo(11);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void parsesValidSyncMinPeersOption() {
    parseCommand("--sync-mode", "FAST", "--sync-min-peers", "11");
    verify(mockControllerBuilder).synchronizerConfiguration(syncConfigurationCaptor.capture());

    final SynchronizerConfiguration syncConfig = syncConfigurationCaptor.getValue();
    assertThat(syncConfig.getSyncMode()).isEqualTo(SyncMode.FAST);
    assertThat(syncConfig.getSyncMinimumPeerCount()).isEqualTo(11);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void parsesInvalidFastSyncMinPeersOptionWrongFormatShouldFail() {

    parseCommand("--sync-mode", "FAST", "--fast-sync-min-peers", "ten");
    verifyNoInteractions(mockRunnerBuilder);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Invalid value for option '--fast-sync-min-peers': 'ten' is not an int");
  }

  @Test
  public void netRestrictParsedCorrectly() {
    final String subnet1 = "127.0.0.1/24";
    final String subnet2 = "10.0.0.1/24";
    parseCommand("--net-restrict", String.join(",", subnet1, subnet2));
    verify(mockRunnerBuilder).allowedSubnets(allowedSubnetsArgumentCaptor.capture());
    assertThat(allowedSubnetsArgumentCaptor.getValue().size()).isEqualTo(2);
    assertThat(allowedSubnetsArgumentCaptor.getValue().get(0).getCidrSignature())
        .isEqualTo(subnet1);
    assertThat(allowedSubnetsArgumentCaptor.getValue().get(1).getCidrSignature())
        .isEqualTo(subnet2);
  }

  @Test
  public void netRestrictInvalidShouldFail() {
    final String subnet = "127.0.0.1/abc";
    parseCommand("--net-restrict", subnet);
    verifyNoInteractions(mockRunnerBuilder);
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Invalid value for option '--net-restrict'");
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
    verifyNoInteractions(mockRunnerBuilder);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains(
            "The `--ethstats-contact` requires ethstats server URL to be provided. Either remove --ethstats-contact or provide a URL (via --ethstats=nodename:secret@host:port)");
  }

  @Test
  public void diffbasedLimitTrieLogsEnabledByDefault() {
    parseCommand();
    verify(mockControllerBuilder)
        .dataStorageConfiguration(dataStorageConfigurationArgumentCaptor.capture());

    final DataStorageConfiguration dataStorageConfiguration =
        dataStorageConfigurationArgumentCaptor.getValue();
    assertThat(dataStorageConfiguration.getDataStorageFormat()).isEqualTo(BONSAI);
    assertThat(
            dataStorageConfiguration
                .getDiffBasedSubStorageConfiguration()
                .getLimitTrieLogsEnabled())
        .isTrue();
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void bonsaiLimitTrieLogsDisabledWhenFullSyncEnabled() {
    parseCommand("--sync-mode=FULL");

    verify(mockControllerBuilder)
        .dataStorageConfiguration(dataStorageConfigurationArgumentCaptor.capture());

    final DataStorageConfiguration dataStorageConfiguration =
        dataStorageConfigurationArgumentCaptor.getValue();
    assertThat(dataStorageConfiguration.getDataStorageFormat()).isEqualTo(BONSAI);
    assertThat(
            dataStorageConfiguration
                .getDiffBasedSubStorageConfiguration()
                .getLimitTrieLogsEnabled())
        .isFalse();
    verify(mockLogger)
        .warn(
            "Forcing {}, since it cannot be enabled with --sync-mode={} and --data-storage-format={}.",
            "--bonsai-limit-trie-logs-enabled=false",
            SyncMode.FULL,
            DataStorageFormat.BONSAI);
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void parsesInvalidWhenFullSyncAndBonsaiLimitTrieLogsExplicitlyTrue() {
    parseCommand("--sync-mode=FULL", "--bonsai-limit-trie-logs-enabled=true");

    verifyNoInteractions(mockRunnerBuilder);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains(
            "Cannot enable --bonsai-limit-trie-logs-enabled with --sync-mode=FULL and --data-storage-format=BONSAI. You must set --bonsai-limit-trie-logs-enabled=false or use a different sync-mode");
  }

  @Test
  public void parsesValidBonsaiHistoricalBlockLimitOption() {
    parseCommand(
        "--bonsai-limit-trie-logs-enabled=false",
        "--data-storage-format",
        "BONSAI",
        "--bonsai-historical-block-limit",
        "11");
    verify(mockControllerBuilder)
        .dataStorageConfiguration(dataStorageConfigurationArgumentCaptor.capture());

    final DataStorageConfiguration dataStorageConfiguration =
        dataStorageConfigurationArgumentCaptor.getValue();
    assertThat(dataStorageConfiguration.getDataStorageFormat()).isEqualTo(BONSAI);
    assertThat(dataStorageConfiguration.getDiffBasedSubStorageConfiguration().getMaxLayersToLoad())
        .isEqualTo(11);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void parsesInvalidBonsaiHistoricalBlockLimitOption() {

    parseCommand("--data-storage-format", "BONSAI", "--bonsai-maximum-back-layers-to-load", "ten");

    verifyNoInteractions(mockRunnerBuilder);
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
    verifyNoInteractions(mockRunnerBuilder);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains(
            "The `--Xdns-update-enabled` requires dns to be enabled. Either remove --Xdns-update-enabled or specify dns is enabled (--Xdns-enabled)");
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
    parseCommand("--rpc-http-enabled", "--engine-jwt-secret", "/tmp/fakeKey.hex");
    verify(mockRunnerBuilder).engineJsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    assertThat(jsonRpcConfigArgumentCaptor.getValue().isAuthenticationEnabled()).isTrue();
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void engineApiDisableAuthOptions() {
    parseCommand(
        "--rpc-http-enabled", "--engine-jwt-disabled", "--engine-jwt-secret", "/tmp/fakeKey.hex");
    verify(mockRunnerBuilder).engineJsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    assertThat(jsonRpcConfigArgumentCaptor.getValue().isAuthenticationEnabled()).isFalse();
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
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
  public void metricsUnknownCategoryRaiseError() {
    parseCommand("--metrics-enabled", "--metrics-category", "UNKNOWN_CATEGORY");

    verifyNoInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith("--metrics-categories contains unknown categories: [UNKNOWN_CATEGORY]");
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

    verifyNoInteractions(mockRunnerBuilder);

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

  @Test
  public void devModeOptionMustBeUsed() {
    parseCommand("--network", "dev");

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue()).isEqualTo(EthNetworkConfig.getNetworkConfig(DEV));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void futureEipsValuesAreUsed() {
    parseCommand("--network", "future_eips");

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
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

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
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

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
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

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue()).isEqualTo(EthNetworkConfig.getNetworkConfig(HOLESKY));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

    verify(mockLogger, never()).warn(contains("Holesky is deprecated and will be shutdown"));
  }

  @Test
  public void holeskyTargetGasLimitIsSetToHoleskyDefaultWhenNoValueSpecified() {
    parseCommand("--network", "holesky");

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    final ArgumentCaptor<MiningConfiguration> miningArg =
        ArgumentCaptor.forClass(MiningConfiguration.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).miningParameters(miningArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue()).isEqualTo(EthNetworkConfig.getNetworkConfig(HOLESKY));

    assertThat(miningArg.getValue().getCoinbase()).isEqualTo(Optional.empty());
    assertThat(miningArg.getValue().getMinTransactionGasPrice()).isEqualTo(Wei.of(1000));
    assertThat(miningArg.getValue().getExtraData()).isEqualTo(Bytes.EMPTY);
    assertThat(miningArg.getValue().getTargetGasLimit().getAsLong())
        .isEqualTo(MiningConfiguration.DEFAULT_TARGET_GAS_LIMIT_HOLESKY);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void holeskyTargetGasLimitIsSetToSpecifiedValueWhenValueSpecified() {
    long customGasLimit = 99000000;
    parseCommand("--network", "holesky", "--target-gas-limit", String.valueOf(customGasLimit));

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    final ArgumentCaptor<MiningConfiguration> miningArg =
        ArgumentCaptor.forClass(MiningConfiguration.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).miningParameters(miningArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue()).isEqualTo(EthNetworkConfig.getNetworkConfig(HOLESKY));

    assertThat(miningArg.getValue().getCoinbase()).isEqualTo(Optional.empty());
    assertThat(miningArg.getValue().getMinTransactionGasPrice()).isEqualTo(Wei.of(1000));
    assertThat(miningArg.getValue().getExtraData()).isEqualTo(Bytes.EMPTY);
    assertThat(miningArg.getValue().getTargetGasLimit().getAsLong()).isEqualTo(customGasLimit);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void luksoValuesAreUsed() {
    parseCommand("--network", "lukso");

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue()).isEqualTo(EthNetworkConfig.getNetworkConfig(LUKSO));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void ephemeryValuesAreUsed() {
    parseCommand("--network", "ephemery");

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();
    assertThat(NetworkName.valueOf(String.valueOf(EPHEMERY))).isEqualTo(EPHEMERY);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void classicValuesAreUsed() {
    parseCommand("--network", "classic");

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue()).isEqualTo(EthNetworkConfig.getNetworkConfig(CLASSIC));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void mordorValuesAreUsed() {
    parseCommand("--network", "mordor");

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue()).isEqualTo(EthNetworkConfig.getNetworkConfig(MORDOR));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void sepoliaValuesCanBeOverridden() {
    networkValuesCanBeOverridden("sepolia");
  }

  @Test
  public void futureEipsValuesCanBeOverridden() {
    networkValuesCanBeOverridden("future_eips");
  }

  @Test
  public void experimentalEipsValuesCanBeOverridden() {
    networkValuesCanBeOverridden("experimental_eips");
  }

  @Test
  public void devValuesCanBeOverridden() {
    networkValuesCanBeOverridden("dev");
  }

  @Test
  public void classicValuesCanBeOverridden() {
    networkValuesCanBeOverridden("classic");
  }

  @Test
  public void mordorValuesCanBeOverridden() {
    networkValuesCanBeOverridden("mordor");
  }

  private void networkValuesCanBeOverridden(final String network) {
    parseCommand(
        "--network",
        network,
        "--network-id",
        "1234567",
        "--bootnodes",
        String.join(",", VALID_ENODE_STRINGS));

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue().bootNodes())
        .isEqualTo(
            Stream.of(VALID_ENODE_STRINGS)
                .map(EnodeURLImpl::fromString)
                .collect(Collectors.toList()));
    assertThat(networkArg.getValue().networkId()).isEqualTo(1234567);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void fullCLIOptionsShown() {
    parseCommand("--help");

    verifyNoInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString(UTF_8)).contains("--config-file");
    assertThat(commandOutput.toString(UTF_8)).contains("--data-path");
    assertThat(commandOutput.toString(UTF_8)).contains("--genesis-file");
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
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
  public void staticNodesFileOptionValidParameter() throws IOException {
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
  public void logsWarningWhenFailToLoadJemalloc() {
    assumeTrue(PlatformDetector.getOSType().equals("linux"));
    setEnvironmentVariable("BESU_USING_JEMALLOC", "false");
    parseCommand();
    verify(mockLogger)
        .warn(
            argThat(
                arg ->
                    arg.equals(
                            "besu_using_jemalloc is present but is not set to true, jemalloc library not loaded")
                        || arg.equals(
                            "besu_using_jemalloc is present but we failed to load jemalloc library to get the version")));
  }

  @Test
  public void logsSuggestInstallingJemallocWhenEnvVarNotPresent() {
    assumeTrue(PlatformDetector.getOSType().equals("linux"));
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
  public void bonsaiFlatDbShouldBeEnabledByDefault() {
    final TestBesuCommand besuCommand = parseCommand();
    assertThat(
            besuCommand
                .getDataStorageOptions()
                .toDomainObject()
                .getDiffBasedSubStorageConfiguration()
                .getUnstable()
                .getFullFlatDbEnabled())
        .isTrue();
  }

  @Test
  public void snapsyncHealingOptionShouldWork() {
    final TestBesuCommand besuCommand = parseCommand("--Xbonsai-full-flat-db-enabled", "false");
    assertThat(
            besuCommand
                .dataStorageOptions
                .toDomainObject()
                .getDiffBasedSubStorageConfiguration()
                .getUnstable()
                .getFullFlatDbEnabled())
        .isFalse();
  }

  @Test
  public void snapsyncForHealingFeaturesShouldFailWhenHealingIsNotEnabled() {
    parseCommand(
        "--Xbonsai-full-flat-db-enabled",
        "false",
        "--Xsnapsync-synchronizer-flat-account-healed-count-per-request",
        "100");
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains(
            "--Xsnapsync-synchronizer-flat option can only be used when --Xbonsai-full-flat-db-enabled is true");

    parseCommand(
        "--Xbonsai-full-flat-db-enabled",
        "false",
        "--Xsnapsync-synchronizer-flat-slot-healed-count-per-request",
        "100");
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains(
            "--Xsnapsync-synchronizer-flat option can only be used when --Xbonsai-full-flat-db-enabled is true");
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

  @Test
  public void genesisStateHashCacheEnabledShouldWork() throws IOException {
    final Path genesisFile = createFakeGenesisFile(GENESIS_VALID_JSON);
    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    parseCommand(
        "--genesis-file", genesisFile.toString(), "--genesis-state-hash-cache-enabled=true");

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();
    verify(mockControllerBuilder).genesisStateHashCacheEnabled(eq(true));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  void helpOutputShouldDisplayCorrectDefaultValues() {
    parseCommand("--help");

    final String commandOutputString = commandOutput.toString(UTF_8);
    final String errorOutputString = commandErrorOutput.toString(UTF_8);

    assertThat(commandOutputString).doesNotContain("$DEFAULT-VALUE");

    assertThat(errorOutputString).isEmpty();
  }
}
