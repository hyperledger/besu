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
import tech.pegasys.pantheon.ethereum.eth.manager.exceptions.PeerBreachedProtocolException;
import tech.pegasys.pantheon.ethereum.eth.manager.exceptions.PeerDisconnectedException;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.WaitForPeerTask;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.OperationTimer;
import tech.pegasys.pantheon.util.ExceptionUtils;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A task that will retry a fixed number of times before completing the associated CompletableFuture
 * exceptionally with a new {@link MaxRetriesReachedException}. If the future returned from {@link
 * #executePeerTask(Optional)} is complete with a non-empty list the retry counter is reset.
 *
 * @param <T> The type as a typed list that the peer task can get partial or full results in.
 */
public abstract class AbstractRetryingPeerTask<T extends Collection<?>> extends AbstractEthTask<T> {

  private static final Logger LOG = LogManager.getLogger();
  private final EthContext ethContext;
  private final int maxRetries;
  private int retryCount = 0;
  private Optional<EthPeer> assignedPeer = Optional.empty();

  /**
   * @param ethContext The context of the current Eth network we are attached to.
   * @param maxRetries Maximum number of retries to accept before completing exceptionally.
   * @param ethTasksTimer The metrics timer to use to time the duration of the task.
   */
  public AbstractRetryingPeerTask(
      final EthContext ethContext,
      final int maxRetries,
      final LabelledMetric<OperationTimer> ethTasksTimer) {
    super(ethTasksTimer);
    this.ethContext = ethContext;
    this.maxRetries = maxRetries;
  }

  public void assignPeer(final EthPeer peer) {
    assignedPeer = Optional.of(peer);
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
    executePeerTask(assignedPeer)
        .whenComplete(
            (peerResult, error) -> {
              if (error != null) {
                handleTaskError(error);
              } else {
                // If we get a partial success reset the retry counter.
                if (peerResult.size() > 0) {
                  retryCount = 0;
                }
                executeTaskTimed();
              }
            });
  }

  protected abstract CompletableFuture<T> executePeerTask(Optional<EthPeer> assignedPeer);

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
      final WaitForPeerTask waitTask = WaitForPeerTask.create(ethContext, ethTasksTimer);
      executeSubTask(
          () ->
              ethContext
                  .getScheduler()
                  .timeout(waitTask, Duration.ofSeconds(5))
                  .whenComplete((r, t) -> executeTaskTimed()));
      return;
    }

    LOG.debug(
        "Retrying after recoverable failure from peer task {}: {}",
        this.getClass().getSimpleName(),
        cause.getMessage());
    // Wait before retrying on failure
    executeSubTask(
        () ->
            ethContext
                .getScheduler()
                .scheduleFutureTask(this::executeTaskTimed, Duration.ofSeconds(1)));
  }

  private boolean isRetryableError(final Throwable error) {
    final boolean isPeerError =
        error instanceof PeerBreachedProtocolException
            || error instanceof PeerDisconnectedException
            || error instanceof NoAvailablePeersException;

    return error instanceof TimeoutException || (!assignedPeer.isPresent() && isPeerError);
  }
}
