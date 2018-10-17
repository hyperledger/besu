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
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Transaction;

public class TransactionWithMetadata {

  private final Transaction transaction;
  private final long blockNumber;
  private final Hash blockHash;
  private final int transactionIndex;

  public TransactionWithMetadata(
      final Transaction transaction,
      final long blockNumber,
      final Hash blockHash,
      final int transactionIndex) {
    this.transaction = transaction;
    this.blockNumber = blockNumber;
    this.blockHash = blockHash;
    this.transactionIndex = transactionIndex;
  }

  public Transaction getTransaction() {
    return transaction;
  }

  public long getBlockNumber() {
    return blockNumber;
  }

  public Hash getBlockHash() {
    return blockHash;
  }

  public int getTransactionIndex() {
    return transactionIndex;
  }
}
