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
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskValidationResponse;
import org.hyperledger.besu.ethereum.eth.messages.EthPV63;
import org.hyperledger.besu.ethereum.eth.messages.GetReceiptsMessage;
import org.hyperledger.besu.ethereum.eth.messages.ReceiptsMessage;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
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
    GetReceiptsFromPeerTask task = new GetReceiptsFromPeerTask(Collections.emptyList(), null);
    Assertions.assertEquals(EthProtocol.get(), task.getSubProtocol());
  }

  @Test
  public void testGetRequestMessage() {
    BlockHeader blockHeader1 = mockBlockHeader(1);
    TransactionReceipt receiptForBlock1 =
        new TransactionReceipt(1, 123, Collections.emptyList(), Optional.empty());
    Mockito.when(blockHeader1.getReceiptsRoot())
        .thenReturn(BodyValidation.receiptsRoot(List.of(receiptForBlock1)));

    BlockHeader blockHeader2 = mockBlockHeader(2);
    TransactionReceipt receiptForBlock2 =
        new TransactionReceipt(1, 456, Collections.emptyList(), Optional.empty());
    Mockito.when(blockHeader2.getReceiptsRoot())
        .thenReturn(BodyValidation.receiptsRoot(List.of(receiptForBlock2)));

    BlockHeader blockHeader3 = mockBlockHeader(3);
    TransactionReceipt receiptForBlock3 =
        new TransactionReceipt(1, 789, Collections.emptyList(), Optional.empty());
    Mockito.when(blockHeader3.getReceiptsRoot())
        .thenReturn(BodyValidation.receiptsRoot(List.of(receiptForBlock3)));

    GetReceiptsFromPeerTask task =
        new GetReceiptsFromPeerTask(List.of(blockHeader1, blockHeader2, blockHeader3), null);

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
    GetReceiptsFromPeerTask task = new GetReceiptsFromPeerTask(Collections.emptyList(), null);
    Assertions.assertThrows(
        InvalidPeerTaskResponseException.class, () -> task.processResponse(null));
  }

  @Test
  public void testParseResponseForInvalidResponse() {
    BlockHeader blockHeader1 = mockBlockHeader(1);
    TransactionReceipt receiptForBlock1 =
        new TransactionReceipt(1, 123, Collections.emptyList(), Optional.empty());
    Mockito.when(blockHeader1.getReceiptsRoot())
        .thenReturn(BodyValidation.receiptsRoot(List.of(receiptForBlock1)));

    BlockHeader blockHeader2 = mockBlockHeader(2);
    TransactionReceipt receiptForBlock2 =
        new TransactionReceipt(1, 456, Collections.emptyList(), Optional.empty());
    Mockito.when(blockHeader2.getReceiptsRoot())
        .thenReturn(BodyValidation.receiptsRoot(List.of(receiptForBlock2)));

    BlockHeader blockHeader3 = mockBlockHeader(3);
    TransactionReceipt receiptForBlock3 =
        new TransactionReceipt(1, 789, Collections.emptyList(), Optional.empty());
    Mockito.when(blockHeader3.getReceiptsRoot())
        .thenReturn(BodyValidation.receiptsRoot(List.of(receiptForBlock3)));
    GetReceiptsFromPeerTask task =
        new GetReceiptsFromPeerTask(List.of(blockHeader1, blockHeader2, blockHeader3), null);
    ReceiptsMessage receiptsMessage =
        ReceiptsMessage.create(
            List.of(
                List.of(receiptForBlock1),
                List.of(receiptForBlock2),
                List.of(receiptForBlock3),
                List.of(
                    new TransactionReceipt(1, 101112, Collections.emptyList(), Optional.empty()))));

    Assertions.assertThrows(
        InvalidPeerTaskResponseException.class, () -> task.processResponse(receiptsMessage));
  }

  @Test
  public void testParseResponse() throws InvalidPeerTaskResponseException {
    BlockHeader blockHeader1 = mockBlockHeader(1);
    TransactionReceipt receiptForBlock1 =
        new TransactionReceipt(1, 123, Collections.emptyList(), Optional.empty());
    Mockito.when(blockHeader1.getReceiptsRoot())
        .thenReturn(BodyValidation.receiptsRoot(List.of(receiptForBlock1)));

    BlockHeader blockHeader2 = mockBlockHeader(2);
    TransactionReceipt receiptForBlock2 =
        new TransactionReceipt(1, 456, Collections.emptyList(), Optional.empty());
    Mockito.when(blockHeader2.getReceiptsRoot())
        .thenReturn(BodyValidation.receiptsRoot(List.of(receiptForBlock2)));

    BlockHeader blockHeader3 = mockBlockHeader(3);
    TransactionReceipt receiptForBlock3 =
        new TransactionReceipt(1, 789, Collections.emptyList(), Optional.empty());
    Mockito.when(blockHeader3.getReceiptsRoot())
        .thenReturn(BodyValidation.receiptsRoot(List.of(receiptForBlock3)));

    BlockHeader blockHeader4 = mockBlockHeader(4);
    Mockito.when(blockHeader4.getReceiptsRoot()).thenReturn(Hash.EMPTY_TRIE_HASH);

    GetReceiptsFromPeerTask task =
        new GetReceiptsFromPeerTask(
            List.of(blockHeader1, blockHeader2, blockHeader3, blockHeader4), null);

    ReceiptsMessage receiptsMessage =
        ReceiptsMessage.create(
            List.of(
                List.of(receiptForBlock1), List.of(receiptForBlock2), List.of(receiptForBlock3)));

    Map<BlockHeader, List<TransactionReceipt>> resultMap = task.processResponse(receiptsMessage);

    Assertions.assertEquals(4, resultMap.size());
    Assertions.assertEquals(Collections.emptyList(), resultMap.get(blockHeader4));
    Assertions.assertEquals(List.of(receiptForBlock1), resultMap.get(blockHeader1));
    Assertions.assertEquals(List.of(receiptForBlock2), resultMap.get(blockHeader2));
    Assertions.assertEquals(List.of(receiptForBlock3), resultMap.get(blockHeader3));
  }

  @Test
  public void testParseResponseForOnlyPrefilledEmptyTrieReceiptsRoots()
      throws InvalidPeerTaskResponseException {
    BlockHeader blockHeader1 = mockBlockHeader(1);
    Mockito.when(blockHeader1.getReceiptsRoot()).thenReturn(Hash.EMPTY_TRIE_HASH);

    GetReceiptsFromPeerTask task = new GetReceiptsFromPeerTask(List.of(blockHeader1), null);

    ReceiptsMessage receiptsMessage = ReceiptsMessage.create(Collections.emptyList());

    Map<BlockHeader, List<TransactionReceipt>> resultMap = task.processResponse(receiptsMessage);

    Assertions.assertEquals(1, resultMap.size());
    Assertions.assertEquals(Collections.emptyList(), resultMap.get(blockHeader1));
  }

  @Test
  public void testGetPeerRequirementFilter() {
    BlockHeader blockHeader1 = mockBlockHeader(1);
    TransactionReceipt receiptForBlock1 =
        new TransactionReceipt(1, 123, Collections.emptyList(), Optional.empty());
    Mockito.when(blockHeader1.getReceiptsRoot())
        .thenReturn(BodyValidation.receiptsRoot(List.of(receiptForBlock1)));

    BlockHeader blockHeader2 = mockBlockHeader(2);
    TransactionReceipt receiptForBlock2 =
        new TransactionReceipt(1, 456, Collections.emptyList(), Optional.empty());
    Mockito.when(blockHeader2.getReceiptsRoot())
        .thenReturn(BodyValidation.receiptsRoot(List.of(receiptForBlock2)));

    BlockHeader blockHeader3 = mockBlockHeader(3);
    TransactionReceipt receiptForBlock3 =
        new TransactionReceipt(1, 789, Collections.emptyList(), Optional.empty());
    Mockito.when(blockHeader3.getReceiptsRoot())
        .thenReturn(BodyValidation.receiptsRoot(List.of(receiptForBlock3)));

    ProtocolSchedule protocolSchedule = Mockito.mock(ProtocolSchedule.class);
    Mockito.when(protocolSchedule.anyMatch(Mockito.any())).thenReturn(false);

    GetReceiptsFromPeerTask task =
        new GetReceiptsFromPeerTask(
            List.of(blockHeader1, blockHeader2, blockHeader3), protocolSchedule);

    EthPeer failForShortChainHeight = mockPeer(1);
    EthPeer successfulCandidate = mockPeer(5);

    Assertions.assertFalse(task.getPeerRequirementFilter().test(failForShortChainHeight));
    Assertions.assertTrue(task.getPeerRequirementFilter().test(successfulCandidate));
  }

  @Test
  public void testValidateResultForPartialSuccess() {
    GetReceiptsFromPeerTask task = new GetReceiptsFromPeerTask(Collections.emptyList(), null);

    Assertions.assertEquals(
        PeerTaskValidationResponse.NO_RESULTS_RETURNED,
        task.validateResult(Collections.emptyMap()));
  }

  @Test
  public void testValidateResultForFullSuccess() {
    GetReceiptsFromPeerTask task = new GetReceiptsFromPeerTask(Collections.emptyList(), null);

    Map<BlockHeader, List<TransactionReceipt>> map = new HashMap<>();
    map.put(mockBlockHeader(1), null);

    Assertions.assertEquals(
        PeerTaskValidationResponse.RESULTS_VALID_AND_GOOD, task.validateResult(map));
  }

  private BlockHeader mockBlockHeader(final long blockNumber) {
    BlockHeader blockHeader = Mockito.mock(BlockHeader.class);
    Mockito.when(blockHeader.getNumber()).thenReturn(blockNumber);
    // second to last hex digit indicates the blockNumber, last hex digit indicates the usage of the
    // hash
    Mockito.when(blockHeader.getHash())
        .thenReturn(Hash.fromHexString(StringUtils.repeat("00", 31) + blockNumber + "1"));

    return blockHeader;
  }

  private EthPeer mockPeer(final long chainHeight) {
    EthPeer ethPeer = Mockito.mock(EthPeer.class);
    ChainState chainState = Mockito.mock(ChainState.class);

    Mockito.when(ethPeer.chainState()).thenReturn(chainState);
    Mockito.when(chainState.getEstimatedHeight()).thenReturn(chainHeight);

    return ethPeer;
  }
}
