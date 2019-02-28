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
package tech.pegasys.pantheon.tests.acceptance.dsl.node.factory;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static tech.pegasys.pantheon.consensus.clique.jsonrpc.CliqueRpcApis.CLIQUE;
import static tech.pegasys.pantheon.consensus.ibft.jsonrpc.IbftRpcApis.IBFT;

import tech.pegasys.pantheon.consensus.clique.CliqueExtraData;
import tech.pegasys.pantheon.consensus.ibft.IbftExtraData;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApi;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApis;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import tech.pegasys.pantheon.ethereum.permissioning.PermissioningConfiguration;
import tech.pegasys.pantheon.ethereum.permissioning.WhitelistPersistor;
import tech.pegasys.pantheon.ethereum.permissioning.WhitelistPersistor.WHITELIST_TYPE;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.GenesisConfigProvider;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.RunnableNode;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.google.common.io.Resources;

public class PantheonNodeFactory {

  private PantheonNode create(final PantheonFactoryConfiguration config) throws IOException {
    ServerSocket serverSocket = new ServerSocket(0);
    final PantheonNode node =
        new PantheonNode(
            config.getName(),
            config.getMiningParameters(),
            config.getPrivacyParameters(),
            config.getJsonRpcConfiguration(),
            config.getWebSocketConfiguration(),
            config.getMetricsConfiguration(),
            config.getPermissioningConfiguration(),
            config.isDevMode(),
            config.getGenesisConfigProvider(),
            serverSocket.getLocalPort(),
            config.getP2pEnabled(),
            config.isDiscoveryEnabled(),
            config.isBootnode(),
            config.getBootnodes());
    serverSocket.close();

    return node;
  }

  public PantheonNode createMinerNode(final String name) throws IOException {
    return create(
        new PantheonFactoryConfigurationBuilder()
            .setName(name)
            .miningEnabled()
            .jsonRpcEnabled()
            .webSocketEnabled()
            .build());
  }

  public PantheonNode createPrivateTransactionEnabledMinerNode(
      final String name, final PrivacyParameters privacyParameters) throws IOException {
    return create(
        new PantheonFactoryConfigurationBuilder()
            .setName(name)
            .miningEnabled()
            .jsonRpcEnabled()
            .enablePrivateTransactions(privacyParameters)
            .webSocketEnabled()
            .build());
  }

  public PantheonNode createArchiveNode(final String name) throws IOException {
    return create(
        new PantheonFactoryConfigurationBuilder()
            .setName(name)
            .jsonRpcEnabled()
            .webSocketEnabled()
            .build());
  }

  public PantheonNode createNonBootnodeArchiveNode(final String name) throws IOException {
    return create(
        new PantheonFactoryConfigurationBuilder()
            .setName(name)
            .jsonRpcEnabled()
            .webSocketEnabled()
            .setIsBootnode(false)
            .build());
  }

  public PantheonNode createArchiveNodeWithDiscoveryDisabledAndAdmin(final String name)
      throws IOException {
    return create(
        new PantheonFactoryConfigurationBuilder()
            .setName(name)
            .setJsonRpcConfiguration(jsonRpcConfigWithAdmin())
            .webSocketEnabled()
            .setDiscoveryEnabled(false)
            .build());
  }

  public PantheonNode createArchiveNodeWithAuthentication(final String name)
      throws IOException, URISyntaxException {
    return create(
        new PantheonFactoryConfigurationBuilder()
            .setName(name)
            .jsonRpcEnabled()
            .jsonRpcAuthenticationEnabled()
            .webSocketEnabled()
            .build());
  }

  public PantheonNode createArchiveNodeWithAuthenticationOverWebSocket(final String name)
      throws IOException, URISyntaxException {
    return create(
        new PantheonFactoryConfigurationBuilder()
            .setName(name)
            .webSocketEnabled()
            .webSocketAuthenticationEnabled()
            .build());
  }

  public PantheonNode createNodeWithP2pDisabled(final String name) throws IOException {
    return create(
        new PantheonFactoryConfigurationBuilder()
            .setName(name)
            .setP2pEnabled(false)
            .setJsonRpcConfiguration(jsonRpcConfigWithPermissioning())
            .build());
  }

  public PantheonNode createNodeWithP2pDisabledAndAdmin(final String name) throws IOException {
    return create(
        new PantheonFactoryConfigurationBuilder()
            .setName(name)
            .setP2pEnabled(false)
            .setJsonRpcConfiguration(jsonRpcConfigWithPermissioningAndAdmin())
            .build());
  }

  public PantheonNode createArchiveNodeWithRpcDisabled(final String name) throws IOException {
    return create(new PantheonFactoryConfigurationBuilder().setName(name).build());
  }

  public PantheonNode createArchiveNodeWithRpcApis(
      final String name, final RpcApi... enabledRpcApis) throws IOException {
    final JsonRpcConfiguration jsonRpcConfig = createJsonRpcEnabledConfig();
    jsonRpcConfig.setRpcApis(asList(enabledRpcApis));
    final WebSocketConfiguration webSocketConfig = createWebSocketEnabledConfig();
    webSocketConfig.setRpcApis(asList(enabledRpcApis));

    return create(
        new PantheonFactoryConfigurationBuilder()
            .setName(name)
            .setJsonRpcConfiguration(jsonRpcConfig)
            .setWebSocketConfiguration(webSocketConfig)
            .build());
  }

  public PantheonNode createNodeWithWhitelistsEnabled(
      final String name,
      final List<URI> nodesWhitelist,
      final List<String> accountsWhitelist,
      final String tempFilePath)
      throws IOException {
    final PermissioningConfiguration permissioningConfiguration =
        PermissioningConfiguration.createDefault();
    permissioningConfiguration.setNodeWhitelist(nodesWhitelist);
    permissioningConfiguration.setAccountWhitelist(accountsWhitelist);
    permissioningConfiguration.setConfigurationFilePath(tempFilePath);

    return create(
        new PantheonFactoryConfigurationBuilder()
            .setName(name)
            .setJsonRpcConfiguration(jsonRpcConfigWithPermissioning())
            .setPermissioningConfiguration(permissioningConfiguration)
            .build());
  }

  public PantheonNode createNodeWithNodesWhitelist(
      final String name, final List<URI> nodesWhitelist) throws IOException {
    final PermissioningConfiguration permissioningConfiguration =
        PermissioningConfiguration.createDefault();
    permissioningConfiguration.setNodeWhitelist(nodesWhitelist);

    final List<String> whitelistAsStrings =
        nodesWhitelist.parallelStream().map(URI::toString).collect(toList());
    final File tempFile = createTempPermissioningConfigurationFile();
    tempFile.deleteOnExit();
    permissioningConfiguration.setConfigurationFilePath(tempFile.getPath());
    initPermissioningConfigurationFile(
        WhitelistPersistor.WHITELIST_TYPE.NODES, whitelistAsStrings, tempFile.toPath());

    return create(
        new PantheonFactoryConfigurationBuilder()
            .setName(name)
            .setJsonRpcConfiguration(jsonRpcConfigWithPermissioning())
            .setPermissioningConfiguration(permissioningConfiguration)
            .setBootnodes(whitelistAsStrings)
            .build());
  }

  public PantheonNode createNodeWithBootnodeAndNodesWhitelist(
      final String name, final List<URI> bootnodes, final List<URI> nodesWhitelist)
      throws IOException {
    final PermissioningConfiguration permissioningConfiguration =
        PermissioningConfiguration.createDefault();
    permissioningConfiguration.setNodeWhitelist(nodesWhitelist);

    final List<String> bootnodesAsStrings =
        bootnodes.parallelStream().map(URI::toString).collect(toList());
    final List<String> whitelistAsStrings =
        nodesWhitelist.parallelStream().map(URI::toString).collect(toList());
    final File tempFile = createTempPermissioningConfigurationFile();
    tempFile.deleteOnExit();
    permissioningConfiguration.setConfigurationFilePath(tempFile.getPath());
    initPermissioningConfigurationFile(
        WhitelistPersistor.WHITELIST_TYPE.NODES, whitelistAsStrings, tempFile.toPath());

    return create(
        new PantheonFactoryConfigurationBuilder()
            .setName(name)
            .setJsonRpcConfiguration(jsonRpcConfigWithPermissioning())
            .setPermissioningConfiguration(permissioningConfiguration)
            .setBootnodes(bootnodesAsStrings)
            .build());
  }

  private void initPermissioningConfigurationFile(
      final WhitelistPersistor.WHITELIST_TYPE listType,
      final Collection<String> whitelistVal,
      final Path configFilePath)
      throws IOException {
    WhitelistPersistor.addNewConfigItem(listType, whitelistVal, configFilePath);
  }

  public PantheonNode createNodeWithAccountsWhitelist(
      final String name, final List<String> accountsWhitelist) throws IOException {
    PermissioningConfiguration permissioningConfiguration =
        PermissioningConfiguration.createDefault();
    permissioningConfiguration.setAccountWhitelist(accountsWhitelist);
    permissioningConfiguration.setConfigurationFilePath(
        createTempPermissioningConfigurationFile().getPath());

    File tempFile = createTempPermissioningConfigurationFile();
    tempFile.deleteOnExit();
    permissioningConfiguration.setConfigurationFilePath(tempFile.getPath());
    initPermissioningConfigurationFile(
        WHITELIST_TYPE.ACCOUNTS, accountsWhitelist, tempFile.toPath());

    return create(
        new PantheonFactoryConfigurationBuilder()
            .setName(name)
            .miningEnabled()
            .setJsonRpcConfiguration(jsonRpcConfigWithPermissioning())
            .setPermissioningConfiguration(permissioningConfiguration)
            .build());
  }

  private File createTempPermissioningConfigurationFile() throws IOException {
    File tempFile = File.createTempFile("temp", "temp");
    tempFile.deleteOnExit();
    return tempFile;
  }

  public PantheonNode createNodeWithNoDiscovery(final String name) throws IOException {
    return create(
        new PantheonFactoryConfigurationBuilder().setName(name).setDiscoveryEnabled(false).build());
  }

  public PantheonNode createCliqueNode(final String name) throws IOException {
    return create(
        new PantheonFactoryConfigurationBuilder()
            .setName(name)
            .miningEnabled()
            .setJsonRpcConfiguration(createJsonRpcConfigWithClique())
            .setWebSocketConfiguration(createWebSocketEnabledConfig())
            .setDevMode(false)
            .setGenesisConfigProvider(this::createCliqueGenesisConfig)
            .build());
  }

  public PantheonNode createIbftNode(final String name) throws IOException {
    return create(
        new PantheonFactoryConfigurationBuilder()
            .setName(name)
            .miningEnabled()
            .setJsonRpcConfiguration(createJsonRpcConfigWithIbft())
            .setWebSocketConfiguration(createWebSocketEnabledConfig())
            .setDevMode(false)
            .setGenesisConfigProvider(this::createIbftGenesisConfig)
            .build());
  }

  public PantheonNode createCliqueNodeWithValidators(final String name, final String... validators)
      throws IOException {

    return create(
        new PantheonFactoryConfigurationBuilder()
            .setName(name)
            .miningEnabled()
            .setJsonRpcConfiguration(createJsonRpcConfigWithClique())
            .setWebSocketConfiguration(createWebSocketEnabledConfig())
            .setDevMode(false)
            .setGenesisConfigProvider(
                nodes ->
                    createGenesisConfigForValidators(
                        asList(validators), nodes, this::createCliqueGenesisConfig))
            .build());
  }

  public PantheonNode createIbftNodeWithValidators(final String name, final String... validators)
      throws IOException {

    return create(
        new PantheonFactoryConfigurationBuilder()
            .setName(name)
            .miningEnabled()
            .setJsonRpcConfiguration(createJsonRpcConfigWithIbft())
            .setWebSocketConfiguration(createWebSocketEnabledConfig())
            .setDevMode(false)
            .setGenesisConfigProvider(
                nodes ->
                    createGenesisConfigForValidators(
                        asList(validators), nodes, this::createIbftGenesisConfig))
            .build());
  }

  private Optional<String> createCliqueGenesisConfig(
      final Collection<? extends RunnableNode> validators) {
    final String template = genesisTemplateConfig("clique/clique.json");
    return updateGenesisExtraData(
        validators, template, CliqueExtraData::createGenesisExtraDataString);
  }

  private Optional<String> createIbftGenesisConfig(
      final Collection<? extends RunnableNode> validators) {
    final String template = genesisTemplateConfig("ibft/ibft.json");
    return updateGenesisExtraData(
        validators, template, IbftExtraData::createGenesisExtraDataString);
  }

  private Optional<String> updateGenesisExtraData(
      final Collection<? extends RunnableNode> validators,
      final String genesisTemplate,
      final Function<List<Address>, String> extraDataCreator) {
    final List<Address> addresses =
        validators.stream().map(RunnableNode::getAddress).collect(toList());
    final String extraDataString = extraDataCreator.apply(addresses);
    final String genesis = genesisTemplate.replaceAll("%extraData%", extraDataString);
    return Optional.of(genesis);
  }

  private String genesisTemplateConfig(final String template) {
    try {
      URI uri = Resources.getResource(template).toURI();
      return Resources.toString(uri.toURL(), Charset.defaultCharset());
    } catch (URISyntaxException | IOException e) {
      throw new IllegalStateException("Unable to get test genesis config " + template);
    }
  }

  private Optional<String> createGenesisConfigForValidators(
      final Collection<String> validators,
      final Collection<? extends RunnableNode> pantheonNodes,
      final GenesisConfigProvider genesisConfigProvider) {
    final List<RunnableNode> nodes =
        pantheonNodes.stream().filter(n -> validators.contains(n.getName())).collect(toList());
    return genesisConfigProvider.createGenesisConfig(nodes);
  }

  private JsonRpcConfiguration createJsonRpcConfigWithClique() {
    return createJsonRpcConfigWithRpcApiEnabled(CLIQUE);
  }

  private JsonRpcConfiguration createJsonRpcConfigWithIbft() {
    return createJsonRpcConfigWithRpcApiEnabled(IBFT);
  }

  private JsonRpcConfiguration createJsonRpcEnabledConfig() {
    final JsonRpcConfiguration config = JsonRpcConfiguration.createDefault();
    config.setEnabled(true);
    config.setPort(0);
    config.setHostsWhitelist(singletonList("*"));
    return config;
  }

  private WebSocketConfiguration createWebSocketEnabledConfig() {
    final WebSocketConfiguration config = WebSocketConfiguration.createDefault();
    config.setEnabled(true);
    config.setPort(0);
    return config;
  }

  private JsonRpcConfiguration jsonRpcConfigWithPermissioning() {
    return createJsonRpcConfigWithRpcApiEnabled(RpcApis.PERM);
  }

  private JsonRpcConfiguration jsonRpcConfigWithAdmin() {
    return createJsonRpcConfigWithRpcApiEnabled(RpcApis.ADMIN);
  }

  private JsonRpcConfiguration jsonRpcConfigWithPermissioningAndAdmin() {
    return createJsonRpcConfigWithRpcApiEnabled(RpcApis.PERM, RpcApis.ADMIN);
  }

  private JsonRpcConfiguration createJsonRpcConfigWithRpcApiEnabled(final RpcApi... rpcApi) {
    final JsonRpcConfiguration jsonRpcConfig = createJsonRpcEnabledConfig();
    final List<RpcApi> rpcApis = new ArrayList<>(jsonRpcConfig.getRpcApis());
    rpcApis.addAll(Arrays.asList(rpcApi));
    jsonRpcConfig.setRpcApis(rpcApis);
    return jsonRpcConfig;
  }
}
