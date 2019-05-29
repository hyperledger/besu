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
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApis;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import tech.pegasys.pantheon.ethereum.permissioning.PermissioningConfiguration;
import tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.configuration.genesis.GenesisConfigurationProvider;

import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class PantheonFactoryConfigurationBuilder {

  private String name;
  private MiningParameters miningParameters =
      new MiningParametersTestBuilder().enabled(false).build();
  private PrivacyParameters privacyParameters = PrivacyParameters.DEFAULT;
  private JsonRpcConfiguration jsonRpcConfiguration = JsonRpcConfiguration.createDefault();
  private WebSocketConfiguration webSocketConfiguration = WebSocketConfiguration.createDefault();
  private MetricsConfiguration metricsConfiguration = MetricsConfiguration.createDefault();
  private Optional<PermissioningConfiguration> permissioningConfiguration = Optional.empty();
  private Optional<String> keyFilePath = Optional.empty();
  private boolean devMode = true;
  private GenesisConfigurationProvider genesisConfigProvider = ignore -> Optional.empty();
  private Boolean p2pEnabled = true;
  private boolean discoveryEnabled = true;
  private boolean bootnodeEligible = true;
  private List<String> plugins = new ArrayList<>();
  private List<String> extraCLIOptions = new ArrayList<>();

  public PantheonFactoryConfigurationBuilder name(final String name) {
    this.name = name;
    return this;
  }

  public PantheonFactoryConfigurationBuilder miningEnabled() {
    this.miningParameters = new MiningParametersTestBuilder().enabled(true).build();
    return this;
  }

  public PantheonFactoryConfigurationBuilder jsonRpcConfiguration(
      final JsonRpcConfiguration jsonRpcConfiguration) {
    this.jsonRpcConfiguration = jsonRpcConfiguration;
    return this;
  }

  public PantheonFactoryConfigurationBuilder jsonRpcEnabled() {
    this.jsonRpcConfiguration.setEnabled(true);
    this.jsonRpcConfiguration.setPort(0);
    this.jsonRpcConfiguration.setHostsWhitelist(singletonList("*"));

    return this;
  }

  public PantheonFactoryConfigurationBuilder enablePrivateTransactions(
      final PrivacyParameters privacyParameters) {
    this.jsonRpcConfiguration.addRpcApi(RpcApis.EEA);
    this.privacyParameters = privacyParameters;
    this.privacyParameters.setEnabled(true);
    return this;
  }

  public PantheonFactoryConfigurationBuilder jsonRpcAuthenticationEnabled()
      throws URISyntaxException {
    final String authTomlPath =
        Paths.get(ClassLoader.getSystemResource("authentication/auth.toml").toURI())
            .toAbsolutePath()
            .toString();

    this.jsonRpcConfiguration.setAuthenticationEnabled(true);
    this.jsonRpcConfiguration.setAuthenticationCredentialsFile(authTomlPath);

    return this;
  }

  public PantheonFactoryConfigurationBuilder webSocketConfiguration(
      final WebSocketConfiguration webSocketConfiguration) {
    this.webSocketConfiguration = webSocketConfiguration;
    return this;
  }

  public PantheonFactoryConfigurationBuilder metricsConfiguration(
      final MetricsConfiguration metricsConfiguration) {
    this.metricsConfiguration = metricsConfiguration;
    return this;
  }

  public PantheonFactoryConfigurationBuilder webSocketEnabled() {
    final WebSocketConfiguration config = WebSocketConfiguration.createDefault();
    config.setEnabled(true);
    config.setPort(0);
    config.setHostsWhitelist(Collections.singleton("*"));

    this.webSocketConfiguration = config;
    return this;
  }

  public PantheonFactoryConfigurationBuilder bootnodeEligible(final boolean bootnodeEligible) {
    this.bootnodeEligible = bootnodeEligible;
    return this;
  }

  public PantheonFactoryConfigurationBuilder webSocketAuthenticationEnabled()
      throws URISyntaxException {
    final String authTomlPath =
        Paths.get(ClassLoader.getSystemResource("authentication/auth.toml").toURI())
            .toAbsolutePath()
            .toString();

    this.webSocketConfiguration.setAuthenticationEnabled(true);
    this.webSocketConfiguration.setAuthenticationCredentialsFile(authTomlPath);

    return this;
  }

  public PantheonFactoryConfigurationBuilder permissioningConfiguration(
      final PermissioningConfiguration permissioningConfiguration) {
    this.permissioningConfiguration = Optional.of(permissioningConfiguration);
    return this;
  }

  public PantheonFactoryConfigurationBuilder keyFilePath(final String keyFilePath) {
    this.keyFilePath = Optional.of(keyFilePath);
    return this;
  }

  public PantheonFactoryConfigurationBuilder devMode(final boolean devMode) {
    this.devMode = devMode;
    return this;
  }

  public PantheonFactoryConfigurationBuilder genesisConfigProvider(
      final GenesisConfigurationProvider genesisConfigProvider) {
    this.genesisConfigProvider = genesisConfigProvider;
    return this;
  }

  public PantheonFactoryConfigurationBuilder p2pEnabled(final Boolean p2pEnabled) {
    this.p2pEnabled = p2pEnabled;
    return this;
  }

  public PantheonFactoryConfigurationBuilder discoveryEnabled(final boolean discoveryEnabled) {
    this.discoveryEnabled = discoveryEnabled;
    return this;
  }

  public PantheonFactoryConfigurationBuilder plugins(final List<String> plugins) {
    this.plugins.clear();
    this.plugins.addAll(plugins);
    return this;
  }

  public PantheonFactoryConfigurationBuilder extraCLIOptions(final List<String> extraCLIOptions) {
    this.extraCLIOptions.clear();
    this.extraCLIOptions.addAll(extraCLIOptions);
    return this;
  }

  public PantheonFactoryConfiguration build() {
    return new PantheonFactoryConfiguration(
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
        discoveryEnabled,
        bootnodeEligible,
        plugins,
        extraCLIOptions);
  }
}
