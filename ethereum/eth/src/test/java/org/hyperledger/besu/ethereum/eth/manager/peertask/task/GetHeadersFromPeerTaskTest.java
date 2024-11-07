/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.eth.manager.peertask.task;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.manager.ChainState;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.peertask.InvalidPeerTaskResponseException;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetHeadersFromPeerTask.Direction;
import org.hyperledger.besu.ethereum.eth.messages.BlockHeadersMessage;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class GetHeadersFromPeerTaskTest {

  @Test
  public void testGetSubProtocol() {
    GetHeadersFromPeerTask task = new GetHeadersFromPeerTask(0, 1, 0, Direction.FORWARD, null);
    Assertions.assertEquals(EthProtocol.get(), task.getSubProtocol());
  }

  @Test
  public void testGetRequestMessageForHash() {
    GetHeadersFromPeerTask task =
        new GetHeadersFromPeerTask(Hash.ZERO, 0, 1, 0, Direction.FORWARD, null);
    MessageData requestMessageData = task.getRequestMessage();
    Assertions.assertEquals(
        "0xe4a00000000000000000000000000000000000000000000000000000000000000000018080",
        requestMessageData.getData().toHexString());
  }

  @Test
  public void testGetRequestMessageForBlockNumber() {
    GetHeadersFromPeerTask task = new GetHeadersFromPeerTask(123, 1, 0, Direction.FORWARD, null);
    MessageData requestMessageData = task.getRequestMessage();
    Assertions.assertEquals("0xc47b018080", requestMessageData.getData().toHexString());
  }

  @Test
  public void testGetRequestMessageForHashWhenBlockNumberAlsoProvided() {
    GetHeadersFromPeerTask task =
        new GetHeadersFromPeerTask(Hash.ZERO, 123, 1, 0, Direction.FORWARD, null);
    MessageData requestMessageData = task.getRequestMessage();
    Assertions.assertEquals(
        "0xe4a00000000000000000000000000000000000000000000000000000000000000000018080",
        requestMessageData.getData().toHexString());
  }

  @Test
  public void testProcessResponseWithNullMessageData() {
    GetHeadersFromPeerTask task = new GetHeadersFromPeerTask(0, 1, 0, Direction.FORWARD, null);
    Assertions.assertThrows(
        InvalidPeerTaskResponseException.class,
        () -> task.processResponse(null),
        "Response MessageData is null");
  }

  @Test
  public void testProcessResponse() throws InvalidPeerTaskResponseException {
    final BlockchainSetupUtil blockchainSetupUtil =
        BlockchainSetupUtil.forTesting(DataStorageFormat.FOREST);
    blockchainSetupUtil.importAllBlocks();
    Blockchain blockchain = blockchainSetupUtil.getBlockchain();
    BlockHeadersMessage responseMessage =
        BlockHeadersMessage.create(blockchain.getChainHeadHeader());

    GetHeadersFromPeerTask task =
        new GetHeadersFromPeerTask(
            0, 1, 0, Direction.FORWARD, blockchainSetupUtil.getProtocolSchedule());

    Assertions.assertEquals(
        List.of(blockchain.getChainHeadHeader()), task.processResponse(responseMessage));
  }

  @Test
  public void testGetPeerRequirementFilter() {
    ProtocolSchedule protocolSchedule = Mockito.mock(ProtocolSchedule.class);
    Mockito.when(protocolSchedule.anyMatch(Mockito.any())).thenReturn(false);

    GetHeadersFromPeerTask task =
        new GetHeadersFromPeerTask(5, 1, 0, Direction.FORWARD, protocolSchedule);

    EthPeer failForIncorrectProtocol = mockPeer("incorrectProtocol", 5);
    EthPeer failForShortChainHeight = mockPeer("incorrectProtocol", 1);
    EthPeer successfulCandidate = mockPeer(EthProtocol.NAME, 5);

    Assertions.assertFalse(task.getPeerRequirementFilter().test(failForIncorrectProtocol));
    Assertions.assertFalse(task.getPeerRequirementFilter().test(failForShortChainHeight));
    Assertions.assertTrue(task.getPeerRequirementFilter().test(successfulCandidate));
  }

  private EthPeer mockPeer(final String protocol, final long chainHeight) {
    EthPeer ethPeer = Mockito.mock(EthPeer.class);
    ChainState chainState = Mockito.mock(ChainState.class);

    Mockito.when(ethPeer.getProtocolName()).thenReturn(protocol);
    Mockito.when(ethPeer.chainState()).thenReturn(chainState);
    Mockito.when(chainState.getEstimatedHeight()).thenReturn(chainHeight);

    return ethPeer;
  }
}
