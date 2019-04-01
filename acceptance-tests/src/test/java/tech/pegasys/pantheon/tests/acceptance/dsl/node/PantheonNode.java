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

import static java.util.Collections.unmodifiableList;
import static net.consensys.cava.io.file.Files.copyResource;
import static org.apache.logging.log4j.LogManager.getLogger;

import tech.pegasys.pantheon.controller.KeyPairUtil;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.core.Util;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import tech.pegasys.pantheon.ethereum.permissioning.PermissioningConfiguration;
import tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration;
import tech.pegasys.pantheon.tests.acceptance.dsl.condition.Condition;
import tech.pegasys.pantheon.tests.acceptance.dsl.httptransaction.HttpRequestFactory;
import tech.pegasys.pantheon.tests.acceptance.dsl.httptransaction.HttpTransaction;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.AdminJsonRpcRequestFactory;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.CliqueJsonRpcRequestFactory;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.EeaJsonRpcRequestFactory;
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

public class PantheonNode implements NodeConfiguration, RunnableNode, AutoCloseable {

  private static final String LOCALHOST = "127.0.0.1";
  private static final Logger LOG = getLogger();

  private final Path homeDirectory;
  private final KeyPair keyPair;
  private final Properties portsProperties = new Properties();
  private final Boolean p2pEnabled;

  private final String name;
  private final MiningParameters miningParameters;
  private final PrivacyParameters privacyParameters;
  private final JsonRpcConfiguration jsonRpcConfiguration;
  private final WebSocketConfiguration webSocketConfiguration;
  private final MetricsConfiguration metricsConfiguration;
  private final Optional<PermissioningConfiguration> permissioningConfiguration;
  private final GenesisConfigProvider genesisConfigProvider;
  private final boolean devMode;
  private final boolean discoveryEnabled;
  private final List<URI> bootnodes = new ArrayList<>();
  private final boolean bootnodeEligible;

  private Optional<String> genesisConfig = Optional.empty();
  private JsonRequestFactories jsonRequestFactories;
  private HttpRequestFactory httpRequestFactory;
  private boolean useWsForJsonRpc = false;
  private String token = null;

  public PantheonNode(
      final String name,
      final MiningParameters miningParameters,
      final PrivacyParameters privacyParameters,
      final JsonRpcConfiguration jsonRpcConfiguration,
      final WebSocketConfiguration webSocketConfiguration,
      final MetricsConfiguration metricsConfiguration,
      final Optional<PermissioningConfiguration> permissioningConfiguration,
      final Optional<String> keyfilePath,
      final boolean devMode,
      final GenesisConfigProvider genesisConfigProvider,
      final boolean p2pEnabled,
      final boolean discoveryEnabled,
      final boolean bootnodeEligible)
      throws IOException {
    this.bootnodeEligible = bootnodeEligible;
    this.homeDirectory = Files.createTempDirectory("acctest");
    keyfilePath.ifPresent(
        path -> {
          try {
            copyResource(path, homeDirectory.resolve("key"));
          } catch (IOException e) {
            LOG.error("Could not find key file \"{}\" in resources", path);
          }
        });
    this.keyPair = KeyPairUtil.loadKeyPair(homeDirectory);
    this.name = name;
    this.miningParameters = miningParameters;
    this.privacyParameters = privacyParameters;
    this.privacyParameters.setSigningKeyPair(keyPair);
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
  public String getNodeId() {
    return keyPair.getPublicKey().toString().substring(2);
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
    Optional<WebSocketService> websocketService = Optional.empty();
    if (jsonRequestFactories == null) {
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

      jsonRequestFactories =
          new JsonRequestFactories(
              new JsonRpc2_0Web3j(web3jService, 2000, Async.defaultExecutorService()),
              new CliqueJsonRpcRequestFactory(web3jService),
              new IbftJsonRpcRequestFactory(web3jService),
              new PermissioningJsonRpcRequestFactory(web3jService),
              new AdminJsonRpcRequestFactory(web3jService),
              new EeaJsonRpcRequestFactory(web3jService),
              websocketService);
    }

    return jsonRequestFactories;
  }

  private HttpRequestFactory httpRequestFactory() {
    if (httpRequestFactory == null) {
      final Optional<String> baseUrl;
      final String port;
      if (useWsForJsonRpc) {
        baseUrl = wsRpcBaseHttpUrl();
        port = "8546";
      } else {
        baseUrl = jsonRpcBaseUrl();
        port = "8545";
      }
      httpRequestFactory =
          new HttpRequestFactory(baseUrl.orElse("http://" + LOCALHOST + ":" + port));
    }
    return httpRequestFactory;
  }

  /** All future JSON-RPC calls are made via a web sockets connection. */
  @Override
  public void useWebSocketsForJsonRpc() {
    final String url = wsRpcBaseUrl().isPresent() ? wsRpcBaseUrl().get() : "ws://127.0.0.1:8546";

    checkIfWebSocketEndpointIsAvailable(url);

    useWsForJsonRpc = true;

    if (jsonRequestFactories != null) {
      jsonRequestFactories.shutdown();
      jsonRequestFactories = null;
    }

    if (httpRequestFactory != null) {
      httpRequestFactory = null;
    }
  }

  /** All future JSON-RPC calls will include the authentication token. */
  @Override
  public void useAuthenticationTokenInHeaderForJsonRpc(final String token) {

    if (jsonRequestFactories != null) {
      jsonRequestFactories.shutdown();
      jsonRequestFactories = null;
    }

    if (httpRequestFactory != null) {
      httpRequestFactory = null;
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
      LOG.info("Ports for node {}: {}", name, portsProperties);
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

  MetricsConfiguration metricsConfiguration() {
    return metricsConfiguration;
  }

  String p2pListenHost() {
    return LOCALHOST;
  }

  @Override
  public List<URI> bootnodes() {
    return unmodifiableList(bootnodes);
  }

  @Override
  public boolean isP2pEnabled() {
    return p2pEnabled;
  }

  @Override
  public boolean isBootnodeEligible() {
    return bootnodeEligible;
  }

  @Override
  public void bootnodes(final List<URI> bootnodes) {
    this.bootnodes.clear();
    this.bootnodes.addAll(bootnodes);
  }

  MiningParameters getMiningParameters() {
    return miningParameters;
  }

  public PrivacyParameters getPrivacyParameters() {
    return privacyParameters;
  }

  public boolean isDevMode() {
    return devMode;
  }

  @Override
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
        .add("homeDirectory", homeDirectory)
        .add("keyPair", keyPair)
        .add("p2pEnabled", p2pEnabled)
        .add("discoveryEnabled", discoveryEnabled)
        .add("privacyEnabled", privacyParameters.isEnabled())
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
  public Optional<String> getGenesisConfig() {
    return genesisConfig;
  }

  @Override
  public void setGenesisConfig(final String config) {
    this.genesisConfig = Optional.of(config);
  }

  @Override
  public <T> T execute(final Transaction<T> transaction) {
    return transaction.execute(jsonRequestFactories());
  }

  @Override
  public <T> T executeHttpTransaction(final HttpTransaction<T> transaction) {
    return transaction.execute(httpRequestFactory());
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
