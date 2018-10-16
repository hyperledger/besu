package tech.pegasys.pantheon.ethereum.eth.manager.exceptions;

public class MaxRetriesReachedException extends EthTaskException {

  public MaxRetriesReachedException() {
    super(FailureReason.MAX_RETRIES_REACHED);
  }
}
