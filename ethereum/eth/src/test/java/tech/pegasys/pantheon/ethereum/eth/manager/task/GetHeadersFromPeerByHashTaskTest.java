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
package tech.pegasys.pantheon.ethereum.eth.manager.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static tech.pegasys.pantheon.ethereum.vm.TestBlockchain.generateTestBlockHash;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManagerTestUtil;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer.Responder;
import tech.pegasys.pantheon.ethereum.eth.manager.ethtaskutils.PeerMessageTaskTest;
import tech.pegasys.pantheon.ethereum.eth.manager.task.AbstractPeerTask.PeerTaskResult;
import tech.pegasys.pantheon.ethereum.eth.messages.BlockHeadersMessage;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.messages.DisconnectMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GetHeadersFromPeerByHashTaskTest extends PeerMessageTaskTest<List<BlockHeader>> {
  @Mock private EthPeer peerMock;

  @Override
  protected void assertPartialResultMatchesExpectation(
      final List<BlockHeader> requestedData, final List<BlockHeader> partialResponse) {
    assertThat(partialResponse.size()).isLessThanOrEqualTo(requestedData.size());
    assertThat(partialResponse.size()).isGreaterThan(0);
    for (final BlockHeader header : partialResponse) {
      assertThat(requestedData).contains(header);
    }
  }

  @Override
  protected List<BlockHeader> generateDataToBeRequested() {
    final int count = 3;
    final List<BlockHeader> requestedHeaders = new ArrayList<>(count);
    for (long i = 0; i < count; i++) {
      requestedHeaders.add(blockchain.getBlockHeader(5 + i).get());
    }
    return requestedHeaders;
  }

  @Override
  protected EthTask<PeerTaskResult<List<BlockHeader>>> createTask(
      final List<BlockHeader> requestedData) {
    final BlockHeader firstHeader = requestedData.get(0);
    return GetHeadersFromPeerByHashTask.startingAtHash(
        protocolSchedule,
        ethContext,
        firstHeader.getHash(),
        firstHeader.getNumber(),
        requestedData.size(),
        metricsSystem);
  }

  @Test
  public void getHeadersFromHashNoSkip() {
    getHeadersFromHash(0, false);
  }

  @Test
  public void getHeadersFromHashNoSkipReversed() {
    getHeadersFromHash(0, true);
  }

  @Test
  public void getHeadersFromHashWithSkip() {
    getHeadersFromHash(2, false);
  }

  @Test
  public void getHeadersFromHashWithSkipReversed() {
    getHeadersFromHash(2, true);
  }

  private void getHeadersFromHash(final int skip, final boolean reverse) {
    // Setup a responsive peer
    final Responder responder = RespondingEthPeer.blockchainResponder(blockchain);
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    // Set up parameters and calculated expected response
    final long startNumber = reverse ? blockchain.getChainHeadBlockNumber() - 2 : 2;
    final int delta = (skip + 1) * (reverse ? -1 : 1);
    final int count = 4;
    final List<BlockHeader> expectedHeaders = new ArrayList<>(count);
    for (long i = 0; i < count; i++) {
      expectedHeaders.add(blockchain.getBlockHeader(startNumber + delta * i).get());
    }

    // Execute task and wait for response
    final AbstractGetHeadersFromPeerTask task =
        new GetHeadersFromPeerByHashTask(
            protocolSchedule,
            ethContext,
            blockchain.getBlockHashByNumber(startNumber).get(),
            startNumber,
            count,
            skip,
            reverse,
            metricsSystem);
    final AtomicReference<PeerTaskResult<List<BlockHeader>>> actualResult = new AtomicReference<>();
    final AtomicBoolean done = new AtomicBoolean(false);
    final CompletableFuture<PeerTaskResult<List<BlockHeader>>> future = task.run();
    respondingPeer.respondWhile(responder, () -> !future.isDone());
    future.whenComplete(
        (result, error) -> {
          actualResult.set(result);
          done.compareAndSet(false, true);
        });

    assertThat(done).isTrue();
    assertThat(actualResult.get().getPeer()).isEqualTo(respondingPeer.getEthPeer());
    assertThat(actualResult.get().getResult()).isEqualTo(expectedHeaders);
  }

  @Test
  public void checkThatSequentialHeadersFormingAChainWorks() {
    final int startNumber = 1;

    final BlockHeader block1 =
        new BlockHeaderTestFixture().number(1).parentHash(generateTestBlockHash(0)).buildHeader();
    final BlockHeader block2 =
        new BlockHeaderTestFixture().number(2).parentHash(block1.getHash()).buildHeader();
    final List<BlockHeader> headers = Arrays.asList(block1, block2);

    final AbstractGetHeadersFromPeerTask task =
        new GetHeadersFromPeerByHashTask(
            protocolSchedule,
            ethContext,
            block1.getHash(),
            startNumber,
            2,
            0,
            false,
            metricsSystem);
    Optional<List<BlockHeader>> optionalBlockHeaders =
        task.processResponse(false, BlockHeadersMessage.create(headers), peerMock);
    assertThat(optionalBlockHeaders).isNotNull();
    assertThat(optionalBlockHeaders).isPresent();
    List<BlockHeader> blockHeaders = optionalBlockHeaders.get();
    MatcherAssert.assertThat(blockHeaders, hasSize(2));
    verify(peerMock, times(0)).disconnect(any());
  }

  @Test
  public void checkThatSequentialHeadersNotFormingAChainFails() {
    final int startNumber = 1;

    final BlockHeader block1 =
        new BlockHeaderTestFixture().number(1).parentHash(generateTestBlockHash(0)).buildHeader();
    final BlockHeader block2 =
        new BlockHeaderTestFixture().number(2).parentHash(generateTestBlockHash(1)).buildHeader();
    final List<BlockHeader> headers = Arrays.asList(block1, block2);
    final AbstractGetHeadersFromPeerTask task =
        new GetHeadersFromPeerByHashTask(
            protocolSchedule,
            ethContext,
            block1.getHash(),
            startNumber,
            2,
            0,
            false,
            metricsSystem);
    Optional<List<BlockHeader>> optionalBlockHeaders =
        task.processResponse(false, BlockHeadersMessage.create(headers), peerMock);
    assertThat(optionalBlockHeaders).isNotNull();
    assertThat(optionalBlockHeaders).isEmpty();
    verify(peerMock).disconnect(DisconnectMessage.DisconnectReason.BREACH_OF_PROTOCOL);
  }
}
