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

import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;

import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
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
public abstract class AbstractPrioritizedTransactions {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractPrioritizedTransactions.class);

  protected final Clock clock;
  protected final TransactionPoolConfiguration poolConfig;

  protected final Object lock = new Object();
  protected final Map<Hash, PendingTransaction> prioritizedPendingTransactions;

  protected final TreeSet<PendingTransaction> orderByFee;
  protected final Map<Address, Long> expectedNonceForSender;

  protected final LabelledMetric<Counter> transactionAddedCounter;
  protected final LabelledMetric<Counter> transactionRemovedCounter;
  protected final LabelledMetric<Counter> transactionReplacedCounter;

  protected final BiFunction<PendingTransaction, PendingTransaction, Boolean>
      transactionReplacementTester;

  public AbstractPrioritizedTransactions(
      final TransactionPoolConfiguration poolConfig,
      final Clock clock,
      final MetricsSystem metricsSystem,
      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
          transactionReplacementTester) {
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

    //    this.readyTransactionsCache = readyTransactionsCache;
    this.orderByFee = new TreeSet<>(this::compareByFee);
  }

  protected abstract int compareByFee(final PendingTransaction pt1, final PendingTransaction pt2);

  public PrioritizeResult maybePrioritizeAddedTransaction(
      final PendingTransaction addedReadyTransaction,
      final long senderNonce,
      final TransactionAddedResult addResult) {

    //    Optional<PendingTransaction> overflowTx = Optional.empty();

    final var prioritizedTxReplaced =
        addResult
            .maybeReplacedTransaction()
            .filter(replacedTx -> prioritizedPendingTransactions.containsKey(replacedTx.getHash()))
            .map(
                replacedTx -> {
                  traceLambda(
                      LOG,
                      "Replace existing transaction {}, with new transaction {}",
                      replacedTx::toTraceLog,
                      addedReadyTransaction::toTraceLog);

                  removeReplacedPrioritizedTransaction(replacedTx);
                  addPrioritizedTransaction(addedReadyTransaction, true);
                  return true;
                })
            .orElse(false);

    if (prioritizedTxReplaced) {
      return PrioritizeResult.REPLACEMENT;
    }

    final var expectedNonce =
        expectedNonceForSender.getOrDefault(addedReadyTransaction.getSender(), senderNonce);

    // only add to prioritized if it appends to the already prioritized for its sender, without
    // gaps
    if (addedReadyTransaction.getNonce() != expectedNonce) {
      traceLambda(
          LOG,
          "Not adding transaction {} to prioritized list since expected next nonce for this sender is {}",
          addedReadyTransaction::toTraceLog,
          () -> expectedNonce);
      return PrioritizeResult.NOT_PRIORITIZED;
    }

    if (prioritizedPendingTransactions.size() >= poolConfig.getTxPoolMaxSize()) {
      LOG.trace("Max number of prioritized transactions reached");

      final var currentLeastPriorityTx = orderByFee.first();
      if (compareByFee(addedReadyTransaction, currentLeastPriorityTx) <= 0) {
        traceLambda(
            LOG,
            "Not adding incoming transaction {} to the prioritized list, "
                + "since it is less valuable than the current least priority transactions {}",
            addedReadyTransaction::toTraceLog,
            currentLeastPriorityTx::toTraceLog);
        return PrioritizeResult.NOT_PRIORITIZED;
      }
      if (currentLeastPriorityTx.getSender().equals(addedReadyTransaction.getSender())) {
        traceLambda(
            LOG,
            "Not adding incoming transaction {} to the prioritized list, "
                + "since it is from the same sender as the least valuable one {}",
            addedReadyTransaction::toTraceLog,
            currentLeastPriorityTx::toTraceLog);
        return PrioritizeResult.NOT_PRIORITIZED;
      }

      traceLambda(
          LOG,
          "Demote transactions for the sender of the current least priority transaction {}, "
              + "to make space for the incoming transaction {}",
          currentLeastPriorityTx::toTraceLog,
          addedReadyTransaction::toTraceLog);
      // demoteLastTransactionForSenderOf(currentLeastPriorityTx);
      addPrioritizedTransaction(addedReadyTransaction);
      return PrioritizeResult.prioritizedDemotingTransaction(currentLeastPriorityTx);
    }

    addPrioritizedTransaction(addedReadyTransaction);
    return PrioritizeResult.PRIORITIZED;
    //      notifyTransactionAdded(addedReadyTransaction.getTransaction());

  }

  public void demoteInvalidTransactions(
      final Address sender,
      final List<PendingTransaction> invalidTransactions,
      final Optional<Long> maybeLastValidSenderNonce) {

    for (final var pendingTransaction : invalidTransactions) {
      if (prioritizedPendingTransactions.remove(pendingTransaction.getHash()) != null) {
        removeFromOrderedTransactions(pendingTransaction, false);
      } else {
        break;
      }
      //      notifyTransactionDropped(pendingTransaction.getTransaction());
    }

    maybeLastValidSenderNonce.ifPresentOrElse(
        lastValidNonce -> expectedNonceForSender.put(sender, lastValidNonce),
        () -> expectedNonceForSender.remove(sender));
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
  //  public List<Transaction> selectTransactions(final
  // PendingTransactionsSorter.TransactionSelector selector) {
  //    synchronized (lock) {
  //      final Set<Transaction> transactionsToRemove = new HashSet<>();
  //      final Map<Address, AccountTransactionOrder> accountTransactions = new HashMap<>();
  //      final Iterator<PendingTransaction> prioritizedTransactions = prioritizedTransactions();
  //      while (prioritizedTransactions.hasNext()) {
  //        final PendingTransaction highestPriorityPendingTransaction =
  // prioritizedTransactions.next();
  //        final AccountTransactionOrder accountTransactionOrder =
  //            accountTransactions.computeIfAbsent(
  //                highestPriorityPendingTransaction.getSender(),
  // this::createSenderTransactionOrder);
  //
  //        for (final Transaction transactionToProcess :
  //            accountTransactionOrder.transactionsToProcess(
  //                highestPriorityPendingTransaction.getTransaction())) {
  //          final TransactionSelectionResult result =
  //              selector.evaluateTransaction(transactionToProcess);
  //          switch (result) {
  //            case DELETE_TRANSACTION_AND_CONTINUE:
  //              transactionsToRemove.add(transactionToProcess);
  //              break;
  //            case CONTINUE:
  //              break;
  //            case COMPLETE_OPERATION:
  //              transactionsToRemove.forEach(this::removeInvalidTransaction);
  //              return;
  //            default:
  //              throw new RuntimeException("Illegal value for TransactionSelectionResult.");
  //          }
  //        }
  //      }
  //      transactionsToRemove.forEach(this::removeInvalidTransaction);
  //    }
  //  }

  public int size() {
    return prioritizedPendingTransactions.size();
  }

  public boolean containsTransaction(final Transaction transaction) {
    return prioritizedPendingTransactions.containsKey(transaction.getHash());
  }

  public Optional<Transaction> getTransactionByHash(final Hash transactionHash) {
    return Optional.ofNullable(prioritizedPendingTransactions.get(transactionHash))
        .map(PendingTransaction::getTransaction);
  }

  public Set<PendingTransaction> getPrioritizedPendingTransactions() {
    return new HashSet<>(prioritizedPendingTransactions.values());
  }

  private void removeReplacedPrioritizedTransaction(final PendingTransaction replacedTx) {
    final PendingTransaction removedPendingTransaction =
        prioritizedPendingTransactions.remove(replacedTx.getHash());
    removeFromOrderedTransactions(removedPendingTransaction, false);
    incrementTransactionRemovedCounter(replacedTx.isReceivedFromLocalSource(), false);
    incrementTransactionReplacedCounter(replacedTx.isReceivedFromLocalSource());
  }

  protected abstract void removeFromOrderedTransactions(
      final PendingTransaction removedPendingTx, final boolean addedToBlock);

  public Iterator<PendingTransaction> prioritizedTransactions() {
    return orderByFee.descendingIterator();
  }
  //
  //  private void demoteLastTransactionForSenderOf(final PendingTransaction firstDemotedTx) {
  //    final var demotableSenderTxs =
  //        readyTransactionsCache
  //            .streamReadyTransactions(firstDemotedTx.getSender(), firstDemotedTx.getNonce())
  //            .iterator();
  //
  //    var lastPrioritizedForSender = firstDemotedTx;
  //    while (demotableSenderTxs.hasNext()) {
  //      final var maybeNewLast = demotableSenderTxs.next();
  //      if (!prioritizedPendingTransactions.containsKey(maybeNewLast.getHash())) {
  //        break;
  //      }
  //      lastPrioritizedForSender = maybeNewLast;
  //    }
  //
  //    traceLambda(
  //        LOG,
  //        "Higher nonce prioritized transaction for sender {} is {}, expected nonce for sender is
  // {}",
  //        firstDemotedTx::getSender,
  //        lastPrioritizedForSender::toTraceLog,
  //        () -> expectedNonceForSender.get(firstDemotedTx.getSender()));
  //
  //    prioritizedPendingTransactions.remove(lastPrioritizedForSender.getHash());
  //    removeFromOrderedTransactions(lastPrioritizedForSender, false);
  //
  //    expectedNonceForSender.compute(
  //        firstDemotedTx.getSender(),
  //        (sender, expectedNonce) -> {
  //          if (expectedNonce == firstDemotedTx.getNonce() + 1
  //              || readyTransactionsCache.get(sender, expectedNonce - 1).isEmpty()) {
  //            return null;
  //          }
  //          return expectedNonce - 1;
  //        });
  //
  //    traceLambda(
  //        LOG,
  //        "Demoted transaction {}, to make space for the incoming transaction",
  //        lastPrioritizedForSender::toTraceLog);
  //  }

  public void demoteLastPrioritizedForSender(
      final PendingTransaction firstDemotedTx,
      final NavigableMap<Long, PendingTransaction> senderReadyTxs) {
    final var demotableSenderTxs =
        senderReadyTxs.tailMap(firstDemotedTx.getNonce(), false).values().stream().iterator();
    //            readyTransactionsCache
    //                    .streamReadyTransactions(firstDemotedTx.getSender(),
    // firstDemotedTx.getNonce())
    //                    .iterator();

    var lastPrioritizedForSender = firstDemotedTx;
    while (demotableSenderTxs.hasNext()) {
      final var maybeNewLast = demotableSenderTxs.next();
      if (!prioritizedPendingTransactions.containsKey(maybeNewLast.getHash())) {
        break;
      }
      lastPrioritizedForSender = maybeNewLast;
    }

    traceLambda(
        LOG,
        "Higher nonce prioritized transaction for sender {} is {}, expected nonce for sender is {}",
        firstDemotedTx::getSender,
        lastPrioritizedForSender::toTraceLog,
        () -> expectedNonceForSender.get(firstDemotedTx.getSender()));

    prioritizedPendingTransactions.remove(lastPrioritizedForSender.getHash());
    removeFromOrderedTransactions(lastPrioritizedForSender, false);

    expectedNonceForSender.compute(
        firstDemotedTx.getSender(),
        (sender, expectedNonce) -> {
          if (expectedNonce == firstDemotedTx.getNonce() + 1
              || senderReadyTxs.containsKey(expectedNonce - 1)) {
            return null;
          }
          return expectedNonce - 1;
        });

    traceLambda(
        LOG,
        "Demoted transaction {}, to make space for the incoming transaction",
        lastPrioritizedForSender::toTraceLog);
  }

  public void addPrioritizedTransaction(final PendingTransaction prioritizedTx) {
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

  public void removeConfirmedTransactions(
      final Map<Address, Optional<Long>> orderedConfirmedNonceBySender,
      final List<PendingTransaction> confirmedTransactions) {
    confirmedTransactions.stream()
        .map(PendingTransaction::getHash)
        .map(prioritizedPendingTransactions::remove)
        .filter(Objects::nonNull)
        .forEach(
            tx -> {
              removeFromOrderedTransactions(tx, true);
              //
              // incrementTransactionRemovedCounter(tx.isReceivedFromLocalSource(), true);
            });

    // update expected nonce for senders
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
  }

  public abstract void manageBlockAdded(final BlockHeader blockHeader, final FeeMarket feeMarket);

  public abstract Predicate<PendingTransaction> getPromotionFilter();

  public String toTraceLog() {
    synchronized (lock) {
      return "Prioritized size "
          + prioritizedPendingTransactions.size()
          + " content in order "
          + StreamSupport.stream(
                  Spliterators.spliteratorUnknownSize(
                      prioritizedTransactions(), Spliterator.ORDERED),
                  false)
              .map(PendingTransaction::toTraceLog)
              .collect(Collectors.joining("; "))
          + ", Expected nonce for sender size "
          + expectedNonceForSender.size()
          + " content "
          + expectedNonceForSender;
    }
  }

  public void reset() {
    prioritizedPendingTransactions.clear();
    orderByFee.clear();
    expectedNonceForSender.clear();
  }

  public static class PrioritizeResult {
    static final PrioritizeResult REPLACEMENT = new PrioritizeResult(true, true, null);
    static final PrioritizeResult NOT_PRIORITIZED = new PrioritizeResult(false, false, null);
    static final PrioritizeResult PRIORITIZED = new PrioritizeResult(true, false, null);
    final boolean prioritized;
    final boolean replacement;
    final Optional<PendingTransaction> maybeDemotedTransaction;

    private PrioritizeResult(
        final boolean prioritized,
        final boolean replacement,
        final PendingTransaction demotedTransaction) {
      this.prioritized = prioritized;
      this.replacement = replacement;
      this.maybeDemotedTransaction = Optional.ofNullable(demotedTransaction);
    }

    static PrioritizeResult prioritizedDemotingTransaction(
        final PendingTransaction demotedTransaction) {
      return new PrioritizeResult(true, false, demotedTransaction);
    }

    public boolean isPrioritized() {
      return prioritized;
    }

    public boolean isReplacement() {
      return replacement;
    }

    public Optional<PendingTransaction> maybeDemotedTransaction() {
      return maybeDemotedTransaction;
    }
  }
}
