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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static tech.pegasys.pantheon.ethereum.p2p.config.DiscoveryConfiguration.MAINNET_BOOTSTRAP_NODES;

import tech.pegasys.pantheon.PantheonInfo;
import tech.pegasys.pantheon.consensus.clique.jsonrpc.CliqueRpcApis;
import tech.pegasys.pantheon.consensus.ibft.jsonrpc.IbftRpcApis;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.eth.sync.SyncMode;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApis;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.io.Resources;
import net.consensys.cava.toml.Toml;
import net.consensys.cava.toml.TomlParseResult;
import org.apache.commons.text.StringEscapeUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import picocli.CommandLine;

public class PantheonCommandTest extends CommandTestAbstract {
  private final String ORION_URI = "http://1.2.3.4:5555";
  private final String VALID_NODE_ID =
      "6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0";

  private static final JsonRpcConfiguration defaultJsonRpcConfiguration;
  private static final WebSocketConfiguration defaultWebSocketConfiguration;
  private static final MetricsConfiguration defaultMetricsConfiguration;
  private static final String GENESIS_CONFIG_TESTDATA = "genesis_config";

  private final String[] validENodeStrings = {
    "enode://" + VALID_NODE_ID + "@192.168.0.1:4567",
    "enode://" + VALID_NODE_ID + "@192.168.0.2:4567",
    "enode://" + VALID_NODE_ID + "@192.168.0.3:4567"
  };

  private final String[] ropstenBootnodes = {
    "enode://6332792c4a00e3e4ee0926ed89e0d27ef985424d97b6a45bf0f23e51f0dcb5e66b875777506458aea7af6f9e4ffb69f43f3778ee73c81ed9d34c51c4b16b0b0f@52.232.243.152:30303",
    "enode://94c15d1b9e2fe7ce56e458b9a3b672ef11894ddedd0c6f247e0f1d3487f52b66208fb4aeb8179fce6e3a749ea93ed147c37976d67af557508d199d9594c35f09@192.81.208.223:30303"
  };

  static {
    final JsonRpcConfiguration rpcConf = JsonRpcConfiguration.createDefault();
    rpcConf.addRpcApi(CliqueRpcApis.CLIQUE);
    rpcConf.addRpcApi(IbftRpcApis.IBFT);
    defaultJsonRpcConfiguration = rpcConf;

    final WebSocketConfiguration websocketConf = WebSocketConfiguration.createDefault();
    websocketConf.addRpcApi(CliqueRpcApis.CLIQUE);
    websocketConf.addRpcApi(IbftRpcApis.IBFT);
    defaultWebSocketConfiguration = websocketConf;

    defaultMetricsConfiguration = MetricsConfiguration.createDefault();
  }

  @Test
  public void callingHelpSubCommandMustDisplayUsage() {
    parseCommand("--help");
    final String expectedOutputStart = String.format("Usage:%n%npantheon [OPTIONS] [COMMAND]");
    assertThat(commandOutput.toString()).startsWith(expectedOutputStart);
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingHelpDisplaysDefaultRpcApisCorrectly() {
    parseCommand("--help");
    assertThat(commandOutput.toString()).contains("default: ETH,NET,WEB3,CLIQUE,IBFT");
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingVersionDisplayPantheonInfoVersion() {
    parseCommand("--version");
    assertThat(commandOutput.toString()).isEqualToIgnoringWhitespace(PantheonInfo.version());
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  // Testing default values
  @Test
  public void callingPantheonCommandWithoutOptionsMustSyncWithDefaultValues() throws Exception {
    parseCommand();

    verify(mockRunnerBuilder).discovery(eq(true));
    verify(mockRunnerBuilder).bootstrapPeers(MAINNET_BOOTSTRAP_NODES);
    verify(mockRunnerBuilder).discoveryHost(eq("127.0.0.1"));
    verify(mockRunnerBuilder).discoveryPort(eq(30303));
    verify(mockRunnerBuilder).maxPeers(eq(25));
    verify(mockRunnerBuilder).jsonRpcConfiguration(eq(defaultJsonRpcConfiguration));
    verify(mockRunnerBuilder).webSocketConfiguration(eq(defaultWebSocketConfiguration));
    verify(mockRunnerBuilder).metricsConfiguration(eq(defaultMetricsConfiguration));
    verify(mockRunnerBuilder).build();

    final ArgumentCaptor<MiningParameters> miningArg =
        ArgumentCaptor.forClass(MiningParameters.class);
    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);
    verify(mockControllerBuilder).synchronizerConfiguration(isNotNull());
    verify(mockControllerBuilder).homePath(isNotNull());
    verify(mockControllerBuilder).ethNetworkConfig(networkArg.capture());
    verify(mockControllerBuilder).syncWithOttoman(eq(false));
    verify(mockControllerBuilder).miningParameters(miningArg.capture());
    verify(mockControllerBuilder).devMode(eq(false));
    verify(mockControllerBuilder).nodePrivateKeyFile(isNotNull());
    verify(mockControllerBuilder).build();

    verify(mockSyncConfBuilder).syncMode(ArgumentMatchers.eq(SyncMode.FULL));

    assertThat(commandErrorOutput.toString()).isEmpty();
    assertThat(miningArg.getValue().getCoinbase()).isEqualTo(Optional.empty());
    assertThat(miningArg.getValue().getMinTransactionGasPrice()).isEqualTo(Wei.of(1000));
    assertThat(miningArg.getValue().getExtraData()).isEqualTo(BytesValue.EMPTY);
    assertThat(networkArg.getValue().getNetworkId()).isEqualTo(1);
    assertThat(networkArg.getValue().getBootNodes()).isEqualTo(MAINNET_BOOTSTRAP_NODES);
  }

  // Testing each option
  @Test
  public void callingWithConfigOptionButNoConfigFileShouldDisplayHelp() {
    assumeTrue(isFullInstantiation());

    parseCommand("--config-file");

    final String expectedOutputStart =
        "Missing required parameter for option '--config-file' (<FILE>)";
    assertThat(commandErrorOutput.toString()).startsWith(expectedOutputStart);
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void callingWithConfigOptionButNonExistingFileShouldDisplayHelp() throws IOException {
    assumeTrue(isFullInstantiation());

    final File tempConfigFile = temp.newFile("an-invalid-file-name-without-extension");
    parseCommand("--config-file", tempConfigFile.getPath());

    final String expectedOutputStart = "Unable to read TOML configuration file " + tempConfigFile;
    assertThat(commandErrorOutput.toString()).startsWith(expectedOutputStart);
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void callingWithConfigOptionButTomlFileNotFoundShouldDisplayHelp() {
    assumeTrue(isFullInstantiation());

    parseCommand("--config-file", "./an-invalid-file-name-sdsd87sjhqoi34io23.toml");

    final String expectedOutputStart = "Unable to read TOML configuration, file not found.";
    assertThat(commandErrorOutput.toString()).startsWith(expectedOutputStart);
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void callingWithConfigOptionButInvalidContentTomlFileShouldDisplayHelp() throws Exception {
    assumeTrue(isFullInstantiation());

    // We write a config file to prevent an invalid file in resource folder to raise errors in
    // code checks (CI + IDE)
    final File tempConfigFile = temp.newFile("invalid_config.toml");
    try (final Writer fileWriter = Files.newBufferedWriter(tempConfigFile.toPath(), UTF_8)) {

      fileWriter.write("."); // an invalid toml content
      fileWriter.flush();

      parseCommand("--config-file", tempConfigFile.getPath());

      final String expectedOutputStart =
          "Invalid TOML configuration : Unexpected '.', expected a-z, A-Z, 0-9, ', \", a table key, "
              + "a newline, or end-of-input (line 1, column 1)";
      assertThat(commandErrorOutput.toString()).startsWith(expectedOutputStart);
      assertThat(commandOutput.toString()).isEmpty();
    }
  }

  @Test
  public void callingWithConfigOptionButInvalidValueTomlFileShouldDisplayHelp() throws Exception {
    assumeTrue(isFullInstantiation());

    // We write a config file to prevent an invalid file in resource folder to raise errors in
    // code checks (CI + IDE)
    final File tempConfigFile = temp.newFile("invalid_config.toml");
    try (final Writer fileWriter = Files.newBufferedWriter(tempConfigFile.toPath(), UTF_8)) {

      fileWriter.write("tester===========......."); // an invalid toml content
      fileWriter.flush();

      parseCommand("--config-file", tempConfigFile.getPath());

      final String expectedOutputStart =
          "Invalid TOML configuration : Unexpected '=', expected ', \", ''', \"\"\", a number, "
              + "a boolean, a date/time, an array, or a table (line 1, column 8)";
      assertThat(commandErrorOutput.toString()).startsWith(expectedOutputStart);
      assertThat(commandOutput.toString()).isEmpty();
    }
  }

  @Test
  public void overrideDefaultValuesIfKeyIsPresentInConfigFile() throws IOException {
    assumeTrue(isFullInstantiation());

    final URL configFile = Resources.getResource("complete_config.toml");
    final Path genesisFile = createFakeGenesisFile();
    final String updatedConfig =
        Resources.toString(configFile, UTF_8)
            .replace("~/genesis.json", escapeTomlString(genesisFile.toString()));
    final Path toml = Files.createTempFile("toml", "");
    Files.write(toml, updatedConfig.getBytes(UTF_8));

    final JsonRpcConfiguration jsonRpcConfiguration = JsonRpcConfiguration.createDefault();
    jsonRpcConfiguration.setEnabled(false);
    jsonRpcConfiguration.setHost("5.6.7.8");
    jsonRpcConfiguration.setPort(5678);
    jsonRpcConfiguration.setCorsAllowedDomains(Collections.emptyList());
    jsonRpcConfiguration.setRpcApis(RpcApis.DEFAULT_JSON_RPC_APIS);
    jsonRpcConfiguration.addRpcApi(CliqueRpcApis.CLIQUE);
    jsonRpcConfiguration.addRpcApi(IbftRpcApis.IBFT);

    final WebSocketConfiguration webSocketConfiguration = WebSocketConfiguration.createDefault();
    webSocketConfiguration.setEnabled(false);
    webSocketConfiguration.setHost("9.10.11.12");
    webSocketConfiguration.setPort(9101);
    webSocketConfiguration.setRpcApis(WebSocketConfiguration.DEFAULT_WEBSOCKET_APIS);
    webSocketConfiguration.addRpcApi(CliqueRpcApis.CLIQUE);
    webSocketConfiguration.addRpcApi(IbftRpcApis.IBFT);

    final MetricsConfiguration metricsConfiguration = MetricsConfiguration.createDefault();
    metricsConfiguration.setEnabled(false);
    metricsConfiguration.setHost("8.6.7.5");
    metricsConfiguration.setPort(309);

    parseCommand("--config-file", toml.toString());

    verify(mockRunnerBuilder).discovery(eq(false));
    verify(mockRunnerBuilder).bootstrapPeers(uriListArgumentCaptor.capture());
    verify(mockRunnerBuilder).discoveryHost(eq("1.2.3.4"));
    verify(mockRunnerBuilder).discoveryPort(eq(1234));
    verify(mockRunnerBuilder).maxPeers(eq(42));
    verify(mockRunnerBuilder).jsonRpcConfiguration(eq(jsonRpcConfiguration));
    verify(mockRunnerBuilder).webSocketConfiguration(eq(webSocketConfiguration));
    verify(mockRunnerBuilder).metricsConfiguration(eq(metricsConfiguration));
    verify(mockRunnerBuilder).build();

    final Collection<URI> nodes =
        asList(
            URI.create("enode://" + VALID_NODE_ID + "@192.168.0.1:4567"),
            URI.create("enode://" + VALID_NODE_ID + "@192.168.0.1:4567"),
            URI.create("enode://" + VALID_NODE_ID + "@192.168.0.1:4567"));
    assertThat(uriListArgumentCaptor.getValue()).isEqualTo(nodes);

    final EthNetworkConfig networkConfig =
        new EthNetworkConfig.Builder(EthNetworkConfig.mainnet())
            .setGenesisConfig(GENESIS_CONFIG_TESTDATA)
            .setBootNodes(nodes)
            .build();
    verify(mockControllerBuilder).homePath(eq(Paths.get("~/pantheondata")));
    verify(mockControllerBuilder).ethNetworkConfig(eq(networkConfig));
    verify(mockControllerBuilder).syncWithOttoman(eq(false));

    // TODO: Re-enable as per NC-1057/NC-1681
    // verify(mockSyncConfBuilder).syncMode(ArgumentMatchers.eq(SyncMode.FAST));

    assertThat(commandErrorOutput.toString()).isEmpty();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void tomlThatConfiguresEverything() throws IOException {
    assumeTrue(isFullInstantiation());

    // Load a TOML that configures literally everything
    final URL configFile = Resources.getResource("everything_config.toml");
    final Path toml = Files.createTempFile("toml", "");
    Files.write(toml, Resources.toByteArray(configFile));

    // Parse it.
    final CommandLine.Model.CommandSpec spec = parseCommand("--config-file", toml.toString());
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
      } else if (Number.class.isAssignableFrom(optionSpec.type())) {
        tomlResult.getLong(tomlKey);
      } else {
        tomlResult.getString(tomlKey);
      }
      options.remove(optionSpec);
    }
    assertThat(options.stream().map(CommandLine.Model.OptionSpec::longestName)).isEmpty();
  }

  @Test
  public void noOverrideDefaultValuesIfKeyIsNotPresentInConfigFile() throws IOException {
    assumeTrue(isFullInstantiation());

    final String configFile = Resources.getResource("partial_config.toml").getFile();

    parseCommand("--config-file", configFile);
    final JsonRpcConfiguration jsonRpcConfiguration = JsonRpcConfiguration.createDefault();
    jsonRpcConfiguration.addRpcApi(CliqueRpcApis.CLIQUE);
    jsonRpcConfiguration.addRpcApi(IbftRpcApis.IBFT);

    final WebSocketConfiguration webSocketConfiguration = WebSocketConfiguration.createDefault();
    webSocketConfiguration.addRpcApi(CliqueRpcApis.CLIQUE);
    webSocketConfiguration.addRpcApi(IbftRpcApis.IBFT);

    final MetricsConfiguration metricsConfiguration = MetricsConfiguration.createDefault();

    verify(mockRunnerBuilder).discovery(eq(true));
    verify(mockRunnerBuilder).bootstrapPeers(MAINNET_BOOTSTRAP_NODES);
    verify(mockRunnerBuilder).discoveryHost(eq("127.0.0.1"));
    verify(mockRunnerBuilder).discoveryPort(eq(30303));
    verify(mockRunnerBuilder).maxPeers(eq(25));
    verify(mockRunnerBuilder).jsonRpcConfiguration(eq(jsonRpcConfiguration));
    verify(mockRunnerBuilder).webSocketConfiguration(eq(webSocketConfiguration));
    verify(mockRunnerBuilder).metricsConfiguration(eq(metricsConfiguration));
    verify(mockRunnerBuilder).build();

    verify(mockControllerBuilder).syncWithOttoman(eq(false));
    verify(mockControllerBuilder).devMode(eq(false));
    verify(mockControllerBuilder).build();

    // TODO: Re-enable as per NC-1057/NC-1681
    // verify(mockSyncConfBuilder).syncMode(ArgumentMatchers.eq(SyncMode.FULL));

    assertThat(commandErrorOutput.toString()).isEmpty();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void configOptionDisabledUnderDocker() {
    System.setProperty("pantheon.docker", "true");

    assumeFalse(isFullInstantiation());

    final Path path = Paths.get(".");
    parseCommand("--config", path.toString());
    assertThat(commandErrorOutput.toString()).startsWith("Unknown options: --config, .");
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void nodekeyOptionMustBeUsed() throws Exception {
    final File file = new File("./specific/key");

    parseCommand("--node-private-key-file", file.getPath());

    verify(mockControllerBuilder).homePath(isNotNull());
    verify(mockControllerBuilder).syncWithOttoman(eq(false));
    verify(mockControllerBuilder).nodePrivateKeyFile(fileArgumentCaptor.capture());
    verify(mockControllerBuilder).build();

    assertThat(fileArgumentCaptor.getValue()).isEqualTo(file);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void dataDirOptionMustBeUsed() throws Exception {
    assumeTrue(isFullInstantiation());

    final Path path = Paths.get(".");

    parseCommand("--data-path", path.toString());

    verify(mockControllerBuilder).homePath(pathArgumentCaptor.capture());
    verify(mockControllerBuilder).syncWithOttoman(eq(false));
    verify(mockControllerBuilder).nodePrivateKeyFile(eq(path.resolve("key").toFile()));
    verify(mockControllerBuilder).build();

    assertThat(pathArgumentCaptor.getValue()).isEqualByComparingTo(path);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void dataDirDisabledUnderDocker() {
    System.setProperty("pantheon.docker", "true");

    assumeFalse(isFullInstantiation());

    final Path path = Paths.get(".");

    parseCommand("--datadir", path.toString());
    assertThat(commandErrorOutput.toString()).startsWith("Unknown options: --datadir, .");
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void dataDirDefaultedUnderDocker() {
    System.setProperty("pantheon.docker", "true");

    assumeFalse(isFullInstantiation());

    parseCommand();

    verify(mockControllerBuilder).homePath(pathArgumentCaptor.capture());
    assertThat(pathArgumentCaptor.getValue()).isEqualByComparingTo(Paths.get("/var/lib/pantheon"));
  }

  @Test
  public void genesisPathOptionMustBeUsed() throws Exception {
    assumeTrue(isFullInstantiation());

    final Path genesisFile = createFakeGenesisFile();
    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    parseCommand("--private-genesis-file", genesisFile.toString());

    verify(mockControllerBuilder).ethNetworkConfig(networkArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue().getGenesisConfig()).isEqualTo("genesis_config");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void genesisPathDisabledUnderDocker() {
    System.setProperty("pantheon.docker", "true");

    assumeFalse(isFullInstantiation());

    final Path path = Paths.get(".");

    parseCommand("--genesis", path.toString());
    assertThat(commandErrorOutput.toString()).startsWith("Unknown options: --genesis, .");
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void p2pEnabledOptionValueTrueMustBeUsed() {
    parseCommand("--p2p-enabled", "true");

    verify(mockRunnerBuilder.p2pEnabled(eq(true))).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void p2pEnabledOptionValueFalseMustBeUsed() {
    parseCommand("--p2p-enabled", "false");

    verify(mockRunnerBuilder.p2pEnabled(eq(false))).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void p2pEnabledOptionFalseValueCannotAlsoHaveBootnodesSpecified() {
    parseCommand("--p2p-enabled", "false", "--bootnodes", String.join(",", validENodeStrings));

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Unable to specify bootnodes if p2p is disabled.");
  }

  @Test
  public void discoveryOptionValueTrueMustBeUsed() {
    parseCommand("--no-discovery", "true");
    // Discovery stored in runner is the negative of the option passed to CLI
    verify(mockRunnerBuilder.discovery(eq(false))).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void discoveryOptionValueFalseMustBeUsed() {
    parseCommand("--no-discovery", "false");
    // Discovery stored in runner is the negative of the option passed to CLI
    verify(mockRunnerBuilder.discovery(eq(true))).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Ignore("NC-2015 - Temporarily enabling zero-arg --bootnodes to permit 'bootnode' configuration")
  @Test
  public void callingWithBootnodesOptionButNoValueMustDisplayErrorAndUsage() {
    parseCommand("--bootnodes");
    assertThat(commandOutput.toString()).isEmpty();
    final String expectedErrorOutputStart =
        "Missing required parameter for option '--bootnodes' at index 0 (<enode://id@host:port>)";
    assertThat(commandErrorOutput.toString()).startsWith(expectedErrorOutputStart);
  }

  @Test
  public void callingWithBootnodesOptionButNoValueMustPassEmptyBootnodeList() {
    parseCommand("--bootnodes");

    verify(mockRunnerBuilder).bootstrapPeers(uriListArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(uriListArgumentCaptor.getValue()).isEmpty();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Ignore(
      "NC-2015 - Temporarily enabling zero-arg --bootnodes to permit 'bootnode' configuration, which changes the error.")
  @Test
  public void callingWithInvalidBootnodesMustDisplayErrorAndUsage() {
    parseCommand("--bootnodes", "invalid_enode_url");
    assertThat(commandOutput.toString()).isEmpty();
    final String expectedErrorOutputStart =
        "Invalid value for option '--bootnodes' at index 0 (<enode://id@host:port>)";
    assertThat(commandErrorOutput.toString()).startsWith(expectedErrorOutputStart);
  }

  @Test
  public void callingWithInvalidBootnodesAndZeroArityMustDisplayAlternateErrorAndUsage() {
    parseCommand("--bootnodes", "invalid_enode_url");
    assertThat(commandOutput.toString()).isEmpty();
    final String expectedErrorOutputStart = "Unmatched argument: invalid_enode_url";
    assertThat(commandErrorOutput.toString()).startsWith(expectedErrorOutputStart);
  }

  @Test
  public void callingWithBannedNodeidsOptionButNoValueMustDisplayErrorAndUsage() {
    parseCommand("--banned-node-ids");
    assertThat(commandOutput.toString()).isEmpty();
    final String expectedErrorOutputStart =
        "Missing required parameter for option '--banned-node-ids' at index 0 (<NODEID>)";
    assertThat(commandErrorOutput.toString()).startsWith(expectedErrorOutputStart);
  }

  @Test
  public void bootnodesOptionMustBeUsed() {
    parseCommand("--bootnodes", String.join(",", validENodeStrings));

    verify(mockRunnerBuilder).bootstrapPeers(uriListArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(uriListArgumentCaptor.getValue())
        .isEqualTo(Stream.of(validENodeStrings).map(URI::create).collect(Collectors.toList()));

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingWithRefreshDelayWithValueMustUseValue() {
    parseCommand("--rpc-ws-refresh-delay", "2000");

    verify(mockRunnerBuilder).webSocketConfiguration(wsRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(wsRpcConfigArgumentCaptor.getValue().getRefreshDelay()).isEqualTo(2000);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingWithRefreshDelayWithNegativeValueMustError() {
    parseCommand("--rpc-ws-refresh-delay", "-2000");

    assertThat(commandOutput.toString()).isEmpty();
    final String expectedErrorMsg = "Refresh delay must be a positive integer between";
    assertThat(commandErrorOutput.toString()).startsWith(expectedErrorMsg);
  }

  @Test
  public void callingWithInvalidNodesWhitelistMustDisplayErrorAndUsage() {
    parseCommand("--nodes-whitelist", "invalid_enode_url");
    assertThat(commandOutput.toString()).isEmpty();
    /*
     Because of the way Picocli handles errors parsing errors for lists with arity 0..*, we don't
     get the nice error msg with that was wrong. It only shows to the user the values that weren't
     parsed correctly.
    */
    final String expectedErrorOutputStart = "Unmatched argument: invalid_enode_url";
    assertThat(commandErrorOutput.toString()).startsWith(expectedErrorOutputStart);
  }

  @Test
  public void nodesWhitelistOptionMustBeUsed() {
    parseCommand(
        "--nodes-whitelist",
        String.join(",", validENodeStrings),
        "--bootnodes",
        validENodeStrings[0]);

    verify(mockRunnerBuilder)
        .permissioningConfiguration(permissioningConfigurationArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    final List<String> enodeURLAsStringList =
        permissioningConfigurationArgumentCaptor
            .getValue()
            .getNodeWhitelist()
            .stream()
            .map(URI::toString)
            .collect(Collectors.toList());

    assertThat(enodeURLAsStringList).containsExactlyInAnyOrder(validENodeStrings);
    assertThat(permissioningConfigurationArgumentCaptor.getValue().isNodeWhitelistSet()).isTrue();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingWithAccountsWhitelistOptionButNoValueMustNotError() {
    parseCommand("--accounts-whitelist");

    verify(mockRunnerBuilder)
        .permissioningConfiguration(permissioningConfigurationArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(permissioningConfigurationArgumentCaptor.getValue().getAccountWhitelist()).isEmpty();
    assertThat(permissioningConfigurationArgumentCaptor.getValue().isAccountWhitelistSet())
        .isTrue();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void accountsWhitelistOptionMustBeUsed() {
    final String[] accounts = {"1111111111111111", "2222222222222222", "ffffffffffffffff"};
    parseCommand("--accounts-whitelist", String.join(",", accounts));

    verify(mockRunnerBuilder)
        .permissioningConfiguration(permissioningConfigurationArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(permissioningConfigurationArgumentCaptor.getValue().getAccountWhitelist())
        .containsExactlyInAnyOrder(accounts);
    assertThat(permissioningConfigurationArgumentCaptor.getValue().isAccountWhitelistSet())
        .isTrue();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void nodesWhitelistOptionMustIncludeBootnodes() {
    parseCommand(
        "--bootnodes",
        String.join(",", validENodeStrings[0], validENodeStrings[1]),
        "--nodes-whitelist",
        String.join(",", validENodeStrings));

    verify(mockRunnerBuilder)
        .permissioningConfiguration(permissioningConfigurationArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    final List<String> enodeURLAsStringList =
        permissioningConfigurationArgumentCaptor
            .getValue()
            .getNodeWhitelist()
            .stream()
            .map(URI::toString)
            .collect(Collectors.toList());

    assertThat(enodeURLAsStringList).containsExactlyInAnyOrder(validENodeStrings);
    assertThat(permissioningConfigurationArgumentCaptor.getValue().isNodeWhitelistSet()).isTrue();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void nodesWhitelistOptionWhichDoesNotIncludeBootnodesMustDisplayError() {
    final String bootNodeNotWhitelisted = "enode://" + VALID_NODE_ID + "@192.168.0.9:4567";
    parseCommand(
        "--bootnodes",
        String.join(",", bootNodeNotWhitelisted, validENodeStrings[2]),
        "--nodes-whitelist",
        String.join(",", validENodeStrings));

    verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Cannot start node with bootnode(s) that are not in nodes-whitelist");
  }

  @Test
  public void ropstenWithNodesWhitelistOptionWhichDoesIncludeRopstenBootnodesMustNotDisplayError() {
    parseCommand("--ropsten", "--nodes-whitelist", String.join(",", ropstenBootnodes));

    verify(mockRunnerBuilder)
        .permissioningConfiguration(permissioningConfigurationArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    final List<String> enodeURLAsStringList =
        permissioningConfigurationArgumentCaptor
            .getValue()
            .getNodeWhitelist()
            .stream()
            .map(URI::toString)
            .collect(Collectors.toList());

    assertThat(enodeURLAsStringList).containsExactlyInAnyOrder(ropstenBootnodes);
    assertThat(permissioningConfigurationArgumentCaptor.getValue().isNodeWhitelistSet()).isTrue();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void ropstenWithNodesWhitelistOptionWhichDoesNotIncludeRopstenBootnodesMustDisplayError() {
    parseCommand("--ropsten", "--nodes-whitelist", String.join(",", validENodeStrings));

    verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Cannot start node with bootnode(s) that are not in nodes-whitelist");
  }

  @Test
  public void nodesWhitelistWithEmptyListAndNonEmptyBootnodesMustDisplayError() {
    parseCommand("--bootnodes", String.join(",", validENodeStrings[0]), "--nodes-whitelist");

    verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Cannot start node with bootnode(s) that are not in nodes-whitelist");
  }

  @Test
  public void bannedNodeIdsOptionMustBeUsed() {
    final String[] nodes = {"0001", "0002", "0003"};
    parseCommand("--banned-node-ids", String.join(",", nodes));

    verify(mockRunnerBuilder).bannedNodeIds(stringListArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(stringListArgumentCaptor.getValue().toArray()).isEqualTo(nodes);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void p2pHostAndPortOptionMustBeUsed() {

    final String host = "1.2.3.4";
    final int port = 1234;
    parseCommand("--p2p-host", host, "--p2p-port", String.valueOf(port));

    verify(mockRunnerBuilder).discoveryHost(stringArgumentCaptor.capture());
    verify(mockRunnerBuilder).discoveryPort(intArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(stringArgumentCaptor.getValue()).isEqualTo(host);
    assertThat(intArgumentCaptor.getValue()).isEqualTo(port);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void maxpeersOptionMustBeUsed() {

    final int maxPeers = 123;
    parseCommand("--max-peers", String.valueOf(maxPeers));

    verify(mockRunnerBuilder).maxPeers(intArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(intArgumentCaptor.getValue()).isEqualTo(maxPeers);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Ignore("Ignored as we only have one mode available for now. See NC-1057/NC-1681")
  @Test
  public void syncModeOptionMustBeUsed() {

    parseCommand("--sync-mode", "FAST");
    verify(mockSyncConfBuilder).syncMode(ArgumentMatchers.eq(SyncMode.FAST));

    parseCommand("--sync-mode", "FULL");
    verify(mockSyncConfBuilder).syncMode(ArgumentMatchers.eq(SyncMode.FULL));

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void maxTrailingPeersMustBeUsed() {
    parseCommand("--max-trailing-peers", "3");
    verify(mockSyncConfBuilder).maxTrailingPeers(3);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void jsonRpcEnabledPropertyDefaultIsFalse() {
    parseCommand();

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().isEnabled()).isFalse();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void jsonRpcEnabledPropertyMustBeUsed() {
    parseCommand("--rpc-http-enabled");

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().isEnabled()).isTrue();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcApisPropertyMustBeUsed() {
    parseCommand("--rpc-http-api", "ETH,NET");

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getRpcApis())
        .containsExactlyInAnyOrder(RpcApis.ETH, RpcApis.NET);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcApisPropertyWithInvalidEntryMustDisplayError() {
    parseCommand("--rpc-http-api", "BOB");

    verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    // PicoCLI uses longest option name for message when option has multiple names, so here plural.
    assertThat(commandErrorOutput.toString())
        .contains("Invalid value for option '--rpc-http-apis'");
  }

  @Test
  public void jsonRpcHostAndPortOptionsMustBeUsed() {

    final String host = "1.2.3.4";
    final int port = 1234;
    parseCommand("--rpc-http-host", host, "--rpc-http-port", String.valueOf(port));

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHost()).isEqualTo(host);
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getPort()).isEqualTo(port);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void jsonRpcCorsOriginsTwoDomainsMustBuildListWithBothDomains() {
    final String[] origins = {"http://domain1.com", "https://domain2.com"};
    parseCommand("--rpc-http-cors-origins", String.join(",", origins));

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getCorsAllowedDomains().toArray())
        .isEqualTo(origins);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void jsonRpcCorsOriginsDoubleCommaFilteredOut() {
    final String[] origins = {"http://domain1.com", "https://domain2.com"};
    parseCommand("--rpc-http-cors-origins", String.join(",,", origins));

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getCorsAllowedDomains().toArray())
        .isEqualTo(origins);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void jsonRpcCorsOriginsWithWildcardMustBuildListWithWildcard() {
    final String[] origins = {"*"};
    parseCommand("--rpc-http-cors-origins", String.join(",", origins));

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getCorsAllowedDomains().toArray())
        .isEqualTo(origins);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void jsonRpcCorsOriginsWithAllMustBuildListWithWildcard() {
    parseCommand("--rpc-http-cors-origins", "all");

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getCorsAllowedDomains()).containsExactly("*");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void jsonRpcCorsOriginsWithNoneMustBuildEmptyList() {
    final String[] origins = {"none"};
    parseCommand("--rpc-http-cors-origins", String.join(",", origins));

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getCorsAllowedDomains()).isEmpty();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void jsonRpcCorsOriginsNoneWithAnotherDomainMustFail() {
    final String[] origins = {"http://domain1.com", "none"};
    parseCommand("--rpc-http-cors-origins", String.join(",", origins));

    verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Value 'none' can't be used with other domains");
  }

  @Test
  public void jsonRpcCorsOriginsNoneWithAnotherDomainMustFailNoneFirst() {
    final String[] origins = {"none", "http://domain1.com"};
    parseCommand("--rpc-http-cors-origins", String.join(",", origins));

    verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Value 'none' can't be used with other domains");
  }

  @Test
  public void jsonRpcCorsOriginsAllWithAnotherDomainMustFail() {
    parseCommand("--rpc-http-cors-origins=http://domain1.com,all");

    verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Values '*' or 'all' can't be used with other domains");
  }

  @Test
  public void jsonRpcCorsOriginsAllWithAnotherDomainMustFailAsFlags() {
    parseCommand("--rpc-http-cors-origins=http://domain1.com", "--rpc-http-cors-origins=all");

    verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Values '*' or 'all' can't be used with other domains");
  }

  @Test
  public void jsonRpcCorsOriginsWildcardWithAnotherDomainMustFail() {
    parseCommand("--rpc-http-cors-origins=http://domain1.com,*");

    verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Values '*' or 'all' can't be used with other domains");
  }

  @Test
  public void jsonRpcCorsOriginsWildcardWithAnotherDomainMustFailAsFlags() {
    parseCommand("--rpc-http-cors-origins=http://domain1.com", "--rpc-http-cors-origins=*");

    verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Values '*' or 'all' can't be used with other domains");
  }

  @Test
  public void jsonRpcCorsOriginsInvalidRegexShouldFail() {
    final String[] origins = {"**"};
    parseCommand("--rpc-http-cors-origins", String.join(",", origins));

    verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Domain values result in invalid regex pattern");
  }

  @Test
  public void jsonRpcCorsOriginsEmtyValueFails() {
    parseCommand("--rpc-http-cors-origins=");

    verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Domain cannot be empty string or null string.");
  }

  @Test
  public void jsonRpcHostWhitelistAcceptsSingleArgument() {
    parseCommand("--host-whitelist", "a");

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsWhitelist().size()).isEqualTo(1);
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsWhitelist()).contains("a");
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsWhitelist())
        .doesNotContain("localhost");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void jsonRpcHostWhitelistAcceptsMultipleArguments() {
    parseCommand("--host-whitelist", "a,b");

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsWhitelist().size()).isEqualTo(2);
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsWhitelist()).contains("a", "b");
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsWhitelist())
        .doesNotContain("*", "localhost");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void jsonRpcHostWhitelistAcceptsDoubleComma() {
    parseCommand("--host-whitelist", "a,,b");

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsWhitelist().size()).isEqualTo(2);
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsWhitelist()).contains("a", "b");
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsWhitelist())
        .doesNotContain("*", "localhost");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void jsonRpcHostWhitelistAcceptsMultipleFlags() {
    parseCommand("--host-whitelist=a", "--host-whitelist=b");

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsWhitelist().size()).isEqualTo(2);
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsWhitelist()).contains("a", "b");
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsWhitelist())
        .doesNotContain("*", "localhost");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void jsonRpcHostWhitelistStarWithAnotherHostnameMustFail() {
    final String[] origins = {"friend", "*"};
    parseCommand("--host-whitelist", String.join(",", origins));

    verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Values '*' or 'all' can't be used with other hostnames");
  }

  @Test
  public void jsonRpcHostWhitelistStarWithAnotherHostnameMustFailStarFirst() {
    final String[] origins = {"*", "friend"};
    parseCommand("--host-whitelist", String.join(",", origins));

    verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Values '*' or 'all' can't be used with other hostnames");
  }

  @Test
  public void jsonRpcHostWhitelistAllWithAnotherHostnameMustFail() {
    final String[] origins = {"friend", "all"};
    parseCommand("--host-whitelist", String.join(",", origins));

    verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Values '*' or 'all' can't be used with other hostnames");
  }

  @Test
  public void jsonRpcHostWhitelistWithNoneMustBuildEmptyList() {
    final String[] origins = {"none"};
    parseCommand("--host-whitelist", String.join(",", origins));

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsWhitelist()).isEmpty();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void jsonRpcHostWhitelistNoneWithAnotherDomainMustFail() {
    final String[] origins = {"http://domain1.com", "none"};
    parseCommand("--host-whitelist", String.join(",", origins));

    verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Value 'none' can't be used with other hostnames");
  }

  @Test
  public void jsonRpcHostWhitelistNoneWithAnotherDomainMustFailNoneFirst() {
    final String[] origins = {"none", "http://domain1.com"};
    parseCommand("--host-whitelist", String.join(",", origins));

    verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Value 'none' can't be used with other hostnames");
  }

  @Test
  public void jsonRpcHostWhitelistEmptyValueFails() {
    parseCommand("--host-whitelist=");

    verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Hostname cannot be empty string or null string.");
  }

  @Test
  public void wsRpcEnabledPropertyDefaultIsFalse() {
    parseCommand();

    verify(mockRunnerBuilder).webSocketConfiguration(wsRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(wsRpcConfigArgumentCaptor.getValue().isEnabled()).isFalse();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void wsRpcEnabledPropertyMustBeUsed() {
    parseCommand("--rpc-ws-enabled");

    verify(mockRunnerBuilder).webSocketConfiguration(wsRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(wsRpcConfigArgumentCaptor.getValue().isEnabled()).isTrue();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void wsApiPropertyMustBeUsed() {
    parseCommand("--rpc-ws-api", "ETH, NET");

    verify(mockRunnerBuilder).webSocketConfiguration(wsRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(wsRpcConfigArgumentCaptor.getValue().getRpcApis())
        .containsExactlyInAnyOrder(RpcApis.ETH, RpcApis.NET);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void wsRpcHostAndPortOptionMustBeUsed() {
    final String host = "1.2.3.4";
    final int port = 1234;
    parseCommand("--rpc-ws-host", host, "--rpc-ws-port", String.valueOf(port));

    verify(mockRunnerBuilder).webSocketConfiguration(wsRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(wsRpcConfigArgumentCaptor.getValue().getHost()).isEqualTo(host);
    assertThat(wsRpcConfigArgumentCaptor.getValue().getPort()).isEqualTo(port);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void metricsEnabledPropertyDefaultIsFalse() {
    parseCommand();

    verify(mockRunnerBuilder).metricsConfiguration(metricsConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(metricsConfigArgumentCaptor.getValue().isEnabled()).isFalse();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void metricsEnabledPropertyMustBeUsed() {
    parseCommand("--metrics-enabled");

    verify(mockRunnerBuilder).metricsConfiguration(metricsConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(metricsConfigArgumentCaptor.getValue().isEnabled()).isTrue();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void metricsHostAndPortOptionMustBeUsed() {
    final String host = "1.2.3.4";
    final int port = 1234;
    parseCommand("--metrics-host", host, "--metrics-port", String.valueOf(port));

    verify(mockRunnerBuilder).metricsConfiguration(metricsConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(metricsConfigArgumentCaptor.getValue().getHost()).isEqualTo(host);
    assertThat(metricsConfigArgumentCaptor.getValue().getPort()).isEqualTo(port);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void metricsModeOptionMustBeUsed() {
    parseCommand("--metrics-mode", "pull");

    verify(mockRunnerBuilder).metricsConfiguration(metricsConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(metricsConfigArgumentCaptor.getValue().getMode()).isEqualTo("pull");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void metricsPushIntervalMustBeUsed() {
    parseCommand("--metrics-push-interval", "42");

    verify(mockRunnerBuilder).metricsConfiguration(metricsConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(metricsConfigArgumentCaptor.getValue().getPushInterval()).isEqualTo(42);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void metricsPrometheusJobMustBeUsed() {
    parseCommand("--metrics-prometheus-job", "pantheon-command-test");

    verify(mockRunnerBuilder).metricsConfiguration(metricsConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(metricsConfigArgumentCaptor.getValue().getPrometheusJob())
        .isEqualTo("pantheon-command-test");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void pantheonDoesNotStartInMiningModeIfCoinbaseNotSet() {
    parseCommand("--miner-enabled");

    verifyZeroInteractions(mockControllerBuilder);
  }

  @Test
  public void miningIsEnabledWhenSpecified() throws Exception {
    final String coinbaseStr = String.format("%020x", 1);
    parseCommand("--miner-enabled", "--miner-coinbase=" + coinbaseStr);

    final ArgumentCaptor<MiningParameters> miningArg =
        ArgumentCaptor.forClass(MiningParameters.class);

    verify(mockControllerBuilder).miningParameters(miningArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
    assertThat(miningArg.getValue().isMiningEnabled()).isTrue();
    assertThat(miningArg.getValue().getCoinbase())
        .isEqualTo(Optional.of(Address.fromHexString(coinbaseStr)));
  }

  @Test
  public void miningParametersAreCaptured() throws Exception {
    final Address requestedCoinbase = Address.fromHexString("0000011111222223333344444");
    final String extraDataString =
        "0x1122334455667788990011223344556677889900112233445566778899001122";
    parseCommand(
        "--miner-coinbase=" + requestedCoinbase.toString(),
        "--min-gas-price=15",
        "--miner-extra-data=" + extraDataString);

    final ArgumentCaptor<MiningParameters> miningArg =
        ArgumentCaptor.forClass(MiningParameters.class);

    verify(mockControllerBuilder).miningParameters(miningArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
    assertThat(miningArg.getValue().getCoinbase()).isEqualTo(Optional.of(requestedCoinbase));
    assertThat(miningArg.getValue().getMinTransactionGasPrice()).isEqualTo(Wei.of(15));
    assertThat(miningArg.getValue().getExtraData())
        .isEqualTo(BytesValue.fromHexString(extraDataString));
  }

  @Test
  public void devModeOptionMustBeUsed() throws Exception {
    parseCommand("--dev-mode");

    verify(mockControllerBuilder).devMode(eq(true));
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rinkebyValuesAreUsed() throws Exception {
    parseCommand("--rinkeby");

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilder).ethNetworkConfig(networkArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
    assertThat(networkArg.getValue()).isEqualTo(EthNetworkConfig.rinkeby());
  }

  @Test
  public void ropstenValuesAreUsed() throws Exception {
    parseCommand("--ropsten");

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilder).ethNetworkConfig(networkArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
    assertThat(networkArg.getValue()).isEqualTo(EthNetworkConfig.ropsten());
  }

  @Test
  public void goerliValuesAreUsed() throws Exception {
    parseCommand("--goerli");

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilder).ethNetworkConfig(networkArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
    assertThat(networkArg.getValue()).isEqualTo(EthNetworkConfig.goerli());
  }

  @Test
  public void noSeveralNetworkOptions() {
    parseCommand("--goerli", "--rinkeby");

    verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).contains("Unable to connect to multiple networks");
  }

  @Test
  public void rinkebyValuesCanBeOverridden() throws Exception {
    networkValuesCanBeOverridden("rinkeby");
  }

  @Test
  public void goerliValuesCanBeOverridden() throws Exception {
    networkValuesCanBeOverridden("goerli");
  }

  private void networkValuesCanBeOverridden(final String network) throws Exception {
    final Path genesisFile = createFakeGenesisFile();
    parseCommand(
        "--" + network,
        "--network-id",
        "1",
        "--bootnodes",
        String.join(",", validENodeStrings),
        "--private-genesis-file",
        genesisFile.toString());

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilder).ethNetworkConfig(networkArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
    assertThat(networkArg.getValue().getGenesisConfig()).isEqualTo("genesis_config");
    assertThat(networkArg.getValue().getBootNodes())
        .isEqualTo(Stream.of(validENodeStrings).map(URI::create).collect(Collectors.toList()));
    assertThat(networkArg.getValue().getNetworkId()).isEqualTo(1);
  }

  @Test
  public void fullCLIOptionsNotShownWhenInDockerContainer() {
    System.setProperty("pantheon.docker", "true");

    parseCommand("--help");

    verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).doesNotContain("--config-file");
    assertThat(commandOutput.toString()).doesNotContain("--data-path");
    assertThat(commandOutput.toString()).doesNotContain("--private-genesis-file");
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void fullCLIOptionsShownWhenNotInDockerContainer() {
    parseCommand("--help");

    verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).contains("--config-file");
    assertThat(commandOutput.toString()).contains("--data-path");
    assertThat(commandOutput.toString()).contains("--private-genesis-file");
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void mustUseOrionUriAndOptions() throws IOException {
    final File file = new File("./specific/public_key");

    parseCommand(
        "--privacy-enabled",
        "--privacy-url",
        ORION_URI,
        "--privacy-public-key-file",
        file.getPath());

    final ArgumentCaptor<PrivacyParameters> orionArg =
        ArgumentCaptor.forClass(PrivacyParameters.class);

    verify(mockControllerBuilder).privacyParameters(orionArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(orionArg.getValue().getUrl()).isEqualTo(ORION_URI);
    assertThat(orionArg.getValue().getPublicKey()).isEqualTo(file);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void mustVerifyPrivacyIsDisabled() throws IOException {
    parseCommand();

    final ArgumentCaptor<PrivacyParameters> orionArg =
        ArgumentCaptor.forClass(PrivacyParameters.class);

    verify(mockControllerBuilder).privacyParameters(orionArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
    assertThat(orionArg.getValue().isEnabled()).isEqualTo(false);
  }

  private Path createFakeGenesisFile() throws IOException {
    final Path genesisFile = Files.createTempFile("genesisFile", "");
    Files.write(genesisFile, "genesis_config".getBytes(UTF_8));
    return genesisFile;
  }

  private boolean isFullInstantiation() {
    return !Boolean.getBoolean("pantheon.docker");
  }

  private static String escapeTomlString(final String s) {
    return StringEscapeUtils.escapeJava(s);
  }
}
