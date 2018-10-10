package net.consensys.pantheon.ethereum.eth.manager;

import net.consensys.pantheon.ethereum.eth.manager.exceptions.NoAvailablePeersException;
import net.consensys.pantheon.ethereum.eth.sync.tasks.WaitForPeerTask;
import net.consensys.pantheon.util.ExceptionUtils;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class AbstractRetryingPeerTask<T> extends AbstractEthTask<T> {

  private static final Logger LOG = LogManager.getLogger();
  private final EthContext ethContext;

  public AbstractRetryingPeerTask(final EthContext ethContext) {
    this.ethContext = ethContext;
  }

  @Override
  protected void executeTask() {
    if (result.get().isDone()) {
      // Return if task is done
      return;
    }

    executePeerTask()
        .whenComplete(
            (peerResult, error) -> {
              if (error != null) {
                handleTaskError(error);
              } else {
                executeTask();
              }
            });
  }

  protected abstract CompletableFuture<?> executePeerTask();

  private void handleTaskError(final Throwable error) {
    final Throwable cause = ExceptionUtils.rootCause(error);
    if (!isRetryableError(cause)) {
      // Complete exceptionally
      result.get().completeExceptionally(cause);
      return;
    }

    if (cause instanceof NoAvailablePeersException) {
      LOG.info("No peers available, wait for peer.");
      // Wait for new peer to connect
      final WaitForPeerTask waitTask = WaitForPeerTask.create(ethContext);
      executeSubTask(
          () ->
              ethContext
                  .getScheduler()
                  .timeout(waitTask, Duration.ofSeconds(5))
                  .whenComplete(
                      (r, t) -> {
                        executeTask();
                      }));
      return;
    }

    LOG.info(
        "Retrying after recoverable failure from peer task {}: {}",
        this.getClass().getSimpleName(),
        cause.getMessage());
    // Wait before retrying on failure
    executeSubTask(
        () ->
            ethContext.getScheduler().scheduleFutureTask(this::executeTask, Duration.ofSeconds(1)));
  }

  protected abstract boolean isRetryableError(Throwable error);
}
