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
package org.hyperledger.besu.ethereum.eth.sync.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.manager.ethtaskutils.RetryingMessageTaskTest;
import org.hyperledger.besu.ethereum.eth.manager.exceptions.MaxRetriesReachedException;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetBodiesFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractPeerTask.PeerTaskResult;
import org.hyperledger.besu.ethereum.eth.manager.task.EthTask;
import org.hyperledger.besu.ethereum.eth.messages.BlockHeadersMessage;
import org.hyperledger.besu.ethereum.eth.messages.EthPV62;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.ValidationPolicy;
import org.hyperledger.besu.ethereum.eth.sync.tasks.exceptions.InvalidBlockException;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class DownloadHeaderSequenceTaskTest extends RetryingMessageTaskTest<List<BlockHeader>> {

  private final ValidationPolicy validationPolicy = () -> HeaderValidationMode.DETACHED_ONLY;

  @Override
  protected List<BlockHeader> generateDataToBeRequested() {
    final List<BlockHeader> requestedHeaders = new ArrayList<>();
    for (long i = 0; i < 3; i++) {
      final long blockNumber = 10 + i;
      final BlockHeader header = blockchain.getBlockHeader(blockNumber).get();
      requestedHeaders.add(header);
    }
    return requestedHeaders;
  }

  @Override
  protected EthTask<List<BlockHeader>> createTask(final List<BlockHeader> requestedData) {
    final BlockHeader lastHeader = requestedData.get(requestedData.size() - 1);
    final BlockHeader referenceHeader = blockchain.getBlockHeader(lastHeader.getNumber() + 1).get();
    return DownloadHeaderSequenceTask.endingAtHeader(
        protocolSchedule,
        protocolContext,
        ethContext,
        SynchronizerConfiguration.builder().isPeerTaskSystemEnabled(false).build(),
        referenceHeader,
        requestedData.size(),
        maxRetries,
        validationPolicy,
        metricsSystem);
  }

  @Test
  public void failsWhenPeerReturnsOnlyReferenceHeader() {
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    // Execute task and wait for response
    final BlockHeader referenceHeader = blockchain.getChainHeadHeader();
    final EthTask<List<BlockHeader>> task =
        DownloadHeaderSequenceTask.endingAtHeader(
            protocolSchedule,
            protocolContext,
            ethContext,
            SynchronizerConfiguration.builder().isPeerTaskSystemEnabled(false).build(),
            referenceHeader,
            10,
            maxRetries,
            validationPolicy,
            metricsSystem);
    final CompletableFuture<List<BlockHeader>> future = task.run();

    // Respond with only the reference header
    final RespondingEthPeer.Responder responder =
        (cap, message) ->
            Optional.of(BlockHeadersMessage.create(Collections.singletonList(referenceHeader)));
    respondingPeer.respondWhile(responder, () -> !future.isDone());

    assertThat(future.isDone()).isTrue();
    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(future::get).hasCauseInstanceOf(MaxRetriesReachedException.class);
    assertNoBadBlocks();
  }

  @Test
  public void failsWhenPeerReturnsOnlyReferenceHeaderUsingPeerTaskSystem() {
    RespondingEthPeer respondingEthPeer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    // Execute task and wait for response
    final BlockHeader referenceHeader = blockchain.getChainHeadHeader();
    Mockito.when(peerTaskExecutor.execute(Mockito.any(GetHeadersFromPeerTask.class)))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(referenceHeader)),
                PeerTaskExecutorResponseCode.SUCCESS,
                Optional.of(respondingEthPeer.getEthPeer())));
    final EthTask<List<BlockHeader>> task =
        DownloadHeaderSequenceTask.endingAtHeader(
            protocolSchedule,
            protocolContext,
            ethContext,
            SynchronizerConfiguration.builder().isPeerTaskSystemEnabled(true).build(),
            referenceHeader,
            10,
            maxRetries,
            validationPolicy,
            metricsSystem);
    final CompletableFuture<List<BlockHeader>> future = task.run();

    assertThat(future.isDone()).isTrue();
    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(future::get).hasCauseInstanceOf(MaxRetriesReachedException.class);
    assertNoBadBlocks();
  }

  @Test
  public void failsWhenPeerReturnsOnlySubsetOfHeaders() {
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    // Execute task and wait for response
    final BlockHeader referenceHeader = blockchain.getChainHeadHeader();
    final EthTask<List<BlockHeader>> task =
        DownloadHeaderSequenceTask.endingAtHeader(
            protocolSchedule,
            protocolContext,
            ethContext,
            SynchronizerConfiguration.builder().isPeerTaskSystemEnabled(false).build(),
            referenceHeader,
            10,
            maxRetries,
            validationPolicy,
            metricsSystem);
    final CompletableFuture<List<BlockHeader>> future = task.run();

    // Filter response to include only reference header and previous header
    final RespondingEthPeer.Responder fullResponder = getFullResponder();
    final RespondingEthPeer.Responder responder =
        (cap, message) -> {
          final Optional<MessageData> fullResponse = fullResponder.respond(cap, message);
          if (!fullResponse.isPresent() || message.getCode() != EthPV62.GET_BLOCK_HEADERS) {
            return fullResponse;
          }
          final BlockHeadersMessage headersMessage =
              BlockHeadersMessage.readFrom(fullResponse.get());
          // Filter for a subset of headers
          final List<BlockHeader> headerSubset =
              headersMessage.getHeaders(protocolSchedule).stream()
                  .filter(h -> h.getNumber() >= referenceHeader.getNumber() - 1L)
                  .collect(Collectors.toList());
          return Optional.of(BlockHeadersMessage.create(headerSubset));
        };
    respondingPeer.respondTimes(responder, 100);

    assertThat(future.isDone()).isTrue();
    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(future::get).hasCauseInstanceOf(MaxRetriesReachedException.class);
    assertNoBadBlocks();
  }

  @Test
  public void failsWhenPeerReturnsOnlySubsetOfHeadersUsingPeerTaskSystem() {
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    // Execute task and wait for response
    final BlockHeader referenceHeader = blockchain.getChainHeadHeader();
    Mockito.when(peerTaskExecutor.execute(Mockito.any(GetHeadersFromPeerTask.class)))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(
                    List.of(
                        referenceHeader,
                        blockchain.getBlockHeader(referenceHeader.getNumber() - 1).get())),
                PeerTaskExecutorResponseCode.SUCCESS,
                Optional.of(respondingPeer.getEthPeer())));

    final EthTask<List<BlockHeader>> task =
        DownloadHeaderSequenceTask.endingAtHeader(
            protocolSchedule,
            protocolContext,
            ethContext,
            SynchronizerConfiguration.builder().isPeerTaskSystemEnabled(true).build(),
            referenceHeader,
            10,
            maxRetries,
            validationPolicy,
            metricsSystem);
    final CompletableFuture<List<BlockHeader>> future = task.run();

    assertThat(future.isDone()).isTrue();
    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(future::get).hasCauseInstanceOf(MaxRetriesReachedException.class);
    assertNoBadBlocks();
  }

  @Test
  public void marksBadBlockWhenHeaderValidationFails() {
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);
    // Set up a chain with an invalid block
    final int blockCount = 5;
    final long startBlock = blockchain.getChainHeadBlockNumber() - blockCount;
    final List<Block> chain = getBlockSequence(startBlock, blockCount);
    final Block badBlock = chain.get(2);
    ProtocolSchedule protocolScheduleSpy = setupHeaderValidationToFail(badBlock.getHeader());

    // Execute the task
    final BlockHeader referenceHeader = chain.get(blockCount - 1).getHeader();
    final EthTask<List<BlockHeader>> task =
        DownloadHeaderSequenceTask.endingAtHeader(
            protocolScheduleSpy,
            protocolContext,
            ethContext,
            SynchronizerConfiguration.builder().isPeerTaskSystemEnabled(false).build(),
            referenceHeader,
            blockCount - 1, // The reference header is not included in this count
            maxRetries,
            validationPolicy,
            metricsSystem);
    final CompletableFuture<List<BlockHeader>> future = task.run();
    final RespondingEthPeer.Responder fullResponder = getFullResponder();
    respondingPeer.respondWhile(fullResponder, () -> !future.isDone());

    // Check that the future completed exceptionally
    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(future::get)
        .hasCauseInstanceOf(InvalidBlockException.class)
        .hasMessageContaining("Header failed validation");

    // Check bad blocks
    assertBadBlock(badBlock);
  }

  @Test
  public void marksBadBlockWhenHeaderValidationFailsUsingPeerTaskSystem() {
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);
    // Set up a chain with an invalid block
    final int blockCount = 5;
    final long startBlock = blockchain.getChainHeadBlockNumber() - blockCount;
    final List<Block> chain = getBlockSequence(startBlock, blockCount);
    final Block badBlock = chain.get(2);
    ProtocolSchedule protocolScheduleSpy = setupHeaderValidationToFail(badBlock.getHeader());

    Mockito.when(peerTaskExecutor.execute(Mockito.any(GetHeadersFromPeerTask.class)))
        .then(
            (invocationOnMock) -> {
              GetHeadersFromPeerTask task =
                  invocationOnMock.getArgument(0, GetHeadersFromPeerTask.class);
              List<BlockHeader> headers = new ArrayList<>();
              for (long i = task.getBlockNumber();
                  i > task.getBlockNumber() - task.getMaxHeaders() * (task.getSkip() + 1);
                  i -= task.getSkip() + 1) {
                Optional<BlockHeader> header = blockchain.getBlockHeader(i);
                if (header.isPresent()) {
                  headers.add(header.get());
                } else {
                  break;
                }
              }
              return new PeerTaskExecutorResult<List<BlockHeader>>(
                  Optional.of(headers),
                  PeerTaskExecutorResponseCode.SUCCESS,
                  Optional.of(respondingPeer.getEthPeer()));
            });

    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetBodiesFromPeerTask.class), Mockito.any(EthPeer.class)))
        .thenAnswer(
            (invocationOnMock) -> {
              GetBodiesFromPeerTask task =
                  invocationOnMock.getArgument(0, GetBodiesFromPeerTask.class);
              EthPeer peer = invocationOnMock.getArgument(1, EthPeer.class);
              List<Block> blocks =
                  task.getBlockHeaders().stream()
                      .map(
                          (blockHeader) ->
                              new Block(
                                  blockHeader,
                                  blockchain.getBlockBody(blockHeader.getBlockHash()).get()))
                      .toList();
              return new PeerTaskExecutorResult<List<Block>>(
                  Optional.of(blocks), PeerTaskExecutorResponseCode.SUCCESS, Optional.of(peer));
            });

    // Execute the task
    final BlockHeader referenceHeader = chain.get(blockCount - 1).getHeader();
    final EthTask<List<BlockHeader>> task =
        DownloadHeaderSequenceTask.endingAtHeader(
            protocolScheduleSpy,
            protocolContext,
            ethContext,
            SynchronizerConfiguration.builder().isPeerTaskSystemEnabled(true).build(),
            referenceHeader,
            blockCount - 1, // The reference header is not included in this count
            maxRetries,
            validationPolicy,
            metricsSystem);
    final CompletableFuture<List<BlockHeader>> future = task.run();

    final RespondingEthPeer.Responder fullResponder = getFullResponder();
    respondingPeer.respondWhile(fullResponder, () -> !future.isDone());

    // Check that the future completed exceptionally
    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(future::get)
        .hasCauseInstanceOf(InvalidBlockException.class)
        .hasMessageContaining("Header failed validation");

    // Check bad blocks
    assertBadBlock(badBlock);
  }

  @Test
  public void processHeaders_markBadBlockWhenHeaderValidationFails() {
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);
    // Set up a chain with an invalid block
    final int blockCount = 5;
    final long startBlock = blockchain.getChainHeadBlockNumber() - blockCount;
    final List<Block> chain = getBlockSequence(startBlock, blockCount);
    final Block badBlock = chain.get(2);
    ProtocolSchedule protocolScheduleSpy = setupHeaderValidationToFail(badBlock.getHeader());

    // Execute the task
    final BlockHeader referenceHeader = chain.get(blockCount - 1).getHeader();
    final DownloadHeaderSequenceTask task =
        DownloadHeaderSequenceTask.endingAtHeader(
            protocolScheduleSpy,
            protocolContext,
            ethContext,
            SynchronizerConfiguration.builder().isPeerTaskSystemEnabled(false).build(),
            referenceHeader,
            blockCount - 1, // The reference header is not included in this count
            maxRetries,
            validationPolicy,
            metricsSystem);

    // Run
    final List<BlockHeader> responseHeaders =
        chain.stream()
            .map(Block::getHeader)
            .sorted(Comparator.comparing(BlockHeader::getNumber).reversed())
            .toList();
    PeerTaskResult<List<BlockHeader>> peerResponse =
        new PeerTaskResult<>(respondingPeer.getEthPeer(), responseHeaders);
    final CompletableFuture<List<BlockHeader>> future = task.processHeaders(peerResponse);
    respondingPeer.respondWhile(this.getFullResponder(), () -> !future.isDone());

    // Check that the future completed exceptionally
    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(future::get)
        .hasCauseInstanceOf(InvalidBlockException.class)
        .hasMessageContaining("Header failed validation");

    // Check bad blocks
    assertBadBlock(badBlock);
  }

  @Test
  public void processHeaders_markBadBlockHashWhenHeaderValidationFailsAndBodyUnavailable() {
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);
    // Set up a chain with an invalid block
    final int blockCount = 5;
    final long startBlock = blockchain.getChainHeadBlockNumber() - blockCount;
    final List<Block> chain = getBlockSequence(startBlock, blockCount);
    final Block badBlock = chain.get(2);
    ProtocolSchedule protocolScheduleSpy = setupHeaderValidationToFail(badBlock.getHeader());

    // Set up the task
    final BlockHeader referenceHeader = chain.get(blockCount - 1).getHeader();
    final DownloadHeaderSequenceTask task =
        DownloadHeaderSequenceTask.endingAtHeader(
            protocolScheduleSpy,
            protocolContext,
            ethContext,
            SynchronizerConfiguration.builder().isPeerTaskSystemEnabled(false).build(),
            referenceHeader,
            blockCount - 1, // The reference header is not included in this count
            maxRetries,
            validationPolicy,
            metricsSystem);

    // Run
    final List<BlockHeader> responseHeaders =
        chain.stream()
            .map(Block::getHeader)
            .sorted(Comparator.comparing(BlockHeader::getNumber).reversed())
            .toList();
    PeerTaskResult<List<BlockHeader>> peerResponse =
        new PeerTaskResult<>(respondingPeer.getEthPeer(), responseHeaders);
    final CompletableFuture<List<BlockHeader>> future = task.processHeaders(peerResponse);
    // Use empty responder so block body cannot be retrieved
    respondingPeer.respondWhile(RespondingEthPeer.emptyResponder(), () -> !future.isDone());

    // Check that the future completed exceptionally
    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(future::get)
        .hasCauseInstanceOf(InvalidBlockException.class)
        .hasMessageContaining("Header failed validation");

    // Check bad blocks
    assertBadHeader(badBlock.getHeader());
  }

  @Test
  public void processHeaders_doesNotMarkBadBlockForOutOfRangeResponse() {
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    // Set up the task
    final int blockCount = 3;
    final long startBlock = blockchain.getChainHeadBlockNumber() - blockCount;
    final List<Block> chain = getBlockSequence(startBlock, blockCount);
    final int segmentLength = blockCount - 1; // The reference header is not included in this count
    final BlockHeader referenceHeader = chain.get(blockCount - 1).getHeader();
    final DownloadHeaderSequenceTask task =
        DownloadHeaderSequenceTask.endingAtHeader(
            protocolSchedule,
            protocolContext,
            ethContext,
            SynchronizerConfiguration.builder().isPeerTaskSystemEnabled(false).build(),
            referenceHeader,
            segmentLength,
            maxRetries,
            validationPolicy,
            metricsSystem);

    // Run
    final Block outOfRangeBlock = blockchain.getBlockByNumber(startBlock - 1).get();
    final List<BlockHeader> headerResponse =
        chain.stream()
            .map(Block::getHeader)
            .sorted(Comparator.comparing(BlockHeader::getNumber).reversed())
            .collect(Collectors.toCollection(ArrayList::new));
    headerResponse.add(outOfRangeBlock.getHeader());

    PeerTaskResult<List<BlockHeader>> peerResponse =
        new PeerTaskResult<>(respondingPeer.getEthPeer(), headerResponse);
    final CompletableFuture<List<BlockHeader>> future = task.processHeaders(peerResponse);

    // Check that the future completed exceptionally
    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(future::get)
        .hasCauseInstanceOf(InvalidBlockException.class)
        .hasMessageNotContaining("Header failed validation")
        .hasMessageContaining("Out-of-range");

    // Check bad blocks
    assertNoBadBlocks();
  }

  @Test
  public void processHeaders_doesNotMarkBadBlockForMisorderedBlocks() {
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    // Set up the task
    final int blockCount = 5;
    final long startBlock = blockchain.getChainHeadBlockNumber() - blockCount;
    final List<Block> chain = getBlockSequence(startBlock, blockCount);
    final int segmentLength = blockCount - 1; // The reference header is not included in this count
    final BlockHeader referenceHeader = blockchain.getChainHeadHeader();
    final DownloadHeaderSequenceTask task =
        DownloadHeaderSequenceTask.endingAtHeader(
            protocolSchedule,
            protocolContext,
            ethContext,
            SynchronizerConfiguration.builder().isPeerTaskSystemEnabled(false).build(),
            referenceHeader,
            segmentLength,
            maxRetries,
            validationPolicy,
            metricsSystem);

    // Return blocks in ascending order
    final List<BlockHeader> responseHeaders = chain.stream().map(Block::getHeader).toList();
    PeerTaskResult<List<BlockHeader>> peerResponse =
        new PeerTaskResult<>(respondingPeer.getEthPeer(), responseHeaders);
    final CompletableFuture<List<BlockHeader>> future = task.processHeaders(peerResponse);

    // Check that the future completed exceptionally
    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(future::get)
        .hasCauseInstanceOf(InvalidBlockException.class)
        .hasMessageNotContaining("Header failed validation")
        .hasMessageContaining("misordered blocks");

    // Check bad blocks
    assertNoBadBlocks();
  }

  private List<Block> getBlockSequence(final long firstBlockNumber, final int blockCount) {
    final List<Block> blocks = new ArrayList<>(blockCount);
    for (int i = 0; i < blockCount; i++) {
      final Block block = blockchain.getBlockByNumber(firstBlockNumber + i).get();
      blocks.add(block);
    }

    return blocks;
  }

  private ProtocolSchedule setupHeaderValidationToFail(final BlockHeader badHeader) {
    ProtocolSchedule protocolScheduleSpy = spy(protocolSchedule);
    ProtocolSpec failingProtocolSpec = spy(protocolSchedule.getByBlockHeader(badHeader));
    BlockHeaderValidator failingValidator = mock(BlockHeaderValidator.class);
    when(failingValidator.validateHeader(eq(badHeader), any(), any(), any())).thenReturn(false);
    when(failingProtocolSpec.getBlockHeaderValidator()).thenReturn(failingValidator);
    doAnswer(
            invocation -> {
              BlockHeader header = invocation.getArgument(0);
              if (header.getNumber() == badHeader.getNumber()) {
                return failingProtocolSpec;
              } else {
                return invocation.callRealMethod();
              }
            })
        .when(protocolScheduleSpy)
        .getByBlockHeader(any(BlockHeader.class));

    return protocolScheduleSpy;
  }

  private void assertBadBlock(final Block badBlock) {
    BadBlockManager badBlockManager = protocolContext.getBadBlockManager();
    assertThat(badBlockManager.getBadBlocks()).containsExactly(badBlock);
    assertThat(badBlockManager.getBadHeaders()).isEmpty();
  }

  private void assertBadHeader(final BlockHeader badHeader) {
    BadBlockManager badBlockManager = protocolContext.getBadBlockManager();
    assertThat(badBlockManager.getBadHeaders()).containsExactly(badHeader);
    assertThat(badBlockManager.getBadBlocks()).isEmpty();
  }
}
