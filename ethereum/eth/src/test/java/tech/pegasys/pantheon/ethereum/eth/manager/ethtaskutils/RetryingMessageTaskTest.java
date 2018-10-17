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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManagerTestUtil;
import tech.pegasys.pantheon.ethereum.eth.manager.EthTask;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer.Responder;
import tech.pegasys.pantheon.ethereum.eth.manager.exceptions.MaxRetriesReachedException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.Test;

/**
 * Tests ethTasks that request data from the network, and retry until all of the data is received.
 *
 * @param <T>
 */
public abstract class RetryingMessageTaskTest<T> extends AbstractMessageTaskTest<T, T> {

  protected final int maxRetries;

  public RetryingMessageTaskTest() {
    this.maxRetries = 3;
  }

  @Override
  protected void assertResultMatchesExpectation(
      final T requestedData, final T response, final EthPeer respondingPeer) {
    assertThat(response).isEqualTo(requestedData);
  }

  @Test
  public void failsWhenPeerRepeatedlyReturnsPartialResult()
      throws ExecutionException, InterruptedException {
    // Setup data to be requested and expected response

    // Setup a partially responsive peer
    final Responder responder = RespondingEthPeer.partialResponder(blockchain, protocolSchedule);
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    // Execute task and wait for response
    final T requestedData = generateDataToBeRequested();
    final EthTask<T> task = createTask(requestedData);
    final CompletableFuture<T> future = task.run();

    // Respond max times
    respondingPeer.respondTimes(responder, maxRetries);
    assertThat(future.isDone()).isFalse();

    // Next retry should fail
    respondingPeer.respond(responder);
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(future::get).hasCauseInstanceOf(MaxRetriesReachedException.class);
  }

  @Test
  public void doesNotCompleteWhenPeersAreUnavailable()
      throws ExecutionException, InterruptedException {
    // Setup data to be requested
    final T requestedData = generateDataToBeRequested();

    final EthTask<T> task = createTask(requestedData);
    final CompletableFuture<T> future = task.run();

    assertThat(future.isDone()).isFalse();
  }

  @Test
  public void completesWhenPeersAreTemporarilyUnavailable()
      throws ExecutionException, InterruptedException, TimeoutException {
    // Setup data to be requested
    final T requestedData = generateDataToBeRequested();

    // Execute task and wait for response
    final EthTask<T> task = createTask(requestedData);
    final CompletableFuture<T> future = task.run();

    assertThat(future.isDone()).isFalse();

    // Setup a peer
    final Responder responder = RespondingEthPeer.blockchainResponder(blockchain);
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);
    respondingPeer.respondWhile(responder, () -> !future.isDone());

    assertResultMatchesExpectation(requestedData, future.get(), respondingPeer.getEthPeer());
  }

  @Test
  public void completeWhenPeersTimeoutTemporarily()
      throws ExecutionException, InterruptedException, TimeoutException {
    peerCountToTimeout.set(1);
    final Responder responder = RespondingEthPeer.blockchainResponder(blockchain);
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);
    final T requestedData = generateDataToBeRequested();

    final EthTask<T> task = createTask(requestedData);
    final CompletableFuture<T> future = task.run();

    assertThat(future.isDone()).isFalse();
    respondingPeer.respondWhile(responder, () -> !future.isDone());

    assertResultMatchesExpectation(requestedData, future.get(), respondingPeer.getEthPeer());
  }

  @Test
  public void failsWhenPeersSendEmptyResponses() throws ExecutionException, InterruptedException {
    // Setup a unresponsive peer
    final Responder responder = RespondingEthPeer.emptyResponder();
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    // Setup data to be requested
    final T requestedData = generateDataToBeRequested();

    // Setup and run task
    final EthTask<T> task = createTask(requestedData);
    final CompletableFuture<T> future = task.run();

    assertThat(future.isDone()).isFalse();

    // Respond max times
    respondingPeer.respondTimes(responder, maxRetries);
    assertThat(future.isDone()).isFalse();

    // Next retry should fail
    respondingPeer.respond(responder);
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(future::get).hasCauseInstanceOf(MaxRetriesReachedException.class);
  }
}
