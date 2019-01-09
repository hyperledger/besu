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

import tech.pegasys.pantheon.consensus.clique.CliqueExtraData;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApi;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApis;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import tech.pegasys.pantheon.ethereum.permissioning.PermissioningConfiguration;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.RunnableNode;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.google.common.io.Resources;

public class PantheonNodeFactory {

  private PantheonNode create(final PantheonFactoryConfiguration config) throws IOException {
    ServerSocket serverSocket = new ServerSocket(0);
    final PantheonNode node =
        new PantheonNode(
            config.getName(),
            config.getMiningParameters(),
            config.getJsonRpcConfiguration(),
            config.getWebSocketConfiguration(),
            config.getMetricsConfiguration(),
            config.getPermissioningConfiguration(),
            config.isDevMode(),
            config.getGenesisConfigProvider(),
            serverSocket.getLocalPort());
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

  public PantheonNode createMinerNodeWithCustomRefreshDelay(
      final String name, final Long refreshDelay) throws IOException {

    WebSocketConfiguration wsConfig = createWebSocketEnabledConfig();
    wsConfig.setRefreshDelay(refreshDelay);

    return create(
        new PantheonFactoryConfigurationBuilder()
            .setName(name)
            .miningEnabled()
            .setJsonRpcConfiguration(createJsonRpcEnabledConfig())
            .setWebSocketConfiguration(wsConfig)
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

  public PantheonNode createArchiveNodeWithCustomRefreshDelay(
      final String name, final Long refreshDelay) throws IOException {
    WebSocketConfiguration wsConfig = createWebSocketEnabledConfig();
    wsConfig.setRefreshDelay(refreshDelay);

    return create(
        new PantheonFactoryConfigurationBuilder()
            .setName(name)
            .setJsonRpcConfiguration(createJsonRpcEnabledConfig())
            .setWebSocketConfiguration(wsConfig)
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

  public PantheonNode createNodeWithNodesWhitelist(
      final String name, final List<URI> nodesWhitelist) throws IOException {
    PermissioningConfiguration permissioningConfiguration =
        PermissioningConfiguration.createDefault();
    permissioningConfiguration.setNodeWhitelist(nodesWhitelist);

    return create(
        new PantheonFactoryConfigurationBuilder()
            .setName(name)
            .setJsonRpcConfiguration(jsonRpcConfigWithPermissioning())
            .setPermissioningConfiguration(permissioningConfiguration)
            .build());
  }

  public PantheonNode createNodeWithAccountsWhitelist(
      final String name, final List<String> accountsWhitelist) throws IOException {
    PermissioningConfiguration permissioningConfiguration =
        PermissioningConfiguration.createDefault();
    permissioningConfiguration.setAccountWhitelist(accountsWhitelist);

    return create(
        new PantheonFactoryConfigurationBuilder()
            .setName(name)
            .miningEnabled()
            .setJsonRpcConfiguration(jsonRpcConfigWithPermissioning())
            .setPermissioningConfiguration(permissioningConfiguration)
            .build());
  }

  public PantheonNode createNodeWithNodesWhitelistAndPermRPC(
      final String name, final List<URI> nodesWhitelist) throws IOException {
    PermissioningConfiguration permissioningConfiguration =
        PermissioningConfiguration.createDefault();
    permissioningConfiguration.setNodeWhitelist(nodesWhitelist);

    JsonRpcConfiguration rpcConfig = JsonRpcConfiguration.createDefault();

    rpcConfig.setEnabled(true);
    rpcConfig.setPort(0);
    rpcConfig.setHostsWhitelist(singletonList("*"));
    rpcConfig.addRpcApi(RpcApis.PERM);

    return create(
        new PantheonFactoryConfigurationBuilder()
            .setName(name)
            .setJsonRpcConfiguration(rpcConfig)
            .setPermissioningConfiguration(permissioningConfiguration)
            .build());
  }

  public PantheonNode createCliqueNode(final String name) throws IOException {
    return create(
        new PantheonFactoryConfigurationBuilder()
            .setName(name)
            .miningEnabled()
            .setJsonRpcConfiguration(jsonRpcConfigWithClique())
            .setWebSocketConfiguration(createWebSocketEnabledConfig())
            .setDevMode(false)
            .setGenesisConfigProvider(this::createCliqueGenesisConfig)
            .build());
  }

  private Optional<String> createCliqueGenesisConfig(final Collection<RunnableNode> validators) {
    String genesisTemplate = cliqueGenesisTemplateConfig();
    String cliqueExtraData = encodeCliqueExtraData(validators);
    String genesis = genesisTemplate.replaceAll("%cliqueExtraData%", cliqueExtraData);
    return Optional.of(genesis);
  }

  private String encodeCliqueExtraData(final Collection<RunnableNode> nodes) {
    final List<Address> addresses = nodes.stream().map(RunnableNode::getAddress).collect(toList());
    return CliqueExtraData.createGenesisExtraDataString(addresses);
  }

  private String cliqueGenesisTemplateConfig() {
    try {
      URI uri = Resources.getResource("clique/clique.json").toURI();
      return Resources.toString(uri.toURL(), Charset.defaultCharset());
    } catch (URISyntaxException | IOException e) {
      throw new IllegalStateException("Unable to get test clique genesis config");
    }
  }

  public PantheonNode createCliqueNodeWithValidators(final String name, final String... validators)
      throws IOException {

    return create(
        new PantheonFactoryConfigurationBuilder()
            .setName(name)
            .miningEnabled()
            .setJsonRpcConfiguration(jsonRpcConfigWithClique())
            .setWebSocketConfiguration(createWebSocketEnabledConfig())
            .setDevMode(false)
            .setGenesisConfigProvider(
                nodes -> createCliqueGenesisConfigForValidators(asList(validators), nodes))
            .build());
  }

  private Optional<String> createCliqueGenesisConfigForValidators(
      final Collection<String> validators, final Collection<RunnableNode> pantheonNodes) {
    List<RunnableNode> collect =
        pantheonNodes.stream().filter(n -> validators.contains(n.getName())).collect(toList());
    return createCliqueGenesisConfig(collect);
  }

  private JsonRpcConfiguration jsonRpcConfigWithClique() {
    final JsonRpcConfiguration jsonRpcConfig = createJsonRpcEnabledConfig();
    final List<RpcApi> rpcApis = new ArrayList<>(jsonRpcConfig.getRpcApis());
    rpcApis.add(CLIQUE);
    jsonRpcConfig.setRpcApis(rpcApis);
    return jsonRpcConfig;
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
    final JsonRpcConfiguration jsonRpcConfig = createJsonRpcEnabledConfig();
    final List<RpcApi> rpcApis = new ArrayList<>(jsonRpcConfig.getRpcApis());
    rpcApis.add(RpcApis.PERM);
    jsonRpcConfig.setRpcApis(rpcApis);
    return jsonRpcConfig;
  }
}
