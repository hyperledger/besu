/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.ethereum.eth.manager.snap;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.manager.ethtaskutils.AbstractMessageTaskTestBase;
import org.hyperledger.besu.ethereum.eth.manager.task.EthTask;
import org.hyperledger.besu.ethereum.eth.messages.snap.GetTrieNodesMessage;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class RetryingGetTrieNodeFromPeerTaskTest
    extends AbstractMessageTaskTestBase<
        GetTrieNodesMessage.TrieNodesPaths, RetryingGetTrieNodeFromPeerTask> {

  private EthTask<Map<Bytes, Bytes>> createTask() {
    return RetryingGetTrieNodeFromPeerTask.forTrieNodes(
        ethContext, Collections.emptyMap(), blockchain.getBlockHeader(1L).get(), metricsSystem);
  }

  @Test
  public void checkEmptyResponseReducesReputation() {
    final SnapProtocolManager snapProtocolManager =
        new SnapProtocolManager(
            Collections.emptyList(),
            ethPeers,
            new EthMessages(),
            protocolContext.getWorldStateArchive());
    final RespondingEthPeer respondingEthPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, snapProtocolManager, 10);

    // Execute task and wait for response
    final EthTask<Map<Bytes, Bytes>> task = createTask();
    final CompletableFuture<Map<Bytes, Bytes>> future = task.run();

    assertThat(respondingEthPeer.getEthPeer().getReputation().getScore()).isEqualTo(100);
    // peer responds with empty response
    respondingEthPeer.respond(RespondingEthPeer.emptyResponderForSnap());

    assertThat(future.isDone()).isTrue();
    assertThat(respondingEthPeer.getEthPeer().getReputation().getScore()).isEqualTo(99);
  }
}
