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
import org.hyperledger.orion.testutil.OrionKeyConfiguration;

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
            new BesuNodeConfigurationBuilder()
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
            new BesuNodeConfigurationBuilder()
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
            new BesuNodeConfigurationBuilder()
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
