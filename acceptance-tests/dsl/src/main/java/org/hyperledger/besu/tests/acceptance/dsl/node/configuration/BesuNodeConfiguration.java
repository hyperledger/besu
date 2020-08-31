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

import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration;
import org.hyperledger.besu.ethereum.permissioning.PermissioningConfiguration;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.genesis.GenesisConfigurationProvider;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class BesuNodeConfiguration {

  private final String name;
  private final Optional<Path> dataPath;
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
  private final boolean secp256k1Native;
  private final boolean altbn128Native;
  private final List<String> plugins;
  private final List<String> extraCLIOptions;
  private final List<String> staticNodes;
  private final boolean isDnsEnabled;
  private final Optional<PrivacyParameters> privacyParameters;
  private final List<String> runCommand;

  BesuNodeConfiguration(
      final String name,
      final Optional<Path> dataPath,
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
      final boolean secp256k1Native,
      final boolean altbn128Native,
      final List<String> plugins,
      final List<String> extraCLIOptions,
      final List<String> staticNodes,
      final boolean isDnsEnabled,
      final Optional<PrivacyParameters> privacyParameters,
      final List<String> runCommand) {
    this.name = name;
    this.miningParameters = miningParameters;
    this.jsonRpcConfiguration = jsonRpcConfiguration;
    this.webSocketConfiguration = webSocketConfiguration;
    this.metricsConfiguration = metricsConfiguration;
    this.permissioningConfiguration = permissioningConfiguration;
    this.keyFilePath = keyFilePath;
    this.dataPath = dataPath;
    this.devMode = devMode;
    this.genesisConfigProvider = genesisConfigProvider;
    this.p2pEnabled = p2pEnabled;
    this.networkingConfiguration = networkingConfiguration;
    this.discoveryEnabled = discoveryEnabled;
    this.bootnodeEligible = bootnodeEligible;
    this.revertReasonEnabled = revertReasonEnabled;
    this.secp256k1Native = secp256k1Native;
    this.altbn128Native = altbn128Native;
    this.plugins = plugins;
    this.extraCLIOptions = extraCLIOptions;
    this.staticNodes = staticNodes;
    this.isDnsEnabled = isDnsEnabled;
    this.privacyParameters = privacyParameters;
    this.runCommand = runCommand;
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

  public Optional<Path> getDataPath() {
    return dataPath;
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

  public boolean isSecp256k1Native() {
    return secp256k1Native;
  }

  public boolean isAltbn128Native() {
    return altbn128Native;
  }

  public List<String> getStaticNodes() {
    return staticNodes;
  }

  public boolean isDnsEnabled() {
    return isDnsEnabled;
  }

  public Optional<PrivacyParameters> getPrivacyParameters() {
    return privacyParameters;
  }

  public List<String> getRunCommand() {
    return runCommand;
  }
}
