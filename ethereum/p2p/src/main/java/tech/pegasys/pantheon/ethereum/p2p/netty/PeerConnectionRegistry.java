package tech.pegasys.pantheon.ethereum.p2p.netty;

import static java.util.Collections.unmodifiableCollection;

import tech.pegasys.pantheon.ethereum.p2p.api.DisconnectCallback;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PeerConnectionRegistry implements DisconnectCallback {

  private final ConcurrentMap<BytesValue, PeerConnection> connections = new ConcurrentHashMap<>();

  public void registerConnection(final PeerConnection connection) {
    connections.put(connection.getPeer().getNodeId(), connection);
  }

  public Collection<PeerConnection> getPeerConnections() {
    return unmodifiableCollection(connections.values());
  }

  public int size() {
    return connections.size();
  }

  public boolean isAlreadyConnected(final BytesValue nodeId) {
    return connections.containsKey(nodeId);
  }

  @Override
  public void onDisconnect(
      final PeerConnection connection,
      final DisconnectReason reason,
      final boolean initiatedByPeer) {
    connections.remove(connection.getPeer().getNodeId());
  }
}
