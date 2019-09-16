/*
 * Copyright 2018 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.eth.sync.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class PendingBlocksTest {

  private PendingBlocks pendingBlocks;
  private BlockDataGenerator gen;

  @Before
  public void setup() {
    pendingBlocks = new PendingBlocks();
    gen = new BlockDataGenerator();
  }

  @Test
  public void registerPendingBlock() {
    final Block block = gen.block();

    // Sanity check
    assertThat(pendingBlocks.contains(block.getHash())).isFalse();

    pendingBlocks.registerPendingBlock(block);

    assertThat(pendingBlocks.contains(block.getHash())).isTrue();
    final List<Block> pendingBlocksForParent =
        pendingBlocks.childrenOf(block.getHeader().getParentHash());
    assertThat(pendingBlocksForParent).isEqualTo(Collections.singletonList(block));
  }

  @Test
  public void deregisterPendingBlock() {
    final Block block = gen.block();
    pendingBlocks.registerPendingBlock(block);
    pendingBlocks.deregisterPendingBlock(block);

    assertThat(pendingBlocks.contains(block.getHash())).isFalse();
    final List<Block> pendingBlocksForParent =
        pendingBlocks.childrenOf(block.getHeader().getParentHash());
    assertThat(pendingBlocksForParent).isEqualTo(Collections.emptyList());
  }

  @Test
  public void registerSiblingBlocks() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Block parentBlock = gen.block();
    final Block childBlock = gen.nextBlock(parentBlock);
    final Block childBlock2 = gen.nextBlock(parentBlock);
    final List<Block> children = Arrays.asList(childBlock, childBlock2);

    pendingBlocks.registerPendingBlock(childBlock);
    pendingBlocks.registerPendingBlock(childBlock2);

    assertThat(pendingBlocks.contains(childBlock.getHash())).isTrue();
    assertThat(pendingBlocks.contains(childBlock2.getHash())).isTrue();

    final List<Block> pendingBlocksForParent = pendingBlocks.childrenOf(parentBlock.getHash());
    assertThat(pendingBlocksForParent.size()).isEqualTo(2);
    assertThat(new HashSet<>(pendingBlocksForParent)).isEqualTo(new HashSet<>(children));
  }

  @Test
  public void deregisterSubsetOfSiblingBlocks() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Block parentBlock = gen.block();
    final Block childBlock = gen.nextBlock(parentBlock);
    final Block childBlock2 = gen.nextBlock(parentBlock);

    pendingBlocks.registerPendingBlock(childBlock);
    pendingBlocks.registerPendingBlock(childBlock2);
    pendingBlocks.deregisterPendingBlock(childBlock);

    assertThat(pendingBlocks.contains(childBlock.getHash())).isFalse();
    assertThat(pendingBlocks.contains(childBlock2.getHash())).isTrue();

    final List<Block> pendingBlocksForParent = pendingBlocks.childrenOf(parentBlock.getHash());
    assertThat(pendingBlocksForParent).isEqualTo(Collections.singletonList(childBlock2));
  }

  @Test
  public void purgeBlocks() {
    final List<Block> blocks = gen.blockSequence(10);

    for (final Block block : blocks) {
      pendingBlocks.registerPendingBlock(block);
      assertThat(pendingBlocks.contains(block.getHash())).isTrue();
    }

    final List<Block> blocksToPurge = blocks.subList(0, 5);
    final List<Block> blocksToKeep = blocks.subList(5, blocks.size());
    pendingBlocks.purgeBlocksOlderThan(blocksToKeep.get(0).getHeader().getNumber());

    for (final Block block : blocksToPurge) {
      assertThat(pendingBlocks.contains(block.getHash())).isFalse();
      assertThat(pendingBlocks.childrenOf(block.getHeader().getParentHash()).size()).isEqualTo(0);
    }
    for (final Block block : blocksToKeep) {
      assertThat(pendingBlocks.contains(block.getHash())).isTrue();
      assertThat(pendingBlocks.childrenOf(block.getHeader().getParentHash()).size()).isEqualTo(1);
    }
  }
}
