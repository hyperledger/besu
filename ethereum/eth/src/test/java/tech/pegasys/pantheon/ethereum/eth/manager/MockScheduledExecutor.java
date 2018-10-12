package net.consensys.pantheon.ethereum.eth.manager;

import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class MockScheduledExecutor extends MockExecutorService implements ScheduledExecutorService {

  @Override
  public ScheduledFuture<?> schedule(
      final Runnable command, final long delay, final TimeUnit unit) {
    final Future<?> future = this.submit(command);
    return new MockScheduledFuture<>(future);
  }

  @Override
  public <V> ScheduledFuture<V> schedule(
      final Callable<V> callable, final long delay, final TimeUnit unit) {
    final Future<V> future = this.submit(callable);
    return new MockScheduledFuture<>(future);
  }

  @Override
  public ScheduledFuture<?> scheduleAtFixedRate(
      final Runnable command, final long initialDelay, final long period, final TimeUnit unit) {
    final Future<?> future = this.submit(command);
    return new MockScheduledFuture<>(future);
  }

  @Override
  public ScheduledFuture<?> scheduleWithFixedDelay(
      final Runnable command, final long initialDelay, final long delay, final TimeUnit unit) {
    final Future<?> future = this.submit(command);
    return new MockScheduledFuture<>(future);
  }

  private static class MockScheduledFuture<T> implements ScheduledFuture<T> {

    private final Future<T> future;

    public MockScheduledFuture(final Future<T> future) {
      this.future = future;
    }

    @Override
    public long getDelay(final TimeUnit unit) {
      return 0;
    }

    @Override
    public int compareTo(final Delayed o) {
      return 0;
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
      return future.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
      return future.isCancelled();
    }

    @Override
    public boolean isDone() {
      return future.isDone();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
      return future.get();
    }

    @Override
    public T get(final long timeout, final TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      return future.get(timeout, unit);
    }
  }
}
