package net.consensys.pantheon.ethereum.eth.manager.exceptions;

public class NoAvailablePeersException extends EthTaskException {

  public NoAvailablePeersException() {
    super(FailureReason.NO_AVAILABLE_PEERS);
  }
}
