package net.consensys.pantheon.ethereum.chain;

import net.consensys.pantheon.ethereum.core.Block;
import net.consensys.pantheon.ethereum.core.Transaction;

import java.util.Collections;
import java.util.List;

public class BlockAddedEvent {

  private final Block block;
  private final List<Transaction> addedTransactions;
  private final List<Transaction> removedTransactions;
  private final EventType eventType;

  public enum EventType {
    HEAD_ADVANCED,
    FORK,
    CHAIN_REORG
  }

  private BlockAddedEvent(
      final EventType eventType,
      final Block block,
      final List<Transaction> addedTransactions,
      final List<Transaction> removedTransactions) {
    this.eventType = eventType;
    this.block = block;
    this.addedTransactions = addedTransactions;
    this.removedTransactions = removedTransactions;
  }

  public static BlockAddedEvent createForHeadAdvancement(final Block block) {
    return new BlockAddedEvent(
        EventType.HEAD_ADVANCED, block, block.getBody().getTransactions(), Collections.emptyList());
  }

  public static BlockAddedEvent createForChainReorg(
      final Block block,
      final List<Transaction> addedTransactions,
      final List<Transaction> removedTransactions) {
    return new BlockAddedEvent(
        EventType.CHAIN_REORG, block, addedTransactions, removedTransactions);
  }

  public static BlockAddedEvent createForFork(final Block block) {
    return new BlockAddedEvent(
        EventType.FORK, block, Collections.emptyList(), Collections.emptyList());
  }

  public Block getBlock() {
    return block;
  }

  public EventType getEventType() {
    return eventType;
  }

  public List<Transaction> getAddedTransactions() {
    return addedTransactions;
  }

  public List<Transaction> getRemovedTransactions() {
    return removedTransactions;
  }
}
