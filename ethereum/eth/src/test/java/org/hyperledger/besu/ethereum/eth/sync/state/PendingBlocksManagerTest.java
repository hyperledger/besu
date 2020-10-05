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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

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
    final int nbBlocks = 7;
    pendingBlocksManager =
        new PendingBlocksManager(
            SynchronizerConfiguration.builder().blockPropagationRange(-1, 2).build());
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Block parentBlock = gen.block();

    // add new blocks from node 1
    final List<Block> childBlockFromNodeOne = new ArrayList<>();
    for (int i = 0; i < nbBlocks; i++) {
      childBlockFromNodeOne.add(gen.nextBlock(parentBlock));
      pendingBlocksManager.registerPendingBlock(childBlockFromNodeOne.get(i), NODE_ID_1);
    }

    // add new block from node 2
    final Block childBlockFromNodeTwo = gen.nextBlock(parentBlock);
    pendingBlocksManager.registerPendingBlock(childBlockFromNodeTwo, NODE_ID_2);

    // check blocks from node 1 in the cache (node 1 could not add all its blocks -> limit 6 blocks)
    List<Block> pendingBlocksForParent = pendingBlocksManager.childrenOf(parentBlock.getHash());
    for (int i = 0; i < nbBlocks; i++) {
      if (i < nbBlocks - 1) {
        assertThat(pendingBlocksManager.contains(childBlockFromNodeOne.get(i).getHash())).isTrue();
        assertThat(pendingBlocksForParent).contains(childBlockFromNodeOne.get(i));
      } else {
        assertThat(pendingBlocksManager.contains(childBlockFromNodeOne.get(i).getHash())).isFalse();
        assertThat(pendingBlocksForParent).doesNotContain(childBlockFromNodeOne.get(i));
      }
    }
    // check blocks from node 2 in the cache (node 1 could not prevent node 2 from adding its
    // blocks)
    assertThat(pendingBlocksManager.contains(childBlockFromNodeTwo.getHash())).isTrue();
    assertThat(pendingBlocksForParent).contains(childBlockFromNodeTwo);

    // if we remove a block from node 1 it can add a new one
    pendingBlocksManager.deregisterPendingBlock(childBlockFromNodeOne.get(0));
    pendingBlocksManager.registerPendingBlock(childBlockFromNodeOne.get(6), NODE_ID_1);

    // check blocks from node 1 in the cache
    pendingBlocksForParent = pendingBlocksManager.childrenOf(parentBlock.getHash());
    for (int i = 0; i < nbBlocks; i++) {
      if (i != 0) {
        assertThat(pendingBlocksManager.contains(childBlockFromNodeOne.get(i).getHash())).isTrue();
        assertThat(pendingBlocksForParent).contains(childBlockFromNodeOne.get(i));
      } else {
        assertThat(pendingBlocksManager.contains(childBlockFromNodeOne.get(i).getHash())).isFalse();
        assertThat(pendingBlocksForParent).doesNotContain(childBlockFromNodeOne.get(i));
      }
    }

    // check blocks from node 2 in the cache
    assertThat(pendingBlocksManager.contains(childBlockFromNodeTwo.getHash())).isTrue();
    assertThat(pendingBlocksForParent).contains(childBlockFromNodeTwo);
  }
}
