package net.consensys.pantheon.ethereum.eth.sync.state;

import net.consensys.pantheon.ethereum.core.Block;
import net.consensys.pantheon.ethereum.core.Hash;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import io.netty.util.internal.ConcurrentSet;

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
              final ConcurrentSet<Hash> set = new ConcurrentSet<>();
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
    pendingBlocks
        .values()
        .stream()
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
    return blocksByParent
        .stream()
        .map(pendingBlocks::get)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }
}
