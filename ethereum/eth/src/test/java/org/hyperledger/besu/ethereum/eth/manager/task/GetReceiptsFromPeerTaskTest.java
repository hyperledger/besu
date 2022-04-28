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

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Receipts;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.manager.ethtaskutils.PeerMessageTaskTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GetReceiptsFromPeerTaskTest extends PeerMessageTaskTest<Map<BlockHeader, Receipts>> {

  @Override
  protected Map<BlockHeader, Receipts> generateDataToBeRequested() {
    final Map<BlockHeader, Receipts> expectedData = new HashMap<>();
    for (long i = 0; i < 3; i++) {
      final BlockHeader header = blockchain.getBlockHeader(10 + i).get();
      final List<TransactionReceipt> transactionReceipts =
          blockchain.getTxReceipts(header.getHash()).get();
      expectedData.put(header, new Receipts(transactionReceipts));
    }
    return expectedData;
  }

  @Override
  protected EthTask<AbstractPeerTask.PeerTaskResult<Map<BlockHeader, Receipts>>> createTask(
      final Map<BlockHeader, Receipts> requestedData) {
    return GetReceiptsFromPeerTask.forHeaders(ethContext, requestedData.keySet(), metricsSystem);
  }

  @Override
  protected void assertPartialResultMatchesExpectation(
      final Map<BlockHeader, Receipts> requestedData,
      final Map<BlockHeader, Receipts> partialResponse) {

    assertThat(partialResponse.size()).isLessThanOrEqualTo(requestedData.size());
    assertThat(partialResponse.size()).isGreaterThan(0);
    partialResponse.forEach(
        (blockHeader, transactionReceipts) -> {
          assertThat(requestedData).containsKey(blockHeader);
          assertThat(requestedData.get(blockHeader)).isEqualTo(transactionReceipts);
        });
  }
}
