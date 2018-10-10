package net.consensys.pantheon.ethereum.p2p.api;

import net.consensys.pantheon.ethereum.p2p.discovery.internal.PeerRequirement;
import net.consensys.pantheon.ethereum.p2p.wire.Capability;
import net.consensys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;

import java.util.List;

/** Represents an object responsible for managing a wire subprotocol. */
public interface ProtocolManager extends AutoCloseable, PeerRequirement {

  String getSupportedProtocol();

  /**
   * Defines the list of capabilities supported by this manager.
   *
   * @return the list of capabilities supported by this manager
   */
  List<Capability> getSupportedCapabilities();

  /** Stops the protocol manager. */
  void stop();

  /**
   * Blocks until protocol manager has stopped.
   *
   * @throws InterruptedException if interrupted while waiting
   */
  void awaitStop() throws InterruptedException;

  /**
   * Processes a message from a peer.
   *
   * @param cap the capability that corresponds to the message
   * @param message the message from the peer
   */
  void processMessage(Capability cap, Message message);

  /**
   * Handles new peer connections.
   *
   * @param peerConnection the new peer connection
   */
  void handleNewConnection(PeerConnection peerConnection);

  /**
   * Handles peer disconnects.
   *
   * @param peerConnection the connection that is being closed
   * @param disconnectReason the reason given for closing the connection
   * @param initiatedByPeer true if the peer requested to disconnect, false if this node requested
   *     the disconnect
   */
  void handleDisconnect(
      PeerConnection peerConnection, DisconnectReason disconnectReason, boolean initiatedByPeer);

  @Override
  default void close() {
    stop();
  }
}
