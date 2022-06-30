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
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration.Builder;
import org.hyperledger.besu.ethereum.eth.messages.BlockBodiesMessage;
import org.hyperledger.besu.ethereum.eth.messages.GetBlockBodiesMessage;
import org.hyperledger.besu.ethereum.eth.messages.GetNodeDataMessage;
import org.hyperledger.besu.ethereum.eth.messages.NodeDataMessage;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.ArrayList;
import java.util.List;
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

  private void setupEthServer(Function<Builder, Builder> configModifier) {
    final Builder configBuilder = EthProtocolConfiguration.builder();
    final EthProtocolConfiguration ethConfig = configModifier.apply(configBuilder).build();

    new EthServer(blockchain, worldStateArchive, transactionPool, ethMessages, ethConfig);
  }

  private List<Block> setupBlocks(final int count) {
    final List<Block> blocks = dataGenerator.blockSequence(count);
    for (Block block : blocks) {
      when(blockchain.getBlockBody(block.getHash())).thenReturn(Optional.of(block.getBody()));
    }

    return blocks;
  }

  private int calculateRlpEncodedSize(final BlockBody blockBody) {
    final BytesValueRLPOutput rlp = new BytesValueRLPOutput();
    blockBody.writeTo(rlp);
    return rlp.encoded().size();
  }
}
