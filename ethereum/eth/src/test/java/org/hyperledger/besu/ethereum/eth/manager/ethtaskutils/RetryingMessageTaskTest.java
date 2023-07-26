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
package org.hyperledger.besu.ethereum.eth.manager.ethtaskutils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.manager.exceptions.MaxRetriesReachedException;
import org.hyperledger.besu.ethereum.eth.manager.task.EthTask;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests ethTasks that request data from the network, and retry until all of the data is received.
 *
 * @param <T> The type of data being requested from the network
 */
public abstract class RetryingMessageTaskTest<T> extends AbstractMessageTaskTest<T, T> {
  protected static final int DEFAULT_MAX_RETRIES = 4;
  protected int maxRetries;

  @BeforeEach
  public void resetMaxRetries() {
    this.maxRetries = DEFAULT_MAX_RETRIES;
  }

  @Override
  protected void assertResultMatchesExpectation(
      final T requestedData, final T response, final EthPeer respondingPeer) {
    assertThat(response).isEqualTo(requestedData);
  }

  @Test
  public void failsWhenPeerReturnsPartialResultThenStops() {
    // Setup data to be requested and expected response

    // Setup a partially responsive peer and a non-responsive peer
    final RespondingEthPeer.Responder partialResponder =
        RespondingEthPeer.partialResponder(
            blockchain,
            protocolContext.getWorldStateArchive(),
            transactionPool,
            protocolSchedule,
            0.5f);
    final RespondingEthPeer.Responder emptyResponder = RespondingEthPeer.emptyResponder();
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    // Execute task and wait for response
    final T requestedData = generateDataToBeRequested();
    final EthTask<T> task = createTask(requestedData);
    final CompletableFuture<T> future = task.run();

    // Respond once with no data
    respondingPeer.respond(emptyResponder);
    assertThat(future.isDone()).isFalse();

    // Respond once with partial data, this should reset failures
    respondingPeer.respond(partialResponder);
    assertThat(future.isDone()).isFalse();

    // Respond max times - 1 with no data
    respondingPeer.respondTimes(emptyResponder, maxRetries - 1);
    assertThat(future).isNotDone();

    // Next retry should fail
    respondingPeer.respond(emptyResponder);
    assertThat(future).isDone();
    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(future::get).hasCauseInstanceOf(MaxRetriesReachedException.class);
  }

  @Test
  public void completesWhenPeerReturnsPartialResult()
      throws ExecutionException, InterruptedException {
    // Setup data to be requested and expected response

    // Setup a partially responsive peer
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    // Execute task and wait for response
    final T requestedData = generateDataToBeRequested();
    final EthTask<T> task = createTask(requestedData);
    final CompletableFuture<T> future = task.run();

    // Respond with partial data up until complete.
    respondingPeer.respond(
        RespondingEthPeer.partialResponder(
            blockchain,
            protocolContext.getWorldStateArchive(),
            transactionPool,
            protocolSchedule,
            0.25f));
    respondingPeer.respond(
        RespondingEthPeer.partialResponder(
            blockchain,
            protocolContext.getWorldStateArchive(),
            transactionPool,
            protocolSchedule,
            0.50f));
    respondingPeer.respond(
        RespondingEthPeer.partialResponder(
            blockchain,
            protocolContext.getWorldStateArchive(),
            transactionPool,
            protocolSchedule,
            0.75f));
    respondingPeer.respond(
        RespondingEthPeer.partialResponder(
            blockchain,
            protocolContext.getWorldStateArchive(),
            transactionPool,
            protocolSchedule,
            1.0f));

    assertThat(future.isDone()).isTrue();
    assertResultMatchesExpectation(requestedData, future.get(), respondingPeer.getEthPeer());
  }

  @Test
  public void doesNotCompleteWhenPeersAreUnavailable() {
    // Setup data to be requested
    final T requestedData = generateDataToBeRequested();

    final EthTask<T> task = createTask(requestedData);
    final CompletableFuture<T> future = task.run();

    assertThat(future.isDone()).isFalse();
  }

  @Test
  public void completesWhenPeersAreTemporarilyUnavailable()
      throws ExecutionException, InterruptedException {
    // Setup data to be requested
    final T requestedData = generateDataToBeRequested();

    // Execute task and wait for response
    final EthTask<T> task = createTask(requestedData);
    final CompletableFuture<T> future = task.run();

    assertThat(future.isDone()).isFalse();

    // Setup a peer
    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(
            blockchain, protocolContext.getWorldStateArchive(), transactionPool);
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);
    respondingPeer.respondWhile(responder, () -> !future.isDone());

    assertResultMatchesExpectation(requestedData, future.get(), respondingPeer.getEthPeer());
  }

  @Test
  public void completeWhenPeersTimeoutTemporarily()
      throws ExecutionException, InterruptedException {
    peerCountToTimeout.set(1);
    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(
            blockchain, protocolContext.getWorldStateArchive(), transactionPool);
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
  public void failsWhenPeersSendEmptyResponses() {
    // Setup a unresponsive peer
    final RespondingEthPeer.Responder responder = RespondingEthPeer.emptyResponder();
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    // Setup data to be requested
    final T requestedData = generateDataToBeRequested();

    // Setup and run task
    final EthTask<T> task = createTask(requestedData);
    final CompletableFuture<T> future = task.run();

    assertThat(future.isDone()).isFalse();

    // Respond max times - 1
    respondingPeer.respondTimes(responder, maxRetries - 1);
    assertThat(future).isNotDone();

    // Next retry should fail
    respondingPeer.respond(responder);
    assertThat(future).isDone();
    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get).hasCauseInstanceOf(MaxRetriesReachedException.class);
  }
}
