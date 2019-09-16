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
package org.hyperledger.besu.ethereum.chain;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Transaction;

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

  public boolean isNewCanonicalHead() {
    return eventType != EventType.FORK;
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
