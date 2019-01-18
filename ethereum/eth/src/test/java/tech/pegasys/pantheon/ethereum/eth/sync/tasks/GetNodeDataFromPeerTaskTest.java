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

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.eth.manager.AbstractPeerTask.PeerTaskResult;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.EthTask;
import tech.pegasys.pantheon.ethereum.eth.manager.ethtaskutils.PeerMessageTaskTest;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.ArrayList;
import java.util.List;

public class GetNodeDataFromPeerTaskTest extends PeerMessageTaskTest<List<BytesValue>> {

  @Override
  protected List<BytesValue> generateDataToBeRequested() {
    final List<BytesValue> requestedData = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      final BlockHeader blockHeader = blockchain.getBlockHeader(10 + i).get();
      requestedData.add(
          protocolContext.getWorldStateArchive().getNodeData(blockHeader.getStateRoot()).get());
    }
    return requestedData;
  }

  @Override
  protected EthTask<PeerTaskResult<List<BytesValue>>> createTask(
      final List<BytesValue> requestedData) {
    final List<Hash> hashes = requestedData.stream().map(Hash::hash).collect(toList());
    return GetNodeDataFromPeerTask.forHashes(ethContext, hashes, ethTasksTimer);
  }

  @Override
  protected void assertPartialResultMatchesExpectation(
      final List<BytesValue> requestedData, final List<BytesValue> partialResponse) {
    assertThat(partialResponse.size()).isLessThanOrEqualTo(requestedData.size());
    assertThat(partialResponse.size()).isGreaterThan(0);
    assertThat(requestedData).containsAll(partialResponse);
  }

  @Override
  protected void assertResultMatchesExpectation(
      final List<BytesValue> requestedData,
      final PeerTaskResult<List<BytesValue>> response,
      final EthPeer respondingPeer) {
    assertThat(response.getResult()).containsExactlyInAnyOrderElementsOf(requestedData);
    assertThat(response.getPeer()).isEqualTo(respondingPeer);
  }
}
