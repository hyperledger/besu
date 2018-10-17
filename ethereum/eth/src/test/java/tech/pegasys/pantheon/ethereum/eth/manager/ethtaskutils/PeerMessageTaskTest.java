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
package tech.pegasys.pantheon.ethereum.eth.manager.ethtaskutils;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.eth.manager.AbstractPeerTask.PeerTaskResult;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManagerTestUtil;
import tech.pegasys.pantheon.ethereum.eth.manager.EthTask;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer.Responder;
import tech.pegasys.pantheon.ethereum.eth.manager.exceptions.EthTaskException;
import tech.pegasys.pantheon.ethereum.eth.manager.exceptions.EthTaskException.FailureReason;
import tech.pegasys.pantheon.util.ExceptionUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

/**
 * Tests ethTasks that interact with a single peer to retrieve data from the network.
 *
 * @param <T> The type of data being retrieved.
 */
public abstract class PeerMessageTaskTest<T> extends AbstractMessageTaskTest<T, PeerTaskResult<T>> {
  @Test
  public void completesWhenPeerReturnsPartialResult()
      throws ExecutionException, InterruptedException {
    // Setup a partially responsive peer
    final Responder responder = RespondingEthPeer.partialResponder(blockchain, protocolSchedule);
    final RespondingEthPeer respondingEthPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    // Execute task and wait for response
    final AtomicReference<T> actualResult = new AtomicReference<>();
    final AtomicReference<EthPeer> actualPeer = new AtomicReference<>();
    final AtomicBoolean done = new AtomicBoolean(false);
    final T requestedData = generateDataToBeRequested();
    final EthTask<PeerTaskResult<T>> task = createTask(requestedData);
    final CompletableFuture<PeerTaskResult<T>> future = task.run();
    respondingEthPeer.respondWhile(responder, () -> !future.isDone());
    future.whenComplete(
        (response, error) -> {
          actualResult.set(response.getResult());
          actualPeer.set(response.getPeer());
          done.compareAndSet(false, true);
        });

    assertThat(done).isTrue();
    assertPartialResultMatchesExpectation(requestedData, actualResult.get());
    assertThat(actualPeer.get()).isEqualTo(respondingEthPeer.getEthPeer());
  }

  @Test
  public void failsWhenNoPeersAreAvailable() throws ExecutionException, InterruptedException {
    // Setup data to be requested
    final T requestedData = generateDataToBeRequested();

    // Execute task
    final EthTask<PeerTaskResult<T>> task = createTask(requestedData);
    final CompletableFuture<PeerTaskResult<T>> future = task.run();
    final AtomicReference<Throwable> failure = new AtomicReference<>();
    future.whenComplete(
        (r, t) -> {
          failure.set(t);
        });

    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThat(failure.get()).isNotNull();
    // Check wrapped failure
    final Throwable error = ExceptionUtils.rootCause(failure.get());
    assertThat(error).isInstanceOf(EthTaskException.class);
    final EthTaskException ethException = (EthTaskException) error;
    assertThat(ethException.reason()).isEqualTo(FailureReason.NO_AVAILABLE_PEERS);

    assertThat(task.run().isCompletedExceptionally()).isTrue();
    task.cancel();
    assertThat(task.run().isCompletedExceptionally()).isTrue();
  }

  @Test
  public void completesWhenPeersSendEmptyResponses() {
    // Setup a unresponsive peer
    final Responder responder = RespondingEthPeer.emptyResponder();
    final RespondingEthPeer respondingEthPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    // Setup data to be requested
    final T requestedData = generateDataToBeRequested();

    // Execute task and wait for response
    final AtomicBoolean done = new AtomicBoolean(false);
    final EthTask<PeerTaskResult<T>> task = createTask(requestedData);
    final CompletableFuture<PeerTaskResult<T>> future = task.run();
    respondingEthPeer.respondWhile(responder, () -> !future.isDone());
    future.whenComplete(
        (response, error) -> {
          done.compareAndSet(false, true);
        });
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCompletedExceptionally()).isFalse();
  }

  @Test
  public void recordsTimeoutAgainstPeerWhenTaskTimesOut() {
    peersDoTimeout.set(true);
    // Setup a unresponsive peer
    final RespondingEthPeer respondingEthPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    // Setup data to be requested
    final T requestedData = generateDataToBeRequested();

    // Execute task and wait for response
    final EthTask<PeerTaskResult<T>> task = createTask(requestedData);
    final CompletableFuture<PeerTaskResult<T>> future = task.run();

    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThat(
            respondingEthPeer
                .getEthPeer()
                .timeoutCounts()
                .values()
                .stream()
                .mapToInt(AtomicInteger::get)
                .sum())
        .isEqualTo(1);
  }

  @Override
  protected void assertResultMatchesExpectation(
      final T requestedData, final PeerTaskResult<T> response, final EthPeer respondingPeer) {
    assertThat(response.getResult()).isEqualTo(requestedData);
    assertThat(response.getPeer()).isEqualTo(respondingPeer);
  }

  protected abstract void assertPartialResultMatchesExpectation(T requestedData, T partialResponse);
}
