package tech.pegasys.pantheon.ethereum.eth.manager.exceptions;

public class PeerBreachedProtocolException extends EthTaskException {

  public PeerBreachedProtocolException() {
    super(FailureReason.PEER_BREACHED_PROTOCOL);
  }
}
