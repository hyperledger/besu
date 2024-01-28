/*
 * Copyright contributors to Hyperledger Besu
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

import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.manager.exceptions.MaxRetriesReachedException;
import org.hyperledger.besu.ethereum.eth.manager.task.EthTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;

/**
 * Tests ethTasks that request data from the network, and retry until all of the data is received.
 *
 * @param <T> The type of data being requested from the network
 */
public abstract class RetryingSwitchingPeerMessageTaskTest<T> extends RetryingMessageTaskTest<T> {

  @Override
  protected EthTask<T> createTask(final T requestedData) {
    return createTask(requestedData, getMaxRetries());
  }

  @Override
  protected int getMaxRetries() {
    return ethPeers.peerCount();
  }

  @Test
  public void completesWhenBestPeerEmptyAndSecondPeerIsResponsive()
      throws ExecutionException, InterruptedException {
    // Setup first unresponsive peer
    final RespondingEthPeer firstPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 10);

    // Setup second responsive peer
    final RespondingEthPeer secondPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 9);

    // Execute task and wait for response
    final T requestedData = generateDataToBeRequested();
    final EthTask<T> task = createTask(requestedData);
    final CompletableFuture<T> future = task.run();

    // First peer is not responsive
    firstPeer.respond(RespondingEthPeer.emptyResponder());
    // Second peer is responsive
    secondPeer.respondTimes(
        RespondingEthPeer.blockchainResponder(
            blockchain, protocolContext.getWorldStateArchive(), transactionPool),
        2);

    assertThat(future.isDone()).isTrue();
    assertResultMatchesExpectation(requestedData, future.get(), secondPeer.getEthPeer());
  }

  @Test
  public void completesWhenBestPeerTimeoutsAndSecondPeerIsResponsive()
      throws ExecutionException, InterruptedException {
    // Setup first unresponsive peer
    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 10);

    // Setup second responsive peer
    final RespondingEthPeer secondPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 9);

    // First peer timeouts
    peerCountToTimeout.set(1);

    // Execute task and wait for response
    final T requestedData = generateDataToBeRequested();
    final EthTask<T> task = createTask(requestedData);
    final CompletableFuture<T> future = task.run();

    // Second peer is responsive
    secondPeer.respondTimes(
        RespondingEthPeer.blockchainResponder(
            blockchain, protocolContext.getWorldStateArchive(), transactionPool),
        2);

    assertThat(future.isDone()).isTrue();
    assertResultMatchesExpectation(requestedData, future.get(), secondPeer.getEthPeer());
  }

  @Test
  public void failsWhenAllPeersFail() {
    // Setup first unresponsive peer
    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 10);

    // Setup second unresponsive peer
    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 9);

    // all peers timeout
    peerCountToTimeout.set(2);

    // Execute task and wait for response
    final T requestedData = generateDataToBeRequested();
    final EthTask<T> task = createTask(requestedData);
    final CompletableFuture<T> future = task.run();

    assertThat(future.isDone()).isTrue();
    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(future::get).hasCauseInstanceOf(MaxRetriesReachedException.class);
  }

  @Test
  public void disconnectAPeerWhenAllPeersTried() {
    final int numPeers = MAX_PEERS;
    final List<RespondingEthPeer> respondingPeers = new ArrayList<>(numPeers);
    for (int i = 0; i < numPeers; i++) {
      respondingPeers.add(EthProtocolManagerTestUtil.createPeer(ethProtocolManager, numPeers - i));
    }

    // all peers timeout
    peerCountToTimeout.set(numPeers);

    // Execute task and wait for response
    final T requestedData = generateDataToBeRequested();
    final EthTask<T> task = createTask(requestedData, MAX_PEERS + 1);
    task.run();

    assertThat(respondingPeers.get(numPeers - 1).getEthPeer().isDisconnected()).isTrue();
  }

  @Test
  @Override
  public void failsWhenPeersSendEmptyResponses() {
    // Setup unresponsive peers
    final RespondingEthPeer.Responder responder = RespondingEthPeer.emptyResponder();
    final RespondingEthPeer respondingPeer1 =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 2);
    final RespondingEthPeer respondingPeer2 =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1);

    // Setup data to be requested
    final T requestedData = generateDataToBeRequested();

    // Setup and run task
    final EthTask<T> task = createTask(requestedData);
    final CompletableFuture<T> future = task.run();

    assertThat(future.isDone()).isFalse();

    // Respond max times - 1
    respondingPeer1.respondTimes(responder, getMaxRetries() - 1);
    assertThat(future).isNotDone();

    // Next retry should fail
    respondingPeer2.respond(responder);
    assertThat(future).isDone();
    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get).hasCauseInstanceOf(MaxRetriesReachedException.class);
  }

  @Test
  @Override
  public void completesWhenPeersAreTemporarilyUnavailable()
      throws ExecutionException, InterruptedException {
    // Setup data to be requested
    final T requestedData = generateDataToBeRequested();

    // Execute task and wait for response
    final EthTask<T> task = createTask(requestedData, 2);
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
}
