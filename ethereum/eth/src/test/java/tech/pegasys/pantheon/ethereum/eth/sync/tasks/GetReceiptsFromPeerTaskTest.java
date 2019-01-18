/*
 * Copyright 2019 ConsenSys AG.
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

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;
import tech.pegasys.pantheon.ethereum.eth.manager.AbstractPeerTask.PeerTaskResult;
import tech.pegasys.pantheon.ethereum.eth.manager.EthTask;
import tech.pegasys.pantheon.ethereum.eth.manager.ethtaskutils.PeerMessageTaskTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GetReceiptsFromPeerTaskTest
    extends PeerMessageTaskTest<Map<BlockHeader, List<TransactionReceipt>>> {

  @Override
  protected Map<BlockHeader, List<TransactionReceipt>> generateDataToBeRequested() {
    final Map<BlockHeader, List<TransactionReceipt>> expectedData = new HashMap<>();
    for (long i = 0; i < 3; i++) {
      final BlockHeader header = blockchain.getBlockHeader(10 + i).get();
      final List<TransactionReceipt> transactionReceipts =
          blockchain.getTxReceipts(header.getHash()).get();
      expectedData.put(header, transactionReceipts);
    }
    return expectedData;
  }

  @Override
  protected EthTask<PeerTaskResult<Map<BlockHeader, List<TransactionReceipt>>>> createTask(
      final Map<BlockHeader, List<TransactionReceipt>> requestedData) {
    return GetReceiptsFromPeerTask.forHeaders(ethContext, requestedData.keySet(), ethTasksTimer);
  }

  @Override
  protected void assertPartialResultMatchesExpectation(
      final Map<BlockHeader, List<TransactionReceipt>> requestedData,
      final Map<BlockHeader, List<TransactionReceipt>> partialResponse) {

    assertThat(partialResponse.size()).isLessThanOrEqualTo(requestedData.size());
    assertThat(partialResponse.size()).isGreaterThan(0);
    partialResponse.forEach(
        (blockHeader, transactionReceipts) -> {
          assertThat(requestedData).containsKey(blockHeader);
          assertThat(requestedData.get(blockHeader)).isEqualTo(transactionReceipts);
        });
  }
}
