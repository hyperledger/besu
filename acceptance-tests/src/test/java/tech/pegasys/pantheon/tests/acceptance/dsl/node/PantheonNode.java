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

import tech.pegasys.pantheon.controller.KeyPairUtil;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import tech.pegasys.pantheon.tests.acceptance.dsl.condition.Condition;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.Transaction;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import com.google.common.base.MoreObjects;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import org.apache.logging.log4j.Logger;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.http.HttpService;
import org.web3j.protocol.websocket.WebSocketService;
import org.web3j.utils.Async;

public class PantheonNode implements Node, AutoCloseable {

  private static final String LOCALHOST = "127.0.0.1";
  private static final Logger LOG = getLogger();

  private final String name;
  private final Path homeDirectory;
  private final KeyPair keyPair;
  private final int p2pPort;
  private final MiningParameters miningParameters;
  private final JsonRpcConfiguration jsonRpcConfiguration;
  private final WebSocketConfiguration webSocketConfiguration;
  private final boolean jsonRpcEnabled;
  private final boolean wsRpcEnabled;
  private final Properties portsProperties = new Properties();

  private List<String> bootnodes = new ArrayList<>();
  private Web3j web3j;

  public PantheonNode(
      final String name,
      final int p2pPort,
      final MiningParameters miningParameters,
      final JsonRpcConfiguration jsonRpcConfiguration,
      final WebSocketConfiguration webSocketConfiguration)
      throws IOException {
    this.name = name;
    this.homeDirectory = Files.createTempDirectory("acctest");
    this.keyPair = KeyPairUtil.loadKeyPair(homeDirectory);
    this.p2pPort = p2pPort;
    this.miningParameters = miningParameters;
    this.jsonRpcConfiguration = jsonRpcConfiguration;
    this.webSocketConfiguration = webSocketConfiguration;
    this.jsonRpcEnabled = jsonRpcConfiguration.isEnabled();
    this.wsRpcEnabled = webSocketConfiguration.isEnabled();
    LOG.info("Created PantheonNode {}", this.toString());
  }

  String getName() {
    return name;
  }

  String enodeUrl() {
    return "enode://" + keyPair.getPublicKey().toString() + "@" + LOCALHOST + ":" + p2pPort;
  }

  private Optional<String> jsonRpcBaseUrl() {
    if (jsonRpcEnabled) {
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
    if (wsRpcEnabled) {
      return Optional.of(
          "ws://" + webSocketConfiguration.getHost() + ":" + portsProperties.getProperty("ws-rpc"));
    } else {
      return Optional.empty();
    }
  }

  public Optional<Integer> jsonRpcWebSocketPort() {
    if (wsRpcEnabled) {
      return Optional.of(Integer.valueOf(portsProperties.getProperty("ws-rpc")));
    } else {
      return Optional.empty();
    }
  }

  public String getHost() {
    return LOCALHOST;
  }

  @Deprecated
  public Web3j web3j() {
    if (!jsonRpcBaseUrl().isPresent()) {
      return web3j(new HttpService("http://" + LOCALHOST + ":8545"));
    }

    if (web3j == null) {
      return web3j(new HttpService(jsonRpcBaseUrl().get()));
    }

    return web3j;
  }

  public void setWeb3j(final Web3jService web3jService) {
    if (web3j != null) {
      web3j.shutdown();
    }

    web3j = Web3j.build(web3jService, 2000, Async.defaultExecutorService());
  }

  @Deprecated
  public Web3j web3j(final Web3jService web3jService) {
    if (web3j == null) {
      web3j = Web3j.build(web3jService, 2000, Async.defaultExecutorService());
    }

    return web3j;
  }

  /** All future JSON-RPC calls are made via a web sockets connection. */
  public void useWebSocketsForJsonRpc() {
    final String url = wsRpcBaseUrl().isPresent() ? wsRpcBaseUrl().get() : "ws://127.0.0.1:8546";

    final WebSocketService webSocketService = new WebSocketService(url, true);

    if (web3j != null) {
      web3j.shutdown();
    }

    web3j = Web3j.build(webSocketService, 2000, Async.defaultExecutorService());
  }

  public int getPeerCount() {
    try {
      return web3j().netPeerCount().send().getQuantity().intValueExact();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void start(final PantheonNodeRunner runner) {
    runner.startNode(this);
    loadPortsFile();
  }

  private void loadPortsFile() {
    try (final FileInputStream fis =
        new FileInputStream(new File(homeDirectory.toFile(), "pantheon.ports"))) {
      portsProperties.load(fis);
    } catch (final IOException e) {
      throw new RuntimeException("Error reading Pantheon ports file", e);
    }
  }

  Path homeDirectory() {
    return homeDirectory;
  }

  boolean jsonRpcEnabled() {
    return jsonRpcEnabled;
  }

  JsonRpcConfiguration jsonRpcConfiguration() {
    return jsonRpcConfiguration;
  }

  Optional<String> jsonRpcListenAddress() {
    if (jsonRpcEnabled) {
      return Optional.of(jsonRpcConfiguration.getHost() + ":" + jsonRpcConfiguration.getPort());
    } else {
      return Optional.empty();
    }
  }

  boolean wsRpcEnabled() {
    return wsRpcEnabled;
  }

  WebSocketConfiguration webSocketConfiguration() {
    return webSocketConfiguration;
  }

  Optional<String> wsRpcListenAddress() {
    return Optional.of(webSocketConfiguration.getHost() + ":" + webSocketConfiguration.getPort());
  }

  int p2pPort() {
    return p2pPort;
  }

  String p2pListenAddress() {
    return LOCALHOST + ":" + p2pPort;
  }

  List<String> bootnodes() {
    return bootnodes
        .stream()
        .filter(node -> !node.equals(this.enodeUrl()))
        .collect(Collectors.toList());
  }

  void bootnodes(final List<String> bootnodes) {
    this.bootnodes = bootnodes;
  }

  MiningParameters getMiningParameters() {
    return miningParameters;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name)
        .add("p2pPort", p2pPort)
        .add("homeDirectory", homeDirectory)
        .add("keyPair", keyPair)
        .toString();
  }

  void stop() {
    if (web3j != null) {
      web3j.shutdown();
      web3j = null;
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
  public <T> T execute(final Transaction<T> transaction) {
    return transaction.execute(web3j());
  }

  public void verify(final Condition expected) {
    expected.verify(this);
  }
}
