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
package tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.results;

import tech.pegasys.pantheon.ethereum.p2p.rlpx.connections.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.Capability;

import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

@JsonPropertyOrder({"version", "name", "caps", "network", "port", "id"})
public class PeerResult {

  private final String version;
  private final String name;
  private final List<JsonNode> caps;
  private final NetworkResult network;
  private final String port;
  private final String id;

  public PeerResult(final PeerConnection peer) {
    this.version = Quantity.create(peer.getPeerInfo().getVersion());
    this.name = peer.getPeerInfo().getClientId();
    this.caps =
        peer.getPeerInfo().getCapabilities().stream()
            .map(Capability::toString)
            .map(TextNode::new)
            .collect(Collectors.toList());
    this.network = new NetworkResult(peer.getLocalAddress(), peer.getRemoteAddress());
    this.port = Quantity.create(peer.getPeerInfo().getPort());
    this.id = peer.getPeerInfo().getNodeId().toString();
  }

  @JsonGetter(value = "version")
  public String getVersion() {
    return version;
  }

  @JsonGetter(value = "name")
  public String getName() {
    return name;
  }

  @JsonGetter(value = "caps")
  public List<JsonNode> getCaps() {
    return caps;
  }

  @JsonGetter(value = "network")
  public NetworkResult getNetwork() {
    return network;
  }

  @JsonGetter(value = "port")
  public String getPort() {
    return port;
  }

  @JsonGetter(value = "id")
  public String getId() {
    return id;
  }
}
