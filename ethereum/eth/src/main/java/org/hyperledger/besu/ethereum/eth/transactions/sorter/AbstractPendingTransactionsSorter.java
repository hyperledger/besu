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
package org.hyperledger.besu.ethereum.eth.transactions.sorter;

import static org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter.TransactionAddedStatus.ADDED;
import static org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter.TransactionAddedStatus.LOWER_NONCE_INVALID_TRANSACTION_KNOWN;
import static org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter.TransactionAddedStatus.REJECTED_UNDERPRICED_REPLACEMENT;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.AccountTransactionOrder;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactionDroppedListener;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactionListener;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolReplacementHandler;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionsForSenderInfo;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.util.Subscribers;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the current set of pending transactions with the ability to iterate them based on priority
 * for mining or look-up by hash.
 *
 * <p>This class is safe for use across multiple threads.
 */
public abstract class AbstractPendingTransactionsSorter {
  private static final Logger LOG =
      LoggerFactory.getLogger(AbstractPendingTransactionsSorter.class);

  protected final Clock clock;
  protected final TransactionPoolConfiguration poolConfig;

  protected final Object lock = new Object();
  protected final Map<Hash, TransactionInfo> pendingTransactions = new ConcurrentHashMap<>();

  protected final Map<Address, TransactionsForSenderInfo> transactionsBySender =
      new ConcurrentHashMap<>();

  protected final Map<Address, Long> lowestInvalidKnownNonceBySender = new ConcurrentHashMap<>();

  protected final Subscribers<PendingTransactionListener> pendingTransactionSubscribers =
      Subscribers.create();

  protected final Subscribers<PendingTransactionDroppedListener> transactionDroppedListeners =
      Subscribers.create();

  protected final LabelledMetric<Counter> transactionRemovedCounter;
  protected final Counter localTransactionAddedCounter;
  protected final Counter remoteTransactionAddedCounter;

  protected final TransactionPoolReplacementHandler transactionReplacementHandler;
  protected final Supplier<BlockHeader> chainHeadHeaderSupplier;

  public AbstractPendingTransactionsSorter(
      final TransactionPoolConfiguration poolConfig,
      final Clock clock,
      final MetricsSystem metricsSystem,
      final Supplier<BlockHeader> chainHeadHeaderSupplier) {
    this.poolConfig = poolConfig;
    this.clock = clock;
    this.chainHeadHeaderSupplier = chainHeadHeaderSupplier;
    this.transactionReplacementHandler =
        new TransactionPoolReplacementHandler(poolConfig.getPriceBump());
    final LabelledMetric<Counter> transactionAddedCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.TRANSACTION_POOL,
            "transactions_added_total",
            "Count of transactions added to the transaction pool",
            "source");
    localTransactionAddedCounter = transactionAddedCounter.labels("local");
    remoteTransactionAddedCounter = transactionAddedCounter.labels("remote");

    transactionRemovedCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.TRANSACTION_POOL,
            "transactions_removed_total",
            "Count of transactions removed from the transaction pool",
            "source",
            "operation");

    metricsSystem.createIntegerGauge(
        BesuMetricCategory.TRANSACTION_POOL,
        "transactions",
        "Current size of the transaction pool",
        pendingTransactions::size);
  }

  public void evictOldTransactions() {
    final Instant removeTransactionsBefore =
        clock.instant().minus(poolConfig.getPendingTxRetentionPeriod(), ChronoUnit.HOURS);

    pendingTransactions.values().stream()
        .filter(transaction -> transaction.getAddedToPoolAt().isBefore(removeTransactionsBefore))
        .forEach(
            transactionInfo -> {
              traceLambda(LOG, "Evicted {} due to age", transactionInfo::toTraceLog);
              removeTransaction(transactionInfo.getTransaction());
            });
  }

  public List<Transaction> getLocalTransactions() {
    return pendingTransactions.values().stream()
        .filter(TransactionInfo::isReceivedFromLocalSource)
        .map(TransactionInfo::getTransaction)
        .collect(Collectors.toList());
  }

  public TransactionAddedStatus addRemoteTransaction(
      final Transaction transaction, final Account senderAccount) {

    final Long maybeInvalidNonce = lowestInvalidKnownNonceBySender.get(transaction.getSender());
    if (maybeInvalidNonce != null && transaction.getNonce() > maybeInvalidNonce) {
      debugLambda(
          LOG,
          "Dropping transaction {} since the sender has an invalid transaction with nonce {}",
          transaction::toTraceLog,
          maybeInvalidNonce::toString);
      return LOWER_NONCE_INVALID_TRANSACTION_KNOWN;
    }

    final TransactionInfo transactionInfo =
        new TransactionInfo(senderAccount, transaction, false, clock.instant());
    final TransactionAddedStatus transactionAddedStatus = addTransaction(transactionInfo);
    if (transactionAddedStatus.equals(ADDED)) {
      lowestInvalidKnownNonceBySender.computeIfPresent(
          transaction.getSender(),
          (address, invalidNonce) -> transaction.getNonce() >= invalidNonce ? null : invalidNonce);
      remoteTransactionAddedCounter.inc();
    }
    return transactionAddedStatus;
  }

  @VisibleForTesting
  public TransactionAddedStatus addLocalTransaction(
      final Transaction transaction, final Account senderAccount) {
    final TransactionAddedStatus transactionAdded =
        addTransaction(new TransactionInfo(senderAccount, transaction, true, clock.instant()));
    if (transactionAdded.equals(ADDED)) {
      localTransactionAddedCounter.inc();
    }
    return transactionAdded;
  }

  public void removeTransaction(final Transaction transaction) {
    doRemoveTransaction(transaction, false);
    notifyTransactionDropped(transaction);
  }

  public void transactionAddedToBlock(final Transaction transaction) {
    doRemoveTransaction(transaction, true);
    lowestInvalidKnownNonceBySender.computeIfPresent(
        transaction.getSender(),
        (address, invalidNonce) -> transaction.getNonce() >= invalidNonce ? null : invalidNonce);
  }

  protected void incrementTransactionRemovedCounter(
      final boolean receivedFromLocalSource, final boolean addedToBlock) {
    final String location = receivedFromLocalSource ? "local" : "remote";
    final String operation = addedToBlock ? "addedToBlock" : "dropped";
    transactionRemovedCounter.labels(location, operation).inc();
  }

  // There's a small edge case here we could encounter.
  // When we pass an upgrade block that has a new transaction type, we start allowing transactions
  // of that new type into our pool.
  // If we then reorg to a block lower than the upgrade block height _and_ we create a block, that
  // block could end up with transactions of the new type.
  // This seems like it would be very rare but worth it to document that we don't handle that case
  // right now.
  public void selectTransactions(final TransactionSelector selector) {
    synchronized (lock) {
      final List<Transaction> transactionsToRemove = new ArrayList<>();
      final Map<Address, AccountTransactionOrder> accountTransactions = new HashMap<>();
      final Iterator<TransactionInfo> prioritizedTransactions = prioritizedTransactions();
      while (prioritizedTransactions.hasNext()) {
        final TransactionInfo highestPriorityTransactionInfo = prioritizedTransactions.next();
        final AccountTransactionOrder accountTransactionOrder =
            accountTransactions.computeIfAbsent(
                highestPriorityTransactionInfo.getSender(), this::createSenderTransactionOrder);

        for (final Transaction transactionToProcess :
            accountTransactionOrder.transactionsToProcess(
                highestPriorityTransactionInfo.getTransaction())) {
          final TransactionSelectionResult result =
              selector.evaluateTransaction(transactionToProcess);
          switch (result) {
            case DELETE_TRANSACTION_AND_CONTINUE:
              transactionsToRemove.add(transactionToProcess);
              signalInvalidTransaction(transactionToProcess);
              break;
            case CONTINUE:
              break;
            case COMPLETE_OPERATION:
              if (LOG.isTraceEnabled()) {
                dump(transactionsToRemove);
              }
              transactionsToRemove.forEach(this::removeTransaction);
              return;
            default:
              throw new RuntimeException("Illegal value for TransactionSelectionResult.");
          }
        }
      }
      if (LOG.isTraceEnabled()) {
        dump(transactionsToRemove);
      }
      transactionsToRemove.forEach(this::removeTransaction);
    }
  }

  protected AccountTransactionOrder createSenderTransactionOrder(final Address address) {
    return new AccountTransactionOrder(
        transactionsBySender
            .get(address)
            .streamTransactionInfos()
            .map(TransactionInfo::getTransaction));
  }

  protected TransactionAddedStatus addTransactionForSenderAndNonce(
      final TransactionInfo transactionInfo) {
    final TransactionInfo existingTransaction =
        getTrackedTransactionBySenderAndNonce(transactionInfo);
    if (existingTransaction != null) {
      if (!transactionReplacementHandler.shouldReplace(
          existingTransaction, transactionInfo, chainHeadHeaderSupplier.get())) {
        traceLambda(
            LOG, "Reject underpriced transaction replacement {}", transactionInfo::toTraceLog);
        return REJECTED_UNDERPRICED_REPLACEMENT;
      }
      traceLambda(
          LOG,
          "Replace existing transaction {}, with new transaction {}",
          existingTransaction::toTraceLog,
          transactionInfo::toTraceLog);
      removeTransaction(existingTransaction.getTransaction());
    }
    trackTransactionBySenderAndNonce(transactionInfo);
    return ADDED;
  }

  protected void trackTransactionBySenderAndNonce(final TransactionInfo transactionInfo) {
    final TransactionsForSenderInfo transactionsForSenderInfo =
        transactionsBySender.computeIfAbsent(
            transactionInfo.getSender(), key -> new TransactionsForSenderInfo());
    transactionsForSenderInfo.addTransactionToTrack(transactionInfo.getNonce(), transactionInfo);
    traceLambda(LOG, "Tracked transaction by sender {}", transactionsForSenderInfo::toTraceLog);
  }

  protected void removeTransactionTrackedBySenderAndNonce(final Transaction transaction) {
    Optional.ofNullable(transactionsBySender.get(transaction.getSender()))
        .ifPresent(
            transactionsForSender -> {
              transactionsForSender.removeTrackedTransaction(transaction.getNonce());
              traceLambda(
                  LOG,
                  "Tracked transaction by sender {} after the removal of {}",
                  transactionsForSender::toTraceLog,
                  transaction::toTraceLog);
            });
  }

  protected TransactionInfo getTrackedTransactionBySenderAndNonce(
      final TransactionInfo transactionInfo) {
    final TransactionsForSenderInfo transactionsForSenderInfo =
        transactionsBySender.computeIfAbsent(
            transactionInfo.getSender(), key -> new TransactionsForSenderInfo());
    return transactionsForSenderInfo.getTransactionInfoForNonce(transactionInfo.getNonce());
  }

  protected void notifyTransactionAdded(final Transaction transaction) {
    pendingTransactionSubscribers.forEach(listener -> listener.onTransactionAdded(transaction));
  }

  protected void notifyTransactionDropped(final Transaction transaction) {
    transactionDroppedListeners.forEach(listener -> listener.onTransactionDropped(transaction));
  }

  public long maxSize() {
    return poolConfig.getTxPoolMaxSize();
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

  public long subscribePendingTransactions(final PendingTransactionListener listener) {
    return pendingTransactionSubscribers.subscribe(listener);
  }

  public void unsubscribePendingTransactions(final long id) {
    pendingTransactionSubscribers.unsubscribe(id);
  }

  public long subscribeDroppedTransactions(final PendingTransactionDroppedListener listener) {
    return transactionDroppedListeners.subscribe(listener);
  }

  public void unsubscribeDroppedTransactions(final long id) {
    transactionDroppedListeners.unsubscribe(id);
  }

  public OptionalLong getNextNonceForSender(final Address sender) {
    final TransactionsForSenderInfo transactionsForSenderInfo = transactionsBySender.get(sender);
    return transactionsForSenderInfo == null
        ? OptionalLong.empty()
        : transactionsForSenderInfo.maybeNextNonce();
  }

  public abstract void manageBlockAdded(final Block block);

  protected abstract void doRemoveTransaction(
      final Transaction transaction, final boolean addedToBlock);

  protected abstract Iterator<TransactionInfo> prioritizedTransactions();

  protected abstract TransactionAddedStatus addTransaction(final TransactionInfo transactionInfo);

  public void signalInvalidTransaction(final Transaction transaction) {
    final long invalidNonce =
        lowestInvalidKnownNonceBySender.merge(
            transaction.getSender(),
            transaction.getNonce(),
            (existingNonce, newNonce) -> {
              traceLambda(
                  LOG,
                  "Invalid transaction {}, previous lowest known invalid nonce for this sender {}",
                  transaction::toTraceLog,
                  existingNonce::toString);
              return Math.min(existingNonce, newNonce);
            });

    TransactionsForSenderInfo txsForSender = transactionsBySender.get(transaction.getSender());
    if (txsForSender != null) {
      txsForSender
          .streamTransactionInfos()
          .filter(txInfo -> txInfo.getTransaction().getNonce() >= invalidNonce)
          .collect(Collectors.toList())
          .forEach(
              txInfo -> {
                traceLambda(
                    LOG,
                    "Removing transaction {} since lowest nonce invalid transaction found {}",
                    txInfo::toTraceLog,
                    transaction::toTraceLog);
                txsForSender.removeTrackedTransaction(txInfo.getNonce());
                removeTransaction(txInfo.getTransaction());
              });
    }
  }

  /**
   * Tracks the additional metadata associated with transactions to enable prioritization for mining
   * and deciding which transactions to drop when the transaction pool reaches its size limit.
   */
  public static class TransactionInfo {

    private static final AtomicLong TRANSACTIONS_ADDED = new AtomicLong();
    private final Account senderAccount;
    private final Transaction transaction;
    private final boolean receivedFromLocalSource;
    private final Instant addedToPoolAt;
    private final long sequence; // Allows prioritization based on order transactions are added

    private final long nonceDistance;

    public TransactionInfo(
        final Account senderAccount,
        final Transaction transaction,
        final boolean receivedFromLocalSource,
        final Instant addedToPoolAt) {
      this.senderAccount = senderAccount;
      this.transaction = transaction;
      this.receivedFromLocalSource = receivedFromLocalSource;
      this.addedToPoolAt = addedToPoolAt;
      this.sequence = TRANSACTIONS_ADDED.getAndIncrement();
      this.nonceDistance = transaction.getNonce() - senderAccount.getNonce();
    }

    public Account getSenderAccount() {
      return senderAccount;
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

    public boolean isReceivedFromLocalSource() {
      return receivedFromLocalSource;
    }

    public Hash getHash() {
      return transaction.getHash();
    }

    public Instant getAddedToPoolAt() {
      return addedToPoolAt;
    }

    public long getNonceDistance() {
      return nonceDistance;
    }

    public static List<Transaction> toTransactionList(
        final Collection<TransactionInfo> transactionsInfo) {
      return transactionsInfo.stream()
          .map(TransactionInfo::getTransaction)
          .collect(Collectors.toUnmodifiableList());
    }

    public String toTraceLog() {
      return "{sequence: "
          + sequence
          + ", addedAt: "
          + addedToPoolAt
          + ", nonceDistance: "
          + nonceDistance
          + ", senderAccount: "
          + senderAccount
          + ", "
          + transaction.toTraceLog()
          + "}";
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
    NONCE_TOO_FAR_IN_FUTURE_FOR_SENDER(),
    LOWER_NONCE_INVALID_TRANSACTION_KNOWN(),
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

  Optional<TransactionInfo> lowestValueTxForRemovalBySender(NavigableSet<TransactionInfo> txSet) {
    return txSet.descendingSet().stream()
        .filter(
            tx ->
                transactionsBySender
                    .get(tx.getSender())
                    .maybeLastTx()
                    .filter(tx::equals)
                    .isPresent())
        .findFirst();
  }

  public String toTraceLog() {
    synchronized (lock) {
      return "Transactions in order { "
          + StreamSupport.stream(
                  Spliterators.spliteratorUnknownSize(
                      prioritizedTransactions(), Spliterator.ORDERED),
                  false)
              .map(TransactionInfo::toTraceLog)
              .collect(Collectors.joining("; "))
          + " }, Transactions by sender { "
          + transactionsBySender.entrySet().stream()
              .map(e -> "(" + e.getKey() + ") " + e.getValue().toTraceLog())
              .collect(Collectors.joining("; "))
          + " }, Lowest invalid nonce by sender {"
          + lowestInvalidKnownNonceBySender
          + "}";
    }
  }

  void dump(final List<Transaction> transactionsToRemove) {
    traceLambda(LOG, "Transaction pool dump {}", this::toTraceLog);
    traceLambda(
        LOG,
        "Transactions to remove {}",
        () ->
            transactionsToRemove.stream()
                .map(Transaction::toTraceLog)
                .collect(Collectors.joining("; ")));
  }
}
