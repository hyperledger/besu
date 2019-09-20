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

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.manager.ethtaskutils.RetryingMessageTaskTest;
import org.hyperledger.besu.ethereum.eth.manager.exceptions.MaxRetriesReachedException;
import org.hyperledger.besu.ethereum.eth.manager.task.EthTask;
import org.hyperledger.besu.ethereum.eth.messages.BlockHeadersMessage;
import org.hyperledger.besu.ethereum.eth.messages.EthPV62;
import org.hyperledger.besu.ethereum.eth.sync.ValidationPolicy;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.junit.Test;

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
            referenceHeader,
            10,
            maxRetries,
            validationPolicy,
            metricsSystem);
    final CompletableFuture<List<BlockHeader>> future = task.run();

    // Filter response to include only reference header and previous header
    final RespondingEthPeer.Responder fullResponder =
        RespondingEthPeer.blockchainResponder(blockchain);
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
  }
}
