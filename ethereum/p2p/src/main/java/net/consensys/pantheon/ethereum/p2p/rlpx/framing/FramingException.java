package net.consensys.pantheon.ethereum.p2p.rlpx.framing;

/** Thrown when the framer encounters an error during framing or deframing. */
public class FramingException extends RuntimeException {

  public FramingException(final String message) {
    super(message);
  }

  public FramingException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
