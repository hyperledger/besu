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

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.ChainHead;
import org.hyperledger.besu.ethereum.p2p.network.P2PNetwork;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURL;
import org.hyperledger.besu.nat.core.NATManager;
import org.hyperledger.besu.nat.core.domain.NATPortMapping;
import org.hyperledger.besu.nat.core.domain.NATServiceType;
import org.hyperledger.besu.nat.core.domain.NetworkProtocol;
import org.hyperledger.besu.util.bytes.BytesValue;

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
  private final NATManager natManager;

  public AdminNodeInfo(
      final String clientVersion,
      final BigInteger networkId,
      final GenesisConfigOptions genesisConfigOptions,
      final P2PNetwork peerNetwork,
      final BlockchainQueries blockchainQueries,
      final NATManager natManager) {
    this.peerNetwork = peerNetwork;
    this.clientVersion = clientVersion;
    this.genesisConfigOptions = genesisConfigOptions;
    this.blockchainQueries = blockchainQueries;
    this.networkId = networkId;
    this.natManager = natManager;
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
    final String ip = getIp(enode);
    response.put("enode", enode.toString());
    response.put("ip", ip);
    if (enode.isListening()) {
      final int listeningPort = getListeningPort(enode);
      response.put("listenAddr", ip + ":" + listeningPort);
      ports.put("listener", listeningPort);
    }
    response.put("id", nodeId.toUnprefixedString());
    response.put("name", clientVersion);

    if (enode.isRunningDiscovery()) {
      ports.put("discovery", getDiscoveryPort(enode));
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

  private String getIp(final EnodeURL enode) {
    return natManager.getAdvertisedIp().orElseGet(enode::getIpAsString);
  }

  private int getDiscoveryPort(final EnodeURL enode) {
    final Optional<NATPortMapping> portMapping =
        natManager.getPortMapping(NATServiceType.DISCOVERY, NetworkProtocol.UDP);
    return portMapping
        .map(NATPortMapping::getExternalPort)
        .orElseGet(enode::getDiscoveryPortOrZero);
  }

  private int getListeningPort(final EnodeURL enode) {
    final Optional<NATPortMapping> portMapping =
        natManager.getPortMapping(NATServiceType.RLPX, NetworkProtocol.TCP);
    return portMapping
        .map(NATPortMapping::getExternalPort)
        .orElse(enode.getListeningPort().getAsInt());
  }
}
