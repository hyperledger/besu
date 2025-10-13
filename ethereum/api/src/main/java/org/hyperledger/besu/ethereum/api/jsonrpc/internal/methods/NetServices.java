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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.graphql.GraphQLConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration;
import org.hyperledger.besu.ethereum.p2p.network.P2PNetwork;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;
import org.hyperledger.besu.plugin.data.EnodeURL;

import com.google.common.collect.ImmutableMap;

public class NetServices implements JsonRpcMethod {

  private final JsonRpcConfiguration jsonRpcConfiguration;
  private final WebSocketConfiguration webSocketConfiguration;
  private final P2PNetwork p2pNetwork;
  private final MetricsConfiguration metricsConfiguration;
  private final GraphQLConfiguration graphQLConfiguration;

  public NetServices(
      final JsonRpcConfiguration jsonRpcConfiguration,
      final WebSocketConfiguration webSocketConfiguration,
      final P2PNetwork p2pNetwork,
      final MetricsConfiguration metricsConfiguration,
      final GraphQLConfiguration graphQLConfiguration) {
    this.jsonRpcConfiguration = jsonRpcConfiguration;
    this.webSocketConfiguration = webSocketConfiguration;
    this.p2pNetwork = p2pNetwork;
    this.metricsConfiguration = metricsConfiguration;
    this.graphQLConfiguration = graphQLConfiguration;
  }

  @Override
  public String getName() {
    return RpcMethod.NET_SERVICES.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
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
                          enode.getIpAsString(), enode.getListeningPort().get())));
    }
    if (metricsConfiguration.isEnabled()) {
      servicesMapBuilder.put(
          "metrics",
          createServiceDetailsMap(
              metricsConfiguration.getHost(), metricsConfiguration.getActualPort()));
    }
    if (graphQLConfiguration.isEnabled()) {
      servicesMapBuilder.put(
          "graphQL",
          createServiceDetailsMap(graphQLConfiguration.getHost(), graphQLConfiguration.getPort()));
    }

    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(), servicesMapBuilder.build());
  }

  private ImmutableMap<String, String> createServiceDetailsMap(final String host, final int port) {
    return ImmutableMap.of("host", host, "port", String.valueOf(port));
  }
}
