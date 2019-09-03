/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.tests.acceptance.dsl.node.configuration.privacy;

import tech.pegasys.orion.testutil.OrionKeyConfiguration;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.configuration.NodeConfigurationFactory;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.configuration.PantheonNodeConfigurationBuilder;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.configuration.genesis.GenesisConfigurationFactory;
import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.PrivacyNode;
import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.account.PrivacyAccount;

import java.io.IOException;

public class PrivacyNodeFactory {

  private final GenesisConfigurationFactory genesis = new GenesisConfigurationFactory();
  private final NodeConfigurationFactory node = new NodeConfigurationFactory();

  private PrivacyNode create(final PrivacyNodeConfiguration privacyNodeConfig) throws IOException {
    return new PrivacyNode(privacyNodeConfig);
  }

  public PrivacyNode createPrivateTransactionEnabledMinerNode(
      final String name, final PrivacyAccount privacyAccount) throws IOException {
    return create(
        new PrivacyNodeConfiguration(
            new PantheonNodeConfigurationBuilder()
                .name(name)
                .miningEnabled()
                .jsonRpcEnabled()
                .webSocketEnabled()
                .enablePrivateTransactions()
                .keyFilePath(privacyAccount.getPrivateKeyPath())
                .build(),
            new OrionKeyConfiguration(
                privacyAccount.getEnclaveKeyPath(), privacyAccount.getEnclavePrivateKeyPath())));
  }

  public PrivacyNode createPrivateTransactionEnabledNode(
      final String name, final PrivacyAccount privacyAccount) throws IOException {
    return create(
        new PrivacyNodeConfiguration(
            new PantheonNodeConfigurationBuilder()
                .name(name)
                .jsonRpcEnabled()
                .keyFilePath(privacyAccount.getPrivateKeyPath())
                .enablePrivateTransactions()
                .webSocketEnabled()
                .build(),
            new OrionKeyConfiguration(
                privacyAccount.getEnclaveKeyPath(), privacyAccount.getEnclavePrivateKeyPath())));
  }

  public PrivacyNode createIbft2NodePrivacyEnabled(
      final String name, final PrivacyAccount privacyAccount) throws IOException {
    return create(
        new PrivacyNodeConfiguration(
            new PantheonNodeConfigurationBuilder()
                .name(name)
                .miningEnabled()
                .jsonRpcConfiguration(node.createJsonRpcWithIbft2EnabledConfig())
                .webSocketConfiguration(node.createWebSocketEnabledConfig())
                .devMode(false)
                .genesisConfigProvider(genesis::createPrivacyIbft2GenesisConfig)
                .keyFilePath(privacyAccount.getPrivateKeyPath())
                .enablePrivateTransactions()
                .build(),
            new OrionKeyConfiguration(
                privacyAccount.getEnclaveKeyPath(), privacyAccount.getEnclavePrivateKeyPath())));
  }
}
