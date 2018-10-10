package net.consensys.pantheon.ethereum.p2p.api;

import net.consensys.pantheon.ethereum.p2p.wire.Capability;
import net.consensys.pantheon.ethereum.p2p.wire.PeerInfo;
import net.consensys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.Set;

/** A P2P connection to another node. */
public interface PeerConnection {

  /**
   * Send given data to the connected node.
   *
   * @param message Data to send
   * @param capability Sub-protocol to use
   * @throws PeerNotConnected On attempt to send to a disconnected peer
   */
  void send(Capability capability, MessageData message) throws PeerNotConnected;

  /** @return a list of shared capabilities between this node and the connected peer */
  Set<Capability> getAgreedCapabilities();

  /**
   * Returns the agreed capability corresponding to given protocol.
   *
   * @param protocol the name of the protocol
   * @return the agreed capability corresponding to this protocol, returns null if no matching
   *     capability is supported
   */
  default Capability capability(final String protocol) {
    for (final Capability cap : getAgreedCapabilities()) {
      if (cap.getName().equalsIgnoreCase(protocol)) {
        return cap;
      }
    }
    return null;
  }

  /**
   * Sends a message to the peer for the given subprotocol
   *
   * @param protocol the subprotocol name
   * @param message the message to send
   * @throws PeerNotConnected if the peer has disconnected
   */
  default void sendForProtocol(final String protocol, final MessageData message)
      throws PeerNotConnected {
    send(capability(protocol), message);
  }

  /**
   * Returns the Peer's Description.
   *
   * @return Peer Description
   */
  PeerInfo getPeer();

  /**
   * Immediately terminate the connection without sending a disconnect message.
   *
   * @param reason the reason for disconnection
   * @param peerInitiated <code>true</code> if and only if the remote peer requested disconnection
   */
  void terminateConnection(DisconnectReason reason, boolean peerInitiated);

  /**
   * Disconnect from this Peer.
   *
   * @param reason Reason for disconnecting
   */
  void disconnect(DisconnectReason reason);

  SocketAddress getLocalAddress();

  SocketAddress getRemoteAddress();

  class PeerNotConnected extends IOException {

    public PeerNotConnected(final String message) {
      super(message);
    }
  }
}
