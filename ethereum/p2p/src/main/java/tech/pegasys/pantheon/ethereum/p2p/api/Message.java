package tech.pegasys.pantheon.ethereum.p2p.api;

/** A P2P network message received from another peer. */
public interface Message {

  /**
   * Returns the {@link MessageData} contained in the message.
   *
   * @return Data in the message
   */
  MessageData getData();

  /**
   * {@link PeerConnection} this message was sent from.
   *
   * @return PeerConnection this message was sent from.
   */
  PeerConnection getConnection();
}
