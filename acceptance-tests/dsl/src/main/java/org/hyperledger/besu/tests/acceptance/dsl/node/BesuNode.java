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
package org.hyperledger.besu.tests.acceptance.dsl.node;

import static java.util.Collections.unmodifiableList;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.apache.tuweni.io.file.Files.copyResource;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.KeyPairUtil;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration;
import org.hyperledger.besu.ethereum.permissioning.PermissioningConfiguration;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;
import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.NodeConfiguration;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.genesis.GenesisConfigurationProvider;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.NodeRequests;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.admin.AdminRequestFactory;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.bft.BftRequestFactory;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.bft.ConsensusType;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.clique.CliqueRequestFactory;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.login.LoginRequestFactory;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.miner.MinerRequestFactory;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.net.CustomRequestFactory;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.perm.PermissioningJsonRpcRequestFactory;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivacyRequestFactory;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.txpool.TxPoolRequestFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import com.google.common.base.MoreObjects;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import org.apache.logging.log4j.Logger;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.JsonRpc2_0Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.protocol.websocket.WebSocketClient;
import org.web3j.protocol.websocket.WebSocketListener;
import org.web3j.protocol.websocket.WebSocketService;
import org.web3j.utils.Async;

public class BesuNode implements NodeConfiguration, RunnableNode, AutoCloseable {

  private static final String LOCALHOST = "127.0.0.1";
  private static final Logger LOG = getLogger();

  private final Path homeDirectory;
  private final KeyPair keyPair;
  private final Properties portsProperties = new Properties();
  private final Boolean p2pEnabled;
  private final NetworkingConfiguration networkingConfiguration;
  private final boolean revertReasonEnabled;

  private final String name;
  private final MiningParameters miningParameters;
  private final List<String> runCommand;
  private PrivacyParameters privacyParameters = PrivacyParameters.DEFAULT;
  private final JsonRpcConfiguration jsonRpcConfiguration;
  private final WebSocketConfiguration webSocketConfiguration;
  private final MetricsConfiguration metricsConfiguration;
  private Optional<PermissioningConfiguration> permissioningConfiguration;
  private final GenesisConfigurationProvider genesisConfigProvider;
  private final boolean devMode;
  private final boolean discoveryEnabled;
  private final List<URI> bootnodes = new ArrayList<>();
  private final boolean bootnodeEligible;
  private final boolean secp256k1Native;
  private final boolean altbn128Native;
  private Optional<String> genesisConfig = Optional.empty();
  private NodeRequests nodeRequests;
  private LoginRequestFactory loginRequestFactory;
  private boolean useWsForJsonRpc = false;
  private String token = null;
  private final List<String> plugins = new ArrayList<>();
  private final List<String> extraCLIOptions;
  private final List<String> staticNodes;
  private boolean isDnsEnabled = false;
  private Optional<Integer> exitCode = Optional.empty();

  public BesuNode(
      final String name,
      final Optional<Path> dataPath,
      final MiningParameters miningParameters,
      final JsonRpcConfiguration jsonRpcConfiguration,
      final WebSocketConfiguration webSocketConfiguration,
      final MetricsConfiguration metricsConfiguration,
      final Optional<PermissioningConfiguration> permissioningConfiguration,
      final Optional<String> keyfilePath,
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
      final List<String> runCommand)
      throws IOException {
    this.homeDirectory = dataPath.orElseGet(BesuNode::createTmpDataDirectory);
    keyfilePath.ifPresent(
        path -> {
          try {
            copyResource(path, homeDirectory.resolve("key"));
          } catch (final IOException e) {
            LOG.error("Could not find key file \"{}\" in resources", path);
          }
        });
    this.keyPair = KeyPairUtil.loadKeyPair(homeDirectory);
    this.name = name;
    this.miningParameters = miningParameters;
    this.jsonRpcConfiguration = jsonRpcConfiguration;
    this.webSocketConfiguration = webSocketConfiguration;
    this.metricsConfiguration = metricsConfiguration;
    this.permissioningConfiguration = permissioningConfiguration;
    this.genesisConfigProvider = genesisConfigProvider;
    this.devMode = devMode;
    this.p2pEnabled = p2pEnabled;
    this.networkingConfiguration = networkingConfiguration;
    this.discoveryEnabled = discoveryEnabled;
    this.bootnodeEligible = bootnodeEligible;
    this.revertReasonEnabled = revertReasonEnabled;
    this.secp256k1Native = secp256k1Native;
    this.altbn128Native = altbn128Native;
    this.runCommand = runCommand;
    plugins.forEach(
        pluginName -> {
          try {
            homeDirectory.resolve("plugins").toFile().mkdirs();
            copyResource(
                pluginName + ".jar", homeDirectory.resolve("plugins/" + pluginName + ".jar"));
            BesuNode.this.plugins.add(pluginName);
          } catch (final IOException e) {
            LOG.error("Could not find plugin \"{}\" in resources", pluginName);
          }
        });
    this.extraCLIOptions = extraCLIOptions;
    this.staticNodes = staticNodes;
    this.isDnsEnabled = isDnsEnabled;
    privacyParameters.ifPresent(this::setPrivacyParameters);
    LOG.info("Created BesuNode {}", this.toString());
  }

  private static Path createTmpDataDirectory() {
    try {
      return Files.createTempDirectory("acctest");
    } catch (final IOException e) {
      throw new RuntimeException("Unable to create temporary data directory", e);
    }
  }

  @Override
  public boolean isJsonRpcEnabled() {
    return jsonRpcConfiguration().isEnabled();
  }

  private boolean isWebSocketsRpcEnabled() {
    return webSocketConfiguration().isEnabled();
  }

  boolean isMetricsEnabled() {
    return metricsConfiguration.isEnabled();
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getNodeId() {
    return keyPair.getPublicKey().toString().substring(2);
  }

  @Override
  public Optional<Integer> exitCode() {
    return exitCode;
  }

  @Override
  public URI enodeUrl() {
    final String discport = isDiscoveryEnabled() ? "?discport=" + getDiscoveryPort() : "";
    return URI.create("enode://" + getNodeId() + "@" + LOCALHOST + ":" + getP2pPort() + discport);
  }

  private String getP2pPort() {
    final String port = portsProperties.getProperty("p2p");
    if (port == null) {
      throw new IllegalStateException("Requested p2p port before ports properties was written");
    }
    return port;
  }

  private String getDiscoveryPort() {
    final String port = portsProperties.getProperty("discovery");
    if (port == null) {
      throw new IllegalStateException(
          "Requested discovery port before ports properties was written");
    }
    return port;
  }

  private Optional<String> jsonRpcBaseUrl() {
    if (isJsonRpcEnabled()) {
      return Optional.of(
          "http://"
              + jsonRpcConfiguration.getHost()
              + ":"
              + portsProperties.getProperty("json-rpc"));
    } else {
      return Optional.empty();
    }
  }

  private Optional<String> wsRpcBaseUrl() {
    if (isWebSocketsRpcEnabled()) {
      return Optional.of(
          "ws://" + webSocketConfiguration.getHost() + ":" + portsProperties.getProperty("ws-rpc"));
    } else {
      return Optional.empty();
    }
  }

  private Optional<String> wsRpcBaseHttpUrl() {
    if (isWebSocketsRpcEnabled()) {
      return Optional.of(
          "http://"
              + webSocketConfiguration.getHost()
              + ":"
              + portsProperties.getProperty("ws-rpc"));
    } else {
      return Optional.empty();
    }
  }

  public Optional<String> metricsHttpUrl() {
    if (isMetricsEnabled()) {
      return Optional.of(
          "http://"
              + metricsConfiguration.getHost()
              + ":"
              + portsProperties.getProperty("metrics")
              + "/metrics");
    } else {
      return Optional.empty();
    }
  }

  @Override
  public Optional<Integer> getJsonRpcWebSocketPort() {
    if (isWebSocketsRpcEnabled()) {
      return Optional.of(Integer.valueOf(portsProperties.getProperty("ws-rpc")));
    } else {
      return Optional.empty();
    }
  }

  public Optional<Integer> getJsonRpcSocketPort() {
    if (isJsonRpcEnabled()) {
      return Optional.of(Integer.valueOf(portsProperties.getProperty("json-rpc")));
    } else {
      return Optional.empty();
    }
  }

  @Override
  public String getHostName() {
    return LOCALHOST;
  }

  private NodeRequests nodeRequests() {
    Optional<WebSocketService> websocketService = Optional.empty();
    if (nodeRequests == null) {
      final Web3jService web3jService;

      if (useWsForJsonRpc) {
        final String url = wsRpcBaseUrl().orElse("ws://" + LOCALHOST + ":" + 8546);
        final Map<String, String> headers = new HashMap<>();
        if (token != null) {
          headers.put("Authorization", "Bearer " + token);
        }
        final WebSocketClient wsClient = new WebSocketClient(URI.create(url), headers);

        web3jService = new WebSocketService(wsClient, false);
        try {
          ((WebSocketService) web3jService).connect();
        } catch (final ConnectException e) {
          throw new RuntimeException(e);
        }

        websocketService = Optional.of((WebSocketService) web3jService);
      } else {
        web3jService =
            jsonRpcBaseUrl()
                .map(HttpService::new)
                .orElse(new HttpService("http://" + LOCALHOST + ":" + 8545));
        if (token != null) {
          ((HttpService) web3jService).addHeader("Authorization", "Bearer " + token);
        }
      }

      final ConsensusType bftType =
          getGenesisConfig()
              .map(
                  gc ->
                      gc.toLowerCase().contains("ibft") ? ConsensusType.IBFT2 : ConsensusType.QBFT)
              .orElse(ConsensusType.IBFT2);

      nodeRequests =
          new NodeRequests(
              new JsonRpc2_0Web3j(web3jService, 2000, Async.defaultExecutorService()),
              new CliqueRequestFactory(web3jService),
              new BftRequestFactory(web3jService, bftType),
              new PermissioningJsonRpcRequestFactory(web3jService),
              new AdminRequestFactory(web3jService),
              new PrivacyRequestFactory(web3jService),
              new CustomRequestFactory(web3jService),
              new MinerRequestFactory(web3jService),
              new TxPoolRequestFactory(web3jService),
              websocketService,
              loginRequestFactory());
    }

    return nodeRequests;
  }

  private LoginRequestFactory loginRequestFactory() {
    if (loginRequestFactory == null) {
      final Optional<String> baseUrl;
      final String port;
      if (useWsForJsonRpc) {
        baseUrl = wsRpcBaseHttpUrl();
        port = "8546";
      } else {
        baseUrl = jsonRpcBaseUrl();
        port = "8545";
      }
      loginRequestFactory =
          new LoginRequestFactory(baseUrl.orElse("http://" + LOCALHOST + ":" + port));
    }
    return loginRequestFactory;
  }

  /** All future JSON-RPC calls are made via a web sockets connection. */
  @Override
  public void useWebSocketsForJsonRpc() {
    final String url = wsRpcBaseUrl().isPresent() ? wsRpcBaseUrl().get() : "ws://127.0.0.1:8546";

    checkIfWebSocketEndpointIsAvailable(url);

    useWsForJsonRpc = true;

    if (nodeRequests != null) {
      nodeRequests.shutdown();
      nodeRequests = null;
    }

    if (loginRequestFactory != null) {
      loginRequestFactory = null;
    }
  }

  /** All future JSON-RPC calls will include the authentication token. */
  @Override
  public void useAuthenticationTokenInHeaderForJsonRpc(final String token) {

    if (nodeRequests != null) {
      nodeRequests.shutdown();
      nodeRequests = null;
    }

    if (loginRequestFactory != null) {
      loginRequestFactory = null;
    }

    this.token = token;
  }

  private void checkIfWebSocketEndpointIsAvailable(final String url) {
    final WebSocketClient webSocketClient = new WebSocketClient(URI.create(url));
    // Web3j implementation always invoke the listener (even when one hasn't been set). We are using
    // this stub implementation to avoid a NullPointerException.
    webSocketClient.setListener(
        new WebSocketListener() {
          @Override
          public void onMessage(final String message) {
            // DO NOTHING
          }

          @Override
          public void onError(final Exception e) {
            // DO NOTHING
          }

          @Override
          public void onClose() {
            // DO NOTHING
          }
        });

    // Because we can't trust the connection timeout of the WebSocket client implementation, we are
    // using this approach to verify if the endpoint is enabled.
    webSocketClient.connect();
    try {
      Awaitility.await().atMost(5, TimeUnit.SECONDS).until(webSocketClient::isOpen);
    } catch (final ConditionTimeoutException e) {
      throw new WebsocketNotConnectedException();
    } finally {
      webSocketClient.close();
    }
  }

  @Override
  public void start(final BesuNodeRunner runner) {
    runner.startNode(this);
    if (runCommand.isEmpty()) {
      loadPortsFile();
    }
  }

  @Override
  public NodeConfiguration getConfiguration() {
    return this;
  }

  @Override
  public void awaitPeerDiscovery(final Condition condition) {
    if (this.isJsonRpcEnabled()) {
      verify(condition);
    }
  }

  private void loadPortsFile() {
    try (final FileInputStream fis =
        new FileInputStream(new File(homeDirectory.toFile(), "besu.ports"))) {
      portsProperties.load(fis);
      LOG.info("Ports for node {}: {}", name, portsProperties);
    } catch (final IOException e) {
      throw new RuntimeException("Error reading Besu ports file", e);
    }
  }

  @Override
  public Address getAddress() {
    return Util.publicKeyToAddress(keyPair.getPublicKey());
  }

  public KeyPair keyPair() {
    return keyPair;
  }

  public Path homeDirectory() {
    return homeDirectory;
  }

  JsonRpcConfiguration jsonRpcConfiguration() {
    return jsonRpcConfiguration;
  }

  Optional<String> jsonRpcListenHost() {
    if (isJsonRpcEnabled()) {
      return Optional.of(jsonRpcConfiguration().getHost());
    } else {
      return Optional.empty();
    }
  }

  Optional<Integer> jsonRpcListenPort() {
    if (isJsonRpcEnabled()) {
      return Optional.of(jsonRpcConfiguration().getPort());
    } else {
      return Optional.empty();
    }
  }

  boolean wsRpcEnabled() {
    return isWebSocketsRpcEnabled();
  }

  WebSocketConfiguration webSocketConfiguration() {
    return webSocketConfiguration;
  }

  Optional<String> wsRpcListenHost() {
    return Optional.of(webSocketConfiguration().getHost());
  }

  Optional<Integer> wsRpcListenPort() {
    return Optional.of(webSocketConfiguration().getPort());
  }

  MetricsConfiguration getMetricsConfiguration() {
    return metricsConfiguration;
  }

  String p2pListenHost() {
    return LOCALHOST;
  }

  @Override
  public List<URI> getBootnodes() {
    return unmodifiableList(bootnodes);
  }

  @Override
  public boolean isP2pEnabled() {
    return p2pEnabled;
  }

  public NetworkingConfiguration getNetworkingConfiguration() {
    return networkingConfiguration;
  }

  @Override
  public boolean isBootnodeEligible() {
    return bootnodeEligible;
  }

  @Override
  public void setBootnodes(final List<URI> bootnodes) {
    this.bootnodes.clear();
    this.bootnodes.addAll(bootnodes);
  }

  MiningParameters getMiningParameters() {
    return miningParameters;
  }

  public PrivacyParameters getPrivacyParameters() {
    return privacyParameters;
  }

  public void setPrivacyParameters(final PrivacyParameters privacyParameters) {
    this.privacyParameters = privacyParameters;
  }

  public boolean isDevMode() {
    return devMode;
  }

  public boolean isSecp256k1Native() {
    return secp256k1Native;
  }

  public boolean isAltbn128Native() {
    return altbn128Native;
  }

  @Override
  public boolean isDiscoveryEnabled() {
    return discoveryEnabled;
  }

  Optional<PermissioningConfiguration> getPermissioningConfiguration() {
    return permissioningConfiguration;
  }

  public void setPermissioningConfiguration(
      final PermissioningConfiguration permissioningConfiguration) {
    this.permissioningConfiguration = Optional.of(permissioningConfiguration);
  }

  public List<String> getPlugins() {
    return plugins;
  }

  @Override
  public List<String> getExtraCLIOptions() {
    return extraCLIOptions;
  }

  @Override
  public boolean isRevertReasonEnabled() {
    return revertReasonEnabled;
  }

  @Override
  public List<String> getStaticNodes() {
    return staticNodes;
  }

  public boolean isDnsEnabled() {
    return isDnsEnabled;
  }

  public boolean hasStaticNodes() {
    return staticNodes != null && !staticNodes.isEmpty();
  }

  public List<String> getRunCommand() {
    return runCommand;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name)
        .add("homeDirectory", homeDirectory)
        .add("keyPair", keyPair)
        .add("p2pEnabled", p2pEnabled)
        .add("discoveryEnabled", discoveryEnabled)
        .add("privacyEnabled", privacyParameters.isEnabled())
        .toString();
  }

  @Override
  public void stop() {
    if (nodeRequests != null) {
      nodeRequests.shutdown();
      nodeRequests = null;
    }
  }

  @Override
  @SuppressWarnings("UnstableApiUsage")
  public void close() {
    stop();
    try {
      MoreFiles.deleteRecursively(homeDirectory, RecursiveDeleteOption.ALLOW_INSECURE);
    } catch (final IOException e) {
      LOG.info("Failed to clean up temporary file: {}", homeDirectory, e);
    }
  }

  @Override
  public GenesisConfigurationProvider getGenesisConfigProvider() {
    return genesisConfigProvider;
  }

  @Override
  public Optional<String> getGenesisConfig() {
    return genesisConfig;
  }

  @Override
  public void setGenesisConfig(final String config) {
    this.genesisConfig = Optional.of(config);
  }

  @Override
  public <T> T execute(final Transaction<T> transaction) {
    return transaction.execute(nodeRequests());
  }

  @Override
  public void verify(final Condition expected) {
    expected.verify(this);
  }

  public void setExitCode(final int exitValue) {
    this.exitCode = Optional.of(exitValue);
  }
}
