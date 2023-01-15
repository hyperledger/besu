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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LayeredPendingTransactions implements PendingTransactions {
  private static final Logger LOG = LoggerFactory.getLogger(LayeredPendingTransactions.class);

  private final TransactionPoolConfiguration poolConfig;
  private final BiFunction<PendingTransaction, PendingTransaction, Boolean>
      transactionReplacementTester;

  private final Map<Hash, PendingTransaction> pendingTransactions = new ConcurrentHashMap<>();
  private final Map<Address, NavigableMap<Long, PendingTransaction>> readyBySender =
      new ConcurrentHashMap<>();
  private final NavigableSet<PendingTransaction> orderByMaxFee =
      new TreeSet<>(
          Comparator.comparing((PendingTransaction pt) -> pt.getTransaction().getMaxGasFee())
              .thenComparing(PendingTransaction::getSequence));

  private final Map<Address, NavigableMap<Long, PendingTransaction>> sparseBySender =
      new ConcurrentHashMap<>();
  private final NavigableSet<PendingTransaction> sparseEvictionOrder =
      new TreeSet<>(Comparator.comparing(PendingTransaction::getSequence));

  private final AtomicLong readyTotalSize = new AtomicLong();

  private final Object lock = new Object();
  private final Set<Address> localSenders = ConcurrentHashMap.newKeySet();

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
    metrics.initReadyTransactionCount(
        () -> pendingTransactions.size() - sparseEvictionOrder.size());
    metrics.initSparseTransactionCount(sparseEvictionOrder::size);
    metrics.initPrioritizedTransactionSize(prioritizedTransactions::size);
  }

  @Override
  public void reset() {
    synchronized (lock) {
      pendingTransactions.clear();
      readyBySender.clear();
      orderByMaxFee.clear();
      sparseBySender.clear();
      sparseEvictionOrder.clear();
      readyTotalSize.set(0);
      prioritizedTransactions.reset();
    }
  }

  @Override
  public TransactionAddedResult addRemoteTransaction(
      final Transaction transaction, final Optional<Account> maybeSenderAccount) {

    return addTransaction(
        new PendingTransaction.Remote(transaction, Instant.now()), maybeSenderAccount);
  }

  @Override
  public TransactionAddedResult addLocalTransaction(
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

    synchronized (lock) {
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
                  decreaseTotalSize(replacedTx);
                });

        pendingTransactions.put(pendingTransaction.getHash(), pendingTransaction);
        increaseTotalSize(pendingTransaction);
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
          if (evictedSparseTxs.contains(pendingTransaction)) {
            // in case the just added transaction is postponed to free space change the returned
            // result
            return TX_POOL_FULL;
          }

          cacheFreeSpace = cacheFreeSpace();
          if (cacheFreeSpace < 0) {
            final var evictedReadyTxs = evictReadyTransactions(-cacheFreeSpace);
            metrics.incrementEvicted("ready", evictedReadyTxs.size());
            if (evictedReadyTxs.contains(pendingTransaction)) {
              // in case the just added transaction is postponed to free space change the returned
              // result
              return TX_POOL_FULL;
            }
          }
        }
        notifyTransactionAdded(pendingTransaction.getTransaction());
      }

      return addStatus;
    }
  }

  private void prioritizeReadyTransaction(
      final PendingTransaction addedReadyTransaction,
      final long senderNonce,
      final TransactionAddedResult addResult) {

    final var prioritizeResult =
        prioritizedTransactions.prioritizeTransaction(
            readyBySender.get(addedReadyTransaction.getSender()),
            addedReadyTransaction,
            senderNonce,
            addResult);

    metrics.incrementPrioritized(
        addedReadyTransaction.isReceivedFromLocalSource(), prioritizeResult);

    if (prioritizeResult.isPrioritized() && !prioritizeResult.isReplacement()) {
      // try to see if we can prioritize any transactions that moved from sparse to ready for this
      // sender
      getReady(addedReadyTransaction.getSender(), addedReadyTransaction.getNonce() + 1)
          .ifPresent(nextReadyTx -> prioritizeReadyTransaction(nextReadyTx, senderNonce, ADDED));
    }
  }

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
      decreaseTotalSize(lastTx);
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
      decreaseTotalSize(oldestSparse);
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
    return poolConfig.getPendingTransactionsCacheSizeBytes() - readyTotalSize.get();
  }

  public long getUsedSpace() {
    return readyTotalSize.get();
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

  private void increaseTotalSize(final PendingTransaction pendingTransaction) {
    readyTotalSize.addAndGet(pendingTransaction.getTransaction().getSize());
  }

  private boolean fitsInCache(final PendingTransaction pendingTransaction) {
    return readyTotalSize.get() + pendingTransaction.getTransaction().getSize()
        <= poolConfig.getPendingTransactionsCacheSizeBytes();
  }

  private void decreaseTotalSize(final PendingTransaction pendingTransaction) {
    decreaseTotalSize(pendingTransaction.getTransaction());
  }

  private void decreaseTotalSize(final Transaction transaction) {
    readyTotalSize.addAndGet(-transaction.getSize());
  }

  private Void removeFromReady(
      final NavigableMap<Long, PendingTransaction> senderTxs, final Transaction transaction) {

    if (senderTxs != null && senderTxs.remove(transaction.getNonce()) != null) {
      decreaseTotalSize(transaction);
    }

    return null;
  }

  private List<PendingTransaction> removeConfirmed(
      final NavigableMap<Long, PendingTransaction> senderTxs, final long maxConfirmedNonce) {
    if (senderTxs != null) {
      final var confirmedTxsToRemove = senderTxs.headMap(maxConfirmedNonce, true);
      final List<PendingTransaction> removed =
          confirmedTxsToRemove.values().stream()
              .peek(this::decreaseTotalSize)
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
  public List<Transaction> getLocalTransactions() {
    return pendingTransactions.values().stream()
        .filter(PendingTransaction::isReceivedFromLocalSource)
        .map(PendingTransaction::getTransaction)
        .collect(Collectors.toList());
  }

  @Override
  public boolean isLocalSender(final Address sender) {
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
  public void selectTransactions(final PendingTransactions.TransactionSelector selector) {
    final List<PendingTransaction> transactionsToRemove = new ArrayList<>();
    final Set<Hash> alreadyChecked = new HashSet<>();
    synchronized (lock) {
      final Iterator<PendingTransaction> itPrioritizedTransactions =
          prioritizedTransactions.prioritizedTransactions();
      outerLoop:
      while (itPrioritizedTransactions.hasNext()) {
        final PendingTransaction highestPriorityPendingTransaction =
            itPrioritizedTransactions.next();
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
  }

  private void removeInvalidTransaction(final PendingTransaction invalidTransaction) {
    // remove the invalid transaction and move all the following to sparse set
    final var sender = invalidTransaction.getSender();
    final var followingTxs =
        streamReadyTransactions(sender, invalidTransaction.getNonce()).collect(Collectors.toList());

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

    final var maybeLastValidSenderNonce =
        getReady(sender, invalidTransaction.getNonce() - 1).map(PendingTransaction::getNonce);

    followingTxs.add(0, invalidTransaction);

    prioritizedTransactions.demoteTransactions(sender, followingTxs, maybeLastValidSenderNonce);

    notifyTransactionDropped(invalidTransaction.getTransaction());
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
  public Set<PendingTransaction> getPendingTransactions() {
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
  public OptionalLong getNextNonceForSender(final Address sender) {
    final var senderTxs = readyBySender.get(sender);
    if (senderTxs != null) {
      return OptionalLong.of(senderTxs.lastKey() + 1);
    }

    return OptionalLong.empty();
  }

  @Override
  public void manageBlockAdded(
      final BlockHeader blockHeader,
      final List<Transaction> confirmedTransactions,
      final FeeMarket feeMarket) {
    synchronized (lock) {
      transactionsAddedToBlock(confirmedTransactions);
      prioritizedTransactions.manageBlockAdded(blockHeader, feeMarket);
      prioritizeReadyTransactions();
    }
  }

  private void transactionsAddedToBlock(final List<Transaction> confirmedTransactions) {
    final var orderedConfirmedNonceBySender = maxConfirmedNonceBySender(confirmedTransactions);
    final var removedTransactions = removeConfirmedTransactions(orderedConfirmedNonceBySender);
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
      final Predicate<PendingTransaction> notAlreadyPrioritized =
          pt -> prioritizedTransactions.containsTransaction(pt.getTransaction());

      final List<PendingTransaction> prioritizeTransactions =
          getPromotableTransactions(
              maxPromotable,
              notAlreadyPrioritized.and(prioritizedTransactions.getPromotionFilter()));

      prioritizeTransactions.forEach(prioritizedTransactions::addPrioritizedTransaction);
    }
  }

  private void notifyTransactionAdded(final Transaction transaction) {
    pendingTransactionSubscribers.forEach(listener -> listener.onTransactionAdded(transaction));
  }

  private void notifyTransactionDropped(final Transaction transaction) {
    transactionDroppedListeners.forEach(listener -> listener.onTransactionDropped(transaction));
  }

  @Override
  public String toTraceLog() {
    synchronized (lock) {
      return "Ready by sender "
          + readyBySender
          + ", Sparse by sender "
          + sparseBySender
          + "; "
          + prioritizedTransactions.toTraceLog();
    }
  }

  @Override
  public String logStats() {
    synchronized (lock) {
      return "Pending "
          + pendingTransactions.size()
          + ", Prioritized "
          + prioritizedTransactions.size()
          + ", Sparse "
          + sparseEvictionOrder.size();
    }
  }
}
