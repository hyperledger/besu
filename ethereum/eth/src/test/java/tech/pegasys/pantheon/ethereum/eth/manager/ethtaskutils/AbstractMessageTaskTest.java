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

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.BlockchainSetupUtil;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManagerTestUtil;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer.Responder;
import tech.pegasys.pantheon.ethereum.eth.manager.task.EthTask;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.plugin.services.MetricsSystem;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @param <T> The type of data being requested from the network
 * @param <R> The type of data returned from the network
 */
public abstract class AbstractMessageTaskTest<T, R> {
  protected static Blockchain blockchain;
  protected static ProtocolSchedule<Void> protocolSchedule;
  protected static ProtocolContext<Void> protocolContext;
  protected static MetricsSystem metricsSystem = new NoOpMetricsSystem();
  protected EthProtocolManager ethProtocolManager;
  protected EthContext ethContext;
  protected AtomicBoolean peersDoTimeout;
  protected AtomicInteger peerCountToTimeout;

  @BeforeClass
  public static void setup() {
    final BlockchainSetupUtil<Void> blockchainSetupUtil = BlockchainSetupUtil.forTesting();
    blockchainSetupUtil.importAllBlocks();
    blockchain = blockchainSetupUtil.getBlockchain();
    protocolSchedule = blockchainSetupUtil.getProtocolSchedule();
    protocolContext = blockchainSetupUtil.getProtocolContext();
    assert (blockchainSetupUtil.getMaxBlockNumber() >= 20L);
  }

  @Before
  public void setupTest() {
    peersDoTimeout = new AtomicBoolean(false);
    peerCountToTimeout = new AtomicInteger(0);
    ethProtocolManager =
        EthProtocolManagerTestUtil.create(
            blockchain,
            protocolContext.getWorldStateArchive(),
            () -> peerCountToTimeout.getAndDecrement() > 0 || peersDoTimeout.get());
    ethContext = ethProtocolManager.ethContext();
  }

  protected abstract T generateDataToBeRequested();

  protected abstract EthTask<R> createTask(T requestedData);

  protected abstract void assertResultMatchesExpectation(
      T requestedData, R response, EthPeer respondingPeer);

  @Test
  public void completesWhenPeersAreResponsive() {
    // Setup a responsive peer
    final Responder responder =
        RespondingEthPeer.blockchainResponder(blockchain, protocolContext.getWorldStateArchive());
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    // Setup data to be requested and expected response
    final T requestedData = generateDataToBeRequested();

    // Execute task and wait for response
    final AtomicReference<R> actualResult = new AtomicReference<>();
    final AtomicBoolean done = new AtomicBoolean(false);
    final EthTask<R> task = createTask(requestedData);
    final CompletableFuture<R> future = task.run();
    respondingPeer.respondWhile(responder, () -> !future.isDone());
    future.whenComplete(
        (result, error) -> {
          actualResult.set(result);
          done.compareAndSet(false, true);
        });

    assertThat(done).isTrue();
    assertResultMatchesExpectation(requestedData, actualResult.get(), respondingPeer.getEthPeer());
  }

  @Test
  public void doesNotCompleteWhenPeersDoNotRespond() {
    // Setup a unresponsive peer
    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    // Setup data to be requested
    final T requestedData = generateDataToBeRequested();

    // Execute task and wait for response
    final AtomicBoolean done = new AtomicBoolean(false);
    final EthTask<R> task = createTask(requestedData);
    final CompletableFuture<R> future = task.run();
    future.whenComplete(
        (response, error) -> {
          done.compareAndSet(false, true);
        });
    assertThat(done).isFalse();
  }

  @Test
  public void cancel() {
    // Setup a unresponsive peer
    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    // Setup data to be requested
    final T requestedData = generateDataToBeRequested();

    // Execute task
    final EthTask<R> task = createTask(requestedData);
    final CompletableFuture<R> future = task.run();

    assertThat(future.isDone()).isFalse();
    task.cancel();
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCancelled()).isTrue();
    assertThat(task.run().isCancelled()).isTrue();
  }
}
