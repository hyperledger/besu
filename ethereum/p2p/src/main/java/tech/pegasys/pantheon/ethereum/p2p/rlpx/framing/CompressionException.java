package tech.pegasys.pantheon.ethereum.p2p.rlpx.framing;

/** Thrown when an error occurs during compression and decompression of payloads. */
public class CompressionException extends RuntimeException {

  public CompressionException(final String message) {
    super(message);
  }

  public CompressionException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
