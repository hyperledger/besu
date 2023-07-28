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
package org.hyperledger.besu.ethereum.eth.sync.state.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PendingBlockCacheTest {

  private static final Bytes NODE_ID = Bytes.of(0);
  private static final Bytes NODE_ID_2 = Bytes.of(1);

  private BlockDataGenerator gen;

  private PendingBlockCache pendingBlockCache;

  private Block parentBlock;

  @BeforeEach
  public void setup() {
    gen = new BlockDataGenerator();
    parentBlock = gen.block();
    pendingBlockCache = new PendingBlockCache(2);
  }

  @Test
  public void shouldReplaceLowestPriorityBlockWhenCacheFull() {
    final ImmutablePendingBlock firstBlock = generateBlock(parentBlock, NODE_ID);

    final ImmutablePendingBlock firstBlockFromSecondNode =
        generateBlock(firstBlock.block(), NODE_ID_2);
    pendingBlockCache.putIfAbsent(
        firstBlockFromSecondNode.block().getHash(), firstBlockFromSecondNode);

    final ImmutablePendingBlock secondBlock = generateBlock(firstBlock.block(), NODE_ID);
    final ImmutablePendingBlock thirdBlock = generateBlock(firstBlock.block(), NODE_ID);
    final ImmutablePendingBlock fourthBlock = generateBlock(firstBlock.block(), NODE_ID);
    final ImmutablePendingBlock fifthBlock = generateBlock(parentBlock, NODE_ID);

    pendingBlockCache.putIfAbsent(thirdBlock.block().getHash(), thirdBlock);
    pendingBlockCache.putIfAbsent(secondBlock.block().getHash(), secondBlock);

    // add more recent block (timestamp)
    pendingBlockCache.putIfAbsent(fourthBlock.block().getHash(), fourthBlock);
    assertThat(pendingBlockCache.values())
        .containsExactlyInAnyOrder(thirdBlock, fourthBlock, firstBlockFromSecondNode);

    // add a block with a number lower than the others already present
    pendingBlockCache.putIfAbsent(firstBlock.block().getHash(), firstBlock);
    assertThat(pendingBlockCache.values())
        .containsExactlyInAnyOrder(firstBlock, fourthBlock, firstBlockFromSecondNode);

    // add a block with a number lower than the others already present and higher timestamp
    pendingBlockCache.putIfAbsent(fifthBlock.block().getHash(), fifthBlock);
    assertThat(pendingBlockCache.values())
        .containsExactlyInAnyOrder(firstBlock, fifthBlock, firstBlockFromSecondNode);
  }

  @Test
  public void shouldNotAddAlreadyPresentBlock() {
    final ImmutablePendingBlock firstBlock = generateBlock(parentBlock, NODE_ID);
    final ImmutablePendingBlock secondBlock = generateBlock(parentBlock, NODE_ID);

    assertThat(pendingBlockCache.putIfAbsent(firstBlock.block().getHash(), firstBlock)).isNull();
    assertThat(pendingBlockCache.putIfAbsent(secondBlock.block().getHash(), secondBlock)).isNull();
    assertThat(pendingBlockCache.putIfAbsent(firstBlock.block().getHash(), firstBlock))
        .isEqualTo(firstBlock);

    assertThat(pendingBlockCache.values()).containsExactlyInAnyOrder(firstBlock, secondBlock);
  }

  @Test
  public void shouldAcceptBlockFromMultipleNode() {
    final ImmutablePendingBlock firstBlockFromFirstNode = generateBlock(parentBlock, NODE_ID);
    final ImmutablePendingBlock secondBlockFromFirstNode = generateBlock(parentBlock, NODE_ID);

    pendingBlockCache.putIfAbsent(
        firstBlockFromFirstNode.block().getHash(), firstBlockFromFirstNode);
    pendingBlockCache.putIfAbsent(
        secondBlockFromFirstNode.block().getHash(), secondBlockFromFirstNode);

    final ImmutablePendingBlock firstBlockFromSecondNode = generateBlock(parentBlock, NODE_ID_2);
    pendingBlockCache.putIfAbsent(
        firstBlockFromSecondNode.block().getHash(), firstBlockFromSecondNode);

    assertThat(pendingBlockCache.values())
        .containsExactlyInAnyOrder(
            firstBlockFromFirstNode, secondBlockFromFirstNode, firstBlockFromSecondNode);
  }

  @Test
  public void shouldNotAddBlockWhenCacheFullAndNewBlockNumberTooHigh() {
    final ImmutablePendingBlock firstBlock = generateBlock(parentBlock, NODE_ID);
    final ImmutablePendingBlock secondBlock = generateBlock(parentBlock, NODE_ID);
    final ImmutablePendingBlock thirdBlock = generateBlock(secondBlock.block(), NODE_ID);

    pendingBlockCache.putIfAbsent(firstBlock.block().getHash(), firstBlock);
    pendingBlockCache.putIfAbsent(secondBlock.block().getHash(), secondBlock);
    pendingBlockCache.putIfAbsent(thirdBlock.block().getHash(), thirdBlock);

    assertThat(pendingBlockCache.values()).containsExactlyInAnyOrder(firstBlock, secondBlock);
  }

  @Test
  public void shouldNotAddBlockWhenCacheFullAndNewBlockTimestampTooLow() {
    final ImmutablePendingBlock firstBlock = generateBlock(parentBlock, NODE_ID);
    final ImmutablePendingBlock secondBlock = generateBlock(parentBlock, NODE_ID);
    final ImmutablePendingBlock thirdBlock = generateBlock(parentBlock, NODE_ID);

    pendingBlockCache.putIfAbsent(secondBlock.block().getHash(), secondBlock);
    pendingBlockCache.putIfAbsent(thirdBlock.block().getHash(), thirdBlock);
    pendingBlockCache.putIfAbsent(firstBlock.block().getHash(), firstBlock);

    assertThat(pendingBlockCache.values()).containsExactlyInAnyOrder(secondBlock, thirdBlock);
  }

  private ImmutablePendingBlock generateBlock(final Block parentBlock, final Bytes nodeId) {
    final Block block =
        gen.block(gen.nextBlockOptions(parentBlock).setTimestamp(System.nanoTime()));
    return ImmutablePendingBlock.builder().block(block).nodeId(nodeId).build();
  }
}
