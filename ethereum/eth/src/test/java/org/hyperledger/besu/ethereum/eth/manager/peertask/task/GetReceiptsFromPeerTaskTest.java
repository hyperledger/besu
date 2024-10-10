/*
 * Copyright contributors to Hyperledger Besu.
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
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.manager.ChainState;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.peertask.InvalidPeerTaskResponseException;
import org.hyperledger.besu.ethereum.eth.messages.EthPV63;
import org.hyperledger.besu.ethereum.eth.messages.GetReceiptsMessage;
import org.hyperledger.besu.ethereum.eth.messages.ReceiptsMessage;
import org.hyperledger.besu.ethereum.mainnet.BodyValidator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class GetReceiptsFromPeerTaskTest {

  @Test
  public void testGetSubProtocol() {
    GetReceiptsFromPeerTask task =
        new GetReceiptsFromPeerTask(Collections.emptyList(), null, () -> null);
    Assertions.assertEquals(EthProtocol.get(), task.getSubProtocol());
  }

  @Test
  public void testGetRequestMessage() {
    GetReceiptsFromPeerTask task =
        new GetReceiptsFromPeerTask(
            List.of(mockBlockHeader(1), mockBlockHeader(2), mockBlockHeader(3)), null, () -> null);

    MessageData messageData = task.getRequestMessage();
    GetReceiptsMessage getReceiptsMessage = GetReceiptsMessage.readFrom(messageData);

    Assertions.assertEquals(EthPV63.GET_RECEIPTS, getReceiptsMessage.getCode());
    Iterable<Hash> hashesInMessage = getReceiptsMessage.hashes();
    List<Hash> expectedHashes =
        List.of(
            Hash.fromHexString(StringUtils.repeat("00", 31) + "11"),
            Hash.fromHexString(StringUtils.repeat("00", 31) + "21"),
            Hash.fromHexString(StringUtils.repeat("00", 31) + "31"));
    List<Hash> actualHashes = new ArrayList<>();
    hashesInMessage.forEach(actualHashes::add);

    Assertions.assertEquals(3, actualHashes.size());
    Assertions.assertEquals(
        expectedHashes.stream().sorted().toList(), actualHashes.stream().sorted().toList());
  }

  @Test
  public void testParseResponseWithNullResponseMessage() {
    GetReceiptsFromPeerTask task =
        new GetReceiptsFromPeerTask(Collections.emptyList(), null, () -> null);
    Assertions.assertThrows(InvalidPeerTaskResponseException.class, () -> task.parseResponse(null));
  }

  @Test
  public void testParseResponseForInvalidResponse() {
    GetReceiptsFromPeerTask task =
        new GetReceiptsFromPeerTask(
            List.of(mockBlockHeader(1), mockBlockHeader(2), mockBlockHeader(3)), null, () -> null);
    ReceiptsMessage receiptsMessage =
        ReceiptsMessage.create(
            List.of(
                List.of(new TransactionReceipt(1, 123, Collections.emptyList(), Optional.empty())),
                List.of(new TransactionReceipt(1, 456, Collections.emptyList(), Optional.empty())),
                List.of(new TransactionReceipt(1, 789, Collections.emptyList(), Optional.empty())),
                List.of(
                    new TransactionReceipt(1, 101112, Collections.emptyList(), Optional.empty()))));

    Assertions.assertThrows(
        InvalidPeerTaskResponseException.class, () -> task.parseResponse(receiptsMessage));
  }

  @Test
  public void testParseResponse() throws InvalidPeerTaskResponseException {
    BodyValidator bodyValidator = Mockito.mock(BodyValidator.class);
    BlockHeader blockHeader1 = mockBlockHeader(1);
    BlockHeader blockHeader2 = mockBlockHeader(2);
    BlockHeader blockHeader3 = mockBlockHeader(3);

    GetReceiptsFromPeerTask task =
        new GetReceiptsFromPeerTask(
            List.of(blockHeader1, blockHeader2, blockHeader3), bodyValidator, () -> null);

    TransactionReceipt receiptForBlock1 =
        new TransactionReceipt(1, 123, Collections.emptyList(), Optional.empty());
    TransactionReceipt receiptForBlock2 =
        new TransactionReceipt(1, 456, Collections.emptyList(), Optional.empty());
    TransactionReceipt receiptForBlock3 =
        new TransactionReceipt(1, 789, Collections.emptyList(), Optional.empty());
    ReceiptsMessage receiptsMessage =
        ReceiptsMessage.create(
            List.of(
                List.of(receiptForBlock1), List.of(receiptForBlock2), List.of(receiptForBlock3)));

    Mockito.when(bodyValidator.receiptsRoot(List.of(receiptForBlock1)))
        .thenReturn(Hash.fromHexString(StringUtils.repeat("00", 31) + "12"));
    Mockito.when(bodyValidator.receiptsRoot(List.of(receiptForBlock2)))
        .thenReturn(Hash.fromHexString(StringUtils.repeat("00", 31) + "22"));
    Mockito.when(bodyValidator.receiptsRoot(List.of(receiptForBlock3)))
        .thenReturn(Hash.fromHexString(StringUtils.repeat("00", 31) + "32"));

    Map<BlockHeader, List<TransactionReceipt>> resultMap = task.parseResponse(receiptsMessage);

    Assertions.assertEquals(3, resultMap.size());
    Assertions.assertEquals(List.of(receiptForBlock1), resultMap.get(blockHeader1));
    Assertions.assertEquals(List.of(receiptForBlock2), resultMap.get(blockHeader2));
    Assertions.assertEquals(List.of(receiptForBlock3), resultMap.get(blockHeader3));
  }

  @Test
  public void testGetPeerRequirementFilter() {
    BlockHeader blockHeader1 = mockBlockHeader(1);
    BlockHeader blockHeader2 = mockBlockHeader(2);
    BlockHeader blockHeader3 = mockBlockHeader(3);

    ProtocolSpec protocolSpec = Mockito.mock(ProtocolSpec.class);
    Mockito.when(protocolSpec.isPoS()).thenReturn(false);

    GetReceiptsFromPeerTask task =
        new GetReceiptsFromPeerTask(
            List.of(blockHeader1, blockHeader2, blockHeader3), null, () -> protocolSpec);

    EthPeer failForIncorrectProtocol = mockPeer("incorrectProtocol", 5);
    EthPeer failForShortChainHeight = mockPeer("incorrectProtocol", 1);
    EthPeer successfulCandidate = mockPeer(EthProtocol.NAME, 5);

    Assertions.assertFalse(task.getPeerRequirementFilter().test(failForIncorrectProtocol));
    Assertions.assertFalse(task.getPeerRequirementFilter().test(failForShortChainHeight));
    Assertions.assertTrue(task.getPeerRequirementFilter().test(successfulCandidate));
  }

  @Test
  public void testIsPartialSuccessForPartialSuccess() {
    GetReceiptsFromPeerTask task =
        new GetReceiptsFromPeerTask(Collections.emptyList(), null, () -> null);

    Assertions.assertTrue(task.isPartialSuccess(Collections.emptyMap()));
  }

  @Test
  public void testIsPartialSuccessForFullSuccess() {
    GetReceiptsFromPeerTask task =
        new GetReceiptsFromPeerTask(Collections.emptyList(), null, () -> null);

    Map<BlockHeader, List<TransactionReceipt>> map = new HashMap<>();
    map.put(mockBlockHeader(1), null);

    Assertions.assertFalse(task.isPartialSuccess(map));
  }

  private BlockHeader mockBlockHeader(final long blockNumber) {
    BlockHeader blockHeader = Mockito.mock(BlockHeader.class);
    Mockito.when(blockHeader.getNumber()).thenReturn(blockNumber);
    // second to last hex digit indicates the blockNumber, last hex digit indicates the usage of the
    // hash
    Mockito.when(blockHeader.getHash())
        .thenReturn(Hash.fromHexString(StringUtils.repeat("00", 31) + blockNumber + "1"));
    Mockito.when(blockHeader.getReceiptsRoot())
        .thenReturn(Hash.fromHexString(StringUtils.repeat("00", 31) + blockNumber + "2"));

    return blockHeader;
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
