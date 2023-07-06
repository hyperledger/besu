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
import static org.hyperledger.besu.ethereum.core.BlockHeader.GENESIS_BLOCK_NUMBER;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.ethtaskutils.PeerMessageTaskTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;

public class GetNodeDataFromPeerTaskTest extends PeerMessageTaskTest<Map<Hash, Bytes>> {

  @Override
  protected Map<Hash, Bytes> generateDataToBeRequested() {
    final Map<Hash, Bytes> requestedData = new HashMap<>();
    for (int i = 0; i < 3; i++) {
      final BlockHeader blockHeader = blockchain.getBlockHeader(10 + i).get();
      requestedData.put(
          Hash.hash(
              protocolContext.getWorldStateArchive().getNodeData(blockHeader.getStateRoot()).get()),
          protocolContext.getWorldStateArchive().getNodeData(blockHeader.getStateRoot()).get());
    }
    return requestedData;
  }

  @Override
  protected EthTask<AbstractPeerTask.PeerTaskResult<Map<Hash, Bytes>>> createTask(
      final Map<Hash, Bytes> requestedData) {
    final List<Hash> hashes = Lists.newArrayList(requestedData.keySet());
    return GetNodeDataFromPeerTask.forHashes(
        ethContext, hashes, GENESIS_BLOCK_NUMBER, metricsSystem);
  }

  @Override
  protected void assertPartialResultMatchesExpectation(
      final Map<Hash, Bytes> requestedData, final Map<Hash, Bytes> partialResponse) {
    assertThat(partialResponse.size()).isLessThanOrEqualTo(requestedData.size());
    assertThat(partialResponse.size()).isGreaterThan(0);
    for (Map.Entry<Hash, Bytes> data : partialResponse.entrySet()) {
      assertThat(requestedData.get(data.getKey())).isEqualTo(data.getValue());
    }
  }

  @Override
  protected void assertResultMatchesExpectation(
      final Map<Hash, Bytes> requestedData,
      final AbstractPeerTask.PeerTaskResult<Map<Hash, Bytes>> response,
      final EthPeer respondingPeer) {
    assertThat(response.getResult().size()).isEqualTo(requestedData.size());
    for (Map.Entry<Hash, Bytes> data : response.getResult().entrySet()) {
      assertThat(requestedData.get(data.getKey())).isEqualTo(data.getValue());
    }
  }
}
