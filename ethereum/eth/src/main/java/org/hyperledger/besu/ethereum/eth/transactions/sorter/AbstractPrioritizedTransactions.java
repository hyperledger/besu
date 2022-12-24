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

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.maxBy;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ADDED;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ALREADY_KNOWN;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.NONCE_TOO_FAR_IN_FUTURE_FOR_SENDER;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.AccountTransactionOrder;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactionDroppedListener;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactionListener;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.cache.ReadyTransactionsCache;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountState;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.util.Subscribers;

import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Predicate;
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
public abstract class AbstractPrioritizedTransactions implements PendingTransactionsSorter {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractPrioritizedTransactions.class);

  protected final Clock clock;
  protected final TransactionPoolConfiguration poolConfig;

  protected final Object lock = new Object();
  protected final Map<Hash, PendingTransaction> prioritizedPendingTransactions;

  protected final TreeSet<PendingTransaction> orderByFee;
  protected final Map<Address, Long> expectedNonceForSender;

  protected final ReadyTransactionsCache readyTransactionsCache;

  protected final Subscribers<PendingTransactionListener> pendingTransactionSubscribers =
      Subscribers.create();

  protected final Subscribers<PendingTransactionDroppedListener> transactionDroppedListeners =
      Subscribers.create();

  protected final LabelledMetric<Counter> transactionAddedCounter;
  protected final LabelledMetric<Counter> transactionRemovedCounter;
  protected final LabelledMetric<Counter> transactionReplacedCounter;

  protected final BiFunction<PendingTransaction, PendingTransaction, Boolean>
      transactionReplacementTester;

  private final Set<Address> localSenders = ConcurrentHashMap.newKeySet();

  public AbstractPrioritizedTransactions(
      final TransactionPoolConfiguration poolConfig,
      final Clock clock,
      final MetricsSystem metricsSystem,
      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
          transactionReplacementTester,
      final ReadyTransactionsCache readyTransactionsCache) {
    this.poolConfig = poolConfig;
    this.prioritizedPendingTransactions = new ConcurrentHashMap<>(poolConfig.getTxPoolMaxSize());
    this.expectedNonceForSender = new HashMap<>();
    this.clock = clock;
    this.transactionReplacementTester = transactionReplacementTester;
    this.transactionAddedCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.TRANSACTION_POOL,
            "transactions_added_total",
            "Count of transactions added to the transaction pool",
            "source");

    transactionRemovedCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.TRANSACTION_POOL,
            "transactions_removed_total",
            "Count of transactions removed from the transaction pool",
            "source",
            "operation");
    transactionReplacedCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.TRANSACTION_POOL,
            "transactions_replaced_total",
            "Count of transactions replaced in the transaction pool",
            "source",
            "list");
    metricsSystem.createIntegerGauge(
        BesuMetricCategory.TRANSACTION_POOL,
        "transactions",
        "Current size of the transaction pool",
        prioritizedPendingTransactions::size);

    this.readyTransactionsCache = readyTransactionsCache;
    this.orderByFee = new TreeSet<>(this::compareByFee);
  }

  protected abstract int compareByFee(final PendingTransaction pt1, final PendingTransaction pt2);

  @Override
  public void evictOldTransactions() {}

  @Override
  public List<Transaction> getLocalTransactions() {
    return prioritizedPendingTransactions.values().stream()
        .filter(PendingTransaction::isReceivedFromLocalSource)
        .map(PendingTransaction::getTransaction)
        .collect(Collectors.toList());
  }

  @Override
  public TransactionAddedResult addRemoteTransaction(
      final Transaction transaction, final Optional<Account> maybeSenderAccount) {

    return addTransaction(
        new PendingTransaction.Remote(transaction, clock.instant()), maybeSenderAccount);
  }

  @Override
  public TransactionAddedResult addLocalTransaction(
      final Transaction transaction, final Optional<Account> maybeSenderAccount) {

    final TransactionAddedResult transactionAdded =
        addTransaction(
            new PendingTransaction.Local(transaction, clock.instant()), maybeSenderAccount);
    if (transactionAdded.equals(ADDED)) {
      localSenders.add(transaction.getSender());
    }
    return transactionAdded;
  }

  private TransactionAddedResult addTransaction(
      final PendingTransaction pendingTransaction, final Optional<Account> maybeSenderAccount) {

    final long senderNonce = maybeSenderAccount.map(AccountState::getNonce).orElse(0L);

    final long nonceDistance = pendingTransaction.getNonce() - senderNonce;

    if (nonceDistance < 0) {
      traceLambda(
          LOG,
          "Drop already confirmed transaction {}, since current sender nonce is {}",
          pendingTransaction::toTraceLog,
          () -> senderNonce);
      return ALREADY_KNOWN;
    } else if (nonceDistance >= poolConfig.getTxPoolMaxFutureTransactionByAccount()) {
      traceLambda(
          LOG,
          "Drop too much in the future transaction {}, since current sender nonce is {}",
          pendingTransaction::toTraceLog,
          () -> senderNonce);
      return NONCE_TOO_FAR_IN_FUTURE_FOR_SENDER;
    }

    synchronized (lock) {
      var addResult = readyTransactionsCache.add(pendingTransaction, senderNonce);

      if (addResult.equals(ADDED) || addResult.isReplacement()) {
        maybePrioritizeAddedTransaction(pendingTransaction, senderNonce, addResult);
      }

      return addResult;
    }
  }

  private void maybePrioritizeAddedTransaction(
      final PendingTransaction addedReadyTransaction,
      final long senderNonce,
      final TransactionAddedResult addResult) {
    addResult
        .maybeReplacedTransaction()
        .ifPresent(
            replacedTx -> {
              traceLambda(
                  LOG,
                  "Replace existing transaction {}, with new transaction {}",
                  replacedTx::toTraceLog,
                  addedReadyTransaction::toTraceLog);
              removeReplacedPrioritizedTransaction(replacedTx);
            });

    final var expectedNonce =
        expectedNonceForSender.getOrDefault(addedReadyTransaction.getSender(), senderNonce);

    // only add to prioritized if it appends to the already prioritized for its sender, without gaps
    if (!addResult.isReplacement() && addedReadyTransaction.getNonce() != expectedNonce) {
      traceLambda(
          LOG,
          "Not adding transaction {} to prioritized list since expected next nonce for this sender is {}",
          addedReadyTransaction::toTraceLog,
          () -> expectedNonce);
      return;
    }

    if (prioritizedPendingTransactions.size() >= poolConfig.getTxPoolMaxSize()) {
      LOG.trace("Max number of prioritized transactions reached");

      var currentLeastPriorityTx = orderByFee.first();
      if (compareByFee(addedReadyTransaction, currentLeastPriorityTx) <= 0) {
        traceLambda(
            LOG,
            "Not adding incoming transaction {} to the prioritized list, since it is less valuable than the current least priority transactions {}",
            addedReadyTransaction::toTraceLog,
            currentLeastPriorityTx::toTraceLog);
        return;
      } else if (currentLeastPriorityTx.getSender().equals(addedReadyTransaction.getSender())) {
        traceLambda(
            LOG,
            "Not adding incoming transaction {} to the prioritized list, since it is from the same sender as the least valuable one {}",
            addedReadyTransaction::toTraceLog,
            currentLeastPriorityTx::toTraceLog);
        return;
      }

      traceLambda(
          LOG,
          "Demote transactions for the sender of the current least priority transaction {}, to make space for the incoming transaction {}",
          currentLeastPriorityTx::toTraceLog,
          addedReadyTransaction::toTraceLog);
      demoteLastTransactionForSenderOf(currentLeastPriorityTx);
    }

    addPrioritizedTransaction(addedReadyTransaction, addResult.isReplacement());
    notifyTransactionAdded(addedReadyTransaction.getTransaction());
  }

  private void removeInvalidTransaction(final Transaction transaction) {
    List<PendingTransaction> allSenderTxs =
        prioritizedPendingTransactions.values().stream()
            .filter(pt -> pt.getSender().equals(transaction.getSender()))
            .collect(Collectors.toUnmodifiableList());

    Transaction lastGoodTx = null;
    for (final var pendingTransaction : allSenderTxs) {
      if (pendingTransaction.getNonce() < transaction.getNonce()) {
        lastGoodTx = pendingTransaction.getTransaction();
      }
      prioritizedPendingTransactions.remove(pendingTransaction.getHash());
      removeFromOrderedTransactions(pendingTransaction, false);
      readyTransactionsCache.remove(pendingTransaction.getTransaction());
      notifyTransactionDropped(pendingTransaction.getTransaction());
    }

    if (lastGoodTx == null) {
      expectedNonceForSender.remove(transaction.getSender());
    } else {
      expectedNonceForSender.put(transaction.getSender(), lastGoodTx.getNonce());
    }
  }

  private void notifyTransactionAdded(final Transaction transaction) {
    pendingTransactionSubscribers.forEach(listener -> listener.onTransactionAdded(transaction));
  }

  private void notifyTransactionDropped(final Transaction transaction) {
    transactionDroppedListeners.forEach(listener -> listener.onTransactionDropped(transaction));
  }

  protected void incrementTransactionAddedCounter(final boolean receivedFromLocalSource) {
    final String location = receivedFromLocalSource ? "local" : "remote";
    transactionAddedCounter.labels(location).inc();
  }

  protected void incrementTransactionRemovedCounter(
      final boolean receivedFromLocalSource, final boolean addedToBlock) {
    final String location = receivedFromLocalSource ? "local" : "remote";
    final String operation = addedToBlock ? "addedToBlock" : "dropped";
    transactionRemovedCounter.labels(location, operation).inc();
  }

  protected void incrementTransactionReplacedCounter(final boolean receivedFromLocalSource) {
    final String location = receivedFromLocalSource ? "local" : "remote";
    transactionReplacedCounter.labels(location, "priority").inc();
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

        for (final Transaction transactionToProcess :
            accountTransactionOrder.transactionsToProcess(
                highestPriorityPendingTransaction.getTransaction())) {
          final TransactionSelectionResult result =
              selector.evaluateTransaction(transactionToProcess);
          switch (result) {
            case DELETE_TRANSACTION_AND_CONTINUE:
              transactionsToRemove.add(transactionToProcess);
              break;
            case CONTINUE:
              break;
            case COMPLETE_OPERATION:
              transactionsToRemove.forEach(this::removeInvalidTransaction);
              return;
            default:
              throw new RuntimeException("Illegal value for TransactionSelectionResult.");
          }
        }
      }
      transactionsToRemove.forEach(this::removeInvalidTransaction);
    }
  }

  private AccountTransactionOrder createSenderTransactionOrder(final Address address) {
    return new AccountTransactionOrder(
        readyTransactionsCache
            .streamReadyTransactions(address)
            .map(PendingTransaction::getTransaction));
  }

  @Override
  public long maxSize() {
    return poolConfig.getTxPoolMaxSize();
  }

  @Override
  public int size() {
    return prioritizedPendingTransactions.size();
  }

  @Override
  public boolean containsTransaction(final Hash transactionHash) {
    return prioritizedPendingTransactions.containsKey(transactionHash);
  }

  @Override
  public Optional<Transaction> getTransactionByHash(final Hash transactionHash) {
    return Optional.ofNullable(prioritizedPendingTransactions.get(transactionHash))
        .map(PendingTransaction::getTransaction);
  }

  @Override
  public Set<PendingTransaction> getPrioritizedPendingTransactions() {
    return new HashSet<>(prioritizedPendingTransactions.values());
  }

  @Override
  public long subscribePendingTransactions(final PendingTransactionListener listener) {
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
    return readyTransactionsCache.getNextReadyNonce(sender);
  }

  @Override
  public void manageBlockAdded(
      final Block block, final List<Transaction> confirmedTransactions, final FeeMarket feeMarket) {
    synchronized (lock) {
      transactionsAddedToBlock(confirmedTransactions);
      manageBlockAdded(block, feeMarket);
      promoteFromReady();
    }
  }

  protected abstract void manageBlockAdded(final Block block, final FeeMarket feeMarket);

  private void transactionsAddedToBlock(final List<Transaction> confirmedTransactions) {

    confirmedTransactions.stream()
        .map(Transaction::getHash)
        .map(prioritizedPendingTransactions::remove)
        .filter(Objects::nonNull)
        .forEach(
            tx -> {
              removeFromOrderedTransactions(tx, true);
              incrementTransactionRemovedCounter(tx.isReceivedFromLocalSource(), true);
            });

    // update expected nonce for senders
    final var orderedConfirmedNonceBySender = maxConfirmedNonceNySender(confirmedTransactions);
    for (final var confirmedNonceEntry : orderedConfirmedNonceBySender.entrySet()) {
      expectedNonceForSender.computeIfPresent(
          confirmedNonceEntry.getKey(),
          (sender, expectedNonce) -> {
            if (confirmedNonceEntry.getValue().get() >= expectedNonce - 1) {
              // all the current prioritized transactions for the sender are confirmed, remove the
              // entry
              return null;
            }
            return expectedNonce;
          });
    }

    readyTransactionsCache.removeConfirmedTransactions(orderedConfirmedNonceBySender);
  }

  private Map<Address, Optional<Long>> maxConfirmedNonceNySender(
      final List<Transaction> confirmedTransactions) {
    return confirmedTransactions.stream()
        .collect(
            groupingBy(
                Transaction::getSender, mapping(Transaction::getNonce, maxBy(Long::compare))));
  }

  protected void promoteFromReady() {
    final int maxPromotable = poolConfig.getTxPoolMaxSize() - prioritizedPendingTransactions.size();

    if (maxPromotable > 0) {
      final Predicate<PendingTransaction> notAlreadyPrioritized =
          pt -> prioritizedPendingTransactions.containsKey(pt.getHash());

      final List<PendingTransaction> promoteTransactions =
          readyTransactionsCache.getPromotableTransactions(
              maxPromotable, notAlreadyPrioritized.and(getPromotionFilter()));

      promoteTransactions.forEach(this::addPrioritizedTransaction);
    }
  }

  private void removeReplacedPrioritizedTransaction(final PendingTransaction pendingTransaction) {
    final PendingTransaction removedPendingTransaction =
        prioritizedPendingTransactions.remove(pendingTransaction.getHash());
    if (removedPendingTransaction != null) {
      removeFromOrderedTransactions(removedPendingTransaction, false);
      incrementTransactionRemovedCounter(pendingTransaction.isReceivedFromLocalSource(), false);
    }
    incrementTransactionReplacedCounter(pendingTransaction.isReceivedFromLocalSource());
  }

  protected abstract void removeFromOrderedTransactions(
      final PendingTransaction removedPendingTx, final boolean addedToBlock);

  private Iterator<PendingTransaction> prioritizedTransactions() {
    return orderByFee.descendingIterator();
  }

  private void demoteLastTransactionForSenderOf(final PendingTransaction firstDemotedTx) {
    final var demotableSenderTxs =
        readyTransactionsCache
            .streamReadyTransactions(firstDemotedTx.getSender(), firstDemotedTx.getNonce())
            .iterator();

    var lastPrioritizedForSender = firstDemotedTx;
    while (demotableSenderTxs.hasNext()) {
      final var maybeNewLast = demotableSenderTxs.next();
      if (!prioritizedPendingTransactions.containsKey(maybeNewLast.getHash())) {
        break;
      }
      lastPrioritizedForSender = maybeNewLast;
    }

    prioritizedPendingTransactions.remove(lastPrioritizedForSender.getHash());
    removeFromOrderedTransactions(lastPrioritizedForSender, false);

    expectedNonceForSender.compute(
        firstDemotedTx.getSender(),
        (sender, expectedNonce) -> {
          if (expectedNonce == firstDemotedTx.getNonce() + 1
              || readyTransactionsCache.get(sender, expectedNonce - 1).isEmpty()) {
            return null;
          }
          return expectedNonce - 1;
        });

    traceLambda(
        LOG,
        "Demoted transaction {}, to make space for the incoming transaction",
        lastPrioritizedForSender::toTraceLog);
  }

  protected void addPrioritizedTransaction(final PendingTransaction prioritizedTx) {
    addPrioritizedTransaction(prioritizedTx, false);
  }

  protected void addPrioritizedTransaction(
      final PendingTransaction prioritizedTx, final boolean isReplacement) {
    prioritizedPendingTransactions.put(prioritizedTx.getHash(), prioritizedTx);
    orderByFee.add(prioritizedTx);
    if (!isReplacement) {
      expectedNonceForSender.put(prioritizedTx.getSender(), prioritizedTx.getNonce() + 1);
    }
    incrementTransactionAddedCounter(prioritizedTx.isReceivedFromLocalSource());
  }

  protected abstract Predicate<PendingTransaction> getPromotionFilter();

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
                  + " } Expected nonce for sender size "
                  + expectedNonceForSender.size()
                  + " content "
                  + expectedNonceForSender.toString());

      return sb.toString();
    }
  }

  @Override
  public String logStats() {
    return "Prioritized " + prioritizedPendingTransactions.size();
  }

  @Override
  public boolean isLocalSender(final Address sender) {
    return localSenders.contains(sender);
  }
}
