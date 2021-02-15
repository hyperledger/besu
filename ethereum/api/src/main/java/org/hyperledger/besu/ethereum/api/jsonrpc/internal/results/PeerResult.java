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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.PeerInfo;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.immutables.value.Value;

@JsonPropertyOrder({"version", "name", "caps", "network", "port", "id", "protocols", "enode"})
@Value.Immutable
@Value.Style(allParameters = true)
public interface PeerResult {

  static PeerResult fromEthPeer(final EthPeer peer) {
    final PeerConnection connection = peer.getConnection();
    final PeerInfo peerInfo = connection.getPeerInfo();
    return ImmutablePeerResult.builder()
        .version(Quantity.create(peerInfo.getVersion()))
        .name(peerInfo.getClientId())
        .caps(
            peerInfo.getCapabilities().stream()
                .map(Capability::toString)
                .map(TextNode::new)
                .collect(Collectors.toList()))
        .network(new NetworkResult(connection.getLocalAddress(), connection.getRemoteAddress()))
        .port(Quantity.create(peerInfo.getPort()))
        .id(peerInfo.getNodeId().toString())
        .protocols(Map.of(peer.getProtocolName(), ProtocolsResult.fromEthPeer(peer)))
        .enode(connection.getRemoteEnode().toString())
        .build();
  }

  @JsonGetter(value = "version")
  String getVersion();

  @JsonGetter(value = "name")
  String getName();

  @JsonGetter(value = "caps")
  List<JsonNode> getCaps();

  @JsonGetter(value = "network")
  NetworkResult getNetwork();

  @JsonGetter(value = "port")
  String getPort();

  @JsonGetter(value = "id")
  String getId();

  @JsonGetter(value = "protocols")
  Map<String, ProtocolsResult> getProtocols();

  @JsonGetter(value = "enode")
  String getEnode();
}
