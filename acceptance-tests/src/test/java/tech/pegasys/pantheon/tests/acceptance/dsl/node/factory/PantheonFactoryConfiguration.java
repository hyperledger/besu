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

import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import tech.pegasys.pantheon.ethereum.permissioning.PermissioningConfiguration;
import tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.GenesisConfigProvider;

class PantheonFactoryConfiguration {

  private final String name;
  private final MiningParameters miningParameters;
  private final JsonRpcConfiguration jsonRpcConfiguration;
  private final WebSocketConfiguration webSocketConfiguration;
  private final MetricsConfiguration metricsConfiguration;
  private final PermissioningConfiguration permissioningConfiguration;
  private final boolean devMode;
  private final GenesisConfigProvider genesisConfigProvider;
  private final Boolean p2pEnabled;

  PantheonFactoryConfiguration(
      final String name,
      final MiningParameters miningParameters,
      final JsonRpcConfiguration jsonRpcConfiguration,
      final WebSocketConfiguration webSocketConfiguration,
      final MetricsConfiguration metricsConfiguration,
      final PermissioningConfiguration permissioningConfiguration,
      final boolean devMode,
      final GenesisConfigProvider genesisConfigProvider,
      final Boolean p2pEnabled) {
    this.name = name;
    this.miningParameters = miningParameters;
    this.jsonRpcConfiguration = jsonRpcConfiguration;
    this.webSocketConfiguration = webSocketConfiguration;
    this.metricsConfiguration = metricsConfiguration;
    this.permissioningConfiguration = permissioningConfiguration;
    this.devMode = devMode;
    this.genesisConfigProvider = genesisConfigProvider;
    this.p2pEnabled = p2pEnabled;
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

  public PermissioningConfiguration getPermissioningConfiguration() {
    return permissioningConfiguration;
  }

  public boolean isDevMode() {
    return devMode;
  }

  public GenesisConfigProvider getGenesisConfigProvider() {
    return genesisConfigProvider;
  }

  public Boolean getP2pEnabled() {
    return p2pEnabled;
  }
}
