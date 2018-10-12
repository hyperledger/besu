package tech.pegasys.pantheon.ethereum.eth.manager;

public class MockEthTask extends AbstractEthTask<Object> {

  private boolean executed = false;

  @Override
  protected void executeTask() {
    executed = true;
  }

  public boolean hasBeenStarted() {
    return executed;
  }

  public void complete() {
    result.get().complete(null);
  }

  public void fail() {
    result.get().completeExceptionally(new RuntimeException("Failure forced for testing"));
  }
}
