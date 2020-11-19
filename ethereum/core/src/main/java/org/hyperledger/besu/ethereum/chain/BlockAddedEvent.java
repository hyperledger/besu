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
package org.hyperledger.besu.ethereum.chain;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.LogWithMetadata;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.transaction.TypedTransaction;

import java.util.Collections;
import java.util.List;

public class BlockAddedEvent {

  private final Block block;
  private final List<TypedTransaction> addedTransactions;
  private final List<TypedTransaction> removedTransactions;
  private final List<TransactionReceipt> transactionReceipts;
  private final EventType eventType;
  private final List<LogWithMetadata> logsWithMetadata;
  private final Hash commonAncestorHash;

  public enum EventType {
    HEAD_ADVANCED,
    FORK,
    CHAIN_REORG
  }

  private BlockAddedEvent(
      final EventType eventType,
      final Block block,
      final List<TypedTransaction> addedTransactions,
      final List<TypedTransaction> removedTransactions,
      final List<TransactionReceipt> transactionReceipts,
      final List<LogWithMetadata> logsWithMetadata,
      final Hash commonAncestorHash) {
    this.eventType = eventType;
    this.block = block;
    this.addedTransactions = addedTransactions;
    this.removedTransactions = removedTransactions;
    this.transactionReceipts = transactionReceipts;
    this.logsWithMetadata = logsWithMetadata;
    this.commonAncestorHash = commonAncestorHash;
  }

  public static BlockAddedEvent createForHeadAdvancement(
      final Block block,
      final List<LogWithMetadata> logsWithMetadata,
      final List<TransactionReceipt> transactionReceipts) {
    return new BlockAddedEvent(
        EventType.HEAD_ADVANCED,
        block,
        block.getBody().getTransactions(),
        Collections.emptyList(),
        transactionReceipts,
        logsWithMetadata,
        block.getHeader().getParentHash());
  }

  public static BlockAddedEvent createForChainReorg(
      final Block block,
      final List<TypedTransaction> addedTransactions,
      final List<TypedTransaction> removedTransactions,
      final List<TransactionReceipt> transactionReceipts,
      final List<LogWithMetadata> logsWithMetadata,
      final Hash commonAncestorHash) {
    return new BlockAddedEvent(
        EventType.CHAIN_REORG,
        block,
        addedTransactions,
        removedTransactions,
        transactionReceipts,
        logsWithMetadata,
        commonAncestorHash);
  }

  public static BlockAddedEvent createForFork(final Block block) {
    return new BlockAddedEvent(
        EventType.FORK,
        block,
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        block.getHeader().getParentHash());
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

  public List<TypedTransaction> getAddedTransactions() {
    return addedTransactions;
  }

  public List<TypedTransaction> getRemovedTransactions() {
    return removedTransactions;
  }

  public List<TransactionReceipt> getTransactionReceipts() {
    return transactionReceipts;
  }

  public List<LogWithMetadata> getLogsWithMetadata() {
    return logsWithMetadata;
  }

  public Hash getCommonAncestorHash() {
    return commonAncestorHash;
  }
}
