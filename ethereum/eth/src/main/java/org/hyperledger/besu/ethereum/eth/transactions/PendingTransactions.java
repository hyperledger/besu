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
package org.hyperledger.besu.ethereum.eth.transactions;

import static java.util.Comparator.comparing;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions.TransactionAddedStatus.ADDED;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions.TransactionAddedStatus.ALREADY_KNOWN;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions.TransactionAddedStatus.REJECTED_UNDERPRICED_REPLACEMENT;

import org.hyperledger.besu.ethereum.core.AccountTransactionOrder;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.fees.EIP1559;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.util.Subscribers;
import org.hyperledger.besu.util.number.Percentage;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.EvictingQueue;

/**
 * Holds the current set of pending transactions with the ability to iterate them based on priority
 * for mining or look-up by hash.
 *
 * <p>This class is safe for use across multiple threads.
 */
public class PendingTransactions {

  private final int maxTransactionRetentionHours;
  private final Clock clock;

  private final Queue<Hash> newPooledHashes;
  private final Map<Hash, TransactionInfo> pendingTransactions = new ConcurrentHashMap<>();
  private final SortedSet<TransactionInfo> prioritizedTransactions =
      new TreeSet<>(
          comparing(TransactionInfo::isReceivedFromLocalSource)
              .thenComparing(TransactionInfo::getSequence)
              .reversed());
  private final Map<Address, TransactionsForSenderInfo> transactionsBySender = new HashMap<>();

  private final Subscribers<PendingTransactionListener> pendingTransactionSubscribers =
      Subscribers.create();

  private final Subscribers<PendingTransactionDroppedListener> transactionDroppedListeners =
      Subscribers.create();

  private final LabelledMetric<Counter> transactionRemovedCounter;
  private final Counter localTransactionAddedCounter;
  private final Counter remoteTransactionAddedCounter;
  private final Counter localTransactionHashesAddedCounter;

  private final long maxPendingTransactions;
  private final TransactionPoolReplacementHandler transactionReplacementHandler;
  private final Supplier<BlockHeader> chainHeadHeaderSupplier;

  public PendingTransactions(
      final int maxTransactionRetentionHours,
      final int maxPendingTransactions,
      final int maxPooledTransactionHashes,
      final Clock clock,
      final MetricsSystem metricsSystem,
      final Supplier<BlockHeader> chainHeadHeaderSupplier,
      final Optional<EIP1559> eip1559,
      final Percentage priceBump) {
    this.maxTransactionRetentionHours = maxTransactionRetentionHours;
    this.maxPendingTransactions = maxPendingTransactions;
    this.clock = clock;
    this.newPooledHashes = EvictingQueue.create(maxPooledTransactionHashes);
    this.chainHeadHeaderSupplier = chainHeadHeaderSupplier;
    this.transactionReplacementHandler = new TransactionPoolReplacementHandler(eip1559, priceBump);
    final LabelledMetric<Counter> transactionAddedCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.TRANSACTION_POOL,
            "transactions_added_total",
            "Count of transactions added to the transaction pool",
            "source");
    localTransactionAddedCounter = transactionAddedCounter.labels("local");
    remoteTransactionAddedCounter = transactionAddedCounter.labels("remote");
    localTransactionHashesAddedCounter = transactionAddedCounter.labels("pool");

    transactionRemovedCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.TRANSACTION_POOL,
            "transactions_removed_total",
            "Count of transactions removed from the transaction pool",
            "source",
            "operation");
  }

  public void evictOldTransactions() {
    final Instant removeTransactionsBefore =
        clock.instant().minus(maxTransactionRetentionHours, ChronoUnit.HOURS);

    pendingTransactions.values().stream()
        .filter(transaction -> transaction.getAddedToPoolAt().isBefore(removeTransactionsBefore))
        .forEach(transaction -> removeTransaction(transaction.getTransaction()));
  }

  List<Transaction> getLocalTransactions() {
    return pendingTransactions.values().stream()
        .filter(TransactionInfo::isReceivedFromLocalSource)
        .map(TransactionInfo::getTransaction)
        .collect(Collectors.toList());
  }

  public boolean addRemoteTransaction(final Transaction transaction) {
    final TransactionInfo transactionInfo =
        new TransactionInfo(transaction, false, clock.instant());
    final TransactionAddedStatus transactionAddedStatus = addTransaction(transactionInfo);
    final boolean added = transactionAddedStatus.equals(ADDED);
    if (added) {
      remoteTransactionAddedCounter.inc();
    }
    return added;
  }

  boolean addTransactionHash(final Hash transactionHash) {
    boolean hashAdded;
    synchronized (newPooledHashes) {
      hashAdded = newPooledHashes.add(transactionHash);
    }
    if (hashAdded) {
      localTransactionHashesAddedCounter.inc();
    }
    return hashAdded;
  }

  @VisibleForTesting
  public TransactionAddedStatus addLocalTransaction(final Transaction transaction) {
    final TransactionAddedStatus transactionAdded =
        addTransaction(new TransactionInfo(transaction, true, clock.instant()));
    if (transactionAdded.equals(ADDED)) {
      localTransactionAddedCounter.inc();
    }
    return transactionAdded;
  }

  void removeTransaction(final Transaction transaction) {
    doRemoveTransaction(transaction, false);
    notifyTransactionDropped(transaction);
  }

  void transactionAddedToBlock(final Transaction transaction) {
    doRemoveTransaction(transaction, true);
  }

  private void doRemoveTransaction(final Transaction transaction, final boolean addedToBlock) {
    synchronized (pendingTransactions) {
      final TransactionInfo removedTransactionInfo =
          pendingTransactions.remove(transaction.getHash());
      if (removedTransactionInfo != null) {
        prioritizedTransactions.remove(removedTransactionInfo);
        removeTransactionTrackedBySenderAndNonce(transaction);
        incrementTransactionRemovedCounter(
            removedTransactionInfo.isReceivedFromLocalSource(), addedToBlock);
      }
    }
  }

  private void incrementTransactionRemovedCounter(
      final boolean receivedFromLocalSource, final boolean addedToBlock) {
    final String location = receivedFromLocalSource ? "local" : "remote";
    final String operation = addedToBlock ? "addedToBlock" : "dropped";
    transactionRemovedCounter.labels(location, operation).inc();
  }

  /*
   * The BlockTransaction selection process (part of block mining) requires synchronised access to
   * all pendingTransactions - this allows it to iterate over the available transactions without
   * releasing the lock in between items.
   *
   */
  public void selectTransactions(final TransactionSelector selector) {
    synchronized (pendingTransactions) {
      final List<Transaction> transactionsToRemove = new ArrayList<>();
      final Map<Address, AccountTransactionOrder> accountTransactions = new HashMap<>();
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
              transactionsToRemove.forEach(this::removeTransaction);
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
        transactionsBySender.get(address).getTransactionsInfos().values().stream()
            .map(TransactionInfo::getTransaction));
  }

  private TransactionAddedStatus addTransaction(final TransactionInfo transactionInfo) {
    Optional<Transaction> droppedTransaction = Optional.empty();
    synchronized (pendingTransactions) {
      if (pendingTransactions.containsKey(transactionInfo.getHash())) {
        return ALREADY_KNOWN;
      }

      final TransactionAddedStatus transactionAddedStatus =
          addTransactionForSenderAndNonce(transactionInfo);
      if (!transactionAddedStatus.equals(ADDED)) {
        return transactionAddedStatus;
      }
      prioritizedTransactions.add(transactionInfo);
      pendingTransactions.put(transactionInfo.getHash(), transactionInfo);
      tryEvictTransactionHash(transactionInfo.getHash());

      if (pendingTransactions.size() > maxPendingTransactions) {
        final TransactionInfo toRemove = prioritizedTransactions.last();
        doRemoveTransaction(toRemove.getTransaction(), false);
        droppedTransaction = Optional.of(toRemove.getTransaction());
      }
    }
    notifyTransactionAdded(transactionInfo.getTransaction());
    droppedTransaction.ifPresent(this::notifyTransactionDropped);
    return ADDED;
  }

  private TransactionAddedStatus addTransactionForSenderAndNonce(
      final TransactionInfo transactionInfo) {
    final TransactionInfo existingTransaction =
        getTrackedTransactionBySenderAndNonce(transactionInfo);
    if (existingTransaction != null) {
      if (!transactionReplacementHandler.shouldReplace(
          existingTransaction, transactionInfo, chainHeadHeaderSupplier.get())) {
        return REJECTED_UNDERPRICED_REPLACEMENT;
      }
      removeTransaction(existingTransaction.getTransaction());
    }
    trackTransactionBySenderAndNonce(transactionInfo);
    return ADDED;
  }

  private void trackTransactionBySenderAndNonce(final TransactionInfo transactionInfo) {
    final TransactionsForSenderInfo transactionsForSenderInfo =
        transactionsBySender.computeIfAbsent(
            transactionInfo.getSender(), key -> new TransactionsForSenderInfo());
    transactionsForSenderInfo.addTransactionToTrack(transactionInfo.getNonce(), transactionInfo);
  }

  private void removeTransactionTrackedBySenderAndNonce(final Transaction transaction) {
    Optional.ofNullable(transactionsBySender.get(transaction.getSender()))
        .ifPresent(
            transactionsForSender -> {
              transactionsForSender.getTransactionsInfos().remove(transaction.getNonce());
              if (transactionsForSender.getTransactionsInfos().isEmpty()) {
                transactionsBySender.remove(transaction.getSender());
                transactionsForSender.updateGaps();
              }
            });
  }

  private TransactionInfo getTrackedTransactionBySenderAndNonce(
      final TransactionInfo transactionInfo) {
    final TransactionsForSenderInfo transactionsForSenderInfo =
        transactionsBySender.computeIfAbsent(
            transactionInfo.getSender(), key -> new TransactionsForSenderInfo());
    return transactionsForSenderInfo.getTransactionsInfos().get(transactionInfo.getNonce());
  }

  private void notifyTransactionAdded(final Transaction transaction) {
    pendingTransactionSubscribers.forEach(listener -> listener.onTransactionAdded(transaction));
  }

  private void notifyTransactionDropped(final Transaction transaction) {
    transactionDroppedListeners.forEach(listener -> listener.onTransactionDropped(transaction));
  }

  public long maxSize() {
    return maxPendingTransactions;
  }

  public int size() {
    return pendingTransactions.size();
  }

  public boolean containsTransaction(final Hash transactionHash) {
    return pendingTransactions.containsKey(transactionHash);
  }

  public Optional<Transaction> getTransactionByHash(final Hash transactionHash) {
    return Optional.ofNullable(pendingTransactions.get(transactionHash))
        .map(TransactionInfo::getTransaction);
  }

  public Set<TransactionInfo> getTransactionInfo() {
    return new HashSet<>(pendingTransactions.values());
  }

  long subscribePendingTransactions(final PendingTransactionListener listener) {
    return pendingTransactionSubscribers.subscribe(listener);
  }

  void unsubscribePendingTransactions(final long id) {
    pendingTransactionSubscribers.unsubscribe(id);
  }

  long subscribeDroppedTransactions(final PendingTransactionDroppedListener listener) {
    return transactionDroppedListeners.subscribe(listener);
  }

  void unsubscribeDroppedTransactions(final long id) {
    transactionDroppedListeners.unsubscribe(id);
  }

  public OptionalLong getNextNonceForSender(final Address sender) {
    synchronized (pendingTransactions) {
      final TransactionsForSenderInfo transactionsForSenderInfo = transactionsBySender.get(sender);
      if (transactionsForSenderInfo == null
          || transactionsForSenderInfo.getTransactionsInfos().isEmpty()) {
        return OptionalLong.empty();
      } else if (!transactionsForSenderInfo.getGaps().isEmpty()) {
        return OptionalLong.of(Objects.requireNonNull(transactionsForSenderInfo.getGaps().poll()));
      } else {
        return OptionalLong.of(transactionsForSenderInfo.getTransactionsInfos().lastKey() + 1);
      }
    }
  }

  public void tryEvictTransactionHash(final Hash hash) {
    synchronized (newPooledHashes) {
      newPooledHashes.remove(hash);
    }
  }

  public Collection<Hash> getNewPooledHashes() {
    return newPooledHashes;
  }

  /**
   * Tracks the additional metadata associated with transactions to enable prioritization for mining
   * and deciding which transactions to drop when the transaction pool reaches its size limit.
   */
  public static class TransactionInfo {

    private static final AtomicLong TRANSACTIONS_ADDED = new AtomicLong();
    private final Transaction transaction;
    private final boolean receivedFromLocalSource;
    private final Instant addedToPoolAt;
    private final long sequence; // Allows prioritization based on order transactions are added

    public TransactionInfo(
        final Transaction transaction,
        final boolean receivedFromLocalSource,
        final Instant addedToPoolAt) {
      this.transaction = transaction;
      this.receivedFromLocalSource = receivedFromLocalSource;
      this.addedToPoolAt = addedToPoolAt;
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
      return transaction.getHash();
    }

    public Instant getAddedToPoolAt() {
      return addedToPoolAt;
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

  public enum TransactionAddedStatus {
    ALREADY_KNOWN(TransactionInvalidReason.TRANSACTION_ALREADY_KNOWN),
    REJECTED_UNDERPRICED_REPLACEMENT(TransactionInvalidReason.TRANSACTION_REPLACEMENT_UNDERPRICED),
    ADDED();

    private final Optional<TransactionInvalidReason> invalidReason;

    TransactionAddedStatus() {
      this.invalidReason = Optional.empty();
    }

    TransactionAddedStatus(final TransactionInvalidReason invalidReason) {
      this.invalidReason = Optional.of(invalidReason);
    }

    public Optional<TransactionInvalidReason> getInvalidReason() {
      return invalidReason;
    }
  }
}
