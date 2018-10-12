package tech.pegasys.pantheon.ethereum.p2p.discovery;

/** Signals that an error occurred while deserializing a discovery packet from the wire. */
public class PeerDiscoveryPacketDecodingException extends RuntimeException {
  public PeerDiscoveryPacketDecodingException(final String message) {
    super(message);
  }

  public PeerDiscoveryPacketDecodingException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
