/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.plugins.graphql.internal.query;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.datatypes.Wei;

import java.util.Optional;

/**
 * A transaction together with its location on the chain, if it is included in a block. Pending
 * transactions have no location.
 */
public class TransactionWithMetadata {

  private final Transaction transaction;
  private final Optional<Long> blockNumber;
  private final Optional<Wei> baseFee;
  private final Optional<Hash> blockHash;
  private final Optional<Integer> transactionIndex;
  private final Optional<Long> blockTimestamp;

  /**
   * Creates a transaction that is not included in a block.
   *
   * @param transaction the transaction
   */
  public TransactionWithMetadata(final Transaction transaction) {
    this.transaction = transaction;
    this.blockNumber = Optional.empty();
    this.baseFee = Optional.empty();
    this.blockHash = Optional.empty();
    this.transactionIndex = Optional.empty();
    this.blockTimestamp = Optional.empty();
  }

  /**
   * Creates a transaction that is included in a block.
   *
   * @param transaction the transaction
   * @param blockNumber the number of the block
   * @param baseFee the base fee of the block
   * @param blockHash the hash of the block
   * @param transactionIndex the index of the transaction in the block
   * @param blockTimestamp the timestamp of the block
   */
  public TransactionWithMetadata(
      final Transaction transaction,
      final long blockNumber,
      final Optional<Wei> baseFee,
      final Hash blockHash,
      final int transactionIndex,
      final long blockTimestamp) {
    this.transaction = transaction;
    this.blockNumber = Optional.of(blockNumber);
    this.baseFee = baseFee;
    this.blockHash = Optional.of(blockHash);
    this.transactionIndex = Optional.of(transactionIndex);
    this.blockTimestamp = Optional.of(blockTimestamp);
  }

  /**
   * Returns the transaction.
   *
   * @return the transaction
   */
  public Transaction getTransaction() {
    return transaction;
  }

  /**
   * Returns the number of the block that includes the transaction.
   *
   * @return the block number, or empty for a pending transaction
   */
  public Optional<Long> getBlockNumber() {
    return blockNumber;
  }

  /**
   * Returns the base fee of the block that includes the transaction.
   *
   * @return the base fee, or empty for a pending transaction or a block without base fee
   */
  public Optional<Wei> getBaseFee() {
    return baseFee;
  }

  /**
   * Returns the hash of the block that includes the transaction.
   *
   * @return the block hash, or empty for a pending transaction
   */
  public Optional<Hash> getBlockHash() {
    return blockHash;
  }

  /**
   * Returns the index of the transaction in its block.
   *
   * @return the transaction index, or empty for a pending transaction
   */
  public Optional<Integer> getTransactionIndex() {
    return transactionIndex;
  }

  /**
   * Returns the timestamp of the block that includes the transaction.
   *
   * @return the block timestamp, or empty for a pending transaction
   */
  public Optional<Long> getBlockTimestamp() {
    return blockTimestamp;
  }
}
