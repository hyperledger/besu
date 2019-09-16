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
package org.hyperledger.besu.ethereum.eth.manager.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.BlockHeader.GENESIS_BLOCK_NUMBER;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.manager.ethtaskutils.RetryingMessageTaskTest;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.google.common.collect.Lists;
import org.junit.Ignore;
import org.junit.Test;

public class RetryingGetNodeDataFromPeerTaskTest
    extends RetryingMessageTaskTest<Map<Hash, BytesValue>> {

  @Override
  protected Map<Hash, BytesValue> generateDataToBeRequested() {
    final Map<Hash, BytesValue> requestedData = new TreeMap<>();
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
  protected EthTask<Map<Hash, BytesValue>> createTask(final Map<Hash, BytesValue> requestedData) {
    final List<Hash> hashes = Lists.newArrayList(requestedData.keySet());
    return RetryingGetNodeDataFromPeerTask.forHashes(
        ethContext, hashes, GENESIS_BLOCK_NUMBER, metricsSystem);
  }

  @Test
  @Override
  public void completesWhenPeerReturnsPartialResult()
      throws ExecutionException, InterruptedException {
    // Setup data to be requested and expected response

    // Setup a partially responsive peer
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    // Execute task and wait for response
    final Map<Hash, BytesValue> requestedData = generateDataToBeRequested();
    final EthTask<Map<Hash, BytesValue>> task = createTask(requestedData);
    final CompletableFuture<Map<Hash, BytesValue>> future = task.run();

    // Respond with partial data.
    respondingPeer.respond(
        RespondingEthPeer.partialResponder(
            blockchain, protocolContext.getWorldStateArchive(), protocolSchedule, 0.50f));

    assertThat(future.isDone()).isTrue();
    // Check that it immediately returns the data we got in the response.
    assertThat(future.get()).hasSize((int) (requestedData.size() * 0.5f));
    assertThat(requestedData).containsAllEntriesOf(future.get());
  }

  @Test
  @Override
  @Ignore("Partial responses are enough to complete the request so this test doesn't apply")
  public void failsWhenPeerReturnsPartialResultThenStops() {}

  @Test
  @Override
  @Ignore("Empty responses count as valid when requesting node data")
  public void failsWhenPeersSendEmptyResponses() {}
}
