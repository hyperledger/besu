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
import org.hyperledger.besu.plugin.data.TransactionReceipt;

import java.util.Optional;

/**
 * A transaction receipt together with the values derived from its block: the gas used by the
 * transaction alone, the offset of its first log in the block, and its blob gas used and price.
 *
 * @param receipt the receipt
 * @param transaction the transaction
 * @param transactionHash the hash of the transaction
 * @param transactionIndex the index of the transaction in its block
 * @param gasUsed the gas used by the transaction alone
 * @param baseFee the base fee of the block
 * @param blockHash the hash of the block
 * @param blockTimestamp the timestamp of the block
 * @param blockNumber the number of the block
 * @param blobGasUsed the blob gas used by a blob transaction, empty otherwise
 * @param blobGasPrice the blob gas price paid by a blob transaction, empty otherwise
 * @param logIndexOffset the index in the block of the first log of the transaction
 */
public record TransactionReceiptWithMetadata(
    TransactionReceipt receipt,
    Transaction transaction,
    Hash transactionHash,
    int transactionIndex,
    long gasUsed,
    Optional<Wei> baseFee,
    Hash blockHash,
    long blockTimestamp,
    long blockNumber,
    Optional<Long> blobGasUsed,
    Optional<Wei> blobGasPrice,
    int logIndexOffset) {

  /**
   * Returns the receipt.
   *
   * @return the receipt
   */
  public TransactionReceipt getReceipt() {
    return receipt;
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
   * Returns the gas used by the transaction alone.
   *
   * @return the gas used
   */
  public long getGasUsed() {
    return gasUsed;
  }

  /**
   * Returns the base fee of the block.
   *
   * @return the base fee
   */
  public Optional<Wei> getBaseFee() {
    return baseFee;
  }

  /**
   * Returns the blob gas used by a blob transaction.
   *
   * @return the blob gas used, empty if the transaction is not a blob transaction
   */
  public Optional<Long> getBlobGasUsed() {
    return blobGasUsed;
  }

  /**
   * Returns the blob gas price paid by a blob transaction.
   *
   * @return the blob gas price, empty if the transaction is not a blob transaction
   */
  public Optional<Wei> getBlobGasPrice() {
    return blobGasPrice;
  }
}
