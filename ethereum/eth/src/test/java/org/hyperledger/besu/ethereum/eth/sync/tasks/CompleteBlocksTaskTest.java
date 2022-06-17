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

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.manager.ethtaskutils.RetryingMessageTaskTest;
import org.hyperledger.besu.ethereum.eth.manager.exceptions.MaxRetriesReachedException;
import org.hyperledger.besu.ethereum.eth.manager.task.EthTask;
import org.hyperledger.besu.ethereum.eth.messages.GetBlockBodiesMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.junit.Test;

public class CompleteBlocksTaskTest extends RetryingMessageTaskTest<List<Block>> {

  @Override
  protected List<Block> generateDataToBeRequested() {
    return generateDataToBeRequested(3);
  }

  protected List<Block> generateDataToBeRequested(final int nbBlock) {
    // Setup data to be requested and expected response
    final List<Block> blocks = new ArrayList<>(nbBlock);
    for (long i = 0; i < nbBlock; i++) {
      final BlockHeader header = blockchain.getBlockHeader(10 + i).get();
      final BlockBody body = blockchain.getBlockBody(header.getHash()).get();
      blocks.add(new Block(header, body));
    }
    return blocks;
  }

  @Override
  protected CompleteBlocksTask createTask(final List<Block> requestedData) {
    final List<BlockHeader> headersToComplete =
        requestedData.stream().map(Block::getHeader).collect(Collectors.toList());
    return CompleteBlocksTask.forHeaders(
        protocolSchedule, ethContext, headersToComplete, maxRetries, new NoOpMetricsSystem());
  }

  @Test
  public void shouldCompleteWithoutPeersWhenAllBlocksAreEmpty() {
    final BlockHeader header1 = new BlockHeaderTestFixture().number(1).buildHeader();
    final BlockHeader header2 = new BlockHeaderTestFixture().number(2).buildHeader();
    final BlockHeader header3 = new BlockHeaderTestFixture().number(3).buildHeader();

    final Block block1 = new Block(header1, BlockBody.empty());
    final Block block2 = new Block(header2, BlockBody.empty());
    final Block block3 = new Block(header3, BlockBody.empty());

    final List<Block> blocks = asList(block1, block2, block3);
    final EthTask<List<Block>> task = createTask(blocks);
    assertThat(task.run()).isCompletedWithValue(blocks);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldReduceTheBlockSegmentSizeAfterEachRetry() {
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    peerCountToTimeout.set(3);
    final List<Block> requestedData = generateDataToBeRequested(10);

    final CompleteBlocksTask task = createTask(requestedData);
    final CompletableFuture<List<Block>> future = task.run();

    final List<MessageData> messageCollector = new ArrayList<>();

    respondingPeer.respond(
        RespondingEthPeer.wrapResponderWithCollector(
            RespondingEthPeer.emptyResponder(), messageCollector));

    assertThat(batchSize(messageCollector.get(0))).isEqualTo(10);
    assertThat(batchSize(messageCollector.get(1))).isEqualTo(5);
    assertThat(batchSize(messageCollector.get(2))).isEqualTo(4);
    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(future::get).hasCauseInstanceOf(MaxRetriesReachedException.class);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldNotReduceTheBlockSegmentSizeIfOnlyOneBlockNeeded() {
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    peerCountToTimeout.set(3);
    final List<Block> requestedData = generateDataToBeRequested(1);

    final EthTask<List<Block>> task = createTask(requestedData);
    final CompletableFuture<List<Block>> future = task.run();

    final List<MessageData> messageCollector = new ArrayList<>();

    respondingPeer.respond(
        RespondingEthPeer.wrapResponderWithCollector(
            RespondingEthPeer.emptyResponder(), messageCollector));

    assertThat(batchSize(messageCollector.get(0))).isEqualTo(1);
    assertThat(batchSize(messageCollector.get(1))).isEqualTo(1);
    assertThat(batchSize(messageCollector.get(2))).isEqualTo(1);
    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(future::get).hasCauseInstanceOf(MaxRetriesReachedException.class);
  }

  private long batchSize(final MessageData msg) {
    return ((GetBlockBodiesMessage) msg).hashes().spliterator().getExactSizeIfKnown();
  }
}
