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
package org.hyperledger.besu.ethereum.api;

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;

import java.util.Optional;

public class TransactionWithMetadata {

  private final Transaction transaction;
  private final Optional<Long> blockNumber;
  private final Optional<Hash> blockHash;
  private final Optional<Integer> transactionIndex;

  public TransactionWithMetadata(final Transaction transaction) {
    this.transaction = transaction;
    this.blockNumber = Optional.empty();
    this.blockHash = Optional.empty();
    this.transactionIndex = Optional.empty();
  }

  public TransactionWithMetadata(
      final Transaction transaction,
      final long blockNumber,
      final Hash blockHash,
      final int transactionIndex) {
    this.transaction = transaction;
    this.blockNumber = Optional.of(blockNumber);
    this.blockHash = Optional.of(blockHash);
    this.transactionIndex = Optional.of(transactionIndex);
  }

  public Transaction getTransaction() {
    return transaction;
  }

  public Optional<Long> getBlockNumber() {
    return blockNumber;
  }

  public Optional<Hash> getBlockHash() {
    return blockHash;
  }

  public Optional<Integer> getTransactionIndex() {
    return transactionIndex;
  }
}
