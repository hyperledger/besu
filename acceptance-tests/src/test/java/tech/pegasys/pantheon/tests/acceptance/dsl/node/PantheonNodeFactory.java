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

import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.core.MiningParametersTestBuilder;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApi;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Arrays;

public class PantheonNodeFactory {

  private PantheonNode create(final PantheonFactoryConfiguration config) throws IOException {
    ServerSocket serverSocket = new ServerSocket(0);
    final PantheonNode node =
        new PantheonNode(
            config.getName(),
            config.getMiningParameters(),
            config.getJsonRpcConfiguration(),
            config.getWebSocketConfiguration(),
            serverSocket.getLocalPort());
    serverSocket.close();

    return node;
  }

  public PantheonNode createMinerNode(final String name) throws IOException {
    return create(
        new PantheonFactoryConfiguration(
            name, createMiningParameters(true), createJsonRpcConfig(), createWebSocketConfig()));
  }

  public PantheonNode createArchiveNode(final String name) throws IOException {
    return create(
        new PantheonFactoryConfiguration(
            name, createMiningParameters(false), createJsonRpcConfig(), createWebSocketConfig()));
  }

  public PantheonNode createArchiveNodeWithRpcDisabled(final String name) throws IOException {
    return create(
        new PantheonFactoryConfiguration(
            name,
            createMiningParameters(false),
            JsonRpcConfiguration.createDefault(),
            WebSocketConfiguration.createDefault()));
  }

  public PantheonNode createArchiveNodeWithRpcApis(
      final String name, final RpcApi... enabledRpcApis) throws IOException {
    final JsonRpcConfiguration jsonRpcConfig = createJsonRpcConfig();
    jsonRpcConfig.setRpcApis(Arrays.asList(enabledRpcApis));
    final WebSocketConfiguration webSocketConfig = createWebSocketConfig();
    webSocketConfig.setRpcApis(Arrays.asList(enabledRpcApis));

    return create(
        new PantheonFactoryConfiguration(
            name, createMiningParameters(false), jsonRpcConfig, webSocketConfig));
  }

  private MiningParameters createMiningParameters(final boolean miner) {
    return new MiningParametersTestBuilder().enabled(miner).build();
  }

  private JsonRpcConfiguration createJsonRpcConfig() {
    final JsonRpcConfiguration config = JsonRpcConfiguration.createDefault();
    config.setEnabled(true);
    config.setPort(0);
    return config;
  }

  private WebSocketConfiguration createWebSocketConfig() {
    final WebSocketConfiguration config = WebSocketConfiguration.createDefault();
    config.setEnabled(true);
    config.setPort(0);
    return config;
  }

  static class PantheonFactoryConfiguration {

    private final String name;
    private final MiningParameters miningParameters;
    private final JsonRpcConfiguration jsonRpcConfiguration;
    private final WebSocketConfiguration webSocketConfiguration;

    public PantheonFactoryConfiguration(
        final String name,
        final MiningParameters miningParameters,
        final JsonRpcConfiguration jsonRpcConfiguration,
        final WebSocketConfiguration webSocketConfiguration) {
      this.name = name;
      this.miningParameters = miningParameters;
      this.jsonRpcConfiguration = jsonRpcConfiguration;
      this.webSocketConfiguration = webSocketConfiguration;
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
  }
}
