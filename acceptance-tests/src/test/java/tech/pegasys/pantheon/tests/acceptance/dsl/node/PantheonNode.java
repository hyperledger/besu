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
package tech.pegasys.pantheon.tests.acceptance.dsl.node;

import static org.apache.logging.log4j.LogManager.getLogger;

import tech.pegasys.pantheon.cli.EthNetworkConfig;
import tech.pegasys.pantheon.controller.KeyPairUtil;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.core.Util;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import tech.pegasys.pantheon.ethereum.permissioning.PermissioningConfiguration;
import tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration;
import tech.pegasys.pantheon.tests.acceptance.dsl.condition.Condition;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.AdminJsonRpcRequestFactory;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.CliqueJsonRpcRequestFactory;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.IbftJsonRpcRequestFactory;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.JsonRequestFactories;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.PermissioningJsonRpcRequestFactory;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.Transaction;
import tech.pegasys.pantheon.tests.acceptance.dsl.waitcondition.WaitCondition;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

public class PantheonNode implements Node, NodeConfiguration, RunnableNode, AutoCloseable {

  private static final String LOCALHOST = "127.0.0.1";
  private static final Logger LOG = getLogger();

  private final Path homeDirectory;
  private final KeyPair keyPair;
  private final int p2pPort;
  private final Properties portsProperties = new Properties();
  private final Boolean p2pEnabled;

  private final String name;
  private final MiningParameters miningParameters;
  private final JsonRpcConfiguration jsonRpcConfiguration;
  private final WebSocketConfiguration webSocketConfiguration;
  private final MetricsConfiguration metricsConfiguration;
  private final Optional<PermissioningConfiguration> permissioningConfiguration;
  private final GenesisConfigProvider genesisConfigProvider;
  private final boolean devMode;
  private final boolean discoveryEnabled;

  private List<String> bootnodes = new ArrayList<>();
  private JsonRequestFactories jsonRequestFactories;
  private Optional<EthNetworkConfig> ethNetworkConfig = Optional.empty();

  public PantheonNode(
      final String name,
      final MiningParameters miningParameters,
      final JsonRpcConfiguration jsonRpcConfiguration,
      final WebSocketConfiguration webSocketConfiguration,
      final MetricsConfiguration metricsConfiguration,
      final Optional<PermissioningConfiguration> permissioningConfiguration,
      final boolean devMode,
      final GenesisConfigProvider genesisConfigProvider,
      final int p2pPort,
      final Boolean p2pEnabled,
      final boolean discoveryEnabled)
      throws IOException {
    this.homeDirectory = Files.createTempDirectory("acctest");
    this.keyPair = KeyPairUtil.loadKeyPair(homeDirectory);
    this.p2pPort = p2pPort;
    this.name = name;
    this.miningParameters = miningParameters;
    this.jsonRpcConfiguration = jsonRpcConfiguration;
    this.webSocketConfiguration = webSocketConfiguration;
    this.metricsConfiguration = metricsConfiguration;
    this.permissioningConfiguration = permissioningConfiguration;
    this.genesisConfigProvider = genesisConfigProvider;
    this.devMode = devMode;
    this.p2pEnabled = p2pEnabled;
    this.discoveryEnabled = discoveryEnabled;
    LOG.info("Created PantheonNode {}", this.toString());
  }

  private boolean isJsonRpcEnabled() {
    return jsonRpcConfiguration().isEnabled();
  }

  private boolean isWebSocketsRpcEnabled() {
    return webSocketConfiguration().isEnabled();
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String enodeUrl() {
    return "enode://" + keyPair.getPublicKey().toString() + "@" + LOCALHOST + ":" + p2pPort;
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

  @Override
  public Optional<Integer> jsonRpcWebSocketPort() {
    if (isWebSocketsRpcEnabled()) {
      return Optional.of(Integer.valueOf(portsProperties.getProperty("ws-rpc")));
    } else {
      return Optional.empty();
    }
  }

  @Override
  public String hostName() {
    return LOCALHOST;
  }

  private JsonRequestFactories jsonRequestFactories() {
    if (jsonRequestFactories == null) {
      final Web3jService web3jService =
          jsonRpcBaseUrl()
              .map(url -> new HttpService(url))
              .orElse(new HttpService("http://" + LOCALHOST + ":8545"));

      jsonRequestFactories =
          new JsonRequestFactories(
              new JsonRpc2_0Web3j(web3jService, 2000, Async.defaultExecutorService()),
              new CliqueJsonRpcRequestFactory(web3jService),
              new IbftJsonRpcRequestFactory(web3jService),
              new PermissioningJsonRpcRequestFactory(web3jService),
              new AdminJsonRpcRequestFactory(web3jService));
    }

    return jsonRequestFactories;
  }

  /** All future JSON-RPC calls are made via a web sockets connection. */
  @Override
  public void useWebSocketsForJsonRpc() {
    final String url = wsRpcBaseUrl().isPresent() ? wsRpcBaseUrl().get() : "ws://127.0.0.1:8546";

    checkIfWebSocketEndpointIsAvailable(url);

    final WebSocketService webSocketService = new WebSocketService(url, true);
    try {
      webSocketService.connect();
    } catch (final ConnectException e) {
      throw new RuntimeException("Error connection to WebSocket endpoint", e);
    }

    if (jsonRequestFactories != null) {
      jsonRequestFactories.shutdown();
    }
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
  public void start(final PantheonNodeRunner runner) {
    runner.startNode(this);
    loadPortsFile();
  }

  @Override
  public NodeConfiguration getConfiguration() {
    return this;
  }

  @Override
  public void awaitPeerDiscovery(final Condition condition) {
    if (jsonRpcEnabled()) {
      verify(condition);
    }
  }

  private void loadPortsFile() {
    try (final FileInputStream fis =
        new FileInputStream(new File(homeDirectory.toFile(), "pantheon.ports"))) {
      portsProperties.load(fis);
    } catch (final IOException e) {
      throw new RuntimeException("Error reading Pantheon ports file", e);
    }
  }

  @Override
  public Address getAddress() {
    return Util.publicKeyToAddress(keyPair.getPublicKey());
  }

  Path homeDirectory() {
    return homeDirectory;
  }

  @Override
  public boolean jsonRpcEnabled() {
    return isJsonRpcEnabled();
  }

  JsonRpcConfiguration jsonRpcConfiguration() {
    return jsonRpcConfiguration;
  }

  Optional<String> jsonRpcListenAddress() {
    if (isJsonRpcEnabled()) {
      return Optional.of(jsonRpcConfiguration().getHost() + ":" + jsonRpcConfiguration().getPort());
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

  Optional<String> wsRpcListenAddress() {
    return Optional.of(
        webSocketConfiguration().getHost() + ":" + webSocketConfiguration().getPort());
  }

  MetricsConfiguration metricsConfiguration() {
    return metricsConfiguration;
  }

  int p2pPort() {
    return p2pPort;
  }

  String p2pListenAddress() {
    return LOCALHOST + ":" + p2pPort;
  }

  List<URI> bootnodes() {
    return bootnodes
        .stream()
        .filter(node -> !node.equals(this.enodeUrl()))
        .map(URI::create)
        .collect(Collectors.toList());
  }

  Boolean p2pEnabled() {
    return p2pEnabled;
  }

  @Override
  public void bootnodes(final List<String> bootnodes) {
    this.bootnodes = bootnodes;
  }

  MiningParameters getMiningParameters() {
    return miningParameters;
  }

  public boolean isDevMode() {
    return devMode;
  }

  public boolean isDiscoveryEnabled() {
    return discoveryEnabled;
  }

  Optional<PermissioningConfiguration> getPermissioningConfiguration() {
    return permissioningConfiguration;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name)
        .add("p2pPort", p2pPort)
        .add("homeDirectory", homeDirectory)
        .add("keyPair", keyPair)
        .add("p2pEnabled", p2pEnabled)
        .add("discoveryEnabled", discoveryEnabled)
        .toString();
  }

  @Override
  public void stop() {
    if (jsonRequestFactories != null) {
      jsonRequestFactories.shutdown();
      jsonRequestFactories = null;
    }
  }

  @Override
  public void close() {
    stop();
    try {
      MoreFiles.deleteRecursively(homeDirectory, RecursiveDeleteOption.ALLOW_INSECURE);
    } catch (final IOException e) {
      LOG.info("Failed to clean up temporary file: {}", homeDirectory, e);
    }
  }

  @Override
  public GenesisConfigProvider genesisConfigProvider() {
    return genesisConfigProvider;
  }

  @Override
  public Optional<EthNetworkConfig> ethNetworkConfig() {
    return ethNetworkConfig;
  }

  @Override
  public void ethNetworkConfig(final Optional<EthNetworkConfig> ethNetworkConfig) {
    this.ethNetworkConfig = ethNetworkConfig;
  }

  @Override
  public <T> T execute(final Transaction<T> transaction) {
    return transaction.execute(jsonRequestFactories());
  }

  @Override
  public void verify(final Condition expected) {
    expected.verify(this);
  }

  @Override
  public void waitUntil(final WaitCondition expected) {
    expected.waitUntil(this);
  }
}
