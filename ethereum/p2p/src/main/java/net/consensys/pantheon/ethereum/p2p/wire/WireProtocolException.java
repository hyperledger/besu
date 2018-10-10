package net.consensys.pantheon.ethereum.p2p.wire;

/** Signals that an exception occurred in the Wire protocol layer of the RLPx stack. */
public class WireProtocolException extends RuntimeException {

  public WireProtocolException(final String message) {
    super(message);
  }

  public WireProtocolException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
