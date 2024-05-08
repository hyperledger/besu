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
import org.hyperledger.besu.ethereum.core.TransactionReceipt;

import java.util.Optional;

/** The type Transaction receipt with metadata. */
public class TransactionReceiptWithMetadata {
  private final TransactionReceipt receipt;
  private final Hash transactionHash;
  private final int transactionIndex;
  private final long gasUsed;
  private final Optional<Wei> baseFee;
  private final long blockNumber;
  private final Hash blockHash;
  private final Transaction transaction;
  private final Optional<Long> blobGasUsed;
  private final Optional<Wei> blobGasPrice;
  private final int logIndexOffset;

  private TransactionReceiptWithMetadata(
      final TransactionReceipt receipt,
      final Transaction transaction,
      final Hash transactionHash,
      final int transactionIndex,
      final long gasUsed,
      final Optional<Wei> baseFee,
      final Hash blockHash,
      final long blockNumber,
      final Optional<Long> blobGasUsed,
      final Optional<Wei> blobGasPrice,
      final int logIndexOffset) {
    this.receipt = receipt;
    this.transactionHash = transactionHash;
    this.transactionIndex = transactionIndex;
    this.gasUsed = gasUsed;
    this.baseFee = baseFee;
    this.blockHash = blockHash;
    this.blockNumber = blockNumber;
    this.transaction = transaction;
    this.blobGasUsed = blobGasUsed;
    this.blobGasPrice = blobGasPrice;
    this.logIndexOffset = logIndexOffset;
  }

  /**
   * Create transaction receipt with metadata.
   *
   * @param receipt the receipt
   * @param transaction the transaction
   * @param transactionHash the transaction hash
   * @param transactionIndex the transaction index
   * @param gasUsed the gas used
   * @param baseFee the base fee
   * @param blockHash the block hash
   * @param blockNumber the block number
   * @param blobGasUsed the blob gas used
   * @param blobGasPrice the blob gas price
   * @param logIndexOffset the log index offset
   * @return the transaction receipt with metadata
   */
  public static TransactionReceiptWithMetadata create(
      final TransactionReceipt receipt,
      final Transaction transaction,
      final Hash transactionHash,
      final int transactionIndex,
      final long gasUsed,
      final Optional<Wei> baseFee,
      final Hash blockHash,
      final long blockNumber,
      final Optional<Long> blobGasUsed,
      final Optional<Wei> blobGasPrice,
      final int logIndexOffset) {
    return new TransactionReceiptWithMetadata(
        receipt,
        transaction,
        transactionHash,
        transactionIndex,
        gasUsed,
        baseFee,
        blockHash,
        blockNumber,
        blobGasUsed,
        blobGasPrice,
        logIndexOffset);
  }

  /**
   * Gets receipt.
   *
   * @return the receipt
   */
  public TransactionReceipt getReceipt() {
    return receipt;
  }

  /**
   * Gets transaction hash.
   *
   * @return the transaction hash
   */
  public Hash getTransactionHash() {
    return transactionHash;
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
   * Gets transaction index.
   *
   * @return the transaction index
   */
  public int getTransactionIndex() {
    return transactionIndex;
  }

  /**
   * Gets block hash.
   *
   * @return the block hash
   */
  public Hash getBlockHash() {
    return blockHash;
  }

  /**
   * Gets block number.
   *
   * @return the block number
   */
  public long getBlockNumber() {
    return blockNumber;
  }

  /**
   * Gets gas used.
   *
   * @return the gas used
   */
  // The gas used for this particular transaction (as opposed to cumulativeGas which is included in
  // the receipt itself)
  public long getGasUsed() {
    return gasUsed;
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
   * Gets blob gas used.
   *
   * @return the blob gas used
   */
  public Optional<Long> getBlobGasUsed() {
    return blobGasUsed;
  }

  /**
   * Gets blob gas price.
   *
   * @return the blob gas price
   */
  public Optional<Wei> getBlobGasPrice() {
    return blobGasPrice;
  }

  /**
   * Gets log index offset.
   *
   * @return the log index offset
   */
  public int getLogIndexOffset() {
    return logIndexOffset;
  }
}
