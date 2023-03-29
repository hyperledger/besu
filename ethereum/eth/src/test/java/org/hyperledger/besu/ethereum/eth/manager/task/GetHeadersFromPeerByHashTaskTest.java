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
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hyperledger.besu.ethereum.referencetests.ReferenceTestBlockchain.generateTestBlockHash;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.DataGas;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.manager.ChainState;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.MockPeerConnection;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer.Responder;
import org.hyperledger.besu.ethereum.eth.manager.ethtaskutils.PeerMessageTaskTest;
import org.hyperledger.besu.ethereum.eth.messages.BlockHeadersMessage;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GetHeadersFromPeerByHashTaskTest extends PeerMessageTaskTest<List<BlockHeader>> {

  public final int DEFAULT_COUNT = 3;

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
    final List<BlockHeader> requestedHeaders = new ArrayList<>(DEFAULT_COUNT);
    for (long i = 0; i < DEFAULT_COUNT; i++) {
      requestedHeaders.add(blockchain.getBlockHeader(5 + i).get());
    }
    return requestedHeaders;
  }

  @Override
  protected EthTask<AbstractPeerTask.PeerTaskResult<List<BlockHeader>>> createTask(
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

  @Test
  public void completesWhenPeersSendEmptyResponsesAndReducesReputation() {
    // Setup a unresponsive peer
    final Responder responder = RespondingEthPeer.emptyResponder();
    final RespondingEthPeer respondingEthPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    // Setup data to be requested
    final List<BlockHeader> requestedData = generateDataToBeRequested();

    // Execute task and wait for response
    final EthTask<AbstractPeerTask.PeerTaskResult<List<BlockHeader>>> task =
        createTask(requestedData);
    final CompletableFuture<AbstractPeerTask.PeerTaskResult<List<BlockHeader>>> future = task.run();
    respondingEthPeer.respond(responder);
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCompletedExceptionally()).isFalse();
    assertThat(respondingEthPeer.getEthPeer().getReputation().getScore()).isEqualTo(99);
  }

  @Test
  public void completesWhenPeerSendsTooManyHeadersAndReducesReputation() {
    // Setup a peer returning too many headers
    final RespondingEthPeer respondingEthPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    // Setup data to be requested
    final List<BlockHeader> requestedData =
        generateDataToBeRequested(); // request DEFAULT_COUNT headers

    // Execute task and wait for response
    final EthTask<AbstractPeerTask.PeerTaskResult<List<BlockHeader>>> task =
        createTask(requestedData);
    final CompletableFuture<AbstractPeerTask.PeerTaskResult<List<BlockHeader>>> future = task.run();
    respondingEthPeer.respond(
        (c, m) -> Optional.of(BlockHeadersMessage.create(dummyBHList(DEFAULT_COUNT + 1))));
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCompletedExceptionally()).isFalse();
    assertThat(respondingEthPeer.getEthPeer().getReputation().getScore()).isEqualTo(99);
  }

  @Test
  public void completesWhenPeerSendsWrongFirstHeaderAndReducesReputation() {
    // Setup a peer returning result where the first header has the wrong number
    final RespondingEthPeer respondingEthPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    // Setup data to be requested
    final List<BlockHeader> requestedData =
        generateDataToBeRequested(); // request DEFAULT_COUNT headers

    // Execute task and wait for response
    final EthTask<AbstractPeerTask.PeerTaskResult<List<BlockHeader>>> task =
        createTask(requestedData);
    final CompletableFuture<AbstractPeerTask.PeerTaskResult<List<BlockHeader>>> future = task.run();
    respondingEthPeer.respond(
        (c, m) -> Optional.of(BlockHeadersMessage.create(dummyBHList(DEFAULT_COUNT))));
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCompletedExceptionally()).isFalse();
    assertThat(respondingEthPeer.getEthPeer().getReputation().getScore()).isEqualTo(99);
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
    final AtomicReference<AbstractPeerTask.PeerTaskResult<List<BlockHeader>>> actualResult =
        new AtomicReference<>();
    final AtomicBoolean done = new AtomicBoolean(false);
    final CompletableFuture<AbstractPeerTask.PeerTaskResult<List<BlockHeader>>> future = task.run();
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

    final EthPeer peer = createPeer();

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
    final Optional<List<BlockHeader>> optionalBlockHeaders =
        task.processResponse(false, BlockHeadersMessage.create(headers), peer);
    assertThat(optionalBlockHeaders).isNotNull();
    assertThat(optionalBlockHeaders).isPresent();
    final List<BlockHeader> blockHeaders = optionalBlockHeaders.get();
    MatcherAssert.assertThat(blockHeaders, hasSize(2));
    assertThat(peer.chainState().getEstimatedHeight()).isEqualTo(2);
    assertThat(peer.isDisconnected()).isFalse();
  }

  @Test
  public void checkThatSequentialHeadersNotFormingAChainFails() {
    final int startNumber = 1;
    final BlockHeader block1 =
        new BlockHeaderTestFixture().number(1).parentHash(generateTestBlockHash(0)).buildHeader();
    final BlockHeader block2 =
        new BlockHeaderTestFixture().number(2).parentHash(generateTestBlockHash(1)).buildHeader();
    final List<BlockHeader> headers = Arrays.asList(block1, block2);

    final EthPeer peer = createPeer();

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
    final Optional<List<BlockHeader>> optionalBlockHeaders =
        task.processResponse(false, BlockHeadersMessage.create(headers), peer);
    assertThat(optionalBlockHeaders).isNotNull();
    assertThat(optionalBlockHeaders.get()).isEmpty();
    assertThat(peer.isDisconnected()).isTrue();
    assertThat(((MockPeerConnection) peer.getConnection()).getDisconnectReason().get())
        .isEqualTo(DisconnectMessage.DisconnectReason.BREACH_OF_PROTOCOL);
  }

  @Test
  public void checkThatStreamClosedResponseCallsPeerRecordUselessResponse() {
    final EthPeer peer = mock(EthPeer.class);
    when(peer.nodeId()).thenReturn(Bytes.EMPTY);
    final AbstractGetHeadersFromPeerTask task =
        new GetHeadersFromPeerByHashTask(
            protocolSchedule, ethContext, Hash.ZERO, 0, 2, 0, false, metricsSystem);
    task.processResponse(true, BlockHeadersMessage.create(Collections.emptyList()), peer);
    Mockito.verify(peer, Mockito.times(1))
        .recordUselessResponse("Stream closed without useful response 0x");
  }

  @Test
  public void checkThatEmptyResponseCallsPeerRecordUselessResponse() {
    final EthPeer peer = mock(EthPeer.class);
    when(peer.nodeId()).thenReturn(Bytes.EMPTY);
    final AbstractGetHeadersFromPeerTask task =
        new GetHeadersFromPeerByHashTask(
            protocolSchedule, ethContext, Hash.ZERO, 0, 2, 0, false, metricsSystem);
    task.processResponse(false, BlockHeadersMessage.create(Collections.emptyList()), peer);
    Mockito.verify(peer, Mockito.times(1)).recordUselessResponse("No headers returned by peer 0x");
  }

  @Test
  public void checkThatTooManyHeadersInResponseCallsPeerRecordUselessResponse() {
    final EthPeer peer = mock(EthPeer.class);
    when(peer.nodeId()).thenReturn(Bytes.EMPTY);
    final AbstractGetHeadersFromPeerTask task =
        new GetHeadersFromPeerByHashTask(
            protocolSchedule, ethContext, Hash.ZERO, 0, 1, 0, false, metricsSystem);
    final BlockHeader block1 =
        new BlockHeaderTestFixture().number(1).parentHash(generateTestBlockHash(1)).buildHeader();
    final BlockHeader block2 =
        new BlockHeaderTestFixture().number(2).parentHash(generateTestBlockHash(2)).buildHeader();
    final List<BlockHeader> headers = Arrays.asList(block1, block2);
    task.processResponse(false, BlockHeadersMessage.create(headers), peer);
    Mockito.verify(peer, Mockito.times(1))
        .recordUselessResponse("Too many headers returned by peer 0x");
  }

  @Test
  public void checkThatFirstHeaderNotMatchingResponseCallsPeerRecordUselessResponse() {
    final EthPeer peer = mock(EthPeer.class);
    when(peer.nodeId()).thenReturn(Bytes.EMPTY);
    final AbstractGetHeadersFromPeerTask task =
        new GetHeadersFromPeerByHashTask(
            protocolSchedule, ethContext, Hash.ZERO, 0, 2, 0, false, metricsSystem);
    final BlockHeader block1 =
        new BlockHeaderTestFixture()
            .number(1)
            .parentHash(
                Hash.fromHexString(
                    "0x0102030405060708091011121314151617181920212223242526272829303132"))
            .buildHeader();
    final BlockHeader block2 =
        new BlockHeaderTestFixture().number(2).parentHash(generateTestBlockHash(2)).buildHeader();
    final List<BlockHeader> headers = Arrays.asList(block1, block2);
    task.processResponse(false, BlockHeadersMessage.create(headers), peer);
    Mockito.verify(peer, Mockito.times(1))
        .recordUselessResponse("First header returned by peer not matching 0x");
  }

  @Test
  public void checkThatWrongNumberResponseCallsPeerRecordUselessResponse() {
    final EthPeer peer = mock(EthPeer.class);
    final ChainState chainState = mock(ChainState.class);
    when(peer.nodeId()).thenReturn(Bytes.EMPTY);
    when(peer.chainState()).thenReturn(chainState);
    final BlockHeader block1 = new BlockHeaderTestFixture().number(0).buildHeader();
    final AbstractGetHeadersFromPeerTask task =
        new GetHeadersFromPeerByHashTask(
            protocolSchedule, ethContext, block1.getHash(), 0, 2, 0, false, metricsSystem);
    final BlockHeader block2 =
        new BlockHeaderTestFixture().number(2).parentHash(generateTestBlockHash(3)).buildHeader();
    final List<BlockHeader> headers = Arrays.asList(block1, block2);
    task.processResponse(false, BlockHeadersMessage.create(headers), peer);
    Mockito.verify(peer, Mockito.times(1))
        .recordUselessResponse("Header returned by peer does not have expected number 0x");
  }

  private List<BlockHeader> dummyBHList(final int count) {
    final ArrayList<BlockHeader> blockHeaders = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      blockHeaders.add(
          new BlockHeader(
              Hash.ZERO,
              Hash.ZERO,
              Address.ZERO,
              Hash.ZERO,
              Hash.ZERO,
              Hash.ZERO,
              new LogsBloomFilter(),
              Difficulty.ZERO,
              133,
              0,
              0,
              0,
              Bytes.fromHexString("abcd"),
              Wei.ZERO,
              Bytes32.ZERO,
              0,
              Hash.ZERO,
              DataGas.fromHexString("0x1234"),
              Hash.ZERO,
              new MainnetBlockHeaderFunctions()));
    }
    return blockHeaders;
  }
}
