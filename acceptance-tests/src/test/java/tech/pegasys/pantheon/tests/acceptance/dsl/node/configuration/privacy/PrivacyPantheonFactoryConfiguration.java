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
package tech.pegasys.pantheon.tests.acceptance.dsl.node.configuration.privacy;

import tech.pegasys.orion.testutil.OrionTestHarness;
import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.config.NetworkingConfiguration;
import tech.pegasys.pantheon.ethereum.permissioning.PermissioningConfiguration;
import tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.configuration.PantheonFactoryConfiguration;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.configuration.genesis.GenesisConfigurationProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PrivacyPantheonFactoryConfiguration extends PantheonFactoryConfiguration {

  private final OrionTestHarness orion;

  PrivacyPantheonFactoryConfiguration(
      final String name,
      final MiningParameters miningParameters,
      final PrivacyParameters privacyParameters,
      final JsonRpcConfiguration jsonRpcConfiguration,
      final WebSocketConfiguration webSocketConfiguration,
      final MetricsConfiguration metricsConfiguration,
      final Optional<PermissioningConfiguration> permissioningConfiguration,
      final Optional<String> keyFilePath,
      final boolean devMode,
      final GenesisConfigurationProvider genesisConfigProvider,
      final boolean p2pEnabled,
      final NetworkingConfiguration networkingConfiguration,
      final boolean discoveryEnabled,
      final boolean bootnodeEligible,
      final boolean revertReasonEnabled,
      final List<String> plugins,
      final List<String> extraCLIOptions,
      final OrionTestHarness orion) {
    super(
        name,
        miningParameters,
        privacyParameters,
        jsonRpcConfiguration,
        webSocketConfiguration,
        metricsConfiguration,
        permissioningConfiguration,
        keyFilePath,
        devMode,
        genesisConfigProvider,
        p2pEnabled,
        networkingConfiguration,
        discoveryEnabled,
        bootnodeEligible,
        revertReasonEnabled,
        plugins,
        extraCLIOptions,
        new ArrayList<>());
    this.orion = orion;
  }

  public OrionTestHarness getOrion() {
    return orion;
  }
}
