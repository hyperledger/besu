package tech.pegasys.pantheon.ethereum.eth.manager.exceptions;

public class EthTaskException extends RuntimeException {

  private final FailureReason failureReason;

  EthTaskException(final FailureReason failureReason) {
    super("Task failed: " + failureReason.name());
    this.failureReason = failureReason;
  }

  public FailureReason reason() {
    return failureReason;
  }

  public enum FailureReason {
    PEER_DISCONNECTED,
    NO_AVAILABLE_PEERS,
    PEER_BREACHED_PROTOCOL,
    INCOMPLETE_RESULTS,
    MAX_RETRIES_REACHED
  }
}
