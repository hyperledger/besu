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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.pantheon.ethereum.p2p.config.DiscoveryConfiguration.MAINNET_BOOTSTRAP_NODES;

import tech.pegasys.pantheon.PantheonInfo;
import tech.pegasys.pantheon.cli.EthNetworkConfig.Builder;
import tech.pegasys.pantheon.consensus.clique.jsonrpc.CliqueRpcApis;
import tech.pegasys.pantheon.consensus.ibft.jsonrpc.IbftRpcApis;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.eth.sync.SyncMode;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApis;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

public class PantheonCommandTest extends CommandTestAbstract {
  @Rule public final TemporaryFolder temp = new TemporaryFolder();
  private static final JsonRpcConfiguration defaultJsonRpcConfiguration;
  private static final WebSocketConfiguration defaultWebSocketConfiguration;
  private static final String GENESIS_CONFIG_TESTDATA = "genesis_config";

  static {
    final JsonRpcConfiguration rpcConf = JsonRpcConfiguration.createDefault();
    rpcConf.addRpcApi(CliqueRpcApis.CLIQUE);
    rpcConf.addRpcApi(IbftRpcApis.IBFT);
    defaultJsonRpcConfiguration = rpcConf;

    final WebSocketConfiguration websocketConf = WebSocketConfiguration.createDefault();
    websocketConf.addRpcApi(CliqueRpcApis.CLIQUE);
    websocketConf.addRpcApi(IbftRpcApis.IBFT);
    defaultWebSocketConfiguration = websocketConf;
  }

  @Override
  @Before
  public void initMocks() throws Exception {
    super.initMocks();

    when(mockRunnerBuilder.vertx(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.pantheonController(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.discovery(anyBoolean())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.bootstrapPeers(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.discoveryHost(anyString())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.discoveryPort(anyInt())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.maxPeers(anyInt())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.jsonRpcConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.webSocketConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.permissioningConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.dataDir(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.bannedNodeIds(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.build()).thenReturn(mockRunner);
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

    parseCommand("--config");

    final String expectedOutputStart = "Missing required parameter for option '--config' (<PATH>)";
    assertThat(commandErrorOutput.toString()).startsWith(expectedOutputStart);
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void callingWithConfigOptionButNonExistingFileShouldDisplayHelp() throws IOException {
    final File tempConfigFile = temp.newFile("an-invalid-file-name-without-extension");
    parseCommand("--config", tempConfigFile.getPath());

    final String expectedOutputStart = "Unable to read TOML configuration file " + tempConfigFile;
    assertThat(commandErrorOutput.toString()).startsWith(expectedOutputStart);
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void callingWithConfigOptionButTomlFileNotFoundShouldDisplayHelp() {

    parseCommand("--config", "./an-invalid-file-name-sdsd87sjhqoi34io23.toml");

    final String expectedOutputStart = "Unable to read TOML configuration, file not found.";
    assertThat(commandErrorOutput.toString()).startsWith(expectedOutputStart);
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void callingWithConfigOptionButInvalidContentTomlFileShouldDisplayHelp() throws Exception {

    // We write a config file to prevent an invalid file in resource folder to raise errors in
    // code checks (CI + IDE)
    final File tempConfigFile = temp.newFile("invalid_config.toml");
    try (final Writer fileWriter = Files.newBufferedWriter(tempConfigFile.toPath(), UTF_8)) {

      fileWriter.write("."); // an invalid toml content
      fileWriter.flush();

      parseCommand("--config", tempConfigFile.getPath());

      final String expectedOutputStart =
          "Invalid TOML configuration : Unexpected '.', expected a-z, A-Z, 0-9, ', \", a table key, "
              + "a newline, or end-of-input (line 1, column 1)";
      assertThat(commandErrorOutput.toString()).startsWith(expectedOutputStart);
      assertThat(commandOutput.toString()).isEmpty();
    }
  }

  @Test
  public void callingWithConfigOptionButInvalidValueTomlFileShouldDisplayHelp() throws Exception {

    // We write a config file to prevent an invalid file in resource folder to raise errors in
    // code checks (CI + IDE)
    final File tempConfigFile = temp.newFile("invalid_config.toml");
    try (final Writer fileWriter = Files.newBufferedWriter(tempConfigFile.toPath(), UTF_8)) {

      fileWriter.write("tester===========......."); // an invalid toml content
      fileWriter.flush();

      parseCommand("--config", tempConfigFile.getPath());

      final String expectedOutputStart =
          "Invalid TOML configuration : Unexpected '=', expected ', \", ''', \"\"\", a number, "
              + "a boolean, a date/time, an array, or a table (line 1, column 8)";
      assertThat(commandErrorOutput.toString()).startsWith(expectedOutputStart);
      assertThat(commandOutput.toString()).isEmpty();
    }
  }

  @Test
  public void overrideDefaultValuesIfKeyIsPresentInConfigFile() throws IOException {
    final URL configFile = Resources.getResource("complete_config.toml");
    final Path genesisFile = createFakeGenesisFile();
    final String updatedConfig =
        Resources.toString(configFile, UTF_8).replaceAll("~/genesis.json", genesisFile.toString());
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

    parseCommand("--config", toml.toString());

    verify(mockRunnerBuilder).discovery(eq(false));
    verify(mockRunnerBuilder).bootstrapPeers(stringListArgumentCaptor.capture());
    verify(mockRunnerBuilder).discoveryHost(eq("1.2.3.4"));
    verify(mockRunnerBuilder).discoveryPort(eq(1234));
    verify(mockRunnerBuilder).maxPeers(eq(42));
    verify(mockRunnerBuilder).jsonRpcConfiguration(eq(jsonRpcConfiguration));
    verify(mockRunnerBuilder).webSocketConfiguration(eq(webSocketConfiguration));
    verify(mockRunnerBuilder).build();

    final Collection<String> nodes =
        asList("enode://001@123:4567", "enode://002@123:4567", "enode://003@123:4567");
    assertThat(stringListArgumentCaptor.getValue()).isEqualTo(nodes);

    final EthNetworkConfig networkConfig =
        new Builder(EthNetworkConfig.mainnet())
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
  public void noOverrideDefaultValuesIfKeyIsNotPresentInConfigFile() throws IOException {
    final String configFile = Resources.getResource("partial_config.toml").getFile();

    parseCommand("--config", configFile);
    final JsonRpcConfiguration jsonRpcConfiguration = JsonRpcConfiguration.createDefault();
    jsonRpcConfiguration.addRpcApi(CliqueRpcApis.CLIQUE);
    jsonRpcConfiguration.addRpcApi(IbftRpcApis.IBFT);

    final WebSocketConfiguration webSocketConfiguration = WebSocketConfiguration.createDefault();
    webSocketConfiguration.addRpcApi(CliqueRpcApis.CLIQUE);
    webSocketConfiguration.addRpcApi(IbftRpcApis.IBFT);

    verify(mockRunnerBuilder).discovery(eq(true));
    verify(mockRunnerBuilder).bootstrapPeers(MAINNET_BOOTSTRAP_NODES);
    verify(mockRunnerBuilder).discoveryHost(eq("127.0.0.1"));
    verify(mockRunnerBuilder).discoveryPort(eq(30303));
    verify(mockRunnerBuilder).maxPeers(eq(25));
    verify(mockRunnerBuilder).jsonRpcConfiguration(eq(jsonRpcConfiguration));
    verify(mockRunnerBuilder).webSocketConfiguration(eq(webSocketConfiguration));
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
  public void nodekeyOptionMustBeUsed() throws Exception {
    final File file = new File("./specific/key");

    parseCommand("--node-private-key", file.getPath());

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
    final Path path = Paths.get(".");

    parseCommand("--datadir", path.toString());

    verify(mockControllerBuilder).homePath(pathArgumentCaptor.capture());
    verify(mockControllerBuilder).syncWithOttoman(eq(false));
    verify(mockControllerBuilder).nodePrivateKeyFile(eq(path.resolve("key").toFile()));
    verify(mockControllerBuilder).build();

    assertThat(pathArgumentCaptor.getValue()).isEqualByComparingTo(path);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void genesisPathOptionMustBeUsed() throws Exception {
    final Path genesisFile = createFakeGenesisFile();
    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    parseCommand("--genesis", genesisFile.toString());

    verify(mockControllerBuilder).ethNetworkConfig(networkArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue().getGenesisConfig()).isEqualTo("genesis_config");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void discoveryOptionMustBeUsed() {
    parseCommand("--no-discovery");
    // Discovery stored in runner is the negative of the option passed to CLI
    // So as passing the option means noDiscovery will be true, then discovery is false in runner

    verify(mockRunnerBuilder.discovery(eq(false))).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingWithBootnodesOptionButNoValueMustDisplayErrorAndUsage() {
    parseCommand("--bootnodes");
    assertThat(commandOutput.toString()).isEmpty();
    final String expectedErrorOutputStart =
        "Missing required parameter for option '--bootnodes' at index 0 (<enode://id@host:port>)";
    assertThat(commandErrorOutput.toString()).startsWith(expectedErrorOutputStart);
  }

  @Test
  public void callingWithBannedNodeidsOptionButNoValueMustDisplayErrorAndUsage() {
    parseCommand("--banned-nodeids");
    assertThat(commandOutput.toString()).isEmpty();
    final String expectedErrorOutputStart =
        "Missing required parameter for option '--banned-nodeids' at index 0 (<bannedNodeIds>)";
    assertThat(commandErrorOutput.toString()).startsWith(expectedErrorOutputStart);
  }

  @Test
  public void bootnodesOptionMustBeUsed() {
    final String[] nodes = {"enode://001@123:4567", "enode://002@123:4567", "enode://003@123:4567"};
    parseCommand("--bootnodes", String.join(",", nodes));

    verify(mockRunnerBuilder).bootstrapPeers(stringListArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(stringListArgumentCaptor.getValue().toArray()).isEqualTo(nodes);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingWithNodesWhitelistOptionButNoValueMustNotError() {
    parseCommand("--nodes-whitelist");

    verify(mockRunnerBuilder)
        .permissioningConfiguration(permissioningConfigurationArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(permissioningConfigurationArgumentCaptor.getValue().getNodeWhitelist()).isEmpty();
    assertThat(permissioningConfigurationArgumentCaptor.getValue().isNodeWhitelistSet()).isTrue();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void nodesWhitelistOptionMustBeUsed() {
    final String[] nodes = {"enode://001@123:4567", "enode://002@123:4567", "enode://003@123:4567"};
    parseCommand("--nodes-whitelist", String.join(",", nodes));

    verify(mockRunnerBuilder)
        .permissioningConfiguration(permissioningConfigurationArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(permissioningConfigurationArgumentCaptor.getValue().getNodeWhitelist())
        .containsExactlyInAnyOrder(nodes);
    assertThat(permissioningConfigurationArgumentCaptor.getValue().isNodeWhitelistSet()).isTrue();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void bannedNodeIdsOptionMustBeUsed() {
    final String[] nodes = {"0001", "0002", "0003"};
    parseCommand("--banned-nodeids", String.join(",", nodes));

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
    parseCommand("--p2p-listen", String.format("%1$s:%2$s", host, port));

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

  @Ignore
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
    parseCommand("--rpc-enabled");

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().isEnabled()).isTrue();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcApisPropertyMustBeUsed() {
    parseCommand("--rpc-api", "ETH,NET");

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getRpcApis())
        .containsExactlyInAnyOrder(RpcApis.ETH, RpcApis.NET);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void jsonRpcHostAndPortOptionMustBeUsed() {

    final String host = "1.2.3.4";
    final int port = 1234;
    parseCommand("--rpc-listen", String.format("%1$s:%2$s", host, port));

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
    parseCommand("--rpc-cors-origins", String.join(",", origins));

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
    parseCommand("--rpc-cors-origins", String.join(",", origins));

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getCorsAllowedDomains().toArray())
        .isEqualTo(origins);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void jsonRpcCorsOriginsWithAllMustBuildListWithWildcard() {
    final String[] origins = {"all"};
    parseCommand("--rpc-cors-origins", String.join(",", origins));

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getCorsAllowedDomains())
        .isEqualTo(Lists.newArrayList("*"));

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void jsonRpcCorsOriginsWithNoneMustBuildEmptyList() {
    final String[] origins = {"none"};
    parseCommand("--rpc-cors-origins", String.join(",", origins));

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getCorsAllowedDomains()).isEmpty();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void jsonRpcCorsOriginsNoneWithAnotherDomainMustFail() {
    final String[] origins = {"http://domain1.com", "none"};
    parseCommand("--rpc-cors-origins", String.join(",", origins));

    verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Value 'none' can't be used with other domains");
  }

  @Test
  public void jsonRpcCorsOriginsAllWithAnotherDomainMustFail() {
    final String[] origins = {"http://domain1.com", "all"};
    parseCommand("--rpc-cors-origins", String.join(",", origins));

    verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Value 'all' can't be used with other domains");
  }

  @Test
  public void jsonRpcCorsOriginsWildcardWithAnotherDomainMustFail() {
    final String[] origins = {"http://domain1.com", "*"};
    parseCommand("--rpc-cors-origins", String.join(",", origins));

    verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Value 'all' can't be used with other domains");
  }

  @Test
  public void jsonRpcCorsOriginsInvalidRegexShouldFail() {
    final String[] origins = {"**"};
    parseCommand("--rpc-cors-origins", String.join(",", origins));

    verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Domain values result in invalid regex pattern");
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
    parseCommand("--ws-enabled");

    verify(mockRunnerBuilder).webSocketConfiguration(wsRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(wsRpcConfigArgumentCaptor.getValue().isEnabled()).isTrue();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void wsApiPropertyMustBeUsed() {
    parseCommand("--ws-api", "ETH, NET");

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
    parseCommand("--ws-listen", String.format("%1$s:%2$s", host, port));

    verify(mockRunnerBuilder).webSocketConfiguration(wsRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(wsRpcConfigArgumentCaptor.getValue().getHost()).isEqualTo(host);
    assertThat(wsRpcConfigArgumentCaptor.getValue().getPort()).isEqualTo(port);

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
        "--miner-minTransactionGasPriceWei=15",
        "--miner-extraData=" + extraDataString);

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
  public void rinkebyValuesCanBeOverridden() throws Exception {
    final String[] nodes = {"enode://001@123:4567", "enode://002@123:4567", "enode://003@123:4567"};
    final Path genesisFile = createFakeGenesisFile();
    parseCommand(
        "--rinkeby",
        "--network-id",
        "1",
        "--bootnodes",
        String.join(",", nodes),
        "--genesis",
        genesisFile.toString());

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilder).ethNetworkConfig(networkArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
    assertThat(networkArg.getValue().getGenesisConfig()).isEqualTo("genesis_config");
    assertThat(networkArg.getValue().getBootNodes()).isEqualTo(Arrays.asList(nodes));
    assertThat(networkArg.getValue().getNetworkId()).isEqualTo(1);
  }

  private Path createFakeGenesisFile() throws IOException {
    final Path genesisFile = Files.createTempFile("genesisFile", "");
    Files.write(genesisFile, "genesis_config".getBytes(UTF_8));
    return genesisFile;
  }
}
