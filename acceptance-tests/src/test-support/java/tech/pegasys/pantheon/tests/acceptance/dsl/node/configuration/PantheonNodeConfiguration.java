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

import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.config.NetworkingConfiguration;
import tech.pegasys.pantheon.ethereum.permissioning.PermissioningConfiguration;
import tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.configuration.genesis.GenesisConfigurationProvider;

import java.util.List;
import java.util.Optional;

public class PantheonNodeConfiguration {

  private final String name;
  private final MiningParameters miningParameters;
  private final JsonRpcConfiguration jsonRpcConfiguration;
  private final WebSocketConfiguration webSocketConfiguration;
  private final MetricsConfiguration metricsConfiguration;
  private final Optional<PermissioningConfiguration> permissioningConfiguration;
  private final Optional<String> keyFilePath;
  private final boolean devMode;
  private final GenesisConfigurationProvider genesisConfigProvider;
  private final boolean p2pEnabled;
  private final NetworkingConfiguration networkingConfiguration;
  private final boolean discoveryEnabled;
  private final boolean bootnodeEligible;
  private final boolean revertReasonEnabled;
  private final List<String> plugins;
  private final List<String> extraCLIOptions;
  private final List<String> staticNodes;

  public PantheonNodeConfiguration(
      final String name,
      final MiningParameters miningParameters,
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
      final List<String> staticNodes) {
    this.name = name;
    this.miningParameters = miningParameters;
    this.jsonRpcConfiguration = jsonRpcConfiguration;
    this.webSocketConfiguration = webSocketConfiguration;
    this.metricsConfiguration = metricsConfiguration;
    this.permissioningConfiguration = permissioningConfiguration;
    this.keyFilePath = keyFilePath;
    this.devMode = devMode;
    this.genesisConfigProvider = genesisConfigProvider;
    this.p2pEnabled = p2pEnabled;
    this.networkingConfiguration = networkingConfiguration;
    this.discoveryEnabled = discoveryEnabled;
    this.bootnodeEligible = bootnodeEligible;
    this.revertReasonEnabled = revertReasonEnabled;
    this.plugins = plugins;
    this.extraCLIOptions = extraCLIOptions;
    this.staticNodes = staticNodes;
  }

  public String getName() {
    return name;
  }

  public MiningParameters getMiningParameters() {
    return miningParameters;
  }

  public JsonRpcConfiguration getJsonRpcConfiguration() {
    return jsonRpcConfiguration;
  }

  public WebSocketConfiguration getWebSocketConfiguration() {
    return webSocketConfiguration;
  }

  public MetricsConfiguration getMetricsConfiguration() {
    return metricsConfiguration;
  }

  public Optional<PermissioningConfiguration> getPermissioningConfiguration() {
    return permissioningConfiguration;
  }

  public Optional<String> getKeyFilePath() {
    return keyFilePath;
  }

  public boolean isDevMode() {
    return devMode;
  }

  public boolean isDiscoveryEnabled() {
    return discoveryEnabled;
  }

  public GenesisConfigurationProvider getGenesisConfigProvider() {
    return genesisConfigProvider;
  }

  public boolean isP2pEnabled() {
    return p2pEnabled;
  }

  public NetworkingConfiguration getNetworkingConfiguration() {
    return networkingConfiguration;
  }

  public boolean isBootnodeEligible() {
    return bootnodeEligible;
  }

  public List<String> getPlugins() {
    return plugins;
  }

  public List<String> getExtraCLIOptions() {
    return extraCLIOptions;
  }

  public boolean isRevertReasonEnabled() {
    return revertReasonEnabled;
  }

  public List<String> getStaticNodes() {
    return staticNodes;
  }
}
