package tech.pegasys.pantheon.ethereum.p2p.discovery;

/** Thrown to indicate that an error occurred during the operation of the P2P Discovery Service. */
public class PeerDiscoveryServiceException extends RuntimeException {

  public PeerDiscoveryServiceException(final String message) {
    super(message);
  }
}
