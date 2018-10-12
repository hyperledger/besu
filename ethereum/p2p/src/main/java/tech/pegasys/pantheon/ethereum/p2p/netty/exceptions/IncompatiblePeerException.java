package tech.pegasys.pantheon.ethereum.p2p.netty.exceptions;

public class IncompatiblePeerException extends RuntimeException {

  public IncompatiblePeerException(final String message) {
    super(message);
  }
}
