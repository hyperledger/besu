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
package org.hyperledger.besu.tests.acceptance.dsl.node.configuration.privacy;

import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.BesuNodeConfigurationBuilder;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.NodeConfigurationFactory;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.genesis.GenesisConfigurationFactory;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.account.PrivacyAccount;
import org.hyperledger.enclave.testutil.EnclaveKeyConfiguration;
import org.hyperledger.enclave.testutil.EnclaveType;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.vertx.core.Vertx;
import org.testcontainers.containers.Network;

public class PrivacyNodeFactory {

  private final NodeConfigurationFactory node = new NodeConfigurationFactory();
  private final Vertx vertx;

  public PrivacyNodeFactory(final Vertx vertx) {
    this.vertx = vertx;
  }

  public PrivacyNode create(
      final PrivacyNodeConfiguration privacyNodeConfig,
      final EnclaveType enclaveType,
      final Optional<Network> containerNetwork)
      throws IOException {
    return new PrivacyNode(privacyNodeConfig, vertx, enclaveType, containerNetwork);
  }

  public PrivacyNode createPrivateTransactionEnabledMinerNode(
      final String name,
      final PrivacyAccount privacyAccount,
      final EnclaveType enclaveType,
      final Optional<Network> containerNetwork)
      throws IOException {
    return createPrivateTransactionEnabledMinerNode(
        name, privacyAccount, enclaveType, containerNetwork, false, false, false);
  }

  public PrivacyNode createPrivateTransactionEnabledMinerNode(
      final String name,
      final PrivacyAccount privacyAccount,
      final EnclaveType enclaveType,
      final Optional<Network> containerNetwork,
      final boolean isFlexiblePrivacyGroupEnabled,
      final boolean isMultitenancyEnabled,
      final boolean isPrivacyPluginEnabled)
      throws IOException {
    return create(
        new PrivacyNodeConfiguration(
            isFlexiblePrivacyGroupEnabled,
            isMultitenancyEnabled,
            isPrivacyPluginEnabled,
            new BesuNodeConfigurationBuilder()
                .name(name)
                .miningEnabled()
                .jsonRpcEnabled()
                .webSocketEnabled()
                .enablePrivateTransactions()
                .keyFilePath(privacyAccount.getPrivateKeyPath())
                .plugins(Collections.singletonList("testPlugins"))
                .extraCLIOptions(List.of("--plugin-privacy-service-encryption-prefix=0xAA"))
                .build(),
            new EnclaveKeyConfiguration(
                privacyAccount.getEnclaveKeyPaths(), privacyAccount.getEnclavePrivateKeyPaths())),
        enclaveType,
        containerNetwork);
  }

  public PrivacyNode createPrivateTransactionEnabledNode(
      final String name,
      final PrivacyAccount privacyAccount,
      final EnclaveType enclaveType,
      final Optional<Network> containerNetwork)
      throws IOException {
    return createPrivateTransactionEnabledNode(
        name, privacyAccount, enclaveType, containerNetwork, false, false, false);
  }

  public PrivacyNode createPrivateTransactionEnabledNode(
      final String name,
      final PrivacyAccount privacyAccount,
      final EnclaveType enclaveType,
      final Optional<Network> containerNetwork,
      final boolean isFlexiblePrivacyGroupEnabled,
      final boolean isMultitenancyEnabled,
      final boolean isPrivacyPluginEnabled)
      throws IOException {
    return create(
        new PrivacyNodeConfiguration(
            isFlexiblePrivacyGroupEnabled,
            isMultitenancyEnabled,
            isPrivacyPluginEnabled,
            new BesuNodeConfigurationBuilder()
                .name(name)
                .jsonRpcEnabled()
                .keyFilePath(privacyAccount.getPrivateKeyPath())
                .enablePrivateTransactions()
                .webSocketEnabled()
                .plugins(Collections.singletonList("testPlugins"))
                .extraCLIOptions(List.of("--plugin-privacy-service-encryption-prefix=0xBB"))
                .build(),
            new EnclaveKeyConfiguration(
                privacyAccount.getEnclaveKeyPaths(), privacyAccount.getEnclavePrivateKeyPaths())),
        enclaveType,
        containerNetwork);
  }

  public PrivacyNode createIbft2NodePrivacyEnabled(
      final String name,
      final PrivacyAccount privacyAccount,
      final EnclaveType enclaveType,
      final Optional<Network> containerNetwork)
      throws IOException {
    return createIbft2NodePrivacyEnabled(
        name, privacyAccount, false, enclaveType, containerNetwork, false, false, false, "0xAA");
  }

  public PrivacyNode createIbft2NodePrivacyEnabled(
      final String name,
      final PrivacyAccount privacyAccount,
      final boolean minerEnabled,
      final EnclaveType enclaveType,
      final Optional<Network> containerNetwork,
      final boolean isFlexiblePrivacyGroupEnabled,
      final boolean isMultitenancyEnabled,
      final boolean isPrivacyPluginEnabled,
      final String unrestrictedPrefix)
      throws IOException {
    return create(
        new PrivacyNodeConfiguration(
            isFlexiblePrivacyGroupEnabled,
            isMultitenancyEnabled,
            isPrivacyPluginEnabled,
            new BesuNodeConfigurationBuilder()
                .name(name)
                .miningEnabled()
                .jsonRpcConfiguration(node.createJsonRpcWithIbft2EnabledConfig(minerEnabled))
                .webSocketConfiguration(node.createWebSocketEnabledConfig())
                .devMode(false)
                .genesisConfigProvider(GenesisConfigurationFactory::createPrivacyIbft2GenesisConfig)
                .keyFilePath(privacyAccount.getPrivateKeyPath())
                .enablePrivateTransactions()
                .plugins(Collections.singletonList("testPlugins"))
                .extraCLIOptions(
                    List.of("--plugin-privacy-service-encryption-prefix=" + unrestrictedPrefix))
                .build(),
            new EnclaveKeyConfiguration(
                privacyAccount.getEnclaveKeyPaths(), privacyAccount.getEnclavePrivateKeyPaths())),
        enclaveType,
        containerNetwork);
  }

  public PrivacyNode createIbft2NodePrivacyEnabledWithGenesis(
      final String name,
      final PrivacyAccount privacyAccount,
      final boolean minerEnabled,
      final EnclaveType enclaveType,
      final Optional<Network> containerNetwork,
      final boolean isFlexiblePrivacyGroupEnabled,
      final boolean isMultitenancyEnabled,
      final boolean isPrivacyPluginEnabled,
      final String unrestrictedPrefix)
      throws IOException {
    return create(
        new PrivacyNodeConfiguration(
            isFlexiblePrivacyGroupEnabled,
            isMultitenancyEnabled,
            isPrivacyPluginEnabled,
            new BesuNodeConfigurationBuilder()
                .name(name)
                .miningEnabled()
                .jsonRpcConfiguration(node.createJsonRpcWithIbft2EnabledConfig(minerEnabled))
                .webSocketConfiguration(node.createWebSocketEnabledConfig())
                .devMode(false)
                .genesisConfigProvider(GenesisConfigurationFactory::createPrivacyIbft2GenesisConfig)
                .keyFilePath(privacyAccount.getPrivateKeyPath())
                .enablePrivateTransactions()
                .plugins(Collections.singletonList("testPlugins"))
                .extraCLIOptions(
                    List.of(
                        "--plugin-privacy-service-encryption-prefix=" + unrestrictedPrefix,
                        "--plugin-privacy-service-genesis-enabled=true"))
                .build(),
            new EnclaveKeyConfiguration(
                privacyAccount.getEnclaveKeyPaths(), privacyAccount.getEnclavePrivateKeyPaths())),
        enclaveType,
        containerNetwork);
  }

  public PrivacyNode createQbftNodePrivacyEnabled(
      final String name,
      final PrivacyAccount privacyAccount,
      final EnclaveType enclaveType,
      final Optional<Network> containerNetwork,
      final boolean isFlexiblePrivacyGroupEnabled,
      final boolean isMultitenancyEnabled,
      final boolean isPrivacyPluginEnabled,
      final String unrestrictedPrefix)
      throws IOException {
    return create(
        new PrivacyNodeConfiguration(
            isFlexiblePrivacyGroupEnabled,
            isMultitenancyEnabled,
            isPrivacyPluginEnabled,
            new BesuNodeConfigurationBuilder()
                .name(name)
                .miningEnabled()
                .jsonRpcConfiguration(node.createJsonRpcWithQbftEnabledConfig(false))
                .webSocketConfiguration(node.createWebSocketEnabledConfig())
                .devMode(false)
                .genesisConfigProvider(GenesisConfigurationFactory::createQbftGenesisConfig)
                .keyFilePath(privacyAccount.getPrivateKeyPath())
                .enablePrivateTransactions()
                .plugins(Collections.singletonList("testPlugins"))
                .extraCLIOptions(
                    List.of("--plugin-privacy-service-encryption-prefix=" + unrestrictedPrefix))
                .build(),
            new EnclaveKeyConfiguration(
                privacyAccount.getEnclaveKeyPaths(), privacyAccount.getEnclavePrivateKeyPaths())),
        enclaveType,
        containerNetwork);
  }

  public PrivacyNode createFlexiblePrivacyGroupEnabledMinerNode(
      final String name,
      final PrivacyAccount privacyAccount,
      final boolean multiTenancyEnabled,
      final EnclaveType enclaveType,
      final Optional<Network> containerNetwork)
      throws IOException, URISyntaxException {
    final BesuNodeConfigurationBuilder besuNodeConfigurationBuilder =
        new BesuNodeConfigurationBuilder();
    if (multiTenancyEnabled) {
      besuNodeConfigurationBuilder.jsonRpcAuthenticationConfiguration(
          "authentication/auth_priv.toml");
    }
    return create(
        new PrivacyNodeConfiguration(
            true,
            multiTenancyEnabled,
            false,
            besuNodeConfigurationBuilder
                .name(name)
                .miningEnabled()
                .jsonRpcEnabled()
                .webSocketEnabled()
                .enablePrivateTransactions()
                .plugins(Collections.singletonList("testPlugins"))
                .extraCLIOptions(List.of("--plugin-privacy-service-genesis-enabled=true"))
                .keyFilePath(privacyAccount.getPrivateKeyPath())
                .build(),
            new EnclaveKeyConfiguration(
                privacyAccount.getEnclaveKeyPaths(), privacyAccount.getEnclavePrivateKeyPaths())),
        enclaveType,
        containerNetwork);
  }

  public PrivacyNode createFlexiblePrivacyGroupEnabledNode(
      final String name,
      final PrivacyAccount privacyAccount,
      final boolean multiTenancyEnabled,
      final EnclaveType enclaveType,
      final Optional<Network> containerNetwork)
      throws IOException {
    return create(
        new PrivacyNodeConfiguration(
            true,
            multiTenancyEnabled,
            false,
            new BesuNodeConfigurationBuilder()
                .name(name)
                .jsonRpcEnabled()
                .keyFilePath(privacyAccount.getPrivateKeyPath())
                .enablePrivateTransactions()
                .plugins(Collections.singletonList("testPlugins"))
                .extraCLIOptions(List.of("--plugin-privacy-service-genesis-enabled=true"))
                .webSocketEnabled()
                .build(),
            new EnclaveKeyConfiguration(
                privacyAccount.getEnclaveKeyPaths(), privacyAccount.getEnclavePrivateKeyPaths())),
        enclaveType,
        containerNetwork);
  }
}
