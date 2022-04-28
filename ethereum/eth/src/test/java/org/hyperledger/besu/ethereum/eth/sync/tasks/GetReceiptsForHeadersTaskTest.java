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
import static org.hyperledger.besu.datatypes.Hash.EMPTY_TRIE_HASH;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Receipts;
import org.hyperledger.besu.ethereum.eth.manager.ethtaskutils.RetryingMessageTaskTest;
import org.hyperledger.besu.ethereum.eth.manager.task.EthTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;

public class GetReceiptsForHeadersTaskTest
    extends RetryingMessageTaskTest<Map<BlockHeader, Receipts>> {

  @Override
  protected Map<BlockHeader, Receipts> generateDataToBeRequested() {
    // Setup data to be requested and expected response
    final Map<BlockHeader, Receipts> blocks = new HashMap<>();
    for (long i = 0; i < 3; i++) {
      final BlockHeader header = blockchain.getBlockHeader(10 + i).get();
      blocks.put(header, new Receipts(blockchain.getTxReceipts(header.getHash()).get()));
    }
    return blocks;
  }

  @Override
  protected EthTask<Map<BlockHeader, Receipts>> createTask(
      final Map<BlockHeader, Receipts> requestedData) {
    final List<BlockHeader> headersToComplete = new ArrayList<>(requestedData.keySet());
    return GetReceiptsForHeadersTask.forHeaders(
        ethContext, headersToComplete, maxRetries, metricsSystem);
  }

  @Test
  public void shouldBeCompleteWhenAllReceiptsAreEmpty() {
    final BlockHeader header1 =
        new BlockHeaderTestFixture().number(1).receiptsRoot(EMPTY_TRIE_HASH).buildHeader();
    final BlockHeader header2 =
        new BlockHeaderTestFixture().number(2).receiptsRoot(EMPTY_TRIE_HASH).buildHeader();
    final BlockHeader header3 =
        new BlockHeaderTestFixture().number(3).receiptsRoot(EMPTY_TRIE_HASH).buildHeader();

    final Map<BlockHeader, Receipts> expected =
        ImmutableMap.of(header1, Receipts.EMPTY, header2, Receipts.EMPTY, header3, Receipts.EMPTY);

    assertThat(createTask(expected).run()).isCompletedWithValue(expected);
  }
}
