package net.consensys.pantheon.ethereum.p2p.rlpx.handshake;

/** Signals that an error occurred during the RLPx cryptographic handshake. */
public class HandshakeException extends RuntimeException {

  public HandshakeException(final String message) {
    super(message);
  }

  public HandshakeException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
