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
package org.hyperledger.besu.ethereum.api.query;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;

import java.util.Optional;

/** The type Transaction with metadata. */
public class TransactionWithMetadata {

  private final Transaction transaction;
  private final Optional<Long> blockNumber;
  private final Optional<Wei> baseFee;
  private final Optional<Hash> blockHash;
  private final Optional<Integer> transactionIndex;

  /**
   * Instantiates a new Transaction with metadata.
   *
   * @param transaction the transaction
   */
  public TransactionWithMetadata(final Transaction transaction) {
    this.transaction = transaction;
    this.blockNumber = Optional.empty();
    this.baseFee = Optional.empty();
    this.blockHash = Optional.empty();
    this.transactionIndex = Optional.empty();
  }

  /**
   * Instantiates a new Transaction with metadata.
   *
   * @param transaction the transaction
   * @param blockNumber the block number
   * @param baseFee the base fee
   * @param blockHash the block hash
   * @param transactionIndex the transaction index
   */
  public TransactionWithMetadata(
      final Transaction transaction,
      final long blockNumber,
      final Optional<Wei> baseFee,
      final Hash blockHash,
      final int transactionIndex) {
    this.transaction = transaction;
    this.blockNumber = Optional.of(blockNumber);
    this.baseFee = baseFee;
    this.blockHash = Optional.of(blockHash);
    this.transactionIndex = Optional.of(transactionIndex);
  }

  /**
   * Gets transaction.
   *
   * @return the transaction
   */
  public Transaction getTransaction() {
    return transaction;
  }

  /**
   * Gets block number.
   *
   * @return the block number
   */
  public Optional<Long> getBlockNumber() {
    return blockNumber;
  }

  /**
   * Gets base fee.
   *
   * @return the base fee
   */
  public Optional<Wei> getBaseFee() {
    return baseFee;
  }

  /**
   * Gets block hash.
   *
   * @return the block hash
   */
  public Optional<Hash> getBlockHash() {
    return blockHash;
  }

  /**
   * Gets transaction index.
   *
   * @return the transaction index
   */
  public Optional<Integer> getTransactionIndex() {
    return transactionIndex;
  }
}
