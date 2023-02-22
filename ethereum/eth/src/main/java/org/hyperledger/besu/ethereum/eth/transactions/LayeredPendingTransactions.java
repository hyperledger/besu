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
package org.hyperledger.besu.ethereum.eth.transactions;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.maxBy;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ADDED;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ADDED_SPARSE;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ALREADY_KNOWN;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.NONCE_TOO_FAR_IN_FUTURE_FOR_SENDER;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.REJECTED_UNDERPRICED_REPLACEMENT;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.TX_POOL_FULL;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.INTERNAL_ERROR;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPrioritizedTransactions;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountState;
import org.hyperledger.besu.util.Subscribers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LayeredPendingTransactions implements PendingTransactions {
  private static final Logger LOG = LoggerFactory.getLogger(LayeredPendingTransactions.class);

  private final TransactionPoolConfiguration poolConfig;
  private final BiFunction<PendingTransaction, PendingTransaction, Boolean>
      transactionReplacementTester;

  private final Map<Hash, PendingTransaction> pendingTransactions = new HashMap<>();
  private final Map<Address, NavigableMap<Long, PendingTransaction>> readyBySender =
      new HashMap<>();
  private final NavigableSet<PendingTransaction> orderByMaxFee =
      new TreeSet<>(
          Comparator.comparing((PendingTransaction pt) -> pt.getTransaction().getMaxGasFee())
              .thenComparing(PendingTransaction::getSequence));

  private final Map<Address, NavigableMap<Long, PendingTransaction>> sparseBySender =
      new HashMap<>();
  private final NavigableSet<PendingTransaction> sparseEvictionOrder =
      new TreeSet<>(Comparator.comparing(PendingTransaction::getSequence));

  private long spaceUsed = 0;

  private final Set<Address> localSenders = new HashSet<>();

  private final Subscribers<PendingTransactionListener> pendingTransactionSubscribers =
      Subscribers.create();

  private final Subscribers<PendingTransactionDroppedListener> transactionDroppedListeners =
      Subscribers.create();

  private final AbstractPrioritizedTransactions prioritizedTransactions;

  final TransactionPoolMetrics metrics;

  public LayeredPendingTransactions(
      final TransactionPoolConfiguration poolConfig,
      final AbstractPrioritizedTransactions prioritizedTransactions,
      final TransactionPoolMetrics metrics,
      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
          transactionReplacementTester) {
    this.poolConfig = poolConfig;
    this.prioritizedTransactions = prioritizedTransactions;
    this.transactionReplacementTester = transactionReplacementTester;
    this.metrics = metrics;
    metrics.initPendingTransactionCount(pendingTransactions::size);
    metrics.initPendingTransactionSpace(this::getUsedSpace);
    metrics.initReadyTransactionCount(this::getReadyCount);
    metrics.initSparseTransactionCount(sparseEvictionOrder::size);
    metrics.initPrioritizedTransactionSize(prioritizedTransactions::size);
  }

  @Override
  public synchronized void reset() {
    pendingTransactions.clear();
    readyBySender.clear();
    orderByMaxFee.clear();
    sparseBySender.clear();
    sparseEvictionOrder.clear();
    spaceUsed = 0;
    prioritizedTransactions.reset();
  }

  @Override
  public synchronized TransactionAddedResult addRemoteTransaction(
      final Transaction transaction, final Optional<Account> maybeSenderAccount) {

    return addTransaction(
        new PendingTransaction.Remote(transaction, Instant.now()), maybeSenderAccount);
  }

  @Override
  public synchronized TransactionAddedResult addLocalTransaction(
      final Transaction transaction, final Optional<Account> maybeSenderAccount) {

    final TransactionAddedResult addedResult =
        addTransaction(
            new PendingTransaction.Local(transaction, Instant.now()), maybeSenderAccount);
    if (addedResult.isSuccess()) {
      localSenders.add(transaction.getSender());
    }
    return addedResult;
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

    final var result = addAndPrioritize(pendingTransaction, senderNonce);
    updateMetrics(pendingTransaction, result);

    if (LOG.isDebugEnabled()) {
      consistencyCheck();
    }

    return result;
  }

  private void updateMetrics(
      final PendingTransaction pendingTransaction, final TransactionAddedResult result) {
    if (result.isSuccess()) {
      result
          .maybeReplacedTransaction()
          .ifPresent(
              replacedTx -> {
                metrics.incrementReplaced(
                    replacedTx.isReceivedFromLocalSource(),
                    result.isPrioritizable() ? "ready" : "sparse");
                metrics.incrementRemoved(replacedTx.isReceivedFromLocalSource(), "replaced");
              });
      metrics.incrementAdded(pendingTransaction.isReceivedFromLocalSource());
    } else {
      final var rejectReason =
          result
              .maybeInvalidReason()
              .orElseGet(
                  () -> {
                    LOG.warn("Missing invalid reason for status {}", result);
                    return INTERNAL_ERROR;
                  });
      metrics.incrementRejected(false, rejectReason);
      traceLambda(
          LOG,
          "Transaction {} rejected reason {}",
          pendingTransaction::toTraceLog,
          rejectReason::toString);
    }
  }

  private TransactionAddedResult addAndPrioritize(
      final PendingTransaction pendingTransaction, final long senderNonce) {

    final var addStatus =
        modifySenderReadyTxsWrapper(
            pendingTransaction.getSender(),
            senderTxs -> tryAddToReady(senderTxs, pendingTransaction, senderNonce));

    if (addStatus.isSuccess()) {

      addStatus
          .maybeReplacedTransaction()
          .ifPresent(
              replacedTx -> {
                pendingTransactions.remove(replacedTx.getHash());
                decreaseSpaceUsed(replacedTx);
              });

      pendingTransactions.put(pendingTransaction.getHash(), pendingTransaction);
      increaseSpaceUsed(pendingTransaction);
      if (addStatus.isPrioritizable()) {
        prioritizeReadyTransaction(pendingTransaction, senderNonce, addStatus);
      }

      var cacheFreeSpace = cacheFreeSpace();
      if (cacheFreeSpace < 0) {
        LOG.trace("Cache full, free space {}", cacheFreeSpace);
        // free some space moving trying first to evict older sparse txs,
        // then less valuable ready to postponed
        final var evictedSparseTxs = evictSparseTransactions(-cacheFreeSpace);
        metrics.incrementEvicted("sparse", evictedSparseTxs.size());
        evictedSparseTxs.forEach(this::notifyTransactionDropped);
        if (evictedSparseTxs.contains(pendingTransaction)) {
          // in case the just added transaction is postponed to free space change the returned
          // result
          return TX_POOL_FULL;
        }

        cacheFreeSpace = cacheFreeSpace();
        if (cacheFreeSpace < 0) {
          final var evictedReadyTxs = evictReadyTransactions(-cacheFreeSpace);
          metrics.incrementEvicted("ready", evictedReadyTxs.size());
          evictedReadyTxs.forEach(this::notifyTransactionDropped);
          if (evictedReadyTxs.contains(pendingTransaction)) {
            // in case the just added transaction is postponed to free space change the returned
            // result
            return TX_POOL_FULL;
          }
        }
      }
      notifyTransactionAdded(pendingTransaction);
    }

    return addStatus;
  }

  private void prioritizeReadyTransaction(
      final PendingTransaction addedReadyTransaction,
      final long senderNonce,
      final TransactionAddedResult addResult) {

    final var prioritizeResult =
        prioritizedTransactions.prioritizeTransaction(
            addedReadyTransaction, senderNonce, addResult);

    prioritizeResult.maybeDemotedTransaction().ifPresent(this::demoteLastPrioritizedForSenderOf);

    metrics.incrementPrioritized(
        addedReadyTransaction.isReceivedFromLocalSource(), prioritizeResult);

    if (prioritizeResult.isPrioritized() && !prioritizeResult.isReplacement()) {
      // try to see if we can prioritize any transactions that moved from sparse to ready for this
      // sender
      getReady(addedReadyTransaction.getSender(), addedReadyTransaction.getNonce() + 1)
          .ifPresent(nextReadyTx -> prioritizeReadyTransaction(nextReadyTx, senderNonce, ADDED));
    }
  }

  private void demoteLastPrioritizedForSenderOf(final PendingTransaction lastPrioTx) {
    final var senderReadyTxs = readyBySender.get(lastPrioTx.getSender());
    final var demotableSenderTxs = senderReadyTxs.tailMap(lastPrioTx.getNonce(), true);

    final var itDemotableTxs = demotableSenderTxs.values().stream().iterator();
    var lastPrioritizedForSender = itDemotableTxs.next();

    while (itDemotableTxs.hasNext()) {
      final var maybeNewLast = itDemotableTxs.next();
      if (!prioritizedTransactions.containsTransaction(maybeNewLast.getTransaction())) {
        break;
      }
      lastPrioritizedForSender = maybeNewLast;
    }

    final boolean prevTxExists =
        lastPrioTx.equals(lastPrioritizedForSender)
            ? senderReadyTxs.containsKey(lastPrioTx.getNonce() - 1)
            : true;

    final Optional<Long> maybeNewExpectedNonce =
        prevTxExists ? Optional.of(lastPrioritizedForSender.getNonce()) : Optional.empty();

    traceLambda(
        LOG,
        "Higher nonce prioritized transaction to demote for sender {} is {}",
        lastPrioTx::getSender,
        lastPrioritizedForSender::toTraceLog);

    prioritizedTransactions.demoteTransactions(
        lastPrioTx.getSender(), List.of(lastPrioritizedForSender), maybeNewExpectedNonce);
  }

  @VisibleForTesting
  Optional<PendingTransaction> getReady(final Address sender, final long nonce) {
    var senderTxs = readyBySender.get(sender);
    if (senderTxs != null) {
      return Optional.ofNullable(senderTxs.get(nonce));
    }
    return Optional.empty();
  }

  private void removeFromReady(final Transaction transaction) {
    modifySenderReadyTxsWrapper(
        transaction.getSender(), senderTxs -> removeFromReady(senderTxs, transaction));
  }

  private List<PendingTransaction> removeConfirmedTransactions(
      final Map<Address, Optional<Long>> orderedConfirmedNonceBySender) {

    final List<PendingTransaction> removed = new ArrayList<>();

    for (var senderMaxConfirmedNonce : orderedConfirmedNonceBySender.entrySet()) {
      final var maxConfirmedNonce = senderMaxConfirmedNonce.getValue().get();
      final var sender = senderMaxConfirmedNonce.getKey();

      removed.addAll(
          modifySenderReadyTxsWrapper(
              sender, senderTxs -> removeConfirmed(senderTxs, maxConfirmedNonce)));
    }
    return removed;
  }

  /**
   * Search and returns ready transaction that could be promoted to prioritized
   *
   * @param maxPromotable max number of ready transactions that could be promoted
   * @param promotionFilter first that a ready transaction needs to pass to get promoted
   * @return a list of transactions that could be prioritized, for each sender the transactions are
   *     in asc nonce order
   */
  private List<PendingTransaction> getPromotableTransactions(
      final int maxPromotable, final Predicate<PendingTransaction> promotionFilter) {

    List<PendingTransaction> promotableTxs = new ArrayList<>(maxPromotable);

    // if there is space pick other ready transactions
    if (maxPromotable > 0) {
      promotableTxs.addAll(promoteReady(maxPromotable, promotionFilter));
    }

    return promotableTxs;
  }

  private Stream<PendingTransaction> streamReadyTransactions(final Address sender) {
    return streamReadyTransactions(sender, -1);
  }

  private Stream<PendingTransaction> streamReadyTransactions(
      final Address sender, final long afterNonce) {
    var senderTxs = readyBySender.get(sender);
    if (senderTxs != null) {
      return senderTxs.tailMap(afterNonce, false).values().stream();
    }
    return Stream.empty();
  }

  private <R> R modifySenderReadyTxsWrapper(
      final Address sender,
      final Function<NavigableMap<Long, PendingTransaction>, R> modifySenderTxs) {

    var senderTxs = readyBySender.get(sender);
    final Optional<PendingTransaction> prevFirstTx = getFirstReadyTransaction(senderTxs);
    final long prevLastNonce = getLastReadyNonce(senderTxs);

    final var result = modifySenderTxs.apply(senderTxs);

    if (senderTxs == null) {
      // it could have been created
      senderTxs = readyBySender.get(sender);
    }

    final Optional<PendingTransaction> currFirstTx = getFirstReadyTransaction(senderTxs);
    final long currLastNonce = getLastReadyNonce(senderTxs);

    if (!prevFirstTx.equals(currFirstTx)) {
      prevFirstTx.ifPresent(orderByMaxFee::remove);
      currFirstTx.ifPresent(orderByMaxFee::add);
    }

    if (prevLastNonce != currLastNonce) {
      sparseToReady(sender, senderTxs, currLastNonce);
    }

    if (senderTxs != null && senderTxs.isEmpty()) {
      readyBySender.remove(sender);
    }

    return result;
  }

  private List<PendingTransaction> evictReadyTransactions(final long evictSize) {
    final var lessReadySender = orderByMaxFee.first().getSender();
    final var lessReadySenderTxs = readyBySender.get(lessReadySender);

    final List<PendingTransaction> evictedTxs = new ArrayList<>();
    long postponedSize = 0;
    PendingTransaction lastTx = null;
    // lastTx must never be null, because the sender have at least the lessReadyTx
    while (postponedSize < evictSize && !lessReadySenderTxs.isEmpty()) {
      lastTx = lessReadySenderTxs.pollLastEntry().getValue();
      pendingTransactions.remove(lastTx.getHash());
      evictedTxs.add(lastTx);
      decreaseSpaceUsed(lastTx);
      postponedSize += lastTx.getTransaction().getSize();
    }

    if (lessReadySenderTxs.isEmpty()) {
      readyBySender.remove(lessReadySender);
      // at this point lastTx was the first for the sender, then remove it from eviction order too
      orderByMaxFee.remove(lastTx);
      if (!readyBySender.isEmpty()) {
        // try next less valuable sender
        evictedTxs.addAll(evictReadyTransactions(evictSize - postponedSize));
      }
    }
    return evictedTxs;
  }

  private List<PendingTransaction> evictSparseTransactions(final long evictSize) {
    final List<PendingTransaction> evictedTxs = new ArrayList<>();
    long postponedSize = 0;
    while (postponedSize < evictSize && !sparseBySender.isEmpty()) {
      final var oldestSparse = sparseEvictionOrder.pollFirst();
      pendingTransactions.remove(oldestSparse.getHash());
      evictedTxs.add(oldestSparse);
      decreaseSpaceUsed(oldestSparse);
      postponedSize += oldestSparse.getTransaction().getSize();

      sparseBySender.compute(
          oldestSparse.getSender(),
          (sender, sparseTxs) -> {
            sparseTxs.remove(oldestSparse.getNonce());
            return sparseTxs.isEmpty() ? null : sparseTxs;
          });
    }
    return evictedTxs;
  }

  private long cacheFreeSpace() {
    return poolConfig.getPendingTransactionsCacheSizeBytes() - spaceUsed;
  }

  public synchronized long getUsedSpace() {
    return spaceUsed;
  }

  private void sparseToReady(
      final Address sender,
      final NavigableMap<Long, PendingTransaction> senderReadyTxs,
      final long currLastNonce) {
    final var senderSparseTxs = sparseBySender.get(sender);
    long nextNonce = currLastNonce + 1;
    if (senderSparseTxs != null) {
      while (senderSparseTxs.containsKey(nextNonce)) {
        final var toReadyTx = senderSparseTxs.remove(nextNonce);
        sparseEvictionOrder.remove(toReadyTx);
        senderReadyTxs.put(nextNonce, toReadyTx);
        nextNonce++;
      }
    }
  }

  private Optional<PendingTransaction> getFirstReadyTransaction(
      final NavigableMap<Long, PendingTransaction> senderTxs) {
    if (senderTxs == null || senderTxs.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(senderTxs.firstEntry().getValue());
  }

  private long getLastReadyNonce(final NavigableMap<Long, PendingTransaction> senderTxs) {
    if (senderTxs == null || senderTxs.isEmpty()) {
      return -1;
    }
    return senderTxs.lastEntry().getKey();
  }

  private TransactionAddedResult tryAddToReady(
      final NavigableMap<Long, PendingTransaction> senderTxs,
      final PendingTransaction pendingTransaction,
      final long senderNonce) {

    if (senderTxs == null) {
      // add to ready set, only if the tx is the next for the sender
      if (pendingTransaction.getNonce() == senderNonce) {
        var newSenderTxs = new TreeMap<Long, PendingTransaction>();
        newSenderTxs.put(pendingTransaction.getNonce(), pendingTransaction);
        readyBySender.put(pendingTransaction.getSender(), newSenderTxs);
        return ADDED;
      }
      return tryAddToSparse(pendingTransaction);
    }

    // is replacing an existing one?
    final var maybeReplaced = maybeReplaceTransaction(senderTxs, pendingTransaction, true);
    if (maybeReplaced != null) {
      return maybeReplaced;
    }

    // is the next one?
    if (pendingTransaction.getNonce() == senderTxs.lastKey() + 1) {
      senderTxs.put(pendingTransaction.getNonce(), pendingTransaction);
      return ADDED;
    }
    return tryAddToSparse(pendingTransaction);
  }

  private TransactionAddedResult tryAddToSparse(final PendingTransaction sparseTransaction) {
    // add if fits in cache or there are other sparse txs that we can evict
    // to make space for this one
    if (fitsInCache(sparseTransaction) || !sparseBySender.isEmpty()) {
      final var senderSparseTxs =
          sparseBySender.computeIfAbsent(sparseTransaction.getSender(), sender -> new TreeMap<>());
      final var maybeReplaced = maybeReplaceTransaction(senderSparseTxs, sparseTransaction, false);
      if (maybeReplaced != null) {
        maybeReplaced
            .maybeReplacedTransaction()
            .ifPresent(
                replacedTx -> {
                  sparseEvictionOrder.remove(replacedTx);
                  sparseEvictionOrder.add(sparseTransaction);
                });
        return maybeReplaced;
      }
      senderSparseTxs.put(sparseTransaction.getNonce(), sparseTransaction);
      sparseEvictionOrder.add(sparseTransaction);
      return ADDED_SPARSE;
    }
    return TX_POOL_FULL;
  }

  private TransactionAddedResult maybeReplaceTransaction(
      final NavigableMap<Long, PendingTransaction> existingTxs,
      final PendingTransaction incomingTx,
      final boolean isReady) {

    final var existingReadyTx = existingTxs.get(incomingTx.getNonce());
    if (existingReadyTx != null) {

      if (existingReadyTx.getHash().equals(incomingTx.getHash())) {
        return ALREADY_KNOWN;
      }

      if (!transactionReplacementTester.apply(existingReadyTx, incomingTx)) {
        return REJECTED_UNDERPRICED_REPLACEMENT;
      }
      existingTxs.put(incomingTx.getNonce(), incomingTx);
      return TransactionAddedResult.createForReplacement(existingReadyTx, isReady);
    }
    return null;
  }

  private void increaseSpaceUsed(final PendingTransaction pendingTransaction) {
    spaceUsed += pendingTransaction.getTransaction().getSize();
  }

  private boolean fitsInCache(final PendingTransaction pendingTransaction) {
    return spaceUsed + pendingTransaction.getTransaction().getSize()
        <= poolConfig.getPendingTransactionsCacheSizeBytes();
  }

  private void decreaseSpaceUsed(final PendingTransaction pendingTransaction) {
    decreaseSpaceUsed(pendingTransaction.getTransaction());
  }

  private void decreaseSpaceUsed(final Transaction transaction) {
    spaceUsed -= transaction.getSize();
  }

  private Void removeFromReady(
      final NavigableMap<Long, PendingTransaction> senderTxs, final Transaction transaction) {

    if (senderTxs != null && senderTxs.remove(transaction.getNonce()) != null) {
      decreaseSpaceUsed(transaction);
    }

    return null;
  }

  private List<PendingTransaction> removeConfirmed(
      final NavigableMap<Long, PendingTransaction> senderTxs, final long maxConfirmedNonce) {
    if (senderTxs != null) {
      final var confirmedTxsToRemove = senderTxs.headMap(maxConfirmedNonce, true);
      final List<PendingTransaction> removed =
          confirmedTxsToRemove.values().stream()
              .peek(this::decreaseSpaceUsed)
              .collect(Collectors.toUnmodifiableList());
      confirmedTxsToRemove.clear();

      return removed;
    }
    return List.of();
  }

  private Collection<PendingTransaction> promoteReady(
      final int maxRemaining, final Predicate<PendingTransaction> promotionFilter) {

    final List<PendingTransaction> promotedTxs = new ArrayList<>(maxRemaining);

    for (var tx : orderByMaxFee.descendingSet()) {
      final int maxForThisSender = maxRemaining - promotedTxs.size();
      if (maxForThisSender <= 0) {
        break;
      }
      promotedTxs.addAll(
          promoteReady(readyBySender.get(tx.getSender()), maxForThisSender, promotionFilter));
    }
    return promotedTxs;
  }

  private Collection<PendingTransaction> promoteReady(
      final NavigableMap<Long, PendingTransaction> senderTxs,
      final int maxRemaining,
      final Predicate<PendingTransaction> promotionFilter) {

    final List<PendingTransaction> promotableTxs = new ArrayList<>(maxRemaining);

    final var itSenderTxs = senderTxs.entrySet().iterator();

    while (itSenderTxs.hasNext() && promotableTxs.size() < maxRemaining) {
      var promotableEntry = itSenderTxs.next();
      if (promotionFilter.test(promotableEntry.getValue())) {
        promotableTxs.add(promotableEntry.getValue());
      } else {
        break;
      }
    }

    return promotableTxs;
  }

  @Override
  public void evictOldTransactions() {}

  @Override
  public synchronized List<Transaction> getLocalTransactions() {
    return pendingTransactions.values().stream()
        .filter(PendingTransaction::isReceivedFromLocalSource)
        .map(PendingTransaction::getTransaction)
        .collect(Collectors.toList());
  }

  @Override
  public synchronized boolean isLocalSender(final Address sender) {
    return localSenders.contains(sender);
  }

  @Override
  // There's a small edge case here we could encounter.
  // When we pass an upgrade block that has a new transaction type, we start allowing transactions
  // of that new type into our pool.
  // If we then reorg to a block lower than the upgrade block height _and_ we create a block, that
  // block could end up with transactions of the new type.
  // This seems like it would be very rare but worth it to document that we don't handle that case
  // right now.
  public synchronized void selectTransactions(
      final PendingTransactions.TransactionSelector selector) {
    final List<PendingTransaction> transactionsToRemove = new ArrayList<>();
    final Set<Hash> alreadyChecked = new HashSet<>();

    final Iterator<PendingTransaction> itPrioritizedTransactions =
        prioritizedTransactions.prioritizedTransactions();
    outerLoop:
    while (itPrioritizedTransactions.hasNext()) {
      final PendingTransaction highestPriorityPendingTransaction = itPrioritizedTransactions.next();
      // get all the txs for this sender up to this highest priority
      final var itSenderCandidateTxs =
          streamReadyTransactions(highestPriorityPendingTransaction.getSender()).iterator();
      while (itSenderCandidateTxs.hasNext()) {
        final var candidateTx = itSenderCandidateTxs.next();
        if (candidateTx.getNonce() <= highestPriorityPendingTransaction.getNonce()
            && !alreadyChecked.contains(candidateTx.getHash())) {
          final PendingTransactions.TransactionSelectionResult result =
              selector.evaluateTransaction(candidateTx.getTransaction());
          switch (result) {
            case DELETE_TRANSACTION_AND_CONTINUE:
              alreadyChecked.add(candidateTx.getHash());
              transactionsToRemove.add(candidateTx);
              break;
            case CONTINUE:
              alreadyChecked.add(candidateTx.getHash());
              break;
            case COMPLETE_OPERATION:
              break outerLoop;
            default:
              throw new RuntimeException("Illegal value for TransactionSelectionResult.");
          }
        }
      }
    }
    transactionsToRemove.forEach(this::removeInvalidTransaction);
  }

  private void removeInvalidTransaction(final PendingTransaction invalidTransaction) {
    // remove the invalid transaction and move all the following to sparse set
    final var sender = invalidTransaction.getSender();
    final var senderReadyTxs = readyBySender.get(sender);

    final var followingTxs = senderReadyTxs.tailMap(invalidTransaction.getNonce(), false).values();

    removeFromReady(invalidTransaction.getTransaction());
    pendingTransactions.remove(invalidTransaction.getHash());
    metrics.incrementRemoved(invalidTransaction.isReceivedFromLocalSource(), "invalid");

    followingTxs.forEach(
        followingTx -> {
          removeFromReady(followingTx.getTransaction());
          sparseBySender
              .computeIfAbsent(sender, unused -> new TreeMap<>())
              .put(followingTx.getNonce(), followingTx);
          sparseEvictionOrder.add(followingTx);
        });

    if (prioritizedTransactions.containsTransaction(invalidTransaction.getTransaction())) {
      // remove the invalid and the following from prioritize
      final List<PendingTransaction> demotedTxs = new ArrayList<>(followingTxs.size() + 1);
      demotedTxs.add(invalidTransaction);
      demotedTxs.addAll(followingTxs);

      // if previous valid transaction is prioritized then update the expected nonce or remove it
      final var prevValidTx = senderReadyTxs.get(invalidTransaction.getNonce() - 1);
      final Optional<Long> newExpectedNonce;
      if (prevValidTx != null
          && prioritizedTransactions.containsTransaction(prevValidTx.getTransaction())) {
        newExpectedNonce = Optional.of(invalidTransaction.getNonce());
      } else {
        newExpectedNonce = Optional.empty();
      }

      prioritizedTransactions.demoteTransactions(sender, demotedTxs, newExpectedNonce);
    }

    notifyTransactionDropped(invalidTransaction);
  }

  @Override
  public long maxSize() {
    return -1;
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
  public synchronized Set<PendingTransaction> getPendingTransactions() {
    return new HashSet<>(pendingTransactions.values());
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
  public synchronized OptionalLong getNextNonceForSender(final Address sender) {
    final var senderTxs = readyBySender.get(sender);
    if (senderTxs != null) {
      return OptionalLong.of(senderTxs.lastKey() + 1);
    }

    return OptionalLong.empty();
  }

  @Override
  public synchronized void manageBlockAdded(
      final BlockHeader blockHeader,
      final List<Transaction> confirmedTransactions,
      final FeeMarket feeMarket) {
    debugLambda(LOG, "Managing new added block {}", blockHeader::toLogString);

    transactionsAddedToBlock(confirmedTransactions);
    prioritizedTransactions.manageBlockAdded(blockHeader, feeMarket);
    prioritizeReadyTransactions();
  }

  private void transactionsAddedToBlock(final List<Transaction> confirmedTransactions) {
    final var orderedConfirmedNonceBySender = maxConfirmedNonceBySender(confirmedTransactions);
    final var removedTransactions = removeConfirmedTransactions(orderedConfirmedNonceBySender);

    traceLambda(
        LOG,
        "Confirmed nonce by sender {}, removed pending transactions {}",
        orderedConfirmedNonceBySender::toString,
        () ->
            removedTransactions.stream()
                .map(PendingTransaction::toTraceLog)
                .collect(Collectors.joining(", ")));

    removedTransactions.stream()
        .peek(pt -> metrics.incrementRemoved(pt.isReceivedFromLocalSource(), "confirmed"))
        .map(PendingTransaction::getHash)
        .forEach(pendingTransactions::remove);

    prioritizedTransactions.removeConfirmedTransactions(
        orderedConfirmedNonceBySender, removedTransactions);
  }

  private Map<Address, Optional<Long>> maxConfirmedNonceBySender(
      final List<Transaction> confirmedTransactions) {
    return confirmedTransactions.stream()
        .collect(
            groupingBy(
                Transaction::getSender, mapping(Transaction::getNonce, maxBy(Long::compare))));
  }

  private void prioritizeReadyTransactions() {
    final int maxPromotable = poolConfig.getTxPoolMaxSize() - prioritizedTransactions.size();

    if (maxPromotable > 0) {
      final List<PendingTransaction> prioritizeTransactions =
          getPromotableTransactions(maxPromotable, prioritizedTransactions::isPromotable);

      traceLambda(
          LOG,
          "Transactions to prioritize {}",
          () ->
              prioritizeTransactions.stream()
                  .map(PendingTransaction::toTraceLog)
                  .collect(Collectors.joining(", ")));

      prioritizeTransactions.forEach(prioritizedTransactions::addPrioritizedTransaction);
    }
  }

  private void notifyTransactionAdded(final PendingTransaction pendingTransaction) {
    pendingTransactionSubscribers.forEach(
        listener -> listener.onTransactionAdded(pendingTransaction.getTransaction()));
  }

  private void notifyTransactionDropped(final PendingTransaction pendingTransaction) {
    transactionDroppedListeners.forEach(
        listener -> listener.onTransactionDropped(pendingTransaction.getTransaction()));
  }

  @Override
  public synchronized String toTraceLog() {

    return "Ready by sender ("
        + toTraceLog(readyBySender)
        + "), Sparse by sender ("
        + toTraceLog(sparseBySender)
        + "); "
        + prioritizedTransactions.toTraceLog();
  }

  private String toTraceLog(final Map<Address, NavigableMap<Long, PendingTransaction>> senderTxs) {
    return senderTxs.entrySet().stream()
        .map(
            e ->
                e.getKey()
                    + "="
                    + e.getValue().entrySet().stream()
                        .map(etx -> etx.getKey() + ":" + etx.getValue().toTraceLog())
                        .collect(Collectors.joining(",", "[", "]")))
        .collect(Collectors.joining(";"));
  }

  private synchronized int getReadyCount() {
    return readyBySender.values().stream().mapToInt(Map::size).sum();
  }

  private synchronized int getSparseCount() {
    return sparseBySender.values().stream().mapToInt(Map::size).sum();
  }

  @Override
  public synchronized String logStats() {

    return "Pending: "
        + pendingTransactions.size()
        + ", Ready: "
        + getReadyCount()
        + ", Prioritized: "
        + prioritizedTransactions.size()
        + ", Sparse: "
        + sparseEvictionOrder.size()
        + ", Space used: "
        + spaceUsed
        + ", Prioritized stats: "
        + prioritizedTransactions.logStats();
  }

  private void consistencyCheck() {
    // check numbers
    final int pendingTotal = pendingTransactions.size();
    final int readyTotal = getReadyCount();
    final int sparseTotal = getSparseCount();
    if (pendingTotal != readyTotal + sparseTotal) {
      LOG.error("Pending != Ready + Sparse ({} != {} + {})", pendingTotal, readyTotal, sparseTotal);
    }

    readyCheck();

    sparseCheck(sparseTotal);

    prioritizedCheck();
  }

  private void readyCheck() {
    if (orderByMaxFee.size() != readyBySender.size()) {
      LOG.error(
          "OrderByMaxFee != ReadyBySender ({} != {})", orderByMaxFee.size(), readyBySender.size());
    }

    orderByMaxFee.stream()
        .filter(
            tx -> {
              if (readyBySender.containsKey(tx.getSender())) {
                if (readyBySender.get(tx.getSender()).firstEntry().getValue().equals(tx)) {
                  return false;
                }
              }
              return true;
            })
        .forEach(tx -> LOG.error("OrderByMaxFee tx {} the first ReadyBySender", tx));

    orderByMaxFee.stream()
        .filter(tx -> !pendingTransactions.containsKey(tx.getHash()))
        .forEach(tx -> LOG.error("OrderByMaxFee tx {} not found in PendingTransactions", tx));

    readyBySender.values().stream()
        .flatMap(senderTxs -> senderTxs.values().stream())
        .filter(tx -> !pendingTransactions.containsKey(tx.getHash()))
        .forEach(tx -> LOG.error("ReadyBySender tx {} not found in PendingTransactions", tx));
  }

  private void sparseCheck(final int sparseTotal) {
    if (sparseTotal != sparseEvictionOrder.size()) {
      LOG.error("Sparse != Eviction order ({} != {})", sparseTotal, sparseEvictionOrder.size());
    }

    sparseEvictionOrder.stream()
        .filter(
            tx -> {
              if (sparseBySender.containsKey(tx.getSender())) {
                if (sparseBySender.get(tx.getSender()).containsKey(tx.getNonce())) {
                  if (sparseBySender.get(tx.getSender()).get(tx.getNonce()).equals(tx)) {
                    return false;
                  }
                }
              }
              return true;
            })
        .forEach(tx -> LOG.error("SparseEvictionOrder tx {} not found in SparseBySender", tx));

    sparseEvictionOrder.stream()
        .filter(tx -> !pendingTransactions.containsKey(tx.getHash()))
        .forEach(tx -> LOG.error("SparseEvictionOrder tx {} not found in PendingTransactions", tx));

    sparseBySender.values().stream()
        .flatMap(senderTxs -> senderTxs.values().stream())
        .filter(tx -> !pendingTransactions.containsKey(tx.getHash()))
        .forEach(tx -> LOG.error("SparseBySender tx {} not found in PendingTransactions", tx));
  }

  private void prioritizedCheck() {
    prioritizedTransactions
        .prioritizedTransactions()
        .forEachRemaining(
            ptx -> {
              if (!pendingTransactions.containsKey(ptx.getHash())) {
                LOG.error("PrioritizedTransaction {} not found in PendingTransactions", ptx);
              }

              if (!readyBySender.containsKey(ptx.getSender())
                  || !readyBySender.get(ptx.getSender()).containsKey(ptx.getNonce())
                  || !readyBySender.get(ptx.getSender()).get(ptx.getNonce()).equals(ptx)) {
                LOG.error("PrioritizedTransaction {} not found in ReadyBySender", ptx);
              }

              if (sparseBySender.containsKey(ptx.getSender())
                  && sparseBySender.get(ptx.getSender()).containsKey(ptx.getNonce())
                  && sparseBySender.get(ptx.getSender()).get(ptx.getNonce()).equals(ptx)) {
                LOG.error("PrioritizedTransaction {} found in SparseBySender", ptx);
              }
            });

    prioritizedTransactions.consistencyCheck();
  }
}
