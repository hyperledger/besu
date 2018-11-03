/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.eth.manager;

import tech.pegasys.pantheon.ethereum.eth.manager.exceptions.MaxRetriesReachedException;
import tech.pegasys.pantheon.ethereum.eth.manager.exceptions.NoAvailablePeersException;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.WaitForPeerTask;
import tech.pegasys.pantheon.util.ExceptionUtils;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A task that will retry a fixed number of times before completing the associated CompletableFuture
 * exceptionally with a new {@link MaxRetriesReachedException}.
 *
 * <p>As an additional semantic subclasses may call {@link #resetRetryCounter} so that if they have
 * partial success they can reset the retry counter and only count zero progress retries against the
 * exception limit. If this facility is used only consecutive zero progress retries count against
 * the maximum retries limit.
 *
 * @param <T> The type of the CompletableFuture this task will return.
 */
public abstract class AbstractRetryingPeerTask<T> extends AbstractEthTask<T> {

  private static final Logger LOG = LogManager.getLogger();
  private final EthContext ethContext;
  private final int maxRetries;
  private int retryCount = 0;

  /**
   * @param ethContext The context of the current Eth network we are attached to.
   * @param maxRetries Maximum number of retries to accept before completing exceptionally.
   */
  public AbstractRetryingPeerTask(final EthContext ethContext, final int maxRetries) {
    this.ethContext = ethContext;
    this.maxRetries = maxRetries;
  }

  @Override
  protected void executeTask() {
    if (result.get().isDone()) {
      // Return if task is done
      return;
    }
    if (retryCount > maxRetries) {
      result.get().completeExceptionally(new MaxRetriesReachedException());
      return;
    }

    retryCount += 1;
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
                  .whenComplete((r, t) -> executeTask()));
      return;
    }

    LOG.debug(
        "Retrying after recoverable failure from peer task {}: {}",
        this.getClass().getSimpleName(),
        cause.getMessage());
    // Wait before retrying on failure
    executeSubTask(
        () ->
            ethContext.getScheduler().scheduleFutureTask(this::executeTask, Duration.ofSeconds(1)));
  }

  /**
   * Reset the retryCounter. Once called executeTask will get a fresh set of retries to complete the
   * task.
   */
  protected void resetRetryCounter() {
    retryCount = 0;
  }

  protected abstract boolean isRetryableError(Throwable error);
}
