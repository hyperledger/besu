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
package org.hyperledger.besu.ethereum.eth.manager;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration.Builder;
import org.hyperledger.besu.ethereum.eth.messages.BlockBodiesMessage;
import org.hyperledger.besu.ethereum.eth.messages.BlockHeadersMessage;
import org.hyperledger.besu.ethereum.eth.messages.GetBlockBodiesMessage;
import org.hyperledger.besu.ethereum.eth.messages.GetBlockHeadersMessage;
import org.hyperledger.besu.ethereum.eth.messages.GetNodeDataMessage;
import org.hyperledger.besu.ethereum.eth.messages.GetReceiptsMessage;
import org.hyperledger.besu.ethereum.eth.messages.NodeDataMessage;
import org.hyperledger.besu.ethereum.eth.messages.ReceiptsMessage;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class EthServerTest {

  private static final Bytes VALUE1 = Bytes.of(1);
  private static final Bytes VALUE2 = Bytes.of(2);
  private static final Bytes VALUE3 = Bytes.of(3);
  private static final Hash HASH1 = Hash.hash(VALUE1);
  private static final Hash HASH2 = Hash.hash(VALUE2);
  private static final Hash HASH3 = Hash.hash(VALUE3);

  private final BlockDataGenerator dataGenerator = new BlockDataGenerator(0);
  private final Blockchain blockchain = mock(Blockchain.class);
  private final WorldStateArchive worldStateArchive = mock(WorldStateArchive.class);
  private final TransactionPool transactionPool = mock(TransactionPool.class);
  private final EthPeer ethPeer = mock(EthPeer.class);
  private final EthMessages ethMessages = new EthMessages();

  @Test
  public void shouldHandleDataBeingUnavailableWhenRespondingToNodeDataRequests() throws Exception {
    setupEthServer(b -> b.maxGetNodeData(2));

    when(worldStateArchive.getNodeData(HASH1)).thenReturn(Optional.of(VALUE1));
    when(worldStateArchive.getNodeData(HASH2)).thenReturn(Optional.empty());
    assertThat(
            ethMessages.dispatch(
                new EthMessage(ethPeer, GetNodeDataMessage.create(asList(HASH1, HASH2)))))
        .contains(NodeDataMessage.create(singletonList(VALUE1)));
  }

  @Test
  public void shouldLimitNumberOfResponsesToNodeDataRequests() throws Exception {
    setupEthServer(b -> b.maxGetNodeData(2));

    when(worldStateArchive.getNodeData(HASH1)).thenReturn(Optional.of(VALUE1));
    when(worldStateArchive.getNodeData(HASH2)).thenReturn(Optional.of(VALUE2));
    assertThat(
            ethMessages.dispatch(
                new EthMessage(ethPeer, GetNodeDataMessage.create(asList(HASH1, HASH2, HASH3)))))
        .contains(NodeDataMessage.create(asList(VALUE1, VALUE2)));
  }

  @Test
  public void shouldLimitTheNumberOfNodeDataResponsesLookedUpNotTheNumberReturned()
      throws Exception {
    setupEthServer(b -> b.maxGetNodeData(2));

    when(worldStateArchive.getNodeData(HASH1)).thenReturn(Optional.of(VALUE1));
    when(worldStateArchive.getNodeData(HASH2)).thenReturn(Optional.empty());
    when(worldStateArchive.getNodeData(HASH3)).thenReturn(Optional.of(VALUE3));
    assertThat(
            ethMessages.dispatch(
                new EthMessage(ethPeer, GetNodeDataMessage.create(asList(HASH1, HASH2, HASH3)))))
        .contains(NodeDataMessage.create(singletonList(VALUE1)));
  }

  @Test
  public void shouldLimitBlockHeadersByMessageSize() {
    final List<Block> blocks = setupBlocks(10);
    final List<BlockHeader> expectedHeaders = new ArrayList<>();
    int sizeLimit = RLP.MAX_PREFIX_SIZE;
    for (int i = 0; i < 4; i++) {
      final BlockHeader header = blocks.get(i).getHeader();
      sizeLimit += calculateRlpEncodedSize(header);
      expectedHeaders.add(header);
    }

    final int msgSizeLimit = sizeLimit;
    setupEthServer(b -> b.maxMessageSize(msgSizeLimit));

    // Request all blocks, which will exceed the limit
    final BlockHeader firstHeader = blocks.get(0).getHeader();
    final GetBlockHeadersMessage headersMsg =
        GetBlockHeadersMessage.create(firstHeader.getHash(), blocks.size(), 0, false);
    final EthMessage ethMsg = new EthMessage(ethPeer, headersMsg);

    // Check response
    final BlockHeadersMessage expectedMsg = BlockHeadersMessage.create(expectedHeaders);
    final Optional<MessageData> result = ethMessages.dispatch(ethMsg);
    assertThat(result).contains(expectedMsg);
  }

  @Test
  public void shouldLimitBlockHeadersByCount() {
    final int blockCount = 10;
    final int limit = 6;
    final List<Block> blocks = setupBlocks(blockCount);
    final List<BlockHeader> expectedHeaders =
        blocks.stream().limit(limit).map(Block::getHeader).collect(Collectors.toList());

    setupEthServer(b -> b.maxGetBlockHeaders(limit));

    // Request all blocks, which will exceed the limit
    final BlockHeader firstHeader = blocks.get(0).getHeader();
    final GetBlockHeadersMessage headersMsg =
        GetBlockHeadersMessage.create(firstHeader.getHash(), blocks.size(), 0, false);
    final EthMessage ethMsg = new EthMessage(ethPeer, headersMsg);

    // Check response
    final BlockHeadersMessage expectedMsg = BlockHeadersMessage.create(expectedHeaders);
    final Optional<MessageData> result = ethMessages.dispatch(ethMsg);
    assertThat(result).contains(expectedMsg);
  }

  @Test
  public void shouldLimitBlockBodiesByMessageSize() {
    final List<Block> blocks = setupBlocks(10);
    final List<BlockBody> expectedBodies = new ArrayList<>();
    int sizeLimit = RLP.MAX_PREFIX_SIZE;
    for (int i = 0; i < 4; i++) {
      final BlockBody body = blocks.get(i).getBody();
      sizeLimit += calculateRlpEncodedSize(body);
      expectedBodies.add(body);
    }

    final int msgSizeLimit = sizeLimit;
    setupEthServer(b -> b.maxMessageSize(msgSizeLimit));

    // Request all blocks, which will exceed the limit
    final List<Hash> blockHashes = blocks.stream().map(Block::getHash).collect(Collectors.toList());
    final GetBlockBodiesMessage bodiesMsg = GetBlockBodiesMessage.create(blockHashes);
    final EthMessage ethMsg = new EthMessage(ethPeer, bodiesMsg);

    // Check response
    final BlockBodiesMessage expectedMsg = BlockBodiesMessage.create(expectedBodies);
    final Optional<MessageData> result = ethMessages.dispatch(ethMsg);
    assertThat(result).contains(expectedMsg);
  }

  @Test
  public void shouldLimitBlockBodiesByCount() {
    final int blockCount = 10;
    final int limit = 6;
    final List<Block> blocks = setupBlocks(blockCount);
    final List<BlockBody> expectedBodies =
        blocks.stream().limit(limit).map(Block::getBody).collect(Collectors.toList());

    setupEthServer(b -> b.maxGetBlockBodies(limit));

    // Request all blocks, which will exceed the limit
    final List<Hash> blockHashes = blocks.stream().map(Block::getHash).collect(Collectors.toList());
    final GetBlockBodiesMessage bodiesMsg = GetBlockBodiesMessage.create(blockHashes);
    final EthMessage ethMsg = new EthMessage(ethPeer, bodiesMsg);

    // Check response
    final BlockBodiesMessage expectedMsg = BlockBodiesMessage.create(expectedBodies);
    final Optional<MessageData> result = ethMessages.dispatch(ethMsg);
    assertThat(result).contains(expectedMsg);
  }

  @Test
  public void shouldLimitTxReceiptsByMessageSize() {
    final Map<Hash, List<TransactionReceipt>> receiptsByHash = setupBlockReceipts(10);
    final List<Hash> hashes = new ArrayList<>(receiptsByHash.keySet());
    final List<List<TransactionReceipt>> expectedResults = new ArrayList<>();
    int sizeLimit = RLP.MAX_PREFIX_SIZE;
    for (int i = 0; i < 4; i++) {
      final List<TransactionReceipt> receipts = receiptsByHash.get(hashes.get(i));
      sizeLimit += calculateRlpEncodedSize(receipts);
      expectedResults.add(receipts);
    }

    final int msgSizeLimit = sizeLimit;
    setupEthServer(b -> b.maxMessageSize(msgSizeLimit));

    // Request all records, which will exceed the limit
    final GetReceiptsMessage bodiesMsg = GetReceiptsMessage.create(hashes);
    final EthMessage ethMsg = new EthMessage(ethPeer, bodiesMsg);

    // Check response
    final ReceiptsMessage expectedMsg = ReceiptsMessage.create(expectedResults);
    final Optional<MessageData> result = ethMessages.dispatch(ethMsg);
    assertThat(result).contains(expectedMsg);
  }

  @Test
  public void shouldLimitTxReceiptsByCount() {
    final int limit = 6;
    final Map<Hash, List<TransactionReceipt>> receiptsByHash = setupBlockReceipts(10);
    final List<Hash> hashes = new ArrayList<>(receiptsByHash.keySet());
    final List<List<TransactionReceipt>> expectedResults =
        receiptsByHash.values().stream().limit(limit).collect(Collectors.toList());

    setupEthServer(b -> b.maxGetReceipts(limit));

    // Request all records, which will exceed the limit
    final GetReceiptsMessage bodiesMsg = GetReceiptsMessage.create(hashes);
    final EthMessage ethMsg = new EthMessage(ethPeer, bodiesMsg);

    // Check response
    final ReceiptsMessage expectedMsg = ReceiptsMessage.create(expectedResults);
    final Optional<MessageData> result = ethMessages.dispatch(ethMsg);
    assertThat(result).contains(expectedMsg);
  }

  private void setupEthServer(Function<Builder, Builder> configModifier) {
    final Builder configBuilder = EthProtocolConfiguration.builder();
    final EthProtocolConfiguration ethConfig = configModifier.apply(configBuilder).build();

    new EthServer(blockchain, worldStateArchive, transactionPool, ethMessages, ethConfig);
  }

  private List<Block> setupBlocks(final int count) {
    final List<Block> blocks = dataGenerator.blockSequence(count);
    for (Block block : blocks) {
      when(blockchain.getBlockBody(block.getHash())).thenReturn(Optional.of(block.getBody()));
      when(blockchain.getBlockHeader(block.getHash())).thenReturn(Optional.of(block.getHeader()));
      when(blockchain.getBlockHeader(block.getHeader().getNumber()))
          .thenReturn(Optional.of(block.getHeader()));
    }

    return blocks;
  }

  private Map<Hash, List<TransactionReceipt>> setupBlockReceipts(final int count) {
    final Map<Hash, List<TransactionReceipt>> txReceiptsByHash = new HashMap<>();
    final List<Block> blocks = dataGenerator.blockSequence(count);
    for (Block block : blocks) {
      final List<TransactionReceipt> receipts = dataGenerator.receipts(block);
      when(blockchain.getTxReceipts(block.getHash())).thenReturn(Optional.of(receipts));
      txReceiptsByHash.put(block.getHash(), receipts);
    }

    return txReceiptsByHash;
  }

  private int calculateRlpEncodedSize(final BlockBody blockBody) {
    return RLP.encode(blockBody::writeTo).size();
  }

  private int calculateRlpEncodedSize(final BlockHeader header) {
    return RLP.encode(header::writeTo).size();
  }

  private int calculateRlpEncodedSize(final List<TransactionReceipt> receipts) {
    final BytesValueRLPOutput rlp = new BytesValueRLPOutput();
    rlp.startList();
    receipts.forEach(r -> r.writeTo(rlp));
    rlp.endList();
    return rlp.encodedSize();
  }
}
