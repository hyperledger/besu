/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 *  the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.tests.acceptance.dsl.node.configuration;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.enclave.EnclaveFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.InMemoryPrivacyStorageProvider;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.permissioning.LocalPermissioningConfiguration;
import org.hyperledger.besu.ethereum.permissioning.PermissioningConfiguration;
import org.hyperledger.besu.pki.keystore.KeyStoreWrapper;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.node.RunnableNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.genesis.GenesisConfigurationFactory;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.pki.PkiKeystoreConfigurationFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import io.vertx.core.Vertx;

public class BesuNodeFactory {

  private final GenesisConfigurationFactory genesis = new GenesisConfigurationFactory();
  private final NodeConfigurationFactory node = new NodeConfigurationFactory();
  private final PkiKeystoreConfigurationFactory pkiKeystoreConfigurationFactory =
      new PkiKeystoreConfigurationFactory();

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
        config.getNetwork(),
        config.getGenesisConfigProvider(),
        config.isP2pEnabled(),
        config.getTLSConfiguration(),
        config.getNetworkingConfiguration(),
        config.isDiscoveryEnabled(),
        config.isBootnodeEligible(),
        config.isRevertReasonEnabled(),
        config.isSecp256k1Native(),
        config.isAltbn128Native(),
        config.getPlugins(),
        config.getExtraCLIOptions(),
        config.getStaticNodes(),
        config.isDnsEnabled(),
        config.getPrivacyParameters(),
        config.getRunCommand(),
        config.getKeyPair(),
        config.getPkiKeyStoreConfiguration());
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
      final String privTransactionSigningKey,
      final boolean enableOnchainPrivacy)
      throws IOException, URISyntaxException {
    final PrivacyParameters.Builder privacyParametersBuilder = new PrivacyParameters.Builder();
    final PrivacyParameters privacyParameters =
        privacyParametersBuilder
            .setMultiTenancyEnabled(true)
            .setEnabled(true)
            .setOnchainPrivacyGroupsEnabled(enableOnchainPrivacy)
            .setStorageProvider(new InMemoryPrivacyStorageProvider())
            .setEnclaveFactory(new EnclaveFactory(Vertx.vertx()))
            .setEnclaveUrl(URI.create(enclaveUrl))
            .setPrivateKeyPath(
                Paths.get(ClassLoader.getSystemResource(privTransactionSigningKey).toURI()))
            .build();

    final MiningParameters miningParameters =
        new MiningParameters.Builder()
            .minTransactionGasPrice(Wei.ZERO)
            .coinbase(AddressHelpers.ofValue(1))
            .enabled(true)
            .build();

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
            .jsonRpcConfiguration(node.createJsonRpcWithIbft2AdminEnabledConfig())
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
        new PermissioningConfiguration(Optional.of(config), Optional.empty(), Optional.empty());
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
                    genesis.createIbft2GenesisConfigFilterBootnode(validators, genesisFile))
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
                    genesis.createIbft2GenesisConfigFilterBootnode(validators, genesisFile))
            .bootnodeEligible(false)
            .build());
  }

  public BesuNode createIbft2Node(final String name) throws IOException {
    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .miningEnabled()
            .jsonRpcConfiguration(node.createJsonRpcWithIbft2EnabledConfig(false))
            .webSocketConfiguration(node.createWebSocketEnabledConfig())
            .devMode(false)
            .genesisConfigProvider(genesis::createIbft2GenesisConfig)
            .build());
  }

  public BesuNode createQbftNodeWithTLS(final String name, final String type) throws IOException {
    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .miningEnabled()
            .p2pTLSEnabled(name, type)
            .jsonRpcConfiguration(node.createJsonRpcWithQbftEnabledConfig(false))
            .webSocketConfiguration(node.createWebSocketEnabledConfig())
            .devMode(false)
            .genesisConfigProvider(genesis::createQbftGenesisConfig)
            .build());
  }

  public BesuNode createQbftNodeWithTLSJKS(final String name) throws IOException {
    return createQbftNodeWithTLS(name, KeyStoreWrapper.KEYSTORE_TYPE_JKS);
  }

  public BesuNode createQbftNodeWithTLSPKCS12(final String name) throws IOException {
    return createQbftNodeWithTLS(name, KeyStoreWrapper.KEYSTORE_TYPE_PKCS12);
  }

  public BesuNode createQbftNodeWithTLSPKCS11(final String name) throws IOException {
    return createQbftNodeWithTLS(name, KeyStoreWrapper.KEYSTORE_TYPE_PKCS11);
  }

  public BesuNode createQbftNode(final String name) throws IOException {
    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .miningEnabled()
            .jsonRpcConfiguration(node.createJsonRpcWithQbftEnabledConfig(false))
            .webSocketConfiguration(node.createWebSocketEnabledConfig())
            .devMode(false)
            .genesisConfigProvider(genesis::createQbftGenesisConfig)
            .build());
  }

  public BesuNode createPkiQbftJKSNode(final String name) throws IOException {
    return createPkiQbftNode(KeyStoreWrapper.KEYSTORE_TYPE_JKS, name);
  }

  public BesuNode createPkiQbftPKCS11Node(final String name) throws IOException {
    return createPkiQbftNode(KeyStoreWrapper.KEYSTORE_TYPE_PKCS11, name);
  }

  public BesuNode createPkiQbftPKCS12Node(final String name) throws IOException {
    return createPkiQbftNode(KeyStoreWrapper.KEYSTORE_TYPE_PKCS12, name);
  }

  public BesuNode createPkiQbftNode(final String type, final String name) throws IOException {
    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .miningEnabled()
            .jsonRpcConfiguration(node.createJsonRpcWithQbftEnabledConfig(false))
            .webSocketConfiguration(node.createWebSocketEnabledConfig())
            .devMode(false)
            .genesisConfigProvider(genesis::createQbftGenesisConfig)
            .pkiBlockCreationEnabled(pkiKeystoreConfigurationFactory.createPkiConfig(type, name))
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
            .jsonRpcConfiguration(node.createJsonRpcWithIbft2EnabledConfig(false))
            .webSocketConfiguration(node.createWebSocketEnabledConfig())
            .devMode(false)
            .genesisConfigProvider(
                nodes ->
                    node.createGenesisConfigForValidators(
                        asList(validators), nodes, genesis::createIbft2GenesisConfig))
            .build());
  }

  public BesuNode createQbftTLSNodeWithValidators(
      final String name, final String type, final String... validators) throws IOException {

    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .miningEnabled()
            .p2pTLSEnabled(name, type)
            .jsonRpcConfiguration(node.createJsonRpcWithIbft2EnabledConfig(false))
            .webSocketConfiguration(node.createWebSocketEnabledConfig())
            .devMode(false)
            .genesisConfigProvider(
                nodes ->
                    node.createGenesisConfigForValidators(
                        asList(validators), nodes, genesis::createIbft2GenesisConfig))
            .build());
  }

  public BesuNode createQbftTLSJKSNodeWithValidators(final String name, final String... validators)
      throws IOException {
    return createQbftTLSNodeWithValidators(name, KeyStoreWrapper.KEYSTORE_TYPE_JKS, validators);
  }

  public BesuNode createQbftTLSPKCS12NodeWithValidators(
      final String name, final String... validators) throws IOException {
    return createQbftTLSNodeWithValidators(name, KeyStoreWrapper.KEYSTORE_TYPE_PKCS12, validators);
  }

  public BesuNode createQbftTLSPKCS11NodeWithValidators(
      final String name, final String... validators) throws IOException {
    return createQbftTLSNodeWithValidators(name, KeyStoreWrapper.KEYSTORE_TYPE_PKCS11, validators);
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
                        asList(validators), nodes, genesis::createQbftGenesisConfig))
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
                        genesis::createQbftValidatorContractGenesisConfig))
            .build());
  }

  public BesuNode createPkiQbftJKSNodeWithValidators(final String name, final String... validators)
      throws IOException {
    return createPkiQbftNodeWithValidators(KeyStoreWrapper.KEYSTORE_TYPE_JKS, name, validators);
  }

  public BesuNode createPkiQbftPKCS11NodeWithValidators(
      final String name, final String... validators) throws IOException {
    return createPkiQbftNodeWithValidators(KeyStoreWrapper.KEYSTORE_TYPE_PKCS11, name, validators);
  }

  public BesuNode createPkiQbftPKCS12NodeWithValidators(
      final String name, final String... validators) throws IOException {
    return createPkiQbftNodeWithValidators(KeyStoreWrapper.KEYSTORE_TYPE_PKCS12, name, validators);
  }

  public BesuNode createPkiQbftNodeWithValidators(
      final String type, final String name, final String... validators) throws IOException {

    return create(
        new BesuNodeConfigurationBuilder()
            .name(name)
            .miningEnabled()
            .jsonRpcConfiguration(node.createJsonRpcWithQbftEnabledConfig(false))
            .webSocketConfiguration(node.createWebSocketEnabledConfig())
            .devMode(false)
            .pkiBlockCreationEnabled(pkiKeystoreConfigurationFactory.createPkiConfig(type, name))
            .genesisConfigProvider(
                nodes ->
                    node.createGenesisConfigForValidators(
                        asList(validators), nodes, genesis::createQbftGenesisConfig))
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

    final GenesisConfigurationFactory genesis = new GenesisConfigurationFactory();
    final String genesisData = genesis.readGenesisFile(genesisPath);

    return builder
        .devMode(false)
        .genesisConfigProvider((nodes) -> Optional.of(genesisData))
        .keyPair(keyPair);
  }

  public BesuNode runCommand(final String command) throws IOException {
    return create(new BesuNodeConfigurationBuilder().name("run " + command).run(command).build());
  }
}
