package net.consensys.pantheon.ethereum.eth.manager;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Schedules tasks that run immediately and synchronously for testing. */
public class DeterministicEthScheduler extends EthScheduler {

  private final TimeoutPolicy timeoutPolicy;

  DeterministicEthScheduler() {
    this(() -> false);
  }

  DeterministicEthScheduler(final TimeoutPolicy timeoutPolicy) {
    super(new MockExecutorService(), new MockScheduledExecutor());
    this.timeoutPolicy = timeoutPolicy;
  }

  MockExecutorService mockWorkerExecutor() {
    return (MockExecutorService) workerExecutor;
  }

  MockScheduledExecutor mockScheduledExecutor() {
    return (MockScheduledExecutor) scheduler;
  }

  @Override
  public <T> void failAfterTimeout(final CompletableFuture<T> promise, final Duration timeout) {
    if (timeoutPolicy.shouldTimeout()) {
      final TimeoutException timeoutException =
          new TimeoutException(
              "Mocked timeout after " + timeout.toMillis() + " " + TimeUnit.MILLISECONDS);
      promise.completeExceptionally(timeoutException);
    }
  }

  @FunctionalInterface
  public interface TimeoutPolicy {
    boolean shouldTimeout();
  }
}
