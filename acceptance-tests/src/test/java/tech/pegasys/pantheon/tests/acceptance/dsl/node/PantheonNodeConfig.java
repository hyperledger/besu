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

public class PantheonNodeConfig {

  private final String name;
  private final MiningParameters miningParameters;
  private final JsonRpcConfiguration jsonRpcConfiguration;
  private final WebSocketConfiguration webSocketConfiguration;
  private ServerSocket serverSocket;

  private PantheonNodeConfig(
      final String name,
      final MiningParameters miningParameters,
      final JsonRpcConfiguration jsonRpcConfiguration,
      final WebSocketConfiguration webSocketConfiguration) {
    this.name = name;
    this.miningParameters = miningParameters;
    this.jsonRpcConfiguration = jsonRpcConfiguration;
    this.webSocketConfiguration = webSocketConfiguration;
  }

  private PantheonNodeConfig(final String name, final MiningParameters miningParameters) {
    this.name = name;
    this.miningParameters = miningParameters;
    this.jsonRpcConfiguration = createJsonRpcConfig();
    this.webSocketConfiguration = createWebSocketConfig();
  }

  private static MiningParameters createMiningParameters(final boolean miner) {
    return new MiningParametersTestBuilder().enabled(miner).build();
  }

  public static PantheonNodeConfig pantheonMinerNode(final String name) {
    return new PantheonNodeConfig(name, createMiningParameters(true));
  }

  public static PantheonNodeConfig pantheonNode(final String name) {
    return new PantheonNodeConfig(name, createMiningParameters(false));
  }

  public static PantheonNodeConfig pantheonRpcDisabledNode(final String name) {
    return new PantheonNodeConfig(
        name,
        createMiningParameters(false),
        JsonRpcConfiguration.createDefault(),
        WebSocketConfiguration.createDefault());
  }

  public static PantheonNodeConfig patheonNodeWithRpcApis(
      final String name, final RpcApi... enabledRpcApis) {
    final JsonRpcConfiguration jsonRpcConfig = createJsonRpcConfig();
    jsonRpcConfig.setRpcApis(Arrays.asList(enabledRpcApis));
    final WebSocketConfiguration webSocketConfig = createWebSocketConfig();
    webSocketConfig.setRpcApis(Arrays.asList(enabledRpcApis));

    return new PantheonNodeConfig(
        name, createMiningParameters(false), jsonRpcConfig, webSocketConfig);
  }

  private static JsonRpcConfiguration createJsonRpcConfig() {
    final JsonRpcConfiguration config = JsonRpcConfiguration.createDefault();
    config.setEnabled(true);
    config.setPort(0);
    return config;
  }

  private static WebSocketConfiguration createWebSocketConfig() {
    final WebSocketConfiguration config = WebSocketConfiguration.createDefault();
    config.setEnabled(true);
    config.setPort(0);
    return config;
  }

  public void initSocket() throws IOException {
    serverSocket = new ServerSocket(0);
  }

  public void closeSocket() throws IOException {
    serverSocket.close();
  }

  public int getSocketPort() {
    return serverSocket.getLocalPort();
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
