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
import tech.pegasys.pantheon.tests.acceptance.dsl.node.GenesisConfigProvider;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.Node;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.RunnableNode;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.google.common.io.Resources;

public class PantheonNodeFactory {

  PantheonNode create(final PantheonFactoryConfiguration config) throws IOException {
    return new PantheonNode(
        config.getName(),
        config.getMiningParameters(),
        config.getPrivacyParameters(),
        config.getJsonRpcConfiguration(),
        config.getWebSocketConfiguration(),
        config.getMetricsConfiguration(),
        config.getPermissioningConfiguration(),
        config.getKeyFilePath(),
        config.isDevMode(),
        config.getGenesisConfigProvider(),
        config.isP2pEnabled(),
        config.isDiscoveryEnabled(),
        config.isBootnodeEligible());
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
      final String name, final PrivacyParameters privacyParameters, final String keyFilePath)
      throws IOException {
    return create(
        new PantheonFactoryConfigurationBuilder()
            .setName(name)
            .miningEnabled()
            .jsonRpcEnabled()
            .setKeyFilePath(keyFilePath)
            .enablePrivateTransactions(privacyParameters)
            .webSocketEnabled()
            .build());
  }

  public PantheonNode createPrivateTransactionEnabledNode(
      final String name, final PrivacyParameters privacyParameters, final String keyFilePath)
      throws IOException {
    return create(
        new PantheonFactoryConfigurationBuilder()
            .setName(name)
            .jsonRpcEnabled()
            .setKeyFilePath(keyFilePath)
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

  public Node createArchiveNodeThatMustNotBeTheBootnode(final String name) throws IOException {
    return create(
        new PantheonFactoryConfigurationBuilder()
            .setName(name)
            .jsonRpcEnabled()
            .webSocketEnabled()
            .bootnodeEligible(false)
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
            .setJsonRpcConfiguration(createJsonRpcEnabledConfig())
            .build());
  }

  public PantheonNode createNodeWithP2pDisabledAndAdmin(final String name) throws IOException {
    return create(
        new PantheonFactoryConfigurationBuilder()
            .setName(name)
            .setP2pEnabled(false)
            .setJsonRpcConfiguration(jsonRpcConfigWithAdmin())
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

  public PantheonNode createCustomGenesisNode(
      final String name, final String genesisPath, final boolean canBeBootnode) throws IOException {
    final String genesisFile = readGenesisFile(genesisPath);
    return create(
        new PantheonFactoryConfigurationBuilder()
            .setName(name)
            .jsonRpcEnabled()
            .webSocketEnabled()
            .setGenesisConfigProvider((a) -> Optional.of(genesisFile))
            .setDevMode(false)
            .bootnodeEligible(canBeBootnode)
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
    final String template = readGenesisFile("clique/clique.json");
    return updateGenesisExtraData(
        validators, template, CliqueExtraData::createGenesisExtraDataString);
  }

  private Optional<String> createIbftGenesisConfig(
      final Collection<? extends RunnableNode> validators) {
    final String template = readGenesisFile("ibft/ibft.json");
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

  private String readGenesisFile(final String filepath) {
    try {
      final URI uri = Resources.getResource(filepath).toURI();
      return Resources.toString(uri.toURL(), Charset.defaultCharset());
    } catch (final URISyntaxException | IOException e) {
      throw new IllegalStateException("Unable to get test genesis config " + filepath);
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

  private JsonRpcConfiguration jsonRpcConfigWithAdmin() {
    return createJsonRpcConfigWithRpcApiEnabled(RpcApis.ADMIN);
  }

  private JsonRpcConfiguration createJsonRpcConfigWithRpcApiEnabled(final RpcApi... rpcApi) {
    final JsonRpcConfiguration jsonRpcConfig = createJsonRpcEnabledConfig();
    final List<RpcApi> rpcApis = new ArrayList<>(jsonRpcConfig.getRpcApis());
    rpcApis.addAll(Arrays.asList(rpcApi));
    jsonRpcConfig.setRpcApis(rpcApis);
    return jsonRpcConfig;
  }
}
