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

import org.hyperledger.besu.datatypes.AccessListEntry;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks the additional metadata associated with transactions to enable prioritization for mining
 * and deciding which transactions to drop when the transaction pool reaches its size limit.
 */
public abstract class PendingTransaction
    implements org.hyperledger.besu.datatypes.PendingTransaction {
  static final int NOT_INITIALIZED = -1;
  static final int FRONTIER_BASE_MEMORY_SIZE = 944;
  static final int ACCESS_LIST_BASE_MEMORY_SIZE = 944;
  static final int EIP1559_BASE_MEMORY_SIZE = 1056;
  static final int OPTIONAL_TO_MEMORY_SIZE = 92;
  static final int PAYLOAD_BASE_MEMORY_SIZE = 32;
  static final int ACCESS_LIST_STORAGE_KEY_MEMORY_SIZE = 32;
  static final int ACCESS_LIST_ENTRY_BASE_MEMORY_SIZE = 128;
  static final int OPTIONAL_ACCESS_LIST_MEMORY_SIZE = 24;
  static final int PENDING_TRANSACTION_MEMORY_SIZE = 40;
  private static final AtomicLong TRANSACTIONS_ADDED = new AtomicLong();
  private final Transaction transaction;
  private final long addedAt;
  private final long sequence; // Allows prioritization based on order transactions are added

  private int memorySize = NOT_INITIALIZED;

  protected PendingTransaction(final Transaction transaction, final long addedAt) {
    this.transaction = transaction;
    this.addedAt = addedAt;
    this.sequence = TRANSACTIONS_ADDED.getAndIncrement();
  }

  @Override
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

  public Hash getHash() {
    return transaction.getHash();
  }

  @Override
  public long getAddedAt() {
    return addedAt;
  }

  public int memorySize() {
    if (memorySize == NOT_INITIALIZED) {
      memorySize = computeMemorySize();
    }
    return memorySize;
  }

  private int computeMemorySize() {
    return switch (transaction.getType()) {
          case FRONTIER -> computeFrontierMemorySize();
          case ACCESS_LIST -> computeAccessListMemorySize();
          case EIP1559 -> computeEIP1559MemorySize();
          case BLOB -> computeBlobMemorySize();
        }
        + PENDING_TRANSACTION_MEMORY_SIZE;
  }

  private int computeFrontierMemorySize() {
    return FRONTIER_BASE_MEMORY_SIZE + computePayloadMemorySize() + computeToMemorySize();
  }

  private int computeAccessListMemorySize() {
    return ACCESS_LIST_BASE_MEMORY_SIZE
        + computePayloadMemorySize()
        + computeToMemorySize()
        + computeAccessListEntriesMemorySize();
  }

  private int computeEIP1559MemorySize() {
    return EIP1559_BASE_MEMORY_SIZE
        + computePayloadMemorySize()
        + computeToMemorySize()
        + computeAccessListEntriesMemorySize();
  }

  private int computeBlobMemorySize() {
    // ToDo 4844: adapt for blobs
    return computeEIP1559MemorySize();
  }

  private int computePayloadMemorySize() {
    return PAYLOAD_BASE_MEMORY_SIZE + transaction.getPayload().size();
  }

  private int computeToMemorySize() {
    if (transaction.getTo().isPresent()) {
      return OPTIONAL_TO_MEMORY_SIZE;
    }
    return 0;
  }

  private int computeAccessListEntriesMemorySize() {
    return transaction
        .getAccessList()
        .map(
            al -> {
              int totalSize = OPTIONAL_ACCESS_LIST_MEMORY_SIZE;
              totalSize += al.size() * ACCESS_LIST_ENTRY_BASE_MEMORY_SIZE;
              totalSize +=
                  al.stream().map(AccessListEntry::storageKeys).mapToInt(List::size).sum()
                      * ACCESS_LIST_STORAGE_KEY_MEMORY_SIZE;
              return totalSize;
            })
        .orElse(0);
  }

  public static List<Transaction> toTransactionList(
      final Collection<PendingTransaction> transactionsInfo) {
    return transactionsInfo.stream().map(PendingTransaction::getTransaction).toList();
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

  @Override
  public String toString() {
    return "Hash="
        + transaction.getHash().toShortHexString()
        + ", nonce="
        + transaction.getNonce()
        + ", sender="
        + transaction.getSender().toShortHexString()
        + ", addedAt="
        + addedAt
        + ", sequence="
        + sequence
        + ", isLocal="
        + isReceivedFromLocalSource()
        + '}';
  }

  public String toTraceLog() {
    return "{sequence: "
        + sequence
        + ", addedAt: "
        + addedAt
        + ", isLocal="
        + isReceivedFromLocalSource()
        + ", "
        + transaction.toTraceLog()
        + "}";
  }

  public static class Local extends PendingTransaction {

    public Local(final Transaction transaction, final long addedAt) {
      super(transaction, addedAt);
    }

    public Local(final Transaction transaction) {
      this(transaction, System.currentTimeMillis());
    }

    @Override
    public boolean isReceivedFromLocalSource() {
      return true;
    }
  }

  public static class Remote extends PendingTransaction {

    public Remote(final Transaction transaction, final long addedAt) {
      super(transaction, addedAt);
    }

    public Remote(final Transaction transaction) {
      this(transaction, System.currentTimeMillis());
    }

    @Override
    public boolean isReceivedFromLocalSource() {
      return false;
    }
  }
}
