/*
 * Copyright Besu contributors.
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
package org.hyperledger.besu.ethereum.eth.transactions;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Tracks the additional metadata associated with transactions to enable prioritization for mining
 * and deciding which transactions to drop when the transaction pool reaches its size limit.
 */
public abstract class PendingTransaction {

  private static final AtomicLong TRANSACTIONS_ADDED = new AtomicLong();
  private final Transaction transaction;
  private final long addedToPoolAt;
  private final long sequence; // Allows prioritization based on order transactions are added

  protected PendingTransaction(final Transaction transaction, final long addedToPoolAt) {
    this.transaction = transaction;
    this.addedToPoolAt = addedToPoolAt;
    this.sequence = TRANSACTIONS_ADDED.getAndIncrement();
  }

  public Transaction getTransaction() {
    return transaction;
  }

  public Wei getGasPrice() {
    return transaction.getGasPrice().orElse(Wei.ZERO);
  }

  public long getSequence() {
    return sequence;
  }

  public long getNonce() {
    return transaction.getNonce();
  }

  public Address getSender() {
    return transaction.getSender();
  }

  public abstract boolean isReceivedFromLocalSource();

  public Hash getHash() {
    return transaction.getHash();
  }

  public long getAddedToPoolAt() {
    return addedToPoolAt;
  }

  public static List<Transaction> toTransactionList(
      final Collection<PendingTransaction> transactionsInfo) {
    return transactionsInfo.stream()
        .map(PendingTransaction::getTransaction)
        .collect(Collectors.toUnmodifiableList());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    PendingTransaction that = (PendingTransaction) o;

    return sequence == that.sequence;
  }

  @Override
  public int hashCode() {
    return 31 * (int) (sequence ^ (sequence >>> 32));
  }

  public String toTraceLog() {
    return "{sequence: "
        + sequence
        + ", addedAt: "
        + addedToPoolAt
        + ", "
        + transaction.toTraceLog()
        + "}";
  }

  public static class Local extends PendingTransaction {

    public Local(final Transaction transaction, final long addedToPoolAt) {
      super(transaction, addedToPoolAt);
    }

    @Override
    public boolean isReceivedFromLocalSource() {
      return true;
    }
  }

  public static class Remote extends PendingTransaction {

    public Remote(final Transaction transaction, final long addedToPoolAt) {
      super(transaction, addedToPoolAt);
    }

    @Override
    public boolean isReceivedFromLocalSource() {
      return false;
    }
  }
}
