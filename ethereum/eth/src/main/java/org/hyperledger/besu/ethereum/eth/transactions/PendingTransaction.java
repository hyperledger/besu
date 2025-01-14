/*
 * Copyright contributors to Hyperledger Besu.
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

import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.ACCESS_LIST_ENTRY_SHALLOW_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.ACCESS_LIST_STORAGE_KEY_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.BLOBS_WITH_COMMITMENTS_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.BLOB_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.CODE_DELEGATION_ENTRY_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.EIP1559_AND_EIP4844_SHALLOW_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.FRONTIER_AND_ACCESS_LIST_SHALLOW_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.KZG_COMMITMENT_OR_PROOF_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.LIST_SHALLOW_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.OPTIONAL_ACCESS_LIST_SHALLOW_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.OPTIONAL_CHAIN_ID_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.OPTIONAL_CODE_DELEGATION_LIST_SHALLOW_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.OPTIONAL_SHALLOW_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.OPTIONAL_TO_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.PAYLOAD_SHALLOW_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.PENDING_TRANSACTION_SHALLOW_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.VERSIONED_HASH_SIZE;

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
  private static final AtomicLong TRANSACTIONS_ADDED = new AtomicLong();
  private final Transaction transaction;
  private final long addedAt;
  private final long sequence; // Allows prioritization based on order transactions are added
  private volatile byte score;

  private int memorySize = NOT_INITIALIZED;

  private PendingTransaction(
      final Transaction transaction, final long addedAt, final long sequence, final byte score) {
    this.transaction = transaction;
    this.addedAt = addedAt;
    this.sequence = sequence;
    this.score = score;
  }

  private PendingTransaction(final Transaction transaction, final long addedAt) {
    this(transaction, addedAt, TRANSACTIONS_ADDED.getAndIncrement(), Byte.MAX_VALUE);
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

  public byte getScore() {
    return score;
  }

  public void decrementScore() {
    // use temp var to avoid non-atomic update of volatile var
    final byte newScore = (byte) (score - 1);

    // check to avoid underflow
    if (newScore < score) {
      score = newScore;
    }
  }

  public abstract PendingTransaction detachedCopy();

  private int computeMemorySize() {
    return switch (transaction.getType()) {
          case FRONTIER -> computeFrontierMemorySize();
          case ACCESS_LIST -> computeAccessListMemorySize();
          case EIP1559 -> computeEIP1559MemorySize();
          case BLOB -> computeBlobMemorySize();
          case DELEGATE_CODE -> computeDelegateCodeMemorySize();
        }
        + PENDING_TRANSACTION_SHALLOW_SIZE;
  }

  private int computeFrontierMemorySize() {
    return FRONTIER_AND_ACCESS_LIST_SHALLOW_SIZE
        + computePayloadMemorySize()
        + computeToMemorySize()
        + computeChainIdMemorySize();
  }

  private int computeAccessListMemorySize() {
    return FRONTIER_AND_ACCESS_LIST_SHALLOW_SIZE
        + computePayloadMemorySize()
        + computeToMemorySize()
        + computeChainIdMemorySize()
        + computeAccessListEntriesMemorySize();
  }

  private int computeEIP1559MemorySize() {
    return EIP1559_AND_EIP4844_SHALLOW_SIZE
        + computePayloadMemorySize()
        + computeToMemorySize()
        + computeChainIdMemorySize()
        + computeAccessListEntriesMemorySize();
  }

  private int computeBlobMemorySize() {
    return computeEIP1559MemorySize()
        + OPTIONAL_SHALLOW_SIZE // for the versionedHashes field
        + computeBlobWithCommitmentsMemorySize();
  }

  private int computeDelegateCodeMemorySize() {
    return computeEIP1559MemorySize() + computeCodeDelegationListMemorySize();
  }

  private int computeBlobWithCommitmentsMemorySize() {
    final int blobCount = transaction.getBlobCount();

    return OPTIONAL_SHALLOW_SIZE
        + BLOBS_WITH_COMMITMENTS_SIZE
        + (LIST_SHALLOW_SIZE * 4)
        + (KZG_COMMITMENT_OR_PROOF_SIZE * blobCount * 2)
        + (VERSIONED_HASH_SIZE * blobCount)
        + (BLOB_SIZE * blobCount);
  }

  private int computePayloadMemorySize() {
    return !transaction.getPayload().isEmpty()
        ? PAYLOAD_SHALLOW_SIZE + transaction.getPayload().size()
        : 0;
  }

  private int computeToMemorySize() {
    if (transaction.getTo().isPresent()) {
      return OPTIONAL_TO_SIZE;
    }
    return 0;
  }

  private int computeChainIdMemorySize() {
    if (transaction.getChainId().isPresent()) {
      return OPTIONAL_CHAIN_ID_SIZE;
    }
    return 0;
  }

  private int computeAccessListEntriesMemorySize() {
    return transaction
        .getAccessList()
        .map(
            al -> {
              int totalSize = OPTIONAL_ACCESS_LIST_SHALLOW_SIZE;
              totalSize += al.size() * ACCESS_LIST_ENTRY_SHALLOW_SIZE;
              totalSize +=
                  al.stream().map(AccessListEntry::storageKeys).mapToInt(List::size).sum()
                      * ACCESS_LIST_STORAGE_KEY_SIZE;
              return totalSize;
            })
        .orElse(0);
  }

  private int computeCodeDelegationListMemorySize() {
    return transaction
        .getCodeDelegationList()
        .map(
            cd -> {
              int totalSize = OPTIONAL_CODE_DELEGATION_LIST_SHALLOW_SIZE;
              totalSize += cd.size() * CODE_DELEGATION_ENTRY_SIZE;
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
    return 31 * Long.hashCode(sequence);
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
        + ", score="
        + score
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
        + ", score="
        + score
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

    private Local(final long sequence, final byte score, final Transaction transaction) {
      super(transaction, System.currentTimeMillis(), sequence, score);
    }

    @Override
    public PendingTransaction detachedCopy() {
      return new Local(getSequence(), getScore(), getTransaction().detachedCopy());
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

      public Priority(final long sequence, final byte score, final Transaction transaction) {
        super(sequence, score, transaction);
      }

      @Override
      public PendingTransaction detachedCopy() {
        return new Priority(getSequence(), getScore(), getTransaction().detachedCopy());
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

    private Remote(final long sequence, final byte score, final Transaction transaction) {
      super(transaction, System.currentTimeMillis(), sequence, score);
    }

    @Override
    public PendingTransaction detachedCopy() {
      return new Remote(getSequence(), getScore(), getTransaction().detachedCopy());
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

      public Priority(final long sequence, final byte score, final Transaction transaction) {
        super(sequence, score, transaction);
      }

      @Override
      public PendingTransaction detachedCopy() {
        return new Priority(getSequence(), getScore(), getTransaction().detachedCopy());
      }

      @Override
      public boolean hasPriority() {
        return true;
      }
    }
  }

  /**
   * The memory size of an object is calculated using the PendingTransactionEstimatedMemorySizeTest
   * look there for the details of the calculation and to adapt the code when any of the related
   * class changes its structure.
   */
  public interface MemorySize {
    int FRONTIER_AND_ACCESS_LIST_SHALLOW_SIZE = 904;
    int EIP1559_AND_EIP4844_SHALLOW_SIZE = 1016;
    int OPTIONAL_TO_SIZE = 112;
    int OPTIONAL_CHAIN_ID_SIZE = 80;
    int PAYLOAD_SHALLOW_SIZE = 32;
    int ACCESS_LIST_STORAGE_KEY_SIZE = 32;
    int ACCESS_LIST_ENTRY_SHALLOW_SIZE = 248;
    int OPTIONAL_ACCESS_LIST_SHALLOW_SIZE = 40;
    int OPTIONAL_CODE_DELEGATION_LIST_SHALLOW_SIZE = 40;
    int CODE_DELEGATION_ENTRY_SIZE = 520;
    int VERSIONED_HASH_SIZE = 96;
    int LIST_SHALLOW_SIZE = 48;
    int OPTIONAL_SHALLOW_SIZE = 16;
    int KZG_COMMITMENT_OR_PROOF_SIZE = 112;
    int BLOB_SIZE = 131136;
    int BLOBS_WITH_COMMITMENTS_SIZE = 40;
    int PENDING_TRANSACTION_SHALLOW_SIZE = 40;
  }
}
