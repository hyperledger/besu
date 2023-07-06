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
package org.hyperledger.besu.ethereum.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.Random;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class BlockchainUtilParameterizedTest {
  private static final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();
  private static final Random random = new Random(1337);

  private static final int chainHeight = 89;
  private final int commonAncestorHeight;
  private static Block genesisBlock;
  private static MutableBlockchain localBlockchain;

  private MutableBlockchain remoteBlockchain;

  private BlockHeader commonHeader;
  private List<BlockHeader> headers;

  public BlockchainUtilParameterizedTest(final int commonAncestorHeight) {
    this.commonAncestorHeight = commonAncestorHeight;
  }

  @BeforeClass
  public static void setupClass() {
    genesisBlock = blockDataGenerator.genesisBlock();
    localBlockchain = InMemoryKeyValueStorageProvider.createInMemoryBlockchain(genesisBlock);
    // Setup local chain.
    for (int i = 1; i <= chainHeight; i++) {
      final BlockDataGenerator.BlockOptions options =
          new BlockDataGenerator.BlockOptions()
              .setBlockNumber(i)
              .setParentHash(localBlockchain.getBlockHashByNumber(i - 1).get());
      final Block block = blockDataGenerator.block(options);
      final List<TransactionReceipt> receipts = blockDataGenerator.receipts(block);
      localBlockchain.appendBlock(block, receipts);
    }
  }

  @Before
  public void setup() {
    remoteBlockchain = InMemoryKeyValueStorageProvider.createInMemoryBlockchain(genesisBlock);

    commonHeader = genesisBlock.getHeader();
    for (long i = 1; i <= commonAncestorHeight; i++) {
      commonHeader = localBlockchain.getBlockHeader(i).get();
      final List<TransactionReceipt> receipts =
          localBlockchain.getTxReceipts(commonHeader.getHash()).get();
      final BlockBody commonBody = localBlockchain.getBlockBody(commonHeader.getHash()).get();
      remoteBlockchain.appendBlock(new Block(commonHeader, commonBody), receipts);
    }
    // Remaining blocks are disparate.
    for (long i = commonAncestorHeight + 1L; i <= chainHeight; i++) {
      final BlockDataGenerator.BlockOptions localOptions =
          new BlockDataGenerator.BlockOptions()
              .setBlockNumber(i)
              .setParentHash(localBlockchain.getBlockHashByNumber(i - 1).get());
      final Block localBlock = blockDataGenerator.block(localOptions);
      final List<TransactionReceipt> localReceipts = blockDataGenerator.receipts(localBlock);
      localBlockchain.appendBlock(localBlock, localReceipts);

      final BlockDataGenerator.BlockOptions remoteOptions =
          new BlockDataGenerator.BlockOptions()
              .setDifficulty(Difficulty.ONE) // differentiator
              .setBlockNumber(i)
              .setParentHash(remoteBlockchain.getBlockHashByNumber(i - 1).get());
      final Block remoteBlock = blockDataGenerator.block(remoteOptions);
      final List<TransactionReceipt> remoteReceipts = blockDataGenerator.receipts(remoteBlock);
      remoteBlockchain.appendBlock(remoteBlock, remoteReceipts);
    }
    headers = new ArrayList<>();
    for (long i = 0L; i <= remoteBlockchain.getChainHeadBlockNumber(); i++) {
      headers.add(remoteBlockchain.getBlockHeader(i).get());
    }
  }

  @Parameterized.Parameters(name = "commonAncestor={0}")
  public static Collection<Object[]> parameters() {
    final List<Object[]> params = new ArrayList<>();
    params.add(new Object[] {0});
    params.add(new Object[] {chainHeight});
    params.add(new Object[] {random.nextInt(chainHeight - 1) + 1});
    params.add(new Object[] {random.nextInt(chainHeight - 1) + 1});
    params.add(new Object[] {random.nextInt(chainHeight - 1) + 1});
    return params;
  }

  @Test
  public void searchesAscending() {
    final OptionalInt maybeAncestorNumber =
        BlockchainUtil.findHighestKnownBlockIndex(localBlockchain, headers, true);
    assertThat(maybeAncestorNumber.getAsInt()).isEqualTo(Math.toIntExact(commonHeader.getNumber()));
  }

  @Test
  public void searchesDescending() {
    Collections.reverse(headers);
    final OptionalInt maybeAncestorNumber =
        BlockchainUtil.findHighestKnownBlockIndex(localBlockchain, headers, false);
    assertThat(maybeAncestorNumber.getAsInt())
        .isEqualTo(Math.toIntExact(chainHeight - commonHeader.getNumber()));
  }
}
