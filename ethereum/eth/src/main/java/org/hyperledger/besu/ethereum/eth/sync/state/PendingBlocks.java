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

import static java.util.Collections.newSetFromMap;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Hash;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PendingBlocks {

  private final Map<Hash, Block> pendingBlocks = new ConcurrentHashMap<>();
  private final Map<Hash, Set<Hash>> pendingBlocksByParentHash = new ConcurrentHashMap<>();

  /**
   * Track the given block.
   *
   * @param pendingBlock the block to track
   * @return true if the block was added (was not previously present)
   */
  public boolean registerPendingBlock(final Block pendingBlock) {
    final Block previousValue =
        this.pendingBlocks.putIfAbsent(pendingBlock.getHash(), pendingBlock);
    if (previousValue != null) {
      return false;
    }

    pendingBlocksByParentHash
        .computeIfAbsent(
            pendingBlock.getHeader().getParentHash(),
            h -> {
              final Set<Hash> set = newSetFromMap(new ConcurrentHashMap<>());
              // Go ahead and add our value at construction, so that we don't set an empty set which
              // could be removed in deregisterPendingBlock
              set.add(pendingBlock.getHash());
              return set;
            })
        .add(pendingBlock.getHash());

    return true;
  }

  /**
   * Stop tracking the given block.
   *
   * @param block the block that is no longer pending
   * @return true if this block was removed
   */
  public boolean deregisterPendingBlock(final Block block) {
    final Hash parentHash = block.getHeader().getParentHash();
    final Block removed = pendingBlocks.remove(block.getHash());
    final Set<Hash> blocksForParent = pendingBlocksByParentHash.get(parentHash);
    if (blocksForParent != null) {
      blocksForParent.remove(block.getHash());
      pendingBlocksByParentHash.remove(parentHash, Collections.emptySet());
    }
    return removed != null;
  }

  public void purgeBlocksOlderThan(final long blockNumber) {
    pendingBlocks.values().stream()
        .filter(b -> b.getHeader().getNumber() < blockNumber)
        .forEach(this::deregisterPendingBlock);
  }

  public boolean contains(final Hash blockHash) {
    return pendingBlocks.containsKey(blockHash);
  }

  public List<Block> childrenOf(final Hash parentBlock) {
    final Set<Hash> blocksByParent = pendingBlocksByParentHash.get(parentBlock);
    if (blocksByParent == null || blocksByParent.size() == 0) {
      return Collections.emptyList();
    }
    return blocksByParent.stream()
        .map(pendingBlocks::get)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }
}
