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
package tech.pegasys.pantheon.tests.acceptance.dsl.node.configuration;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApi;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.Node;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.RunnableNode;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.configuration.genesis.GenesisConfigurationFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

public class PantheonNodeFactory {

  private final GenesisConfigurationFactory genesis = new GenesisConfigurationFactory();
  private final NodeConfigurationFactory node = new NodeConfigurationFactory();

  public PantheonNode create(final PantheonNodeConfiguration config) throws IOException {
    return new PantheonNode(
        config.getName(),
        config.getMiningParameters(),
        config.getJsonRpcConfiguration(),
        config.getWebSocketConfiguration(),
        config.getMetricsConfiguration(),
        config.getPermissioningConfiguration(),
        config.getKeyFilePath(),
        config.isDevMode(),
        config.getGenesisConfigProvider(),
        config.isP2pEnabled(),
        config.getNetworkingConfiguration(),
        config.isDiscoveryEnabled(),
        config.isBootnodeEligible(),
        config.isRevertReasonEnabled(),
        config.getPlugins(),
        config.getExtraCLIOptions(),
        config.getStaticNodes());
  }

  public PantheonNode createMinerNode(final String name) throws IOException {
    return create(
        new PantheonNodeConfigurationBuilder()
            .name(name)
            .miningEnabled()
            .jsonRpcEnabled()
            .webSocketEnabled()
            .build());
  }

  public PantheonNode createMinerNodeWithRevertReasonEnabled(final String name) throws IOException {
    return create(
        new PantheonNodeConfigurationBuilder()
            .name(name)
            .miningEnabled()
            .jsonRpcEnabled()
            .webSocketEnabled()
            .revertReasonEnabled()
            .build());
  }

  public PantheonNode createArchiveNode(final String name) throws IOException {
    return create(
        new PantheonNodeConfigurationBuilder()
            .name(name)
            .jsonRpcEnabled()
            .webSocketEnabled()
            .build());
  }

  public Node createArchiveNodeThatMustNotBeTheBootnode(final String name) throws IOException {
    return create(
        new PantheonNodeConfigurationBuilder()
            .name(name)
            .jsonRpcEnabled()
            .webSocketEnabled()
            .bootnodeEligible(false)
            .build());
  }

  public PantheonNode createArchiveNodeWithDiscoveryDisabledAndAdmin(final String name)
      throws IOException {
    return create(
        new PantheonNodeConfigurationBuilder()
            .name(name)
            .jsonRpcConfiguration(node.jsonRpcConfigWithAdmin())
            .webSocketEnabled()
            .discoveryEnabled(false)
            .build());
  }

  public PantheonNode createArchiveNodeNetServicesEnabled(final String name) throws IOException {
    // TODO: Enable metrics coverage in the acceptance tests. See PIE-1606
    // final MetricsConfiguration metricsConfiguration = MetricsConfiguration.createDefault();
    // metricsConfiguration.setEnabled(true);
    // metricsConfiguration.setPort(0);
    return create(
        new PantheonNodeConfigurationBuilder()
            .name(name)
            // .setMetricsConfiguration(metricsConfiguration)
            .jsonRpcConfiguration(node.jsonRpcConfigWithAdmin())
            .webSocketEnabled()
            .p2pEnabled(true)
            .build());
  }

  public PantheonNode createArchiveNodeNetServicesDisabled(final String name) throws IOException {
    return create(
        new PantheonNodeConfigurationBuilder()
            .name(name)
            .jsonRpcConfiguration(node.jsonRpcConfigWithAdmin())
            .p2pEnabled(false)
            .build());
  }

  public PantheonNode createArchiveNodeWithAuthentication(final String name)
      throws IOException, URISyntaxException {
    return create(
        new PantheonNodeConfigurationBuilder()
            .name(name)
            .jsonRpcEnabled()
            .jsonRpcAuthenticationEnabled()
            .webSocketEnabled()
            .build());
  }

  public PantheonNode createArchiveNodeWithAuthenticationOverWebSocket(final String name)
      throws IOException, URISyntaxException {
    return create(
        new PantheonNodeConfigurationBuilder()
            .name(name)
            .webSocketEnabled()
            .webSocketAuthenticationEnabled()
            .build());
  }

  public PantheonNode createNodeWithP2pDisabled(final String name) throws IOException {
    return create(
        new PantheonNodeConfigurationBuilder()
            .name(name)
            .p2pEnabled(false)
            .jsonRpcConfiguration(node.createJsonRpcEnabledConfig())
            .build());
  }

  public PantheonNode createArchiveNodeWithRpcDisabled(final String name) throws IOException {
    return create(new PantheonNodeConfigurationBuilder().name(name).build());
  }

  public PantheonNode createPluginsNode(
      final String name, final List<String> plugins, final List<String> extraCLIOptions)
      throws IOException {
    return create(
        new PantheonNodeConfigurationBuilder()
            .name(name)
            .plugins(plugins)
            .extraCLIOptions(extraCLIOptions)
            .build());
  }

  public PantheonNode createArchiveNodeWithRpcApis(
      final String name, final RpcApi... enabledRpcApis) throws IOException {
    final JsonRpcConfiguration jsonRpcConfig = node.createJsonRpcEnabledConfig();
    jsonRpcConfig.setRpcApis(asList(enabledRpcApis));
    final WebSocketConfiguration webSocketConfig = node.createWebSocketEnabledConfig();
    webSocketConfig.setRpcApis(asList(enabledRpcApis));

    return create(
        new PantheonNodeConfigurationBuilder()
            .name(name)
            .jsonRpcConfiguration(jsonRpcConfig)
            .webSocketConfiguration(webSocketConfig)
            .build());
  }

  public PantheonNode createNodeWithNoDiscovery(final String name) throws IOException {
    return create(
        new PantheonNodeConfigurationBuilder().name(name).discoveryEnabled(false).build());
  }

  public PantheonNode createCliqueNode(final String name) throws IOException {
    return create(
        new PantheonNodeConfigurationBuilder()
            .name(name)
            .miningEnabled()
            .jsonRpcConfiguration(node.createJsonRpcWithCliqueEnabledConfig())
            .webSocketConfiguration(node.createWebSocketEnabledConfig())
            .devMode(false)
            .genesisConfigProvider(genesis::createCliqueGenesisConfig)
            .build());
  }

  public PantheonNode createIbft2Node(final String name) throws IOException {
    return create(
        new PantheonNodeConfigurationBuilder()
            .name(name)
            .miningEnabled()
            .jsonRpcConfiguration(node.createJsonRpcWithIbft2EnabledConfig())
            .webSocketConfiguration(node.createWebSocketEnabledConfig())
            .devMode(false)
            .genesisConfigProvider(genesis::createIbft2GenesisConfig)
            .build());
  }

  public PantheonNode createCustomGenesisNode(
      final String name, final String genesisPath, final boolean canBeBootnode) throws IOException {
    return createCustomGenesisNode(name, genesisPath, canBeBootnode, false);
  }

  public PantheonNode createCustomGenesisNode(
      final String name,
      final String genesisPath,
      final boolean canBeBootnode,
      final boolean mining)
      throws IOException {
    final String genesisFile = genesis.readGenesisFile(genesisPath);
    final PantheonNodeConfigurationBuilder builder =
        new PantheonNodeConfigurationBuilder()
            .name(name)
            .jsonRpcEnabled()
            .webSocketEnabled()
            .genesisConfigProvider((a) -> Optional.of(genesisFile))
            .devMode(false)
            .bootnodeEligible(canBeBootnode);

    if (mining) {
      builder.miningEnabled();
    }

    return create(builder.build());
  }

  public PantheonNode createCliqueNodeWithValidators(final String name, final String... validators)
      throws IOException {

    return create(
        new PantheonNodeConfigurationBuilder()
            .name(name)
            .miningEnabled()
            .jsonRpcConfiguration(node.createJsonRpcWithCliqueEnabledConfig())
            .webSocketConfiguration(node.createWebSocketEnabledConfig())
            .devMode(false)
            .genesisConfigProvider(
                nodes ->
                    node.createGenesisConfigForValidators(
                        asList(validators), nodes, genesis::createCliqueGenesisConfig))
            .build());
  }

  public PantheonNode createIbft2NodeWithValidators(final String name, final String... validators)
      throws IOException {

    return create(
        new PantheonNodeConfigurationBuilder()
            .name(name)
            .miningEnabled()
            .jsonRpcConfiguration(node.createJsonRpcWithIbft2EnabledConfig())
            .webSocketConfiguration(node.createWebSocketEnabledConfig())
            .devMode(false)
            .genesisConfigProvider(
                nodes ->
                    node.createGenesisConfigForValidators(
                        asList(validators), nodes, genesis::createIbft2GenesisConfig))
            .build());
  }

  public PantheonNode createNodeWithStaticNodes(final String name, final List<Node> staticNodes)
      throws IOException {

    final List<String> staticNodesUrls =
        staticNodes.stream()
            .map(node -> (RunnableNode) node)
            .map(RunnableNode::enodeUrl)
            .map(URI::toASCIIString)
            .collect(toList());

    return create(
        new PantheonNodeConfigurationBuilder()
            .name(name)
            .jsonRpcEnabled()
            .webSocketEnabled()
            .discoveryEnabled(false)
            .staticNodes(staticNodesUrls)
            .bootnodeEligible(false)
            .build());
  }
}
