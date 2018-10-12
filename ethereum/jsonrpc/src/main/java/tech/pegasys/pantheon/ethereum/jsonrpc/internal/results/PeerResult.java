package tech.pegasys.pantheon.ethereum.jsonrpc.internal.results;

import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;

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
    this.version = Quantity.create(peer.getPeer().getVersion());
    this.name = peer.getPeer().getClientId();
    this.caps =
        peer.getPeer()
            .getCapabilities()
            .stream()
            .map(Capability::toString)
            .map(TextNode::new)
            .collect(Collectors.toList());
    this.network = new NetworkResult(peer.getLocalAddress(), peer.getRemoteAddress());
    this.port = Quantity.create(peer.getPeer().getPort());
    this.id = peer.getPeer().getNodeId().toString();
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
