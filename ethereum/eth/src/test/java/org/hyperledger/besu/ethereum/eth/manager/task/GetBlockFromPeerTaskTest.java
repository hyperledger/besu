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

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.manager.ethtaskutils.AbstractMessageTaskTest;
import org.hyperledger.besu.ethereum.eth.manager.exceptions.EthTaskException;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.util.ExceptionUtils;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class GetBlockFromPeerTaskTest
    extends AbstractMessageTaskTest<Block, AbstractPeerTask.PeerTaskResult<Block>> {

  private static final int BLOCK_NUMBER = 5;

  @Override
  protected Block generateDataToBeRequested() {
    final BlockHeader header = blockchain.getBlockHeader(BLOCK_NUMBER).get();
    final BlockBody body = blockchain.getBlockBody(header.getHash()).get();
    return new Block(header, body);
  }

  @Override
  protected EthTask<AbstractPeerTask.PeerTaskResult<Block>> createTask(final Block requestedData) {
    peerTaskExecutor = Mockito.mock(PeerTaskExecutor.class);
    return GetBlockFromPeerTask.create(
        protocolSchedule,
        ethContext,
        SynchronizerConfiguration.builder().build(),
        Optional.of(requestedData.getHash()),
        BLOCK_NUMBER,
        metricsSystem);
  }

  @Override
  protected void assertResultMatchesExpectation(
      final Block requestedData,
      final AbstractPeerTask.PeerTaskResult<Block> response,
      final EthPeer respondingPeer) {
    assertThat(response.getResult()).isEqualTo(requestedData);
    assertThat(response.getPeer()).isEqualTo(respondingPeer);
  }

  @Test
  public void failsWhenNoPeersAreAvailable() throws ExecutionException, InterruptedException {
    // Setup data to be requested
    final Block requestedData = generateDataToBeRequested();

    // Execute task
    final EthTask<AbstractPeerTask.PeerTaskResult<Block>> task = createTask(requestedData);
    final CompletableFuture<AbstractPeerTask.PeerTaskResult<Block>> future = task.run();
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
    assertThat(ethException.reason()).isEqualTo(EthTaskException.FailureReason.NO_AVAILABLE_PEERS);

    assertThat(task.run().isCompletedExceptionally()).isTrue();
    task.cancel();
    assertThat(task.run().isCompletedExceptionally()).isTrue();
  }

  @Test
  public void failsWhenPeersSendEmptyResponses() throws ExecutionException, InterruptedException {
    // Setup a unresponsive peer
    final RespondingEthPeer.Responder responder = RespondingEthPeer.emptyResponder();
    final RespondingEthPeer respondingEthPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    // Setup data to be requested
    final Block requestedData = generateDataToBeRequested();

    // Execute task and wait for response
    final EthTask<AbstractPeerTask.PeerTaskResult<Block>> task = createTask(requestedData);
    final CompletableFuture<AbstractPeerTask.PeerTaskResult<Block>> future = task.run();
    respondingEthPeer.respondWhile(responder, () -> !future.isDone());
    final AtomicReference<Throwable> failure = new AtomicReference<>();
    future.whenComplete(
        (response, error) -> {
          failure.set(error);
        });

    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThat(failure.get()).isNotNull();
    // Check wrapped failure
    final Throwable error = ExceptionUtils.rootCause(failure.get());
    assertThat(error).isInstanceOf(EthTaskException.class);
    assertThat(((EthTaskException) error).reason())
        .isEqualTo(EthTaskException.FailureReason.INCOMPLETE_RESULTS);

    assertThat(task.run().isCompletedExceptionally()).isTrue();
    task.cancel();
    assertThat(task.run().isCompletedExceptionally()).isTrue();
  }
}
