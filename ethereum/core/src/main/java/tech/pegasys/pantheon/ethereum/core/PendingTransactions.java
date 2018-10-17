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
package tech.pegasys.pantheon.ethereum.core;

import static java.util.Collections.newSetFromMap;
import static java.util.Comparator.comparing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Holds the current set of pending transactions with the ability to iterate them based on priority
 * for mining or look-up by hash.
 *
 * <p>This class is safe for use across multiple threads.
 */
public class PendingTransactions {
  public static final int MAX_PENDING_TRANSACTIONS = 30_000;

  private final Map<Hash, TransactionInfo> pendingTransactions = new HashMap<>();
  private final SortedSet<TransactionInfo> prioritizedTransactions =
      new TreeSet<>(
          comparing(TransactionInfo::isReceivedFromLocalSource)
              .thenComparing(TransactionInfo::getSequence)
              .reversed());
  private final Map<Address, SortedMap<Long, TransactionInfo>> transactionsBySender =
      new HashMap<>();

  private final Collection<PendingTransactionListener> listeners =
      newSetFromMap(new ConcurrentHashMap<>());

  private final int maxPendingTransactions;

  public PendingTransactions(final int maxPendingTransactions) {
    this.maxPendingTransactions = maxPendingTransactions;
  }

  public boolean addRemoteTransaction(final Transaction transaction) {
    final TransactionInfo transactionInfo = new TransactionInfo(transaction, false);
    return addTransaction(transactionInfo);
  }

  boolean addLocalTransaction(final Transaction transaction) {
    return addTransaction(new TransactionInfo(transaction, true));
  }

  public void removeTransaction(final Transaction transaction) {
    synchronized (pendingTransactions) {
      final TransactionInfo removedTransactionInfo = pendingTransactions.remove(transaction.hash());
      if (removedTransactionInfo != null) {
        prioritizedTransactions.remove(removedTransactionInfo);
        Optional.ofNullable(transactionsBySender.get(transaction.getSender()))
            .ifPresent(
                transactionsForSender -> {
                  transactionsForSender.remove(transaction.getNonce());
                  if (transactionsForSender.isEmpty()) {
                    transactionsBySender.remove(transaction.getSender());
                  }
                });
      }
    }
  }

  /*
   * The BlockTransaction selection process (part of block mining) requires synchronised access to
   * all pendingTransactions - this allows it to iterate over the available transactions without
   * releasing the lock in between items.
   *
   */
  public void selectTransactions(final TransactionSelector selector) {
    synchronized (pendingTransactions) {
      final Map<Address, AccountTransactionOrder> accountTransactions = new HashMap<>();
      final List<Transaction> transactionsToRemove = new ArrayList<>();
      for (final TransactionInfo transactionInfo : prioritizedTransactions) {
        final AccountTransactionOrder accountTransactionOrder =
            accountTransactions.computeIfAbsent(
                transactionInfo.getSender(), this::createSenderTransactionOrder);

        for (final Transaction transactionToProcess :
            accountTransactionOrder.transactionsToProcess(transactionInfo.getTransaction())) {
          final TransactionSelectionResult result =
              selector.evaluateTransaction(transactionToProcess);
          switch (result) {
            case DELETE_TRANSACTION_AND_CONTINUE:
              transactionsToRemove.add(transactionToProcess);
              break;
            case CONTINUE:
              break;
            case COMPLETE_OPERATION:
              return;
            default:
              throw new RuntimeException("Illegal value for TransactionSelectionResult.");
          }
        }
      }
      transactionsToRemove.forEach(this::removeTransaction);
    }
  }

  private AccountTransactionOrder createSenderTransactionOrder(final Address address) {
    return new AccountTransactionOrder(
        transactionsBySender.get(address).values().stream().map(TransactionInfo::getTransaction));
  }

  private boolean addTransaction(final TransactionInfo transactionInfo) {
    synchronized (pendingTransactions) {
      if (pendingTransactions.containsKey(transactionInfo.getHash())) {
        return false;
      }

      if (!addTransactionForSenderAndNonce(transactionInfo)) {
        return false;
      }
      prioritizedTransactions.add(transactionInfo);
      pendingTransactions.put(transactionInfo.getHash(), transactionInfo);

      notifyTransactionAdded(transactionInfo.getTransaction());
      if (pendingTransactions.size() > maxPendingTransactions) {
        final TransactionInfo toRemove = prioritizedTransactions.last();
        removeTransaction(toRemove.getTransaction());
      }
      return true;
    }
  }

  private boolean addTransactionForSenderAndNonce(final TransactionInfo transactionInfo) {
    final Map<Long, TransactionInfo> transactionsForSender =
        transactionsBySender.computeIfAbsent(transactionInfo.getSender(), key -> new TreeMap<>());
    final TransactionInfo existingTransaction =
        transactionsForSender.get(transactionInfo.getNonce());
    if (existingTransaction != null) {
      if (!shouldReplace(existingTransaction, transactionInfo)) {
        return false;
      }
      removeTransaction(existingTransaction.getTransaction());
    }
    transactionsForSender.put(transactionInfo.getNonce(), transactionInfo);
    return true;
  }

  private boolean shouldReplace(
      final TransactionInfo existingTransaction, final TransactionInfo newTransaction) {
    return newTransaction
            .getTransaction()
            .getGasPrice()
            .compareTo(existingTransaction.getTransaction().getGasPrice())
        > 0;
  }

  private void notifyTransactionAdded(final Transaction transaction) {
    listeners.forEach(listener -> listener.onTransactionAdded(transaction));
  }

  public int size() {
    synchronized (pendingTransactions) {
      return pendingTransactions.size();
    }
  }

  public Optional<Transaction> getTransactionByHash(final Hash transactionHash) {
    synchronized (pendingTransactions) {
      return Optional.ofNullable(pendingTransactions.get(transactionHash))
          .map(TransactionInfo::getTransaction);
    }
  }

  public void addTransactionListener(final PendingTransactionListener listener) {
    listeners.add(listener);
  }

  public OptionalLong getNextNonceForSender(final Address sender) {
    synchronized (pendingTransactions) {
      final SortedMap<Long, TransactionInfo> transactionsForSender =
          transactionsBySender.get(sender);
      if (transactionsForSender == null) {
        return OptionalLong.empty();
      }
      return OptionalLong.of(transactionsForSender.lastKey() + 1);
    }
  }

  /**
   * Tracks the additional metadata associated with transactions to enable prioritization for mining
   * and deciding which transactions to drop when the transaction pool reaches its size limit.
   */
  private static class TransactionInfo {

    private static final AtomicLong TRANSACTIONS_ADDED = new AtomicLong();
    private final Transaction transaction;
    private final boolean receivedFromLocalSource;
    private final long sequence; // Allows prioritization based on order transactions are added

    private TransactionInfo(final Transaction transaction, final boolean receivedFromLocalSource) {
      this.transaction = transaction;
      this.receivedFromLocalSource = receivedFromLocalSource;
      this.sequence = TRANSACTIONS_ADDED.getAndIncrement();
    }

    public Transaction getTransaction() {
      return transaction;
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

    public boolean isReceivedFromLocalSource() {
      return receivedFromLocalSource;
    }

    public Hash getHash() {
      return transaction.hash();
    }
  }

  public enum TransactionSelectionResult {
    DELETE_TRANSACTION_AND_CONTINUE,
    CONTINUE,
    COMPLETE_OPERATION
  }

  @FunctionalInterface
  public interface TransactionSelector {
    TransactionSelectionResult evaluateTransaction(final Transaction transaction);
  }
}
