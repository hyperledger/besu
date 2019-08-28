/*
 * Copyright 2019 ConsenSys AG.
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

import tech.pegasys.pantheon.config.GenesisConfigOptions;
import tech.pegasys.pantheon.ethereum.chain.ChainHead;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.p2p.network.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.peers.EnodeURL;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.ImmutableMap;

public class AdminNodeInfo implements JsonRpcMethod {

  private final String clientVersion;
  private final BigInteger networkId;
  private final GenesisConfigOptions genesisConfigOptions;
  private final P2PNetwork peerNetwork;
  private final BlockchainQueries blockchainQueries;

  public AdminNodeInfo(
      final String clientVersion,
      final BigInteger networkId,
      final GenesisConfigOptions genesisConfigOptions,
      final P2PNetwork peerNetwork,
      final BlockchainQueries blockchainQueries) {
    this.peerNetwork = peerNetwork;
    this.clientVersion = clientVersion;
    this.genesisConfigOptions = genesisConfigOptions;
    this.blockchainQueries = blockchainQueries;
    this.networkId = networkId;
  }

  @Override
  public String getName() {
    return RpcMethod.ADMIN_NODE_INFO.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest req) {
    final Map<String, Object> response = new HashMap<>();
    final Map<String, Integer> ports = new HashMap<>();

    if (!peerNetwork.isP2pEnabled()) {
      return new JsonRpcErrorResponse(req.getId(), JsonRpcError.P2P_DISABLED);
    }
    final Optional<EnodeURL> maybeEnode = peerNetwork.getLocalEnode();
    if (maybeEnode.isEmpty()) {
      return new JsonRpcErrorResponse(req.getId(), JsonRpcError.P2P_NETWORK_NOT_RUNNING);
    }
    final EnodeURL enode = maybeEnode.get();

    final BytesValue nodeId = enode.getNodeId();
    response.put("enode", enode.toString());
    response.put("ip", enode.getIpAsString());
    if (enode.isListening()) {
      response.put("listenAddr", enode.getIpAsString() + ":" + enode.getListeningPort().getAsInt());
    }
    response.put("id", nodeId.toUnprefixedString());
    response.put("name", clientVersion);

    if (enode.isRunningDiscovery()) {
      ports.put("discovery", enode.getDiscoveryPortOrZero());
    }
    if (enode.isListening()) {
      ports.put("listener", enode.getListeningPort().getAsInt());
    }
    response.put("ports", ports);

    final ChainHead chainHead = blockchainQueries.getBlockchain().getChainHead();
    response.put(
        "protocols",
        ImmutableMap.of(
            "eth",
            ImmutableMap.of(
                "config",
                genesisConfigOptions.asMap(),
                "difficulty",
                chainHead.getTotalDifficulty().toLong(),
                "genesis",
                blockchainQueries.getBlockHashByNumber(0).get().toString(),
                "head",
                chainHead.getHash().toString(),
                "network",
                networkId)));

    return new JsonRpcSuccessResponse(req.getId(), response);
  }
}
