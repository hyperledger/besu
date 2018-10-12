package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.PeerResult;
import tech.pegasys.pantheon.ethereum.p2p.api.P2PNetwork;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AdminPeers implements JsonRpcMethod {
  private static final Logger LOG = LogManager.getLogger();
  private final P2PNetwork peerDiscoveryAgent;

  public AdminPeers(final P2PNetwork peerDiscoveryAgent) {
    this.peerDiscoveryAgent = peerDiscoveryAgent;
  }

  @Override
  public String getName() {
    return "admin_peers";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest req) {

    try {
      final List<PeerResult> peers =
          peerDiscoveryAgent.getPeers().stream().map(PeerResult::new).collect(Collectors.toList());
      final JsonRpcResponse result = new JsonRpcSuccessResponse(req.getId(), peers);
      return result;
    } catch (final Exception e) {
      LOG.error("Error processing request: " + req, e);
      throw e;
    }
  }
}
