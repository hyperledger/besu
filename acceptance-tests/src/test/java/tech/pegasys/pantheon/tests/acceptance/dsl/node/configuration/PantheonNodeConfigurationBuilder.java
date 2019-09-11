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

import static java.util.Collections.singletonList;

import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.core.MiningParametersTestBuilder;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApis;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.config.NetworkingConfiguration;
import tech.pegasys.pantheon.ethereum.permissioning.PermissioningConfiguration;
import tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.configuration.genesis.GenesisConfigurationProvider;

import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class PantheonNodeConfigurationBuilder {

  private String name;
  private MiningParameters miningParameters =
      new MiningParametersTestBuilder().enabled(false).build();
  private JsonRpcConfiguration jsonRpcConfiguration = JsonRpcConfiguration.createDefault();
  private WebSocketConfiguration webSocketConfiguration = WebSocketConfiguration.createDefault();
  private MetricsConfiguration metricsConfiguration = MetricsConfiguration.builder().build();
  private Optional<PermissioningConfiguration> permissioningConfiguration = Optional.empty();
  private Optional<String> keyFilePath = Optional.empty();
  private boolean devMode = true;
  private GenesisConfigurationProvider genesisConfigProvider = ignore -> Optional.empty();
  private Boolean p2pEnabled = true;
  private NetworkingConfiguration networkingConfiguration = NetworkingConfiguration.create();
  private boolean discoveryEnabled = true;
  private boolean bootnodeEligible = true;
  private boolean revertReasonEnabled = false;
  private List<String> plugins = new ArrayList<>();
  private List<String> extraCLIOptions = new ArrayList<>();
  private List<String> staticNodes = new ArrayList<>();

  public PantheonNodeConfigurationBuilder() {
    // Check connections more frequently during acceptance tests to cut down on
    // intermittent failures due to the fact that we're running over a real network
    networkingConfiguration.setInitiateConnectionsFrequency(5);
  }

  public PantheonNodeConfigurationBuilder name(final String name) {
    this.name = name;
    return this;
  }

  public PantheonNodeConfigurationBuilder miningEnabled() {
    this.miningParameters = new MiningParametersTestBuilder().enabled(true).build();
    this.jsonRpcConfiguration.addRpcApi(RpcApis.MINER);
    return this;
  }

  public PantheonNodeConfigurationBuilder jsonRpcConfiguration(
      final JsonRpcConfiguration jsonRpcConfiguration) {
    this.jsonRpcConfiguration = jsonRpcConfiguration;
    return this;
  }

  public PantheonNodeConfigurationBuilder jsonRpcEnabled() {
    this.jsonRpcConfiguration.setEnabled(true);
    this.jsonRpcConfiguration.setPort(0);
    this.jsonRpcConfiguration.setHostsWhitelist(singletonList("*"));

    return this;
  }

  public PantheonNodeConfigurationBuilder metricsEnabled() {
    this.metricsConfiguration =
        MetricsConfiguration.builder()
            .enabled(true)
            .port(0)
            .hostsWhitelist(singletonList("*"))
            .build();

    return this;
  }

  public PantheonNodeConfigurationBuilder enablePrivateTransactions() {
    this.jsonRpcConfiguration.addRpcApi(RpcApis.EEA);
    this.jsonRpcConfiguration.addRpcApi(RpcApis.PRIV);
    return this;
  }

  public PantheonNodeConfigurationBuilder jsonRpcAuthenticationEnabled() throws URISyntaxException {
    final String authTomlPath =
        Paths.get(ClassLoader.getSystemResource("authentication/auth.toml").toURI())
            .toAbsolutePath()
            .toString();

    this.jsonRpcConfiguration.setAuthenticationEnabled(true);
    this.jsonRpcConfiguration.setAuthenticationCredentialsFile(authTomlPath);

    return this;
  }

  public PantheonNodeConfigurationBuilder webSocketConfiguration(
      final WebSocketConfiguration webSocketConfiguration) {
    this.webSocketConfiguration = webSocketConfiguration;
    return this;
  }

  public PantheonNodeConfigurationBuilder metricsConfiguration(
      final MetricsConfiguration metricsConfiguration) {
    this.metricsConfiguration = metricsConfiguration;
    return this;
  }

  public PantheonNodeConfigurationBuilder webSocketEnabled() {
    final WebSocketConfiguration config = WebSocketConfiguration.createDefault();
    config.setEnabled(true);
    config.setPort(0);
    config.setHostsWhitelist(Collections.singleton("*"));

    this.webSocketConfiguration = config;
    return this;
  }

  public PantheonNodeConfigurationBuilder bootnodeEligible(final boolean bootnodeEligible) {
    this.bootnodeEligible = bootnodeEligible;
    return this;
  }

  public PantheonNodeConfigurationBuilder webSocketAuthenticationEnabled()
      throws URISyntaxException {
    final String authTomlPath =
        Paths.get(ClassLoader.getSystemResource("authentication/auth.toml").toURI())
            .toAbsolutePath()
            .toString();

    this.webSocketConfiguration.setAuthenticationEnabled(true);
    this.webSocketConfiguration.setAuthenticationCredentialsFile(authTomlPath);

    return this;
  }

  public PantheonNodeConfigurationBuilder permissioningConfiguration(
      final PermissioningConfiguration permissioningConfiguration) {
    this.permissioningConfiguration = Optional.of(permissioningConfiguration);
    return this;
  }

  public PantheonNodeConfigurationBuilder keyFilePath(final String keyFilePath) {
    this.keyFilePath = Optional.of(keyFilePath);
    return this;
  }

  public PantheonNodeConfigurationBuilder devMode(final boolean devMode) {
    this.devMode = devMode;
    return this;
  }

  public PantheonNodeConfigurationBuilder genesisConfigProvider(
      final GenesisConfigurationProvider genesisConfigProvider) {
    this.genesisConfigProvider = genesisConfigProvider;
    return this;
  }

  public PantheonNodeConfigurationBuilder p2pEnabled(final Boolean p2pEnabled) {
    this.p2pEnabled = p2pEnabled;
    return this;
  }

  public PantheonNodeConfigurationBuilder discoveryEnabled(final boolean discoveryEnabled) {
    this.discoveryEnabled = discoveryEnabled;
    return this;
  }

  public PantheonNodeConfigurationBuilder plugins(final List<String> plugins) {
    this.plugins.clear();
    this.plugins.addAll(plugins);
    return this;
  }

  public PantheonNodeConfigurationBuilder extraCLIOptions(final List<String> extraCLIOptions) {
    this.extraCLIOptions.clear();
    this.extraCLIOptions.addAll(extraCLIOptions);
    return this;
  }

  public PantheonNodeConfigurationBuilder revertReasonEnabled() {
    this.revertReasonEnabled = true;
    return this;
  }

  public PantheonNodeConfigurationBuilder staticNodes(final List<String> staticNodes) {
    this.staticNodes = staticNodes;
    return this;
  }

  public PantheonNodeConfiguration build() {
    return new PantheonNodeConfiguration(
        name,
        miningParameters,
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
        staticNodes);
  }
}
