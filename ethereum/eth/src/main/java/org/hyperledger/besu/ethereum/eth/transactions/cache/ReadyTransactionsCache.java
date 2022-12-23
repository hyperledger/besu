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
package org.hyperledger.besu.ethereum.eth.transactions.cache;

import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ADDED;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ALREADY_KNOWN;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.POSTPONED;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.REJECTED_UNDERPRICED_REPLACEMENT;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReadyTransactionsCache {
  private static final Logger LOG = LoggerFactory.getLogger(ReadyTransactionsCache.class);

  private final TransactionPoolConfiguration poolConfig;
  private final PostponedTransactionsCache postponedCache;
  private final BiFunction<PendingTransaction, PendingTransaction, Boolean>
      transactionReplacementTester;
  private final Map<Address, NavigableMap<Long, PendingTransaction>> readyBySender =
      new ConcurrentHashMap<>();

  private final NavigableSet<Transaction> orderByMaxFee =
      new TreeSet<>(
          Comparator.comparing(Transaction::getMaxGasFee).thenComparing(Transaction::getSender));

  private final AtomicLong readyTotalSize = new AtomicLong();

  public ReadyTransactionsCache(
      final TransactionPoolConfiguration poolConfig,
      final PostponedTransactionsCache postponedCache,
      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
          transactionReplacementTester) {
    this.poolConfig = poolConfig;
    this.postponedCache = postponedCache;
    this.transactionReplacementTester = transactionReplacementTester;
  }

  public TransactionAddedResult add(
      final PendingTransaction pendingTransaction, final long senderNonce) {

    // try to add to the ready set
    var addToReadyStatus =
        modifySenderReadyTxsWrapper(
            pendingTransaction.getSender(),
            senderTxs -> tryAddToReady(senderTxs, pendingTransaction, senderNonce));

    if (addToReadyStatus.equals(POSTPONED)) {
      postponedCache.add(pendingTransaction);
    }

    if (addToReadyStatus.equals(ADDED) || addToReadyStatus.isReplacement()) {
      final var cacheFreeSpace = cacheFreeSpace();
      if (cacheFreeSpace < 0) {
        // free some space moving less valuable ready to postponed
        final var evictedTransactions = evictReadyTransactions(-cacheFreeSpace);
        postponedCache.addAll(evictedTransactions);

        if (evictedTransactions.contains(pendingTransaction)) {
          // in case the just added transaction is postponed to free space change the returned
          // result
          return POSTPONED;
        }
      }
    }

    return addToReadyStatus;
  }

  public Optional<PendingTransaction> get(final Address sender, final long nonce) {
    var senderTxs = readyBySender.get(sender);
    if (senderTxs != null) {
      return Optional.ofNullable(senderTxs.get(nonce));
    }
    return Optional.empty();
  }

  public void remove(final Transaction transaction) {
    modifySenderReadyTxsWrapper(
        transaction.getSender(), senderTxs -> remove(senderTxs, transaction));
  }

  public OptionalLong getNextReadyNonce(final Address sender) {
    var senderTxs = readyBySender.get(sender);
    if (senderTxs != null) {
      return OptionalLong.of(senderTxs.lastKey() + 1);
    }
    return OptionalLong.empty();
  }

  public void removeConfirmedTransactions(
      final Map<Address, Optional<Long>> orderedConfirmedNonceBySender) {

    for (var senderMaxConfirmedNonce : orderedConfirmedNonceBySender.entrySet()) {
      final var maxConfirmedNonce = senderMaxConfirmedNonce.getValue().get();
      final var sender = senderMaxConfirmedNonce.getKey();

      modifySenderReadyTxsWrapper(
          sender, senderTxs -> removeConfirmed(sender, senderTxs, maxConfirmedNonce));
    }
  }

  /**
   * Search and returns ready transaction that could be promoted to prioritized
   *
   * @param maxPromotable max number of ready transactions that could be promoted
   * @param promotionFilter first that a ready transaction needs to pass to get promoted
   * @return a list of transactions that could be prioritized, for each sender the transactions are
   *     in asc nonce order
   */
  public List<PendingTransaction> getPromotableTransactions(
      final int maxPromotable, final Predicate<PendingTransaction> promotionFilter) {

    List<PendingTransaction> promotableTxs = new ArrayList<>(maxPromotable);

    // if there is space pick other ready transactions
    if (maxPromotable > 0) {
      promotableTxs.addAll(promoteReady(maxPromotable, promotionFilter));
    }

    return promotableTxs;
  }

  public Stream<PendingTransaction> streamReadyTransactions(final Address sender) {
    return streamReadyTransactions(sender, -1);
  }

  public Stream<PendingTransaction> streamReadyTransactions(
      final Address sender, final long afterNonce) {
    var senderTxs = readyBySender.get(sender);
    if (senderTxs != null) {
      return senderTxs.tailMap(afterNonce, false).values().stream();
    }
    return Stream.empty();
  }

  private synchronized <R> R modifySenderReadyTxsWrapper(
      final Address sender,
      final Function<NavigableMap<Long, PendingTransaction>, R> modifySenderTxs) {

    var senderTxs = readyBySender.get(sender);
    final Optional<Transaction> prevFirstTx = getFirstReadyTransaction(senderTxs);
    final long prevLastNonce = getLastReadyNonce(senderTxs);

    final var result = modifySenderTxs.apply(senderTxs);

    if (senderTxs == null) {
      // it could have been created
      senderTxs = readyBySender.get(sender);
    }

    final Optional<Transaction> currFirstTx = getFirstReadyTransaction(senderTxs);
    final long currLastNonce = getLastReadyNonce(senderTxs);

    if (!prevFirstTx.equals(currFirstTx)) {
      prevFirstTx.ifPresent(orderByMaxFee::remove);
      currFirstTx.ifPresent(orderByMaxFee::add);
    }

    if (prevLastNonce != currLastNonce) {
      promoteFromPostponed(sender, currLastNonce);
    }

    if (senderTxs != null && senderTxs.isEmpty()) {
      readyBySender.remove(sender);
    }

    return result;
  }

  private List<PendingTransaction> evictReadyTransactions(final long evictSize) {
    final var lessReadySender = orderByMaxFee.first().getSender();
    final var lessReadySenderTxs = readyBySender.get(lessReadySender);

    final List<PendingTransaction> toPostponed = new ArrayList<>();
    long postponedSize = 0;
    PendingTransaction lastTx = null;
    // lastTx must never be null, because the sender have at least the lessReadyTx
    while (postponedSize < evictSize && !lessReadySenderTxs.isEmpty()) {
      lastTx = lessReadySenderTxs.pollLastEntry().getValue();
      toPostponed.add(lastTx);
      decreaseTotalSize(lastTx);
      postponedSize += lastTx.getTransaction().getSize();
    }

    if (lessReadySenderTxs.isEmpty()) {
      readyBySender.remove(lessReadySender);
      // at this point lastTx was the first for the sender, then remove it from eviction order too
      orderByMaxFee.remove(lastTx.getTransaction());
      if (!readyBySender.isEmpty()) {
        // try next less valuable sender
        toPostponed.addAll(evictReadyTransactions(evictSize - postponedSize));
      }
    }
    return toPostponed;
  }

  private long cacheFreeSpace() {
    return poolConfig.getPendingTransactionsCacheSizeBytes() - readyTotalSize.get();
  }

  private void promoteFromPostponed(final Address sender, final long currLastNonce) {

    final long maxSize = cacheFreeSpace();
    if (maxSize > 0) {
      postponedCache
          .promoteForSender(sender, currLastNonce, maxSize)
          .thenAccept(
              toReadyTxs -> {
                modifySenderReadyTxsWrapper(
                    sender, senderTxs -> postponedToReady(senderTxs, toReadyTxs));
              })
          .exceptionally(
              throwable -> {
                LOG.debug(
                    "Error moving from postponed to ready for sender {}, last nonce {}, max size {} bytes, cause {}",
                    sender,
                    currLastNonce,
                    maxSize,
                    throwable.getMessage());
                return null;
              });
    }
  }

  private Void postponedToReady(
      final NavigableMap<Long, PendingTransaction> senderTxs,
      final List<PendingTransaction> toReadyTxs) {

    var expectedNonce = senderTxs.lastKey() + 1;

    for (var tx : toReadyTxs) {
      if (!fitsInCache(tx)) {
        // cache full, stop moving to ready
        break;
      }
      if (tx.getNonce() == expectedNonce++) {
        senderTxs.put(tx.getNonce(), tx);
        increaseTotalSize(tx);
      }
    }
    return null;
  }

  private Optional<Transaction> getFirstReadyTransaction(
      final NavigableMap<Long, PendingTransaction> senderTxs) {
    if (senderTxs == null || senderTxs.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(senderTxs.firstEntry().getValue().getTransaction());
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
        increaseTotalSize(pendingTransaction);
        return ADDED;
      }
      return POSTPONED;
    }

    // is replacing an existing one?
    var existingReadyTx = senderTxs.get(pendingTransaction.getNonce());
    if (existingReadyTx != null) {

      if (existingReadyTx.getHash().equals(pendingTransaction.getHash())) {
        return ALREADY_KNOWN;
      }

      if (!transactionReplacementTester.apply(existingReadyTx, pendingTransaction)) {
        return REJECTED_UNDERPRICED_REPLACEMENT;
      }
      senderTxs.put(pendingTransaction.getNonce(), pendingTransaction);
      decreaseTotalSize(existingReadyTx);
      increaseTotalSize(pendingTransaction);
      return TransactionAddedResult.createForReplacement(existingReadyTx);
    }

    // is the next one?
    if (pendingTransaction.getNonce() == senderTxs.lastKey() + 1) {
      senderTxs.put(pendingTransaction.getNonce(), pendingTransaction);
      increaseTotalSize(pendingTransaction);
      return ADDED;
    }
    return POSTPONED;
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

  private Void remove(
      final NavigableMap<Long, PendingTransaction> senderTxs, final Transaction transaction) {

    if (senderTxs != null && senderTxs.remove(transaction.getNonce()) != null) {
      decreaseTotalSize(transaction);
    }

    postponedCache.remove(transaction);
    return null;
  }

  private Void removeConfirmed(
      final Address sender,
      final NavigableMap<Long, PendingTransaction> senderTxs,
      final long maxConfirmedNonce) {
    if (senderTxs != null) {
      final var confirmedTxsToRemove = senderTxs.headMap(maxConfirmedNonce, true);
      confirmedTxsToRemove.values().stream().forEach(this::decreaseTotalSize);
      confirmedTxsToRemove.clear();

      postponedCache.removeForSenderBelowNonce(sender, maxConfirmedNonce);
    }
    return null;
  }

  private Collection<PendingTransaction> promoteReady(
      final int maxRemaining, final Predicate<PendingTransaction> promotionFilter) {

    final List<PendingTransaction> promotedTxs = new ArrayList<>(maxRemaining);

    for (var senderEntry : orderByMaxFee.descendingSet()) {
      final int maxForThisSender = maxRemaining - promotedTxs.size();
      if (maxForThisSender <= 0) {
        break;
      }
      promotedTxs.addAll(
          modifySenderReadyTxsWrapper(
              senderEntry.getSender(),
              senderTxs -> promoteReady(senderTxs, maxForThisSender, promotionFilter)));
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
}
