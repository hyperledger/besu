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
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis.ADMIN;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis.IBFT;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration;
import org.hyperledger.besu.ethereum.permissioning.LocalPermissioningConfiguration;
import org.hyperledger.besu.ethereum.permissioning.PermissioningConfiguration;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.node.RunnableNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.genesis.GenesisConfigurationFactory;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.genesis.GenesisConfigurationFactory.CliqueOptions;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

public class BesuNodeFactory {

  private final NodeConfigurationFactory node = new NodeConfigurationFactory();

  public BesuNode create(final BesuNodeConfiguration config) throws IOException {
    return new BesuNode(
        config.getName(),
        config.getDataPath(),
        config.getMiningParameters(),
        config.getTransactionPoolConfiguration(),
        config.getJsonRpcConfiguration(),
        config.getEngineRpcConfiguration(),
        config.getWebSocketConfiguration(),
        config.getJsonRpcIpcConfiguration(),
        config.getInProcessRpcConfiguration(),
        config.getMetricsConfiguration(),
        config.getPermissioningConfiguration(),
        config.getApiConfiguration(),
        config.getDataStorageConfiguration(),
        config.getKeyFilePath(),
        config.isDevMode(),
        config.getNetwork(),
        config.getGenesisConfigProvider(),
        config.isP2pEnabled(),
        config.getP2pPort(),
        config.getNetworkingConfiguration(),
        config.isDiscoveryEnabled(),
        config.isBootnodeEligible(),
        config.isRevertReasonEnabled(),
        config.isSecp256k1Native(),
        config.isAltbn128Native(),
        config.getPlugins(),
        config.getRequestedPlugins(),
        config.getExtraCLIOptions(),
        config.getStaticNodes(),
        config.isDnsEnabled(),
        config.getPrivacyParameters(),
        config.getRunCommand(),
        config.getKeyPair(),
        config.isStrictTxReplayProtectionEnabled(),
        config.getEnvironment());
  }

  public BesuNode createMinerNode(
      final String name, final UnaryOperator<BesuNodeConfigurationBuilder> configModifier)
      throws IOException {
    BesuNodeConfigurationBuilder builder =
        new BesuNodeConfigurationBuilder()
            .name(name)
            .miningEnabled()
            .jsonRpcEnabled()
            .webSocketEnabled();
    builder = configModifier.apply(builder);
    final BesuNodeConfiguration config = builder.build();

    return create(config);
  }

  public BesuNode createMinerNodeWithExtraCliOptions(
      final String name,
      final UnaryOperator<BesuNodeConfigurationBuilder> configModifier,
      final List<String> extraCliOptions)
      throws IOException {
    BesuNodeConfigurationBuilder builder =
        new BesuNodeConfigurationBuilder()
            .name(name)
            .miningEnabled()
            .jsonRpcEnabled()
            .jsonRpcTxPool()
            .webSocketEnabled()
            .extraCLIOptions(extraCliOptions);
    builder = configModifier.apply(builder);
    final BesuNodeConfiguration config = builder.build();

    return create(config);
  }

  public BesuNode createMinerNode(final String name) throws IOException {
    return createMinerNode(name, UnaryOperator.identity());
  }

  public BesuNode createMinerNodeWithExtraCliOptions(
      final String name, final List<String> extraCliOptions) throws IOException {
    return createMinerNodeWithExtraCliOptions(name, UnaryOperator.identity(), extraCliOptions);
  }

  public BesuNode createMinerNodeWithRevertReasonEnabled(final String name) throws IOException {
    return createMinerNode(name, BesuNodeConfigurationBuilder::revertReasonEnabled);
  }

  public BesuNode createArchiveNode(final String name) throws IOException {
    return createArchiveNode(name, UnaryOperator.identity());
  }

  public BesuNode createArchiveNode(
      final String name, final UnaryOperator<BesuNodeConfigurationBuilder> configModifier)
      throws IOException {
    BesuNodeConfigurationBuilder builder =
        new BesuNodeConfigurationBuilder()
            .name(name)
            .jsonRpcEnabled()
            .jsonRpcTxPool()
            .webSocketEnabled();

    builder = configModifier.apply(builder);

    return create(builder.build());
  }

  public BesuNode createNode(
      final String name, final UnaryOperator<BesuNodeConfigurationBuilder> configModifier)
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

  public BesuNode createNodeWithAuthFileAndNoAuthApi(
      final String name, final String authFile, final List<String> noAuthApiMethods)
      throws URISyntaxException, IOException {
    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .jsonRpcEnabled()
            .jsonRpcAuthenticationConfiguration(authFile, noAuthApiMethods)
            .webSocketEnabled()
            .webSocketAuthenticationEnabled()
            .build());
  }

  public BesuNode createWsNodeWithAuthFileAndNoAuthApi(
      final String name, final String authFile, final List<String> noAuthApiMethods)
      throws URISyntaxException, IOException {
    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .jsonRpcEnabled()
            .jsonRpcAuthenticationConfiguration(authFile)
            .webSocketEnabled()
            .webSocketAuthenticationEnabledWithNoAuthMethods(noAuthApiMethods)
            .build());
  }

  public BesuNode createNodeWithAuthenticationUsingRsaJwtPublicKey(final String name)
      throws IOException, URISyntaxException {
    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .jsonRpcEnabled()
            .jsonRpcAuthenticationUsingRSA()
            .webSocketEnabled()
            .webSocketAuthenticationUsingRsaPublicKeyEnabled()
            .build());
  }

  public BesuNode createNodeWithAuthenticationUsingEcdsaJwtPublicKey(final String name)
      throws IOException, URISyntaxException {
    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .jsonRpcEnabled()
            .jsonRpcAuthenticationUsingECDSA()
            .webSocketEnabled()
            .webSocketAuthenticationUsingEcdsaPublicKeyEnabled()
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

  public BesuNode createArchiveNodeWithRpcDisabled(final String name) throws IOException {
    return create(new BesuNodeConfigurationBuilder().name(name).build());
  }

  public BesuNode createPluginsNode(
      final String name,
      final List<String> plugins,
      final List<String> extraCLIOptions,
      final String... extraRpcApis)
      throws IOException {

    final List<String> enableRpcApis = new ArrayList<>(Arrays.asList(extraRpcApis));
    enableRpcApis.addAll(List.of(IBFT.name(), ADMIN.name()));

    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .jsonRpcConfiguration(
                node.createJsonRpcWithRpcApiEnabledConfig(enableRpcApis.toArray(String[]::new)))
            .webSocketConfiguration(node.createWebSocketEnabledConfig())
            .plugins(plugins)
            .extraCLIOptions(extraCLIOptions)
            .build());
  }

  public BesuNode createArchiveNodeWithRpcApis(final String name, final String... enabledRpcApis)
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
    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .discoveryEnabled(false)
            .engineRpcEnabled(false)
            .build());
  }

  public BesuNode createCliqueNode(final String name) throws IOException {
    return createCliqueNode(name, CliqueOptions.DEFAULT);
  }

  public BesuNode createCliqueNode(final String name, final CliqueOptions cliqueOptions)
      throws IOException {
    return createCliqueNodeWithExtraCliOptionsAndRpcApis(name, cliqueOptions, List.of());
  }

  public BesuNode createCliqueNodeWithExtraCliOptionsAndRpcApis(
      final String name, final CliqueOptions cliqueOptions, final List<String> extraCliOptions)
      throws IOException {
    return createCliqueNodeWithExtraCliOptionsAndRpcApis(
        name, cliqueOptions, extraCliOptions, Set.of());
  }

  public BesuNode createCliqueNodeWithExtraCliOptionsAndRpcApis(
      final String name,
      final CliqueOptions cliqueOptions,
      final List<String> extraCliOptions,
      final Set<String> extraRpcApis)
      throws IOException {
    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .miningEnabled()
            .jsonRpcConfiguration(node.createJsonRpcWithCliqueEnabledConfig(extraRpcApis))
            .webSocketConfiguration(node.createWebSocketEnabledConfig())
            .inProcessRpcConfiguration(node.createInProcessRpcConfiguration(extraRpcApis))
            .devMode(false)
            .jsonRpcTxPool()
            .genesisConfigProvider(
                validators ->
                    GenesisConfigurationFactory.createCliqueGenesisConfig(
                        validators, cliqueOptions))
            .extraCLIOptions(extraCliOptions)
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
                    GenesisConfigurationFactory.createIbft2GenesisConfigFilterBootnode(
                        validators, genesisFile))
            .bootnodeEligible(true)
            .build());
  }

  public BesuNode createIbft2NodeWithLocalAccountPermissioning(
      final String name,
      final String genesisFile,
      final List<String> accountAllowList,
      final File configFile)
      throws IOException {
    final LocalPermissioningConfiguration config = LocalPermissioningConfiguration.createDefault();
    config.setAccountAllowlist(accountAllowList);
    config.setAccountPermissioningConfigFilePath(configFile.getAbsolutePath());
    final PermissioningConfiguration permissioningConfiguration =
        new PermissioningConfiguration(Optional.of(config), Optional.empty());
    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .miningEnabled()
            .jsonRpcConfiguration(node.createJsonRpcWithIbft2AdminEnabledConfig())
            .webSocketConfiguration(node.createWebSocketEnabledConfig())
            .permissioningConfiguration(permissioningConfiguration)
            .devMode(false)
            .genesisConfigProvider(
                validators ->
                    GenesisConfigurationFactory.createIbft2GenesisConfigFilterBootnode(
                        validators, genesisFile))
            .bootnodeEligible(false)
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
                    GenesisConfigurationFactory.createIbft2GenesisConfigFilterBootnode(
                        validators, genesisFile))
            .bootnodeEligible(false)
            .build());
  }

  public BesuNode createIbft2Node(
      final String name, final boolean fixedPort, final DataStorageFormat storageFormat)
      throws IOException {
    JsonRpcConfiguration rpcConfig = node.createJsonRpcWithIbft2EnabledConfig(false);
    rpcConfig.addRpcApi("ADMIN,TXPOOL");
    if (fixedPort) {
      rpcConfig.setPort(
          Math.abs(name.hashCode() % 60000)
              + 1024); // Generate a consistent port for p2p based on node name
    }

    BesuNodeConfigurationBuilder builder =
        new BesuNodeConfigurationBuilder()
            .name(name)
            .miningEnabled()
            .jsonRpcConfiguration(rpcConfig)
            .webSocketConfiguration(node.createWebSocketEnabledConfig())
            .devMode(false)
            .dataStorageConfiguration(
                storageFormat == DataStorageFormat.FOREST
                    ? DataStorageConfiguration.DEFAULT_FOREST_CONFIG
                    : DataStorageConfiguration.DEFAULT_BONSAI_CONFIG)
            .genesisConfigProvider(GenesisConfigurationFactory::createIbft2GenesisConfig);
    if (fixedPort) {
      builder.p2pPort(
          Math.abs(name.hashCode() % 60000)
              + 1024
              + 500); // Generate a consistent port for p2p based on node name (+ 500 to avoid
      // clashing with RPC port or other nodes with a similar name)
    }
    return create(builder.build());
  }

  public BesuNode createQbftNode(
      final String name, final boolean fixedPort, final DataStorageFormat storageFormat)
      throws IOException {
    JsonRpcConfiguration rpcConfig = node.createJsonRpcWithQbftEnabledConfig(false);
    rpcConfig.addRpcApi("ADMIN,TXPOOL");
    if (fixedPort) {
      rpcConfig.setPort(
          Math.abs(name.hashCode() % 60000)
              + 1024); // Generate a consistent port for p2p based on node name
    }

    BesuNodeConfigurationBuilder builder =
        new BesuNodeConfigurationBuilder()
            .name(name)
            .miningEnabled()
            .jsonRpcConfiguration(rpcConfig)
            .webSocketConfiguration(node.createWebSocketEnabledConfig())
            .devMode(false)
            .dataStorageConfiguration(
                storageFormat == DataStorageFormat.FOREST
                    ? DataStorageConfiguration.DEFAULT_FOREST_CONFIG
                    : DataStorageConfiguration.DEFAULT_BONSAI_CONFIG)
            .genesisConfigProvider(GenesisConfigurationFactory::createQbftGenesisConfig);
    if (fixedPort) {
      builder.p2pPort(
          Math.abs(name.hashCode() % 60000)
              + 1024
              + 500); // Generate a consistent port for p2p based on node name (+ 500 to avoid
      // clashing with RPC port or other nodes with a similar name)
    }
    return create(builder.build());
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
    final String genesisFile = GenesisConfigurationFactory.readGenesisFile(genesisPath);
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

  public BesuNode createExecutionEngineGenesisNode(final String name, final String genesisPath)
      throws IOException {
    final String genesisFile = GenesisConfigurationFactory.readGenesisFile(genesisPath);

    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .genesisConfigProvider((a) -> Optional.of(genesisFile))
            .devMode(false)
            .bootnodeEligible(false)
            .miningEnabled()
            .jsonRpcEnabled()
            .jsonRpcTxPool()
            .engineRpcEnabled(true)
            .jsonRpcDebug()
            .build());
  }

  public BesuNode createCliqueNodeWithValidators(final String name, final String... validators)
      throws IOException {

    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .miningEnabled()
            .jsonRpcConfiguration(node.createJsonRpcWithCliqueEnabledConfig(Set.of()))
            .webSocketConfiguration(node.createWebSocketEnabledConfig())
            .jsonRpcTxPool()
            .devMode(false)
            .genesisConfigProvider(
                nodes ->
                    node.createGenesisConfigForValidators(
                        asList(validators),
                        nodes,
                        GenesisConfigurationFactory::createCliqueGenesisConfig))
            .build());
  }

  public BesuNode createIbft2NodeWithValidators(final String name, final String... validators)
      throws IOException {

    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .miningEnabled()
            .jsonRpcConfiguration(node.createJsonRpcWithIbft2EnabledConfig(false))
            .webSocketConfiguration(node.createWebSocketEnabledConfig())
            .devMode(false)
            .genesisConfigProvider(
                nodes ->
                    node.createGenesisConfigForValidators(
                        asList(validators),
                        nodes,
                        GenesisConfigurationFactory::createIbft2GenesisConfig))
            .build());
  }

  public BesuNode createQbftTLSNodeWithValidators(
      final String name, final String type, final String... validators) throws IOException {

    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .miningEnabled()
            .jsonRpcConfiguration(node.createJsonRpcWithIbft2EnabledConfig(false))
            .webSocketConfiguration(node.createWebSocketEnabledConfig())
            .devMode(false)
            .genesisConfigProvider(
                nodes ->
                    node.createGenesisConfigForValidators(
                        asList(validators),
                        nodes,
                        GenesisConfigurationFactory::createIbft2GenesisConfig))
            .build());
  }

  public BesuNode createQbftNodeWithValidators(final String name, final String... validators)
      throws IOException {

    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .miningEnabled()
            .jsonRpcConfiguration(node.createJsonRpcWithQbftEnabledConfig(false))
            .webSocketConfiguration(node.createWebSocketEnabledConfig())
            .devMode(false)
            .genesisConfigProvider(
                nodes ->
                    node.createGenesisConfigForValidators(
                        asList(validators),
                        nodes,
                        GenesisConfigurationFactory::createQbftGenesisConfig))
            .build());
  }

  public BesuNode createQbftNodeWithContractBasedValidators(
      final String name, final String... validators) throws IOException {
    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .miningEnabled()
            .jsonRpcConfiguration(node.createJsonRpcWithQbftEnabledConfig(false))
            .webSocketConfiguration(node.createWebSocketEnabledConfig())
            .devMode(false)
            .genesisConfigProvider(
                nodes ->
                    node.createGenesisConfigForValidators(
                        asList(validators),
                        nodes,
                        GenesisConfigurationFactory::createQbftValidatorContractGenesisConfig))
            .build());
  }

  public BesuNode createNodeWithStaticNodes(final String name, final List<Node> staticNodes)
      throws IOException {

    BesuNodeConfigurationBuilder builder =
        createConfigurationBuilderWithStaticNodes(name, staticNodes);
    return create(builder.build());
  }

  private BesuNodeConfigurationBuilder createConfigurationBuilderWithStaticNodes(
      final String name, final List<Node> staticNodes) {
    final List<String> staticNodesUrls =
        staticNodes.stream()
            .map(node -> (RunnableNode) node)
            .map(RunnableNode::enodeUrl)
            .map(URI::toASCIIString)
            .collect(toList());

    return new BesuNodeConfigurationBuilder()
        .name(name)
        .jsonRpcEnabled()
        .webSocketEnabled()
        .discoveryEnabled(false)
        .staticNodes(staticNodesUrls)
        .bootnodeEligible(false);
  }

  public BesuNode createNodeWithNonDefaultSignatureAlgorithm(
      final String name, final String genesisPath, final KeyPair keyPair) throws IOException {
    BesuNodeConfigurationBuilder builder =
        createNodeConfigurationWithNonDefaultSignatureAlgorithm(
            name, genesisPath, keyPair, new ArrayList<>());
    builder.miningEnabled();

    return create(builder.build());
  }

  public BesuNode createNodeWithNonDefaultSignatureAlgorithm(
      final String name,
      final String genesisPath,
      final KeyPair keyPair,
      final List<Node> staticNodes)
      throws IOException {
    BesuNodeConfigurationBuilder builder =
        createNodeConfigurationWithNonDefaultSignatureAlgorithm(
            name, genesisPath, keyPair, staticNodes);
    return create(builder.build());
  }

  public BesuNodeConfigurationBuilder createNodeConfigurationWithNonDefaultSignatureAlgorithm(
      final String name,
      final String genesisPath,
      final KeyPair keyPair,
      final List<Node> staticNodes) {
    BesuNodeConfigurationBuilder builder =
        createConfigurationBuilderWithStaticNodes(name, staticNodes);

    final String genesisData = GenesisConfigurationFactory.readGenesisFile(genesisPath);

    return builder
        .devMode(false)
        .genesisConfigProvider((nodes) -> Optional.of(genesisData))
        .keyPair(keyPair);
  }

  public BesuNode runCommand(final String command) throws IOException {
    return create(new BesuNodeConfigurationBuilder().name("run " + command).run(command).build());
  }
}
