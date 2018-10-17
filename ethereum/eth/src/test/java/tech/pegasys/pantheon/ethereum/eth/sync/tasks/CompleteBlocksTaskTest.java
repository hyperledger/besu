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
package tech.pegasys.pantheon.ethereum.eth.sync.tasks;

import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.EthTask;
import tech.pegasys.pantheon.ethereum.eth.manager.ethtaskutils.RetryingMessageTaskTest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
        protocolSchedule, ethContext, headersToComplete, maxRetries);
  }
}
