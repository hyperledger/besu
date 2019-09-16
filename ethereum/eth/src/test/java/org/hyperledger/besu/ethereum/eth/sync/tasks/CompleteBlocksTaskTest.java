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
package org.hyperledger.besu.ethereum.eth.sync.tasks;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.eth.manager.ethtaskutils.RetryingMessageTaskTest;
import org.hyperledger.besu.ethereum.eth.manager.task.EthTask;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

public class CompleteBlocksTaskTest extends RetryingMessageTaskTest<List<Block>> {

  @Override
  protected List<Block> generateDataToBeRequested() {
    // Setup data to be requested and expected response
    final List<Block> blocks = new ArrayList<>();
    for (long i = 0; i < 3; i++) {
      final BlockHeader header = blockchain.getBlockHeader(10 + i).get();
      final BlockBody body = blockchain.getBlockBody(header.getHash()).get();
      blocks.add(new Block(header, body));
    }
    return blocks;
  }

  @Override
  protected EthTask<List<Block>> createTask(final List<Block> requestedData) {
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
}
