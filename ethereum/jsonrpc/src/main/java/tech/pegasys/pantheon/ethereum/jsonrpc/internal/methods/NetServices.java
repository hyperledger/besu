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
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.network.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.peers.EnodeURL;
import tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration;

import com.google.common.collect.ImmutableMap;

public class NetServices implements JsonRpcMethod {

  private final JsonRpcConfiguration jsonRpcConfiguration;
  private final WebSocketConfiguration webSocketConfiguration;
  private final P2PNetwork p2pNetwork;
  private final MetricsConfiguration metricsConfiguration;

  public NetServices(
      final JsonRpcConfiguration jsonRpcConfiguration,
      final WebSocketConfiguration webSocketConfiguration,
      final P2PNetwork p2pNetwork,
      final MetricsConfiguration metricsConfiguration) {
    this.jsonRpcConfiguration = jsonRpcConfiguration;
    this.webSocketConfiguration = webSocketConfiguration;
    this.p2pNetwork = p2pNetwork;
    this.metricsConfiguration = metricsConfiguration;
  }

  @Override
  public String getName() {
    return RpcMethod.NET_SERVICES.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest req) {
    final ImmutableMap.Builder<String, ImmutableMap<String, String>> servicesMapBuilder =
        ImmutableMap.builder();

    if (jsonRpcConfiguration.isEnabled()) {
      servicesMapBuilder.put(
          "jsonrpc",
          createServiceDetailsMap(jsonRpcConfiguration.getHost(), jsonRpcConfiguration.getPort()));
    }
    if (webSocketConfiguration.isEnabled()) {
      servicesMapBuilder.put(
          "ws",
          createServiceDetailsMap(
              webSocketConfiguration.getHost(), webSocketConfiguration.getPort()));
    }
    if (p2pNetwork.isP2pEnabled()) {
      p2pNetwork
          .getLocalEnode()
          .filter(EnodeURL::isListening)
          .ifPresent(
              enode ->
                  servicesMapBuilder.put(
                      "p2p",
                      createServiceDetailsMap(
                          enode.getIpAsString(), enode.getListeningPort().getAsInt())));
    }
    if (metricsConfiguration.isEnabled()) {
      servicesMapBuilder.put(
          "metrics",
          createServiceDetailsMap(
              metricsConfiguration.getHost(), metricsConfiguration.getActualPort()));
    }

    return new JsonRpcSuccessResponse(req.getId(), servicesMapBuilder.build());
  }

  private ImmutableMap<String, String> createServiceDetailsMap(final String host, final int port) {
    return ImmutableMap.of("host", host, "port", String.valueOf(port));
  }
}
