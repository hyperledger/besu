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

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.datatypes.Hash.EMPTY_TRIE_HASH;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.manager.ethtaskutils.RetryingMessageTaskTest;
import org.hyperledger.besu.ethereum.eth.manager.task.EthTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class GetReceiptsForHeadersTaskTest
    extends RetryingMessageTaskTest<Map<BlockHeader, List<TransactionReceipt>>> {

  @BeforeEach
  @Override
  public void resetMaxRetries() {
    maxRetries = GetReceiptsForHeadersTask.DEFAULT_RETRIES;
  }

  @Override
  protected Map<BlockHeader, List<TransactionReceipt>> generateDataToBeRequested() {
    // Setup data to be requested and expected response
    final Map<BlockHeader, List<TransactionReceipt>> blocks = new HashMap<>();
    for (long i = 0; i < 3; i++) {
      final BlockHeader header = blockchain.getBlockHeader(10 + i).get();
      blocks.put(header, blockchain.getTxReceipts(header.getHash()).get());
    }
    return blocks;
  }

  @Override
  protected EthTask<Map<BlockHeader, List<TransactionReceipt>>> createTask(
      final Map<BlockHeader, List<TransactionReceipt>> requestedData) {
    final List<BlockHeader> headersToComplete = new ArrayList<>(requestedData.keySet());
    return GetReceiptsForHeadersTask.forHeaders(ethContext, headersToComplete, metricsSystem);
  }

  @Test
  public void shouldBeCompleteWhenAllReceiptsAreEmpty() {
    final BlockHeader header1 =
        new BlockHeaderTestFixture().number(1).receiptsRoot(EMPTY_TRIE_HASH).buildHeader();
    final BlockHeader header2 =
        new BlockHeaderTestFixture().number(2).receiptsRoot(EMPTY_TRIE_HASH).buildHeader();
    final BlockHeader header3 =
        new BlockHeaderTestFixture().number(3).receiptsRoot(EMPTY_TRIE_HASH).buildHeader();

    final Map<BlockHeader, List<TransactionReceipt>> expected =
        ImmutableMap.of(header1, emptyList(), header2, emptyList(), header3, emptyList());

    assertThat(createTask(expected).run()).isCompletedWithValue(expected);
  }

  @Override
  @Test
  @Disabled
  public void failsWhenPeerReturnsPartialResultThenStops() {
    // Test is not valid when more than 4 retries are allowed, as is always the case with
    // GetReceiptsForHeadersTask, because the peer is forcefully disconnected after failing
    // too many times
  }
}
