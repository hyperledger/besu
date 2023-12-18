/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.eth.manager.task;

import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.exceptions.IncompleteResultsException;
import org.hyperledger.besu.ethereum.eth.manager.exceptions.MaxRetriesReachedException;
import org.hyperledger.besu.ethereum.eth.manager.exceptions.NoAvailablePeersException;
import org.hyperledger.besu.ethereum.eth.manager.exceptions.PeerBreachedProtocolException;
import org.hyperledger.besu.ethereum.eth.manager.exceptions.PeerDisconnectedException;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.util.ExceptionUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A task that will retry a fixed number of times before completing the associated CompletableFuture
 * exceptionally with a new {@link MaxRetriesReachedException}. If the future returned from {@link
 * #executePeerTask(Optional)} is considered an empty result by {@link #emptyResult(Object)} the
 * peer is demoted, if the result is complete according to {@link #successfulResult(Object)} then
 * the final task result is set, otherwise the result is considered partial and the retry counter is
 * reset.
 *
 * <p><b>Note:</b> extending classes should never set the final task result, using {@code
 * result.complete} by themselves, but should return true from {@link #successfulResult(Object)}
 * when done.
 *
 * @param <T> The type as a typed list that the peer task can get partial or full results in.
 */
public abstract class AbstractRetryingPeerTask<T> extends AbstractEthTask<T> {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractRetryingPeerTask.class);
  private final EthContext ethContext;
  private final int maxRetries;
  private final MetricsSystem metricsSystem;
  private int retryCount = 0;
  private Optional<EthPeer> assignedPeer = Optional.empty();

  /**
   * @param ethContext The context of the current Eth network we are attached to.
   * @param maxRetries Maximum number of retries to accept before completing exceptionally.
   * @param metricsSystem The metrics system used to measure task.
   */
  protected AbstractRetryingPeerTask(
      final EthContext ethContext, final int maxRetries, final MetricsSystem metricsSystem) {
    super(metricsSystem);
    this.ethContext = ethContext;
    this.maxRetries = maxRetries;
    this.metricsSystem = metricsSystem;
  }

  public void assignPeer(final EthPeer peer) {
    assignedPeer = Optional.of(peer);
  }

  public Optional<EthPeer> getAssignedPeer() {
    return assignedPeer;
  }

  @Override
  protected void executeTask() {
    if (result.isDone()) {
      // Return if task is done
      return;
    }
    if (retryCount >= maxRetries) {
      result.completeExceptionally(new MaxRetriesReachedException());
      return;
    }

    retryCount += 1;
    executePeerTask(assignedPeer)
        .whenComplete(
            (peerResult, error) -> {
              if (error != null) {
                handleTaskError(error);
              } else {
                if (successfulResult(peerResult)) {
                  result.complete(peerResult);
                } else {
                  final boolean emptyResult = emptyResult(peerResult);
                  if (emptyResult && reportUselessIfEmptyResponse()) {
                    // record this empty response, so that the peer will be disconnected if there
                    // were too many
                    assignedPeer.ifPresent(
                        peer -> peer.recordUselessResponse(getClass().getSimpleName()));
                  }
                  if (!emptyResult) {
                    // If we get a partial success, reset the retry counter
                    retryCount = 0;
                  }
                  // retry
                  executeTaskTimed();
                }
              }
            });
  }

  protected boolean reportUselessIfEmptyResponse() {
    return true;
  }

  protected abstract CompletableFuture<T> executePeerTask(Optional<EthPeer> assignedPeer);

  protected void handleTaskError(final Throwable error) {
    final Throwable rootCause = ExceptionUtils.rootCause(error);
    if (!isRetryableError(rootCause)) {
      // Complete exceptionally
      result.completeExceptionally(rootCause);
      return;
    }

    if (rootCause instanceof NoAvailablePeersException) {
      LOG.debug(
          "No useful peer found, wait max 5 seconds for new peer to connect: current peers {}",
          ethContext.getEthPeers().peerCount());

      final WaitForPeerTask waitTask = WaitForPeerTask.create(ethContext, metricsSystem);
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
        rootCause.getMessage());
    // Wait before retrying on failure
    executeSubTask(
        () ->
            ethContext
                .getScheduler()
                .scheduleFutureTask(this::executeTaskTimed, Duration.ofSeconds(1)));
  }

  protected boolean isRetryableError(final Throwable rootCause) {
    return rootCause instanceof IncompleteResultsException
        || rootCause instanceof TimeoutException
        || (!assignedPeer.isPresent() && isPeerFailure(rootCause));
  }

  protected boolean isPeerFailure(final Throwable rootCause) {
    return rootCause instanceof PeerBreachedProtocolException
        || rootCause instanceof PeerDisconnectedException
        || rootCause instanceof NoAvailablePeersException;
  }

  protected EthContext getEthContext() {
    return ethContext;
  }

  protected MetricsSystem getMetricsSystem() {
    return metricsSystem;
  }

  public int getRetryCount() {
    return retryCount;
  }

  public int getMaxRetries() {
    return maxRetries;
  }

  /**
   * Identify if the result is empty.
   *
   * @param peerResult the result to check
   * @return true if the result is empty and the peer should be demoted and the request retried
   */
  protected abstract boolean emptyResult(final T peerResult);

  /**
   * Identify a successful and complete result. Partial results that are not considered successful
   * should return false, so that the request is retried. This check has precedence over the {@link
   * #emptyResult(Object)}, so if an empty result is also successful the task completes successfully
   * with an empty result.
   *
   * @param peerResult the result to check
   * @return true if the result is successful and can be set as the task result
   */
  protected abstract boolean successfulResult(final T peerResult);
}
