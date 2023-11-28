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
  static final int FRONTIER_AND_ACCESS_LIST_BASE_MEMORY_SIZE = 872;
  static final int EIP1559_AND_EIP4844_BASE_MEMORY_SIZE = 984;
  static final int OPTIONAL_TO_MEMORY_SIZE = 112;
  static final int OPTIONAL_CHAIN_ID_MEMORY_SIZE = 80;
  static final int PAYLOAD_BASE_MEMORY_SIZE = 32;
  static final int ACCESS_LIST_STORAGE_KEY_MEMORY_SIZE = 32;
  static final int ACCESS_LIST_ENTRY_BASE_MEMORY_SIZE = 248;
  static final int OPTIONAL_ACCESS_LIST_MEMORY_SIZE = 24;
  static final int VERSIONED_HASH_SIZE = 96;
  static final int BASE_LIST_SIZE = 48;
  static final int BASE_OPTIONAL_SIZE = 16;
  static final int KZG_COMMITMENT_OR_PROOF_SIZE = 112;
  static final int BLOB_SIZE = 131136;
  static final int BLOBS_WITH_COMMITMENTS_SIZE = 32;
  static final int PENDING_TRANSACTION_MEMORY_SIZE = 40;
  private static final AtomicLong TRANSACTIONS_ADDED = new AtomicLong();
  private final Transaction transaction;
  private final long addedAt;
  private final long sequence; // Allows prioritization based on order transactions are added

  private int memorySize = NOT_INITIALIZED;

  private PendingTransaction(
      final Transaction transaction, final long addedAt, final long sequence) {
    this.transaction = transaction;
    this.addedAt = addedAt;
    this.sequence = sequence;
  }

  private PendingTransaction(final Transaction transaction, final long addedAt) {
    this(transaction, addedAt, TRANSACTIONS_ADDED.getAndIncrement());
  }

  public static PendingTransaction newPendingTransaction(
      final Transaction transaction, final boolean isLocal, final boolean hasPriority) {
    return newPendingTransaction(transaction, isLocal, hasPriority, System.currentTimeMillis());
  }

  public static PendingTransaction newPendingTransaction(
      final Transaction transaction,
      final boolean isLocal,
      final boolean hasPriority,
      final long addedAt) {
    if (isLocal) {
      if (hasPriority) {
        return new Local.Priority(transaction, addedAt);
      }
      return new Local(transaction, addedAt);
    }
    if (hasPriority) {
      return new Remote.Priority(transaction, addedAt);
    }
    return new Remote(transaction, addedAt);
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

  public abstract PendingTransaction detachedCopy();

  private int computeMemorySize() {
    return switch (transaction.getType()) {
          case FRONTIER -> computeFrontierMemorySize();
          case AUTH_SERVICE -> computeFrontierMemorySize();
          case ACCESS_LIST -> computeAccessListMemorySize();
          case EIP1559 -> computeEIP1559MemorySize();
          case BLOB -> computeBlobMemorySize();
        }
        + PENDING_TRANSACTION_MEMORY_SIZE;
  }

  private int computeFrontierMemorySize() {
    return FRONTIER_AND_ACCESS_LIST_BASE_MEMORY_SIZE
        + computePayloadMemorySize()
        + computeToMemorySize()
        + computeChainIdMemorySize();
  }

  private int computeAccessListMemorySize() {
    return FRONTIER_AND_ACCESS_LIST_BASE_MEMORY_SIZE
        + computePayloadMemorySize()
        + computeToMemorySize()
        + computeChainIdMemorySize()
        + computeAccessListEntriesMemorySize();
  }

  private int computeEIP1559MemorySize() {
    return EIP1559_AND_EIP4844_BASE_MEMORY_SIZE
        + computePayloadMemorySize()
        + computeToMemorySize()
        + computeChainIdMemorySize()
        + computeAccessListEntriesMemorySize();
  }

  private int computeBlobMemorySize() {
    return computeEIP1559MemorySize()
        + BASE_OPTIONAL_SIZE // for the versionedHashes field
        + computeBlobWithCommitmentsMemorySize();
  }

  private int computeBlobWithCommitmentsMemorySize() {
    final int blobCount = transaction.getBlobCount();

    return BASE_OPTIONAL_SIZE
        + BLOBS_WITH_COMMITMENTS_SIZE
        + (BASE_LIST_SIZE * 4)
        + (KZG_COMMITMENT_OR_PROOF_SIZE * blobCount * 2)
        + (VERSIONED_HASH_SIZE * blobCount)
        + (BLOB_SIZE * blobCount);
  }

  private int computePayloadMemorySize() {
    return transaction.getPayload().size() > 0
        ? PAYLOAD_BASE_MEMORY_SIZE + transaction.getPayload().size()
        : 0;
  }

  private int computeToMemorySize() {
    if (transaction.getTo().isPresent()) {
      return OPTIONAL_TO_MEMORY_SIZE;
    }
    return 0;
  }

  private int computeChainIdMemorySize() {
    if (transaction.getChainId().isPresent()) {
      return OPTIONAL_CHAIN_ID_MEMORY_SIZE;
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
        + ", hasPriority="
        + hasPriority()
        + '}';
  }

  public String toTraceLog() {
    return "{sequence: "
        + sequence
        + ", addedAt: "
        + addedAt
        + ", isLocal="
        + isReceivedFromLocalSource()
        + ", hasPriority="
        + hasPriority()
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

    private Local(final long sequence, final Transaction transaction) {
      super(transaction, System.currentTimeMillis(), sequence);
    }

    @Override
    public PendingTransaction detachedCopy() {
      return new Local(getSequence(), getTransaction().detachedCopy());
    }

    @Override
    public boolean isReceivedFromLocalSource() {
      return true;
    }

    @Override
    public boolean hasPriority() {
      return false;
    }

    public static class Priority extends Local {
      public Priority(final Transaction transaction) {
        this(transaction, System.currentTimeMillis());
      }

      public Priority(final Transaction transaction, final long addedAt) {
        super(transaction, addedAt);
      }

      public Priority(final long sequence, final Transaction transaction) {
        super(sequence, transaction);
      }

      @Override
      public PendingTransaction detachedCopy() {
        return new Priority(getSequence(), getTransaction().detachedCopy());
      }

      @Override
      public boolean hasPriority() {
        return true;
      }
    }
  }

  public static class Remote extends PendingTransaction {

    public Remote(final Transaction transaction, final long addedAt) {
      super(transaction, addedAt);
    }

    public Remote(final Transaction transaction) {
      this(transaction, System.currentTimeMillis());
    }

    private Remote(final long sequence, final Transaction transaction) {
      super(transaction, System.currentTimeMillis(), sequence);
    }

    @Override
    public PendingTransaction detachedCopy() {
      return new Remote(getSequence(), getTransaction().detachedCopy());
    }

    @Override
    public boolean isReceivedFromLocalSource() {
      return false;
    }

    @Override
    public boolean hasPriority() {
      return false;
    }

    public static class Priority extends Remote {
      public Priority(final Transaction transaction) {
        this(transaction, System.currentTimeMillis());
      }

      public Priority(final Transaction transaction, final long addedAt) {
        super(transaction, addedAt);
      }

      public Priority(final long sequence, final Transaction transaction) {
        super(sequence, transaction);
      }

      @Override
      public PendingTransaction detachedCopy() {
        return new Priority(getSequence(), getTransaction().detachedCopy());
      }

      @Override
      public boolean hasPriority() {
        return true;
      }
    }
  }
}
