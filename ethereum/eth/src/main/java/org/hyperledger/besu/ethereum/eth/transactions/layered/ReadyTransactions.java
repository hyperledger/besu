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
package org.hyperledger.besu.ethereum.eth.transactions.layered;

import com.google.common.annotations.VisibleForTesting;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactionDroppedListener;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactionListener;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;
import org.hyperledger.besu.util.Subscribers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.maxBy;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ADDED;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ALREADY_KNOWN;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.REJECTED_UNDERPRICED_REPLACEMENT;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.TX_POOL_FULL;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.INTERNAL_ERROR;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;

public class ReadyTransactions extends AbstractTransactionsLayer {
  private static final Logger LOG = LoggerFactory.getLogger(ReadyTransactions.class);

//  private final TransactionPoolConfiguration poolConfig;
//  private final BiFunction<PendingTransaction, PendingTransaction, Boolean>
//      transactionReplacementTester;

 // private final Map<Hash, PendingTransaction> pendingTransactions = new HashMap<>();
//  private final Map<Address, NavigableMap<Long, PendingTransaction>> readyBySender =
//      new HashMap<>();
  private final NavigableSet<PendingTransaction> orderByMaxFee =
      new TreeSet<>(
          Comparator.comparing((PendingTransaction pt) -> pt.getTransaction().getMaxGasPrice())
              .thenComparing(PendingTransaction::getSequence));
/*
  private final Map<Address, NavigableMap<Long, PendingTransaction>> sparseBySender =
      new HashMap<>();
  private final NavigableSet<PendingTransaction> sparseEvictionOrder =
      new TreeSet<>(Comparator.comparing(PendingTransaction::getSequence));
*/
//  private long spaceUsed = 0;

//  private final Set<Address> localSenders = new HashSet<>();

//  private final Subscribers<PendingTransactionListener> pendingTransactionSubscribers =
//      Subscribers.create();
//
//  private final Subscribers<PendingTransactionDroppedListener> transactionDroppedListeners =
//      Subscribers.create();

  private final AbstractPrioritizedTransactions prioritizedTransactions;

//  final TransactionPoolMetrics metrics;
//  final SparseTransactions sparseTransactions;

  public ReadyTransactions(
      final TransactionPoolConfiguration poolConfig,
      final AbstractPrioritizedTransactions prioritizedTransactions,
      final TransactionPoolMetrics metrics,
      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
          transactionReplacementTester) {
      super(poolConfig, new SparseTransactions(poolConfig, metrics, transactionReplacementTester), transactionReplacementTester, metrics);
//    this.poolConfig = poolConfig;
    this.prioritizedTransactions = prioritizedTransactions;
//    this.transactionReplacementTester = transactionReplacementTester;
//    this.metrics = metrics;
    metrics.initPendingTransactionCount(pendingTransactions::size);
    metrics.initPendingTransactionSpace(this::getUsedSpace);
    metrics.initReadyTransactionCount(this::getReadyCount);
//    metrics.initSparseTransactionCount(sparseEvictionOrder::size);
    metrics.initPrioritizedTransactionSize(prioritizedTransactions::size);
//    this.sparseTransactions = new SparseTransactions(poolConfig, metrics, transactionReplacementTester);
  }

  @Override
  public String name() {
    return "ready";
  }

  @Override
  public synchronized void reset() {
    super.reset();
    orderByMaxFee.clear();
    prioritizedTransactions.reset();
  }
/*
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
    } else if (nonceDistance >= poolConfig.getMaxFutureBySender()) {
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
*/
//
//  private void updateMetrics(
//      final PendingTransaction pendingTransaction, final TransactionAddedResult result) {
//    if (result.isSuccess()) {
//      result
//          .maybeReplacedTransaction()
//          .ifPresent(
//              replacedTx -> {
//                metrics.incrementReplaced(
//                    replacedTx.isReceivedFromLocalSource(),
//                    result.isPrioritizable() ? "ready" : "sparse");
//                metrics.incrementRemoved(replacedTx.isReceivedFromLocalSource(), "replaced");
//              });
//      metrics.incrementAdded(pendingTransaction.isReceivedFromLocalSource());
//    } else {
//      final var rejectReason =
//          result
//              .maybeInvalidReason()
//              .orElseGet(
//                  () -> {
//                    LOG.warn("Missing invalid reason for status {}", result);
//                    return INTERNAL_ERROR;
//                  });
//      metrics.incrementRejected(false, rejectReason);
//      traceLambda(
//          LOG,
//          "Transaction {} rejected reason {}",
//          pendingTransaction::toTraceLog,
//          rejectReason::toString);
//    }
//  }

    @Override
    protected TransactionAddedResult internalAdd(
            final PendingTransaction pendingTransaction, final long senderNonce) {

    final var addStatus =
        modifySenderReadyTxsWrapper(
            pendingTransaction.getSender(),
            senderTxs -> tryAddToReady(senderTxs, pendingTransaction, senderNonce));
//
//    if (addStatus.isSuccess()) {
//       prioritize(pendingTransaction, senderNonce, addStatus);
//    }

    return addStatus;
  }

  private void prioritize(
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
          .ifPresent(nextReadyTx -> prioritize(nextReadyTx, senderNonce, ADDED));
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

  @Override
  protected
  List<PendingTransaction> internalRemove(final PendingTransaction pendingTransaction) {

    final List<PendingTransaction> demotedTxs = modifySenderReadyTxsWrapper(
            pendingTransaction.getSender(), senderTxs ->
                    removeFromReady(senderTxs, pendingTransaction.getTransaction()));

      orderByMaxFee.remove(pendingTransaction);

      // de-prioritize from the highest nonce first
      for(int i = demotedTxs.size() - 1; i >= 0; --i) {
          prioritizedTransactions.remove(demotedTxs.get(i));
      }

      return demotedTxs;
  }

    @Override
    protected void internalReplace(final PendingTransaction replacedTx) {
        orderByMaxFee.remove(replacedTx);
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

  @Override
  protected void innerConfirmed(final PendingTransaction pendingTransaction) {
      prioritizedTransactions.removeConfirmedTransactions(
              orderedConfirmedNonceBySender, removedTransactions);
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
      sparseTransactions.sparseToReady(sender, senderTxs, currLastNonce);
    }

    if (senderTxs != null && senderTxs.isEmpty()) {
      readyBySender.remove(sender);
    }

    return result;
  }

  @Override
  protected PendingTransaction getEvictable() {
      return orderByMaxFee.first();
  }


//  protected List<PendingTransaction> evict(final long spaceToFree) {
//    final var lessReadySender = orderByMaxFee.first().getSender();
//    final var lessReadySenderTxs = readyBySender.get(lessReadySender);
//
//    final List<PendingTransaction> evictedTxs = new ArrayList<>();
//    long postponedSize = 0;
//    PendingTransaction lastTx = null;
//    // lastTx must never be null, because the sender have at least the lessReadyTx
//    while (postponedSize < spaceToFree && !lessReadySenderTxs.isEmpty()) {
//      lastTx = lessReadySenderTxs.pollLastEntry().getValue();
//      pendingTransactions.remove(lastTx.getHash());
//      evictedTxs.add(lastTx);
//      decreaseSpaceUsed(lastTx);
//      postponedSize += lastTx.getTransaction().getSize();
//    }
//
//    if (lessReadySenderTxs.isEmpty()) {
//      readyBySender.remove(lessReadySender);
//      // at this point lastTx was the first for the sender, then remove it from eviction order too
//      orderByMaxFee.remove(lastTx);
//      if (!readyBySender.isEmpty()) {
//        // try next less valuable sender
//        evictedTxs.addAll(evict(spaceToFree - postponedSize));
//      }
//    }
//    return evictedTxs;
//  }

/*
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

 */
//
//  private long cacheFreeSpace() {
//    return poolConfig.getPendingTransactionsMaxCapacityBytes() - getUsedSpace();
//  }

    /*
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
  */
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
      return nextLayer.add(pendingTransaction, senderNonce);
    }

    // is the next one?
    if (pendingTransaction.getNonce() == senderTxs.lastKey() + 1) {
      senderTxs.put(pendingTransaction.getNonce(), pendingTransaction);
      return ADDED;
    }
      return nextLayer.add(pendingTransaction, senderNonce);
//    return sparseTransactions.tryAddToSparse(pendingTransaction);
  }
/*
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
*/
/*
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
 */

//  private void increaseSpaceUsed(final PendingTransaction pendingTransaction) {
//    spaceUsed += pendingTransaction.getTransaction().getSize();
//  }
//
//  private boolean fitsInCache(final PendingTransaction pendingTransaction) {
//    return getUsedSpace() + pendingTransaction.getTransaction().getSize()
//        <= poolConfig.getPendingTransactionsMaxCapacityBytes();
//  }
//
//  private void decreaseSpaceUsed(final PendingTransaction pendingTransaction) {
//    decreaseSpaceUsed(pendingTransaction.getTransaction());
//  }
//
//  private void decreaseSpaceUsed(final Transaction transaction) {
//    spaceUsed -= transaction.getSize();
//  }

  private List<PendingTransaction> removeFromReady(
      final NavigableMap<Long, PendingTransaction> senderTxs, final Transaction transaction) {

      // remove the transaction and any dependent transaction, and return a list of transaction
      // to move to the next layer
    if (senderTxs != null) {
        final var txsToRemove = senderTxs.tailMap(transaction.getNonce(), true);
        final var followingTxs = txsToRemove.values().stream()
                .peek(this::decreaseSpaceUsed)
                .skip(1)
                .collect(Collectors.toUnmodifiableList());
        txsToRemove.clear();
        return followingTxs;
    }

    return List.of();
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
  void removeInvalid(final List<PendingTransaction> invalidTransactions) {
    invalidTransactions.forEach(
        invalidTransaction -> {
          // remove the invalid transaction and move all the following to sparse set
          final var sender = invalidTransaction.getSender();
          final var senderReadyTxs = readyBySender.get(sender);

          final var followingTxs =
              senderReadyTxs.tailMap(invalidTransaction.getNonce(), false).values();

          removeFromReady(invalidTransaction.getTransaction());
          pendingTransactions.remove(invalidTransaction.getHash());
          metrics.incrementRemoved(invalidTransaction.isReceivedFromLocalSource(), "invalid", name());

          followingTxs.stream()
              // skip following that are invalid here, they will be removed in a next cycle
              .filter(followingTx -> !invalidTransactions.contains(followingTx))
              .forEach(
                  followingTx -> {
                    removeFromReady(followingTx.getTransaction());
                    sparseTransactions.tryAddToSparse(followingTx);
                  /*  sparseBySender
                        .computeIfAbsent(sender, unused -> new TreeMap<>())
                        .put(followingTx.getNonce(), followingTx);
                    sparseEvictionOrder.add(followingTx);

                   */
                  });

          if (prioritizedTransactions.containsTransaction(invalidTransaction.getTransaction())) {
            // remove the invalid and the following from prioritize
            final List<PendingTransaction> demotedTxs = new ArrayList<>(followingTxs.size() + 1);
            demotedTxs.add(invalidTransaction);
            demotedTxs.addAll(followingTxs);

            // if previous valid transaction is prioritized then update the expected nonce or remove
            // it
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
        });
  }

@Override
    void remove(final PendingTransaction pendingTransaction) {
        // remove the invalid transaction and move all the following to sparse set
        final var sender = pendingTransaction.getSender();
        final var senderReadyTxs = readyBySender.get(sender);

        final var followingTxs =
                senderReadyTxs.tailMap(pendingTransaction.getNonce(), false).values();

        removeFromReady(pendingTransaction.getTransaction());
        pendingTransactions.remove(invalidTransaction.getHash());
        metrics.incrementRemoved(invalidTransaction.isReceivedFromLocalSource(), "invalid");

        followingTxs.stream()
                // skip following that are invalid here, they will be removed in a next cycle
                .filter(followingTx -> !invalidTransactions.contains(followingTx))
                .forEach(
                        followingTx -> {
                            removeFromReady(followingTx.getTransaction());
                            sparseTransactions.tryAddToSparse(followingTx);
                  /*  sparseBySender
                        .computeIfAbsent(sender, unused -> new TreeMap<>())
                        .put(followingTx.getNonce(), followingTx);
                    sparseEvictionOrder.add(followingTx);

                   */
                        });

        if (prioritizedTransactions.containsTransaction(invalidTransaction.getTransaction())) {
            // remove the invalid and the following from prioritize
            final List<PendingTransaction> demotedTxs = new ArrayList<>(followingTxs.size() + 1);
            demotedTxs.add(invalidTransaction);
            demotedTxs.addAll(followingTxs);

            // if previous valid transaction is prioritized then update the expected nonce or remove
            // it
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


    private void transactionsAddedToBlock(final List<Transaction> confirmedTransactions) {
    final var orderedConfirmedNonceBySender = maxConfirmedNonceBySender(confirmedTransactions);
    final var removedTransactions = removeConfirmedTransactions(orderedConfirmedNonceBySender);
    sparseTransactions.removeConfirmedTransactions(orderedConfirmedNonceBySender);
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
    final int maxPromotable =
        poolConfig.getMaxPrioritizedTransactions() - prioritizedTransactions.size();

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
//
//  private void notifyTransactionAdded(final PendingTransaction pendingTransaction) {
//    pendingTransactionSubscribers.forEach(
//        listener -> listener.onTransactionAdded(pendingTransaction.getTransaction()));
//  }
//
//  private void notifyTransactionDropped(final PendingTransaction pendingTransaction) {
//    transactionDroppedListeners.forEach(
//        listener -> listener.onTransactionDropped(pendingTransaction.getTransaction()));
//  }

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

  private void consistencyCheck() {
    // check numbers
    final int pendingTotal = pendingTransactions.size();
    final int readyTotal = getReadyCount();
    final int sparseTotal = sparseTransactions.getSparseCount();
    if (pendingTotal != readyTotal + sparseTotal) {
      LOG.error("Pending != Ready + Sparse ({} != {} + {})", pendingTotal, readyTotal, sparseTotal);
    }

    readyCheck();

    sparseTransactions.consistencyCheck();

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
/*
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
*/
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
/*
              if (sparseBySender.containsKey(ptx.getSender())
                  && sparseBySender.get(ptx.getSender()).containsKey(ptx.getNonce())
                  && sparseBySender.get(ptx.getSender()).get(ptx.getNonce()).equals(ptx)) {
                LOG.error("PrioritizedTransaction {} found in SparseBySender", ptx);
              }

 */
            });

    prioritizedTransactions.consistencyCheck();
  }
}
