/*
 * Copyright Hyperledger Besu Contributors.
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

import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ADDED;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ALREADY_KNOWN;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.LOWER_NONCE_INVALID_TRANSACTION_KNOWN;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.NONCE_TOO_FAR_IN_FUTURE_FOR_SENDER;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.REJECTED_UNDERPRICED_REPLACEMENT;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactionAddedListener;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactionDroppedListener;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolReplacementHandler;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountState;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.util.Subscribers;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the current set of pending transactions with the ability to iterate them based on priority
 * for mining or look-up by hash.
 *
 * <p>This class is safe for use across multiple threads.
 */
public abstract class AbstractPendingTransactionsSorter implements PendingTransactions {
  private static final int DEFAULT_LOWEST_INVALID_KNOWN_NONCE_CACHE = 10_000;
  private static final Logger LOG =
      LoggerFactory.getLogger(AbstractPendingTransactionsSorter.class);

  protected final Clock clock;
  protected final TransactionPoolConfiguration poolConfig;

  protected final Object lock = new Object();
  protected final Map<Hash, PendingTransaction> pendingTransactions;

  protected final Map<Address, PendingTransactionsForSender> transactionsBySender =
      new ConcurrentHashMap<>();

  protected final LowestInvalidNonceCache lowestInvalidKnownNonceCache =
      new LowestInvalidNonceCache(DEFAULT_LOWEST_INVALID_KNOWN_NONCE_CACHE);
  protected final Subscribers<PendingTransactionAddedListener> pendingTransactionSubscribers =
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
    this.pendingTransactions = new ConcurrentHashMap<>(poolConfig.getTxPoolMaxSize());
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

  @Override
  public void reset() {
    pendingTransactions.clear();
    transactionsBySender.clear();
    lowestInvalidKnownNonceCache.reset();
  }

  @Override
  public void evictOldTransactions() {
    final long removeTransactionsBefore =
        clock
            .instant()
            .minus(poolConfig.getPendingTxRetentionPeriod(), ChronoUnit.HOURS)
            .toEpochMilli();

    pendingTransactions.values().stream()
        .filter(transaction -> transaction.getAddedAt() < removeTransactionsBefore)
        .forEach(
            transactionInfo -> {
              LOG.atTrace()
                  .setMessage("Evicted {} due to age")
                  .addArgument(transactionInfo::toTraceLog)
                  .log();
              removeTransaction(transactionInfo.getTransaction());
            });
  }

  @Override
  public List<Transaction> getLocalTransactions() {
    return pendingTransactions.values().stream()
        .filter(PendingTransaction::isReceivedFromLocalSource)
        .map(PendingTransaction::getTransaction)
        .collect(Collectors.toList());
  }

  @Override
  public List<Transaction> getPriorityTransactions() {
    return pendingTransactions.values().stream()
        .filter(PendingTransaction::hasPriority)
        .map(PendingTransaction::getTransaction)
        .collect(Collectors.toList());
  }

  @Override
  public TransactionAddedResult addTransaction(
      final PendingTransaction transaction, final Optional<Account> maybeSenderAccount) {

    if (!transaction.isReceivedFromLocalSource()
        && lowestInvalidKnownNonceCache.hasInvalidLowerNonce(transaction.getTransaction())) {
      LOG.atDebug()
          .setMessage(
              "Dropping transaction {} since the sender has an invalid transaction with lower nonce")
          .addArgument(transaction::toTraceLog)
          .log();
      return LOWER_NONCE_INVALID_TRANSACTION_KNOWN;
    }

    final TransactionAddedResult transactionAddedStatus =
        internalAddTransaction(transaction, maybeSenderAccount);
    if (transactionAddedStatus.equals(ADDED)) {
      if (!transaction.isReceivedFromLocalSource()) {
        lowestInvalidKnownNonceCache.registerValidTransaction(transaction.getTransaction());
        remoteTransactionAddedCounter.inc();
      } else {
        localTransactionAddedCounter.inc();
      }
    }
    return transactionAddedStatus;
  }

  void removeTransaction(final Transaction transaction) {
    removeTransaction(transaction, false);
    notifyTransactionDropped(transaction);
  }

  @Override
  public void manageBlockAdded(
      final BlockHeader blockHeader,
      final List<Transaction> confirmedTransactions,
      final List<Transaction> reorgTransactions,
      final FeeMarket feeMarket) {
    synchronized (lock) {
      confirmedTransactions.forEach(this::transactionAddedToBlock);
      manageBlockAdded(blockHeader);
    }
  }

  protected abstract void manageBlockAdded(final BlockHeader blockHeader);

  public void transactionAddedToBlock(final Transaction transaction) {
    removeTransaction(transaction, true);
    lowestInvalidKnownNonceCache.registerValidTransaction(transaction);
  }

  private void incrementTransactionRemovedCounter(
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
  @Override
  public void selectTransactions(final TransactionSelector selector) {
    synchronized (lock) {
      final Set<Transaction> transactionsToRemove = new HashSet<>();
      final Map<Address, AccountTransactionOrder> accountTransactions = new HashMap<>();
      final Iterator<PendingTransaction> prioritizedTransactions = prioritizedTransactions();
      while (prioritizedTransactions.hasNext()) {
        final PendingTransaction highestPriorityPendingTransaction = prioritizedTransactions.next();
        final AccountTransactionOrder accountTransactionOrder =
            accountTransactions.computeIfAbsent(
                highestPriorityPendingTransaction.getSender(), this::createSenderTransactionOrder);

        for (final PendingTransaction transactionToProcess :
            accountTransactionOrder.transactionsToProcess(highestPriorityPendingTransaction)) {
          final TransactionSelectionResult result =
              selector.evaluateTransaction(transactionToProcess);

          if (result.discard()) {
            transactionsToRemove.add(transactionToProcess.getTransaction());
            transactionsToRemove.addAll(
                signalInvalidAndGetDependentTransactions(transactionToProcess.getTransaction()));
          }

          if (result.stop()) {
            transactionsToRemove.forEach(this::removeTransaction);
            return;
          }
        }
      }
      transactionsToRemove.forEach(this::removeTransaction);
    }
  }

  private AccountTransactionOrder createSenderTransactionOrder(final Address address) {
    return new AccountTransactionOrder(
        transactionsBySender.get(address).streamPendingTransactions());
  }

  private TransactionAddedResult addTransactionForSenderAndNonce(
      final PendingTransaction pendingTransaction, final Optional<Account> maybeSenderAccount) {

    PendingTransactionsForSender pendingTxsForSender =
        transactionsBySender.computeIfAbsent(
            pendingTransaction.getSender(),
            address -> new PendingTransactionsForSender(maybeSenderAccount));

    PendingTransaction existingPendingTx =
        pendingTxsForSender.getPendingTransactionForNonce(pendingTransaction.getNonce());

    final Optional<Transaction> maybeReplacedTransaction;
    if (existingPendingTx != null) {
      if (!transactionReplacementHandler.shouldReplace(
          existingPendingTx, pendingTransaction, chainHeadHeaderSupplier.get())) {
        LOG.atTrace()
            .setMessage("Reject underpriced transaction replacement {}")
            .addArgument(pendingTransaction::toTraceLog)
            .log();
        return REJECTED_UNDERPRICED_REPLACEMENT;
      }
      LOG.atTrace()
          .setMessage("Replace existing transaction {}, with new transaction {}")
          .addArgument(existingPendingTx::toTraceLog)
          .addArgument(pendingTransaction::toTraceLog)
          .log();
      maybeReplacedTransaction = Optional.of(existingPendingTx.getTransaction());
    } else {
      maybeReplacedTransaction = Optional.empty();
    }

    pendingTxsForSender.updateSenderAccount(maybeSenderAccount);
    pendingTxsForSender.trackPendingTransaction(pendingTransaction);
    LOG.atTrace()
        .setMessage("Tracked transaction by sender {}")
        .addArgument(pendingTxsForSender::toTraceLog)
        .log();
    maybeReplacedTransaction.ifPresent(this::removeTransaction);
    return ADDED;
  }

  private void removePendingTransactionBySenderAndNonce(
      final PendingTransaction pendingTransaction) {
    final Transaction transaction = pendingTransaction.getTransaction();
    Optional.ofNullable(transactionsBySender.get(transaction.getSender()))
        .ifPresent(
            pendingTxsForSender -> {
              pendingTxsForSender.removeTrackedPendingTransaction(pendingTransaction);
              if (pendingTxsForSender.transactionCount() == 0) {
                LOG.trace(
                    "Removing sender {} from transactionBySender since no more tracked transactions",
                    transaction.getSender());
                transactionsBySender.remove(transaction.getSender());
              } else {
                LOG.atTrace()
                    .setMessage("Tracked transaction by sender {} after the removal of {}")
                    .addArgument(pendingTxsForSender::toTraceLog)
                    .addArgument(transaction::toTraceLog)
                    .log();
              }
            });
  }

  private void notifyTransactionAdded(final Transaction transaction) {
    pendingTransactionSubscribers.forEach(listener -> listener.onTransactionAdded(transaction));
  }

  private void notifyTransactionDropped(final Transaction transaction) {
    transactionDroppedListeners.forEach(listener -> listener.onTransactionDropped(transaction));
  }

  @Override
  public long maxSize() {
    return poolConfig.getTxPoolMaxSize();
  }

  @Override
  public int size() {
    return pendingTransactions.size();
  }

  @Override
  public boolean containsTransaction(final Transaction transaction) {
    return pendingTransactions.containsKey(transaction.getHash());
  }

  @Override
  public Optional<Transaction> getTransactionByHash(final Hash transactionHash) {
    return Optional.ofNullable(pendingTransactions.get(transactionHash))
        .map(PendingTransaction::getTransaction);
  }

  @Override
  public List<PendingTransaction> getPendingTransactions() {
    return new ArrayList<>(pendingTransactions.values());
  }

  @Override
  public long subscribePendingTransactions(final PendingTransactionAddedListener listener) {
    return pendingTransactionSubscribers.subscribe(listener);
  }

  @Override
  public void unsubscribePendingTransactions(final long id) {
    pendingTransactionSubscribers.unsubscribe(id);
  }

  @Override
  public long subscribeDroppedTransactions(final PendingTransactionDroppedListener listener) {
    return transactionDroppedListeners.subscribe(listener);
  }

  @Override
  public void unsubscribeDroppedTransactions(final long id) {
    transactionDroppedListeners.unsubscribe(id);
  }

  @Override
  public OptionalLong getNextNonceForSender(final Address sender) {
    final PendingTransactionsForSender pendingTransactionsForSender =
        transactionsBySender.get(sender);
    return pendingTransactionsForSender == null
        ? OptionalLong.empty()
        : pendingTransactionsForSender.maybeNextNonce();
  }

  private void removeTransaction(final Transaction transaction, final boolean addedToBlock) {
    synchronized (lock) {
      final PendingTransaction removedPendingTx = pendingTransactions.remove(transaction.getHash());
      if (removedPendingTx != null) {
        removePrioritizedTransaction(removedPendingTx);
        removePendingTransactionBySenderAndNonce(removedPendingTx);
        incrementTransactionRemovedCounter(
            removedPendingTx.isReceivedFromLocalSource(), addedToBlock);
      }
    }
  }

  protected abstract void removePrioritizedTransaction(PendingTransaction removedPendingTx);

  protected abstract Iterator<PendingTransaction> prioritizedTransactions();

  protected abstract void prioritizeTransaction(final PendingTransaction pendingTransaction);

  private TransactionAddedResult internalAddTransaction(
      final PendingTransaction pendingTransaction, final Optional<Account> maybeSenderAccount) {
    final Transaction transaction = pendingTransaction.getTransaction();
    synchronized (lock) {
      if (pendingTransactions.containsKey(pendingTransaction.getHash())) {
        LOG.atTrace()
            .setMessage("Already known transaction {}")
            .addArgument(pendingTransaction::toTraceLog)
            .log();
        return ALREADY_KNOWN;
      }

      if (transaction.getNonce() - maybeSenderAccount.map(AccountState::getNonce).orElse(0L)
          >= poolConfig.getTxPoolMaxFutureTransactionByAccount()) {
        LOG.atTrace()
            .setMessage(
                "Transaction {} not added because nonce too far in the future for sender {}")
            .addArgument(transaction::toTraceLog)
            .addArgument(
                () ->
                    maybeSenderAccount
                        .map(Account::getAddress)
                        .map(Address::toString)
                        .orElse("unknown"))
            .log();
        return NONCE_TOO_FAR_IN_FUTURE_FOR_SENDER;
      }

      final TransactionAddedResult transactionAddedStatus =
          addTransactionForSenderAndNonce(pendingTransaction, maybeSenderAccount);

      if (!transactionAddedStatus.equals(ADDED)) {
        return transactionAddedStatus;
      }

      pendingTransactions.put(pendingTransaction.getHash(), pendingTransaction);
      prioritizeTransaction(pendingTransaction);

      if (pendingTransactions.size() > poolConfig.getTxPoolMaxSize()) {
        evictLessPriorityTransactions();
      }
    }
    notifyTransactionAdded(pendingTransaction.getTransaction());
    return ADDED;
  }

  protected abstract PendingTransaction getLeastPriorityTransaction();

  private void evictLessPriorityTransactions() {
    final PendingTransaction leastPriorityTx = getLeastPriorityTransaction();
    // evict all txs for the sender with nonce >= the least priority one to avoid gaps
    final var pendingTxsForSender = transactionsBySender.get(leastPriorityTx.getSender());
    final var txsToEvict = pendingTxsForSender.getPendingTransactions(leastPriorityTx.getNonce());

    // remove backward to avoid gaps
    for (int i = txsToEvict.size() - 1; i >= 0; i--) {
      removeTransaction(txsToEvict.get(i).getTransaction());
    }
  }

  @Override
  public String logStats() {
    return "Pending " + pendingTransactions.size();
  }

  @Override
  public String toTraceLog() {
    synchronized (lock) {
      StringBuilder sb =
          new StringBuilder(
              "Prioritized transactions { "
                  + StreamSupport.stream(
                          Spliterators.spliteratorUnknownSize(
                              prioritizedTransactions(), Spliterator.ORDERED),
                          false)
                      .map(PendingTransaction::toTraceLog)
                      .collect(Collectors.joining("; "))
                  + " }");

      return sb.toString();
    }
  }

  @Override
  public List<Transaction> signalInvalidAndGetDependentTransactions(final Transaction transaction) {
    final long invalidNonce = lowestInvalidKnownNonceCache.registerInvalidTransaction(transaction);

    PendingTransactionsForSender txsForSender = transactionsBySender.get(transaction.getSender());
    if (txsForSender != null) {
      return txsForSender
          .streamPendingTransactions()
          .filter(pendingTx -> pendingTx.getTransaction().getNonce() > invalidNonce)
          .peek(
              pendingTx ->
                  LOG.atTrace()
                      .setMessage(
                          "Transaction {} invalid since there is a lower invalid nonce {} for the sender")
                      .addArgument(pendingTx::toTraceLog)
                      .addArgument(invalidNonce)
                      .log())
          .map(PendingTransaction::getTransaction)
          .collect(Collectors.toList());
    }
    return List.of();
  }

  @Override
  public void signalInvalidAndRemoveDependentTransactions(final Transaction transaction) {
    synchronized (lock) {
      signalInvalidAndGetDependentTransactions(transaction).forEach(this::removeTransaction);
    }
  }
}
