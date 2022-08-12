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
package org.hyperledger.besu.ethereum.eth.sync.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;

public class PendingBlocksManagerTest {

  private static final Bytes NODE_ID_1 = Bytes.fromHexString("0x00");
  private static final Bytes NODE_ID_2 = Bytes.fromHexString("0x01");

  private PendingBlocksManager pendingBlocksManager;
  private BlockDataGenerator gen;

  @Before
  public void setup() {
    pendingBlocksManager =
        new PendingBlocksManager(
            SynchronizerConfiguration.builder().blockPropagationRange(-10, 30).build());
    gen = new BlockDataGenerator();
  }

  @Test
  public void registerPendingBlock() {
    final Block block = gen.block();

    // Sanity check
    assertThat(pendingBlocksManager.contains(block.getHash())).isFalse();

    pendingBlocksManager.registerPendingBlock(block, NODE_ID_1);

    assertThat(pendingBlocksManager.contains(block.getHash())).isTrue();
    final List<Block> pendingBlocksForParent =
        pendingBlocksManager.childrenOf(block.getHeader().getParentHash());
    assertThat(pendingBlocksForParent).isEqualTo(Collections.singletonList(block));
  }

  @Test
  public void deregisterPendingBlock() {
    final Block block = gen.block();
    pendingBlocksManager.registerPendingBlock(block, NODE_ID_1);
    pendingBlocksManager.deregisterPendingBlock(block);

    assertThat(pendingBlocksManager.contains(block.getHash())).isFalse();
    final List<Block> pendingBlocksForParent =
        pendingBlocksManager.childrenOf(block.getHeader().getParentHash());
    assertThat(pendingBlocksForParent).isEqualTo(Collections.emptyList());
  }

  @Test
  public void registerSiblingBlocks() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Block parentBlock = gen.block();
    final Block childBlock = gen.nextBlock(parentBlock);
    final Block childBlock2 = gen.nextBlock(parentBlock);
    final List<Block> children = Arrays.asList(childBlock, childBlock2);

    pendingBlocksManager.registerPendingBlock(childBlock, NODE_ID_1);
    pendingBlocksManager.registerPendingBlock(childBlock2, NODE_ID_1);

    assertThat(pendingBlocksManager.contains(childBlock.getHash())).isTrue();
    assertThat(pendingBlocksManager.contains(childBlock2.getHash())).isTrue();

    final List<Block> pendingBlocksForParent =
        pendingBlocksManager.childrenOf(parentBlock.getHash());
    assertThat(pendingBlocksForParent.size()).isEqualTo(2);
    assertThat(new HashSet<>(pendingBlocksForParent)).isEqualTo(new HashSet<>(children));
  }

  @Test
  public void deregisterSubsetOfSiblingBlocks() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Block parentBlock = gen.block();
    final Block childBlock = gen.nextBlock(parentBlock);
    final Block childBlock2 = gen.nextBlock(parentBlock);

    pendingBlocksManager.registerPendingBlock(childBlock, NODE_ID_1);
    pendingBlocksManager.registerPendingBlock(childBlock2, NODE_ID_1);
    pendingBlocksManager.deregisterPendingBlock(childBlock);

    assertThat(pendingBlocksManager.contains(childBlock.getHash())).isFalse();
    assertThat(pendingBlocksManager.contains(childBlock2.getHash())).isTrue();

    final List<Block> pendingBlocksForParent =
        pendingBlocksManager.childrenOf(parentBlock.getHash());
    assertThat(pendingBlocksForParent).isEqualTo(Collections.singletonList(childBlock2));
  }

  @Test
  public void purgeBlocks() {
    pendingBlocksManager =
        new PendingBlocksManager(
            SynchronizerConfiguration.builder().blockPropagationRange(0, 15).build());
    final List<Block> blocks = gen.blockSequence(10);

    for (final Block block : blocks) {
      pendingBlocksManager.registerPendingBlock(block, NODE_ID_1);
      assertThat(pendingBlocksManager.contains(block.getHash())).isTrue();
    }

    final List<Block> blocksToPurge = blocks.subList(0, 5);
    final List<Block> blocksToKeep = blocks.subList(5, blocks.size());
    pendingBlocksManager.purgeBlocksOlderThan(blocksToKeep.get(0).getHeader().getNumber());

    for (final Block block : blocksToPurge) {
      assertThat(pendingBlocksManager.contains(block.getHash())).isFalse();
      assertThat(pendingBlocksManager.childrenOf(block.getHeader().getParentHash()).size())
          .isEqualTo(0);
    }
    for (final Block block : blocksToKeep) {
      assertThat(pendingBlocksManager.contains(block.getHash())).isTrue();
      assertThat(pendingBlocksManager.childrenOf(block.getHeader().getParentHash()).size())
          .isEqualTo(1);
    }
  }

  @Test
  public void shouldPreventNodeFromFillingCache() {
    final int nbBlocks = 4;
    pendingBlocksManager =
        new PendingBlocksManager(
            SynchronizerConfiguration.builder().blockPropagationRange(-1, 2).build());
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Block parentBlock = gen.block();

    // add new blocks from node 1
    final ArrayDeque<Block> childBlockFromNodeOne = new ArrayDeque<>();
    for (int i = 0; i < nbBlocks; i++) {
      final Block generatedBlock =
          gen.block(gen.nextBlockOptions(parentBlock).setTimestamp((long) i));
      childBlockFromNodeOne.add(generatedBlock);
      pendingBlocksManager.registerPendingBlock(generatedBlock, NODE_ID_1);
    }

    // add new block from node 2
    final Block childBlockFromNodeTwo = gen.nextBlock(parentBlock);
    pendingBlocksManager.registerPendingBlock(childBlockFromNodeTwo, NODE_ID_2);

    // check blocks from node 1 in the cache (node 1 should replace the lowest priority block)
    List<Block> pendingBlocksForParent = pendingBlocksManager.childrenOf(parentBlock.getHash());
    for (int i = 0; i < nbBlocks; i++) {
      final Block foundBlock = childBlockFromNodeOne.poll();
      if (i != 0) {
        assertThat(pendingBlocksManager.contains(foundBlock.getHash())).isTrue();
        assertThat(pendingBlocksForParent).contains(foundBlock);
      } else {
        assertThat(pendingBlocksManager.contains(foundBlock.getHash())).isFalse();
        assertThat(pendingBlocksForParent).doesNotContain(foundBlock);
      }
    }
    // check blocks from node 2 in the cache (node 1 could not prevent node 2 from adding its
    // blocks)
    assertThat(pendingBlocksManager.contains(childBlockFromNodeTwo.getHash())).isTrue();
    assertThat(pendingBlocksForParent).contains(childBlockFromNodeTwo);
  }

  @Test
  public void shouldReplaceLowestPriorityBlockWhenCacheIsFull() {
    pendingBlocksManager =
        new PendingBlocksManager(
            SynchronizerConfiguration.builder().blockPropagationRange(-1, 3).build());
    final BlockDataGenerator gen = new BlockDataGenerator();

    final ArrayDeque<Block> childBlockFromNodeOne = new ArrayDeque<>();

    // block 0
    childBlockFromNodeOne.add(
        gen.block(new BlockDataGenerator.BlockOptions().setBlockNumber(0).setTimestamp(0L)));
    pendingBlocksManager.registerPendingBlock(childBlockFromNodeOne.getLast(), NODE_ID_1);

    // block 1
    childBlockFromNodeOne.add(
        gen.block(
            gen.nextBlockOptions(childBlockFromNodeOne.element())
                .setBlockNumber(1)
                .setTimestamp(1L)));
    pendingBlocksManager.registerPendingBlock(childBlockFromNodeOne.getLast(), NODE_ID_1);

    // block 1 reorg
    final Block reorgBlock =
        gen.block(
            gen.nextBlockOptions(childBlockFromNodeOne.element())
                .setBlockNumber(1)
                .setTimestamp(3L));
    childBlockFromNodeOne.add(reorgBlock);
    pendingBlocksManager.registerPendingBlock(reorgBlock, NODE_ID_1);

    // block 2
    childBlockFromNodeOne.add(
        gen.block(
            gen.nextBlockOptions(childBlockFromNodeOne.element())
                .setBlockNumber(2)
                .setTimestamp(2L)));
    pendingBlocksManager.registerPendingBlock(childBlockFromNodeOne.getLast(), NODE_ID_1);

    assertThat(pendingBlocksManager.contains(reorgBlock.getHash())).isTrue();

    // try to add a new block (not added because low priority : block number too high)
    final Block lowPriorityBlock =
        gen.block(BlockDataGenerator.BlockOptions.create().setBlockNumber(10));
    pendingBlocksManager.registerPendingBlock(lowPriorityBlock, NODE_ID_1);
    assertThat(pendingBlocksManager.contains(lowPriorityBlock.getHash())).isFalse();

    // try to add a new block (added because high priority : low block number and high timestamp)
    final Block highPriorityBlock =
        gen.block(
            gen.nextBlockOptions(childBlockFromNodeOne.getFirst()).setTimestamp(Long.MAX_VALUE));
    pendingBlocksManager.registerPendingBlock(highPriorityBlock, NODE_ID_1);
    assertThat(pendingBlocksManager.contains(highPriorityBlock.getHash())).isTrue();

    // check blocks in the cache
    // and verify remove the block with the lowest priority (BLOCK-2)
    for (Block block : childBlockFromNodeOne) {
      if (block.getHeader().getNumber() == 2) {
        assertThat(pendingBlocksManager.contains(block.getHash())).isFalse();
      } else {
        assertThat(pendingBlocksManager.contains(block.getHash())).isTrue();
      }
    }
    assertThat(pendingBlocksManager.contains(reorgBlock.getHash())).isTrue();
  }

  @Test
  public void shouldReturnLowestBlockByNumber() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Block parentBlock = gen.block();
    final Block childBlock = gen.nextBlock(parentBlock);
    final Block childBlock2 = gen.nextBlock(parentBlock);

    pendingBlocksManager.registerPendingBlock(parentBlock, NODE_ID_1);
    pendingBlocksManager.registerPendingBlock(childBlock, NODE_ID_1);
    pendingBlocksManager.registerPendingBlock(childBlock2, NODE_ID_1);

    assertThat(pendingBlocksManager.lowestAnnouncedBlock()).contains(parentBlock.getHeader());
  }

  @Test
  public void shouldReturnLowestAncestorPendingBlock() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Block parentBlock = gen.block();

    final Block childBlock = gen.nextBlock(parentBlock);
    final Block childBlock2 = gen.nextBlock(childBlock);
    final Block childBlock3 = gen.nextBlock(childBlock2);

    pendingBlocksManager.registerPendingBlock(parentBlock, NODE_ID_1);
    pendingBlocksManager.registerPendingBlock(childBlock2, NODE_ID_1);
    pendingBlocksManager.registerPendingBlock(childBlock3, NODE_ID_1);

    Optional<Block> block =
        pendingBlocksManager.pendingAncestorBlockOf(childBlock3, pendingBlocksManager.size());
    assertThat(block.isPresent()).isTrue();
    assertThat(block.get().getHeader().getHash()).isEqualTo(childBlock2.getHeader().getHash());
    assertThat(block.get().getHeader().getParentHash()).isEqualTo(childBlock.getHeader().getHash());
  }

  @Test
  public void shouldReturnLowestAncestorPendingBlock_maxAncestorLevel() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Block block = gen.block();
    final Block childBlock1 = gen.nextBlock(block);
    final Block childBlock2 = gen.nextBlock(childBlock1);
    final Block childBlock3 = gen.nextBlock(childBlock2);

    pendingBlocksManager.registerPendingBlock(block, NODE_ID_1);
    pendingBlocksManager.registerPendingBlock(childBlock1, NODE_ID_1);
    pendingBlocksManager.registerPendingBlock(childBlock2, NODE_ID_1);
    pendingBlocksManager.registerPendingBlock(childBlock3, NODE_ID_1);

    // Must return the lowest pending block in the chain
    Optional<Block> ancestor =
        pendingBlocksManager.pendingAncestorBlockOf(childBlock3, pendingBlocksManager.size());
    assertThat(ancestor.get().getHeader().getHash()).isEqualTo(block.getHeader().getHash());

    Optional<Block> ancestor0 = pendingBlocksManager.pendingAncestorBlockOf(childBlock3, 0);
    assertThat(ancestor0.get().getHeader().getHash()).isEqualTo(childBlock3.getHeader().getHash());

    Optional<Block> ancestor1 = pendingBlocksManager.pendingAncestorBlockOf(childBlock3, 1);
    assertThat(ancestor1.get().getHeader().getHash()).isEqualTo(childBlock2.getHeader().getHash());

    Optional<Block> ancestor2 = pendingBlocksManager.pendingAncestorBlockOf(childBlock3, 2);
    assertThat(ancestor2.get().getHeader().getHash()).isEqualTo(childBlock1.getHeader().getHash());
  }

  @Test
  public void shouldReturnLowestAncestorPendingBlock_sameBlock() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Block block = gen.block();
    pendingBlocksManager.registerPendingBlock(block, NODE_ID_1);
    Optional<Block> b =
        pendingBlocksManager.pendingAncestorBlockOf(block, pendingBlocksManager.size());
    assertThat(b).contains(block);
  }
}
