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
package org.hyperledger.besu.tests.acceptance.dsl.node.configuration;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import org.hyperledger.besu.enclave.EnclaveFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApi;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration;
import org.hyperledger.besu.ethereum.core.InMemoryPrivacyStorageProvider;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.MiningParametersTestBuilder;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.node.RunnableNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.genesis.GenesisConfigurationFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import io.vertx.core.Vertx;

public class BesuNodeFactory {

  private final GenesisConfigurationFactory genesis = new GenesisConfigurationFactory();
  private final NodeConfigurationFactory node = new NodeConfigurationFactory();

  public BesuNode create(final BesuNodeConfiguration config) throws IOException {
    return new BesuNode(
        config.getName(),
        config.getDataPath(),
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
        config.isSecp256k1Native(),
        config.isAltbn128Native(),
        config.getPlugins(),
        config.getExtraCLIOptions(),
        config.getStaticNodes(),
        config.getPrivacyParameters(),
        config.getRunCommand());
  }

  public BesuNode createMinerNode(final String name) throws IOException {
    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .miningEnabled()
            .jsonRpcEnabled()
            .webSocketEnabled()
            .build());
  }

  public BesuNode createMinerNodeWithRevertReasonEnabled(final String name) throws IOException {
    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .miningEnabled()
            .jsonRpcEnabled()
            .webSocketEnabled()
            .revertReasonEnabled()
            .build());
  }

  public BesuNode createArchiveNode(final String name) throws IOException {
    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .jsonRpcEnabled()
            .jsonRpcTxPool()
            .webSocketEnabled()
            .build());
  }

  public BesuNode createNode(
      final String name,
      final Function<BesuNodeConfigurationBuilder, BesuNodeConfigurationBuilder> configModifier)
      throws IOException {
    final BesuNodeConfigurationBuilder configBuilder =
        configModifier.apply(new BesuNodeConfigurationBuilder().name(name));
    return create(configBuilder.build());
  }

  public Node createArchiveNodeThatMustNotBeTheBootnode(final String name) throws IOException {
    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .jsonRpcEnabled()
            .webSocketEnabled()
            .bootnodeEligible(false)
            .build());
  }

  public BesuNode createArchiveNodeWithDiscoveryDisabledAndAdmin(final String name)
      throws IOException {
    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .jsonRpcConfiguration(node.jsonRpcConfigWithAdmin())
            .webSocketEnabled()
            .discoveryEnabled(false)
            .build());
  }

  public BesuNode createArchiveNodeNetServicesEnabled(final String name) throws IOException {
    // TODO: Enable metrics coverage in the acceptance tests. See PIE-1606
    // final MetricsConfiguration metricsConfiguration = MetricsConfiguration.createDefault();
    // metricsConfiguration.setEnabled(true);
    // metricsConfiguration.setPort(0);
    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            // .setMetricsConfiguration(metricsConfiguration)
            .jsonRpcConfiguration(node.jsonRpcConfigWithAdmin())
            .webSocketEnabled()
            .p2pEnabled(true)
            .build());
  }

  public BesuNode createArchiveNodeNetServicesDisabled(final String name) throws IOException {
    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .jsonRpcConfiguration(node.jsonRpcConfigWithAdmin())
            .p2pEnabled(false)
            .build());
  }

  public BesuNode createNodeWithAuthentication(final String name, final String authFile)
      throws IOException, URISyntaxException {
    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .jsonRpcEnabled()
            .jsonRpcAuthenticationConfiguration(authFile)
            .webSocketEnabled()
            .webSocketAuthenticationEnabled()
            .build());
  }

  public BesuNode createNodeWithAuthenticationUsingJwtPublicKey(final String name)
      throws IOException, URISyntaxException {
    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .jsonRpcEnabled()
            .jsonRpcAuthenticationUsingPublicKeyEnabled()
            .webSocketEnabled()
            .webSocketAuthenticationUsingPublicKeyEnabled()
            .build());
  }

  public BesuNode createNodeWithP2pDisabled(final String name) throws IOException {
    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .p2pEnabled(false)
            .jsonRpcConfiguration(node.createJsonRpcEnabledConfig())
            .build());
  }

  public BesuNode createNodeWithMultiTenantedPrivacy(
      final String name,
      final String enclaveUrl,
      final String authFile,
      final String privTransactionSigningKey)
      throws IOException, URISyntaxException {
    final PrivacyParameters.Builder privacyParametersBuilder = new PrivacyParameters.Builder();
    final PrivacyParameters privacyParameters =
        privacyParametersBuilder
            .setMultiTenancyEnabled(true)
            .setEnabled(true)
            .setStorageProvider(new InMemoryPrivacyStorageProvider())
            .setEnclaveFactory(new EnclaveFactory(Vertx.vertx()))
            .setEnclaveUrl(URI.create(enclaveUrl))
            .setPrivateKeyPath(
                Paths.get(ClassLoader.getSystemResource(privTransactionSigningKey).toURI()))
            .build();

    final MiningParameters miningParameters =
        new MiningParametersTestBuilder().minTransactionGasPrice(Wei.ZERO).enabled(true).build();

    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .jsonRpcEnabled()
            .jsonRpcAuthenticationConfiguration(authFile)
            .enablePrivateTransactions()
            .privacyParameters(privacyParameters)
            .miningConfiguration(miningParameters)
            .build());
  }

  public BesuNode createArchiveNodeWithRpcDisabled(final String name) throws IOException {
    return create(new BesuNodeConfigurationBuilder().name(name).build());
  }

  public BesuNode createPluginsNode(
      final String name, final List<String> plugins, final List<String> extraCLIOptions)
      throws IOException {
    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .plugins(plugins)
            .extraCLIOptions(extraCLIOptions)
            .build());
  }

  public BesuNode createArchiveNodeWithRpcApis(final String name, final RpcApi... enabledRpcApis)
      throws IOException {
    final JsonRpcConfiguration jsonRpcConfig = node.createJsonRpcEnabledConfig();
    jsonRpcConfig.setRpcApis(asList(enabledRpcApis));
    final WebSocketConfiguration webSocketConfig = node.createWebSocketEnabledConfig();
    webSocketConfig.setRpcApis(asList(enabledRpcApis));

    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .jsonRpcConfiguration(jsonRpcConfig)
            .webSocketConfiguration(webSocketConfig)
            .build());
  }

  public BesuNode createNodeWithNoDiscovery(final String name) throws IOException {
    return create(new BesuNodeConfigurationBuilder().name(name).discoveryEnabled(false).build());
  }

  public BesuNode createCliqueNode(final String name) throws IOException {
    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .miningEnabled()
            .jsonRpcConfiguration(node.createJsonRpcWithCliqueEnabledConfig())
            .webSocketConfiguration(node.createWebSocketEnabledConfig())
            .devMode(false)
            .genesisConfigProvider(genesis::createCliqueGenesisConfig)
            .build());
  }

  public BesuNode createIbft2NonValidatorBootnode(final String name, final String genesisFile)
      throws IOException {
    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .jsonRpcConfiguration(node.createJsonRpcWithIbft2AdminEnabledConfig())
            .webSocketConfiguration(node.createWebSocketEnabledConfig())
            .devMode(false)
            .genesisConfigProvider(
                validators ->
                    genesis.createIbft2GenesisConfigFilterBootnode(validators, genesisFile))
            .bootnodeEligible(true)
            .build());
  }

  public BesuNode createIbft2Node(final String name, final String genesisFile) throws IOException {
    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .miningEnabled()
            .jsonRpcConfiguration(node.createJsonRpcWithIbft2AdminEnabledConfig())
            .webSocketConfiguration(node.createWebSocketEnabledConfig())
            .devMode(false)
            .genesisConfigProvider(
                validators ->
                    genesis.createIbft2GenesisConfigFilterBootnode(validators, genesisFile))
            .bootnodeEligible(false)
            .build());
  }

  public BesuNode createIbft2Node(final String name) throws IOException {
    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .miningEnabled()
            .jsonRpcConfiguration(node.createJsonRpcWithIbft2EnabledConfig())
            .webSocketConfiguration(node.createWebSocketEnabledConfig())
            .devMode(false)
            .genesisConfigProvider(genesis::createIbft2GenesisConfig)
            .build());
  }

  public BesuNode createCustomGenesisNode(
      final String name, final String genesisPath, final boolean canBeBootnode) throws IOException {
    return createCustomGenesisNode(name, genesisPath, canBeBootnode, false);
  }

  public BesuNode createCustomGenesisNode(
      final String name,
      final String genesisPath,
      final boolean canBeBootnode,
      final boolean mining)
      throws IOException {
    final String genesisFile = genesis.readGenesisFile(genesisPath);
    final BesuNodeConfigurationBuilder builder =
        new BesuNodeConfigurationBuilder()
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

  public BesuNode createCliqueNodeWithValidators(final String name, final String... validators)
      throws IOException {

    return create(
        new BesuNodeConfigurationBuilder()
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

  public BesuNode createIbft2NodeWithValidators(final String name, final String... validators)
      throws IOException {

    return create(
        new BesuNodeConfigurationBuilder()
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

  public BesuNode createNodeWithStaticNodes(final String name, final List<Node> staticNodes)
      throws IOException {

    final List<String> staticNodesUrls =
        staticNodes.stream()
            .map(node -> (RunnableNode) node)
            .map(RunnableNode::enodeUrl)
            .map(URI::toASCIIString)
            .collect(toList());

    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .jsonRpcEnabled()
            .webSocketEnabled()
            .discoveryEnabled(false)
            .staticNodes(staticNodesUrls)
            .bootnodeEligible(false)
            .build());
  }

  public BesuNode runCommand(final String command) throws IOException {
    return create(new BesuNodeConfigurationBuilder().name("run " + command).run(command).build());
  }
}
