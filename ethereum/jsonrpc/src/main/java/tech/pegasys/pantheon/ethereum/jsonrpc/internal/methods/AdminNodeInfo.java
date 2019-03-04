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
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.p2p.P2pDisabledException;
import tech.pegasys.pantheon.ethereum.p2p.api.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.wire.PeerInfo;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AdminNodeInfo implements JsonRpcMethod {

  private static final Logger LOG = LogManager.getLogger();

  private final String clientVersion;
  private final int networkId;
  private final GenesisConfigOptions genesisConfigOptions;
  private final P2PNetwork peerNetwork;
  private final BlockchainQueries blockchainQueries;

  public AdminNodeInfo(
      final String clientVersion,
      final int networkId,
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
    return "admin_nodeInfo";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest req) {

    try {
      final Map<String, Object> response = new HashMap<>();
      final Map<String, Integer> ports = new HashMap<>();

      final PeerInfo peerInfo = peerNetwork.getLocalPeerInfo();
      final BytesValue nodeId = peerInfo.getNodeId();
      peerNetwork
          .getAdvertisedPeer()
          .ifPresent(
              advertisedPeer -> {
                response.put("enode", advertisedPeer.getEnodeURI());
                ports.put("discovery", advertisedPeer.getEndpoint().getUdpPort());
                response.put("ip", advertisedPeer.getEndpoint().getHost());
                response.put(
                    "listenAddr",
                    advertisedPeer.getEndpoint().getHost() + ":" + peerInfo.getPort());
              });
      response.put("id", nodeId.toString().substring(2));
      response.put("name", clientVersion);

      ports.put("listener", peerInfo.getPort());
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
    } catch (final P2pDisabledException e) {
      return new JsonRpcErrorResponse(req.getId(), JsonRpcError.P2P_DISABLED);
    } catch (final Exception e) {
      LOG.error("Error processing request: " + req, e);
      throw e;
    }
  }
}
