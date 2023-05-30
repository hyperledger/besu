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

import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ADDED;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ALREADY_KNOWN;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.REJECTED_UNDERPRICED_REPLACEMENT;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.TRY_NEXT_LAYER;
import static org.hyperledger.besu.ethereum.eth.transactions.layered.TransactionsLayer.RemovalReason.CONFIRMED;
import static org.hyperledger.besu.ethereum.eth.transactions.layered.TransactionsLayer.RemovalReason.CROSS_LAYER_REPLACED;
import static org.hyperledger.besu.ethereum.eth.transactions.layered.TransactionsLayer.RemovalReason.EVICTED;
import static org.hyperledger.besu.ethereum.eth.transactions.layered.TransactionsLayer.RemovalReason.PROMOTED;
import static org.hyperledger.besu.ethereum.eth.transactions.layered.TransactionsLayer.RemovalReason.REPLACED;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactionAddedListener;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactionDroppedListener;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.util.Subscribers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractTransactionsLayer implements TransactionsLayer {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractTransactionsLayer.class);
  private static final NavigableMap<Long, PendingTransaction> EMPTY_SENDER_TXS = new TreeMap<>();
  protected final TransactionPoolConfiguration poolConfig;
  protected final TransactionsLayer nextLayer;
  protected final BiFunction<PendingTransaction, PendingTransaction, Boolean>
      transactionReplacementTester;
  protected final TransactionPoolMetrics metrics;
  protected final Map<Hash, PendingTransaction> pendingTransactions = new HashMap<>();
  protected final Map<Address, NavigableMap<Long, PendingTransaction>> txsBySender =
      new HashMap<>();
  private final Subscribers<PendingTransactionAddedListener> onAddedListeners =
      Subscribers.create();
  private final Subscribers<PendingTransactionDroppedListener> onDroppedListeners =
      Subscribers.create();
  private OptionalLong nextLayerOnAddedListenerId = OptionalLong.empty();
  private OptionalLong nextLayerOnDroppedListenerId = OptionalLong.empty();
  protected long spaceUsed = 0;

  public AbstractTransactionsLayer(
      final TransactionPoolConfiguration poolConfig,
      final TransactionsLayer nextLayer,
      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
          transactionReplacementTester,
      final TransactionPoolMetrics metrics) {
    this.poolConfig = poolConfig;
    this.nextLayer = nextLayer;
    this.transactionReplacementTester = transactionReplacementTester;
    this.metrics = metrics;
    metrics.initSpaceUsed(this::getLayerSpaceUsed, name());
    metrics.initTransactionCount(pendingTransactions::size, name());
    metrics.initUniqueSenderCount(txsBySender::size, name());
  }

  protected abstract boolean gapsAllowed();

  @Override
  public void reset() {
    pendingTransactions.clear();
    txsBySender.clear();
    spaceUsed = 0;
    nextLayer.reset();
  }

  @Override
  public Optional<Transaction> getByHash(final Hash transactionHash) {
    final var currLayerTx = pendingTransactions.get(transactionHash);
    if (currLayerTx == null) {
      return nextLayer.getByHash(transactionHash);
    }
    return Optional.of(currLayerTx.getTransaction());
  }

  @Override
  public boolean contains(final Transaction transaction) {
    return pendingTransactions.containsKey(transaction.getHash())
        || nextLayer.contains(transaction);
  }

  @Override
  public List<PendingTransaction> getAll() {
    final List<PendingTransaction> allNextLayers = nextLayer.getAll();
    final List<PendingTransaction> allTxs =
        new ArrayList<>(pendingTransactions.size() + allNextLayers.size());
    allTxs.addAll(pendingTransactions.values());
    allTxs.addAll(allNextLayers);
    return allTxs;
  }

  @Override
  public long getCumulativeUsedSpace() {
    return getLayerSpaceUsed() + nextLayer.getCumulativeUsedSpace();
  }

  protected long getLayerSpaceUsed() {
    return spaceUsed;
  }

  protected abstract TransactionAddedResult canAdd(
      final PendingTransaction pendingTransaction, final int gap);

  @Override
  public TransactionAddedResult add(final PendingTransaction pendingTransaction, final int gap) {

    // is replacing an existing one?
    TransactionAddedResult addStatus = maybeReplaceTransaction(pendingTransaction);
    if (addStatus == null) {
      addStatus = canAdd(pendingTransaction, gap);
    }

    if (addStatus.equals(TRY_NEXT_LAYER)) {
      return addToNextLayer(pendingTransaction, gap);
    }

    if (addStatus.isSuccess()) {
      processAdded(pendingTransaction);
      addStatus.maybeReplacedTransaction().ifPresent(this::replaced);

      nextLayer.notifyAdded(pendingTransaction);

      if (!maybeFull()) {
        // if there is space try to see if the added tx filled some gaps
        tryFillGap(addStatus, pendingTransaction);
      }

      notifyTransactionAdded(pendingTransaction);
    } else {
      final var rejectReason = addStatus.maybeInvalidReason().orElseThrow();
      metrics.incrementRejected(false, rejectReason, name());
      LOG.atTrace()
          .setMessage("Transaction {} rejected reason {}")
          .addArgument(pendingTransaction::toTraceLog)
          .addArgument(rejectReason)
          .log();
    }

    return addStatus;
  }

  private boolean maybeFull() {
    final long cacheFreeSpace = cacheFreeSpace();
    final int overflowTxsCount = pendingTransactions.size() - maxTransactionsNumber();
    if (cacheFreeSpace < 0 || overflowTxsCount > 0) {
      LOG.atDebug()
          .setMessage("Layer full: {}")
          .addArgument(
              () ->
                  cacheFreeSpace < 0
                      ? "need to free " + (-cacheFreeSpace) + " space"
                      : "need to evict " + overflowTxsCount + " transaction(s)")
          .log();

      evict(-cacheFreeSpace, overflowTxsCount);
      return true;
    }
    return false;
  }

  private void tryFillGap(
      final TransactionAddedResult addStatus, final PendingTransaction pendingTransaction) {
    // it makes sense to fill gaps only if the add is not a replacement and this layer does not
    // allow gaps
    if (!addStatus.isReplacement() && !gapsAllowed()) {
      final PendingTransaction promotedTx =
          nextLayer.promoteFor(pendingTransaction.getSender(), pendingTransaction.getNonce());
      if (promotedTx != null) {
        processAdded(promotedTx);
        if (!maybeFull()) {
          tryFillGap(ADDED, promotedTx);
        }
      }
    }
  }

  @Override
  public void notifyAdded(final PendingTransaction pendingTransaction) {
    final Address sender = pendingTransaction.getSender();
    final var senderTxs = txsBySender.get(sender);
    if (senderTxs != null) {
      if (senderTxs.firstKey() < pendingTransaction.getNonce()) {
        // in the case the world state has been updated but the confirmed txs have not yet been
        // processed
        confirmed(sender, pendingTransaction.getNonce());
      } else if (senderTxs.firstKey() == pendingTransaction.getNonce()) {
        // it is a cross layer replacement, namely added to a previous layer
        final PendingTransaction replacedTx = senderTxs.pollFirstEntry().getValue();
        processRemove(senderTxs, replacedTx.getTransaction(), CROSS_LAYER_REPLACED);

        if (senderTxs.isEmpty()) {
          txsBySender.remove(sender);
        }
      } else {
        internalNotifyAdded(senderTxs, pendingTransaction);
      }
    }
    nextLayer.notifyAdded(pendingTransaction);
  }

  protected abstract void internalNotifyAdded(
      final NavigableMap<Long, PendingTransaction> senderTxs,
      final PendingTransaction pendingTransaction);

  @Override
  public PendingTransaction promoteFor(final Address sender, final long nonce) {
    final var senderTxs = txsBySender.get(sender);
    if (senderTxs != null) {
      long expectedNonce = nonce + 1;
      if (senderTxs.firstKey() == expectedNonce) {
        final PendingTransaction promotedTx = senderTxs.pollFirstEntry().getValue();
        processRemove(senderTxs, promotedTx.getTransaction(), PROMOTED);
        metrics.incrementRemoved(promotedTx.isReceivedFromLocalSource(), "promoted", name());

        if (senderTxs.isEmpty()) {
          txsBySender.remove(sender);
        }
        return promotedTx;
      }
    }
    return nextLayer.promoteFor(sender, nonce);
  }

  private TransactionAddedResult addToNextLayer(
      final PendingTransaction pendingTransaction, final int distance) {
    return addToNextLayer(
        txsBySender.getOrDefault(pendingTransaction.getSender(), EMPTY_SENDER_TXS),
        pendingTransaction,
        distance);
  }

  private TransactionAddedResult addToNextLayer(
      final NavigableMap<Long, PendingTransaction> senderTxs,
      final PendingTransaction pendingTransaction,
      final int distance) {
    final int nextLayerDistance;
    if (senderTxs.isEmpty()) {
      nextLayerDistance = distance;
    } else {
      nextLayerDistance = (int) (pendingTransaction.getNonce() - (senderTxs.lastKey() + 1));
    }
    return nextLayer.add(pendingTransaction, nextLayerDistance);
  }

  private void processAdded(final PendingTransaction addedTx) {
    pendingTransactions.put(addedTx.getHash(), addedTx);
    final var senderTxs = txsBySender.computeIfAbsent(addedTx.getSender(), s -> new TreeMap<>());
    senderTxs.put(addedTx.getNonce(), addedTx);
    increaseSpaceUsed(addedTx);
    metrics.incrementAdded(addedTx.isReceivedFromLocalSource(), name());
    internalAdd(senderTxs, addedTx);
  }

  protected abstract void internalAdd(
      final NavigableMap<Long, PendingTransaction> senderTxs, final PendingTransaction addedTx);

  protected abstract int maxTransactionsNumber();

  private void evict(final long spaceToFree, final int txsToEvict) {
    final var evictableTx = getEvictable();
    if (evictableTx != null) {
      final var lessReadySender = evictableTx.getSender();
      final var lessReadySenderTxs = txsBySender.get(lessReadySender);

      long evictedSize = 0;
      int evictedCount = 0;
      PendingTransaction lastTx;
      // lastTx must never be null, because the sender have at least the lessReadyTx
      while ((evictedSize < spaceToFree || txsToEvict > evictedCount)
          && !lessReadySenderTxs.isEmpty()) {
        lastTx = lessReadySenderTxs.pollLastEntry().getValue();
        processEvict(lessReadySenderTxs, lastTx);
        ++evictedCount;
        evictedSize += lastTx.memorySize();
        // evicted can always be added to the next layer
        addToNextLayer(lessReadySenderTxs, lastTx, 0);
      }

      if (lessReadySenderTxs.isEmpty()) {
        txsBySender.remove(lessReadySender);
      }

      final long newSpaceToFree = spaceToFree - evictedSize;
      final int newTxsToEvict = txsToEvict - evictedCount;

      if ((newSpaceToFree > 0 || newTxsToEvict > 0) && !txsBySender.isEmpty()) {
        // try next less valuable sender
        evict(newSpaceToFree, newTxsToEvict);
      }
    }
  }

  protected void replaced(final PendingTransaction replacedTx) {
    pendingTransactions.remove(replacedTx.getHash());
    decreaseSpaceUsed(replacedTx);
    metrics.incrementRemoved(replacedTx.isReceivedFromLocalSource(), REPLACED.label(), name());
    internalReplaced(replacedTx);
  }

  protected abstract void internalReplaced(final PendingTransaction replacedTx);

  private TransactionAddedResult maybeReplaceTransaction(final PendingTransaction incomingTx) {

    final var existingTxs = txsBySender.get(incomingTx.getSender());

    if (existingTxs != null) {
      final var existingReadyTx = existingTxs.get(incomingTx.getNonce());
      if (existingReadyTx != null) {

        if (existingReadyTx.getHash().equals(incomingTx.getHash())) {
          return ALREADY_KNOWN;
        }

        if (!transactionReplacementTester.apply(existingReadyTx, incomingTx)) {
          return REJECTED_UNDERPRICED_REPLACEMENT;
        }
        return TransactionAddedResult.createForReplacement(existingReadyTx);
      }
    }
    return null;
  }

  protected PendingTransaction processRemove(
      final NavigableMap<Long, PendingTransaction> senderTxs,
      final Transaction transaction,
      final RemovalReason removalReason) {
    final PendingTransaction removedTx = pendingTransactions.remove(transaction.getHash());
    if (removedTx != null) {
      decreaseSpaceUsed(removedTx);
      metrics.incrementRemoved(
          removedTx.isReceivedFromLocalSource(), removalReason.label(), name());
      internalRemove(senderTxs, removedTx, removalReason);
    }
    return removedTx;
  }

  protected PendingTransaction processEvict(
      final NavigableMap<Long, PendingTransaction> senderTxs, final PendingTransaction evictedTx) {
    final PendingTransaction removedTx = pendingTransactions.remove(evictedTx.getHash());
    if (removedTx != null) {
      decreaseSpaceUsed(evictedTx);
      metrics.incrementRemoved(evictedTx.isReceivedFromLocalSource(), EVICTED.label(), name());
      internalEvict(senderTxs, removedTx);
    }
    return removedTx;
  }

  protected abstract void internalEvict(
      final NavigableMap<Long, PendingTransaction> lessReadySenderTxs,
      final PendingTransaction evictedTx);

  @Override
  public final void blockAdded(
      final FeeMarket feeMarket,
      final BlockHeader blockHeader,
      final Map<Address, Long> maxConfirmedNonceBySender) {
    LOG.atDebug()
        .setMessage("Managing new added block {}")
        .addArgument(blockHeader::toLogString)
        .log();

    nextLayer.blockAdded(feeMarket, blockHeader, maxConfirmedNonceBySender);
    maxConfirmedNonceBySender.forEach(this::confirmed);
    internalBlockAdded(blockHeader, feeMarket);
  }

  protected abstract void internalBlockAdded(
      final BlockHeader blockHeader, final FeeMarket feeMarket);

  final void promoteTransactions() {
    int freeSlots = maxTransactionsNumber() - pendingTransactions.size();

    while (cacheFreeSpace() > 0 && freeSlots > 0) {
      final var promotedTx = nextLayer.promote(this::promotionFilter);
      if (promotedTx != null) {
        processAdded(promotedTx);
        --freeSlots;
      } else {
        break;
      }
    }
  }

  private void confirmed(final Address sender, final long maxConfirmedNonce) {
    final var senderTxs = txsBySender.get(sender);

    if (senderTxs != null) {
      final var confirmedTxs = senderTxs.headMap(maxConfirmedNonce, true);
      final var highestNonceRemovedTx =
          confirmedTxs.isEmpty() ? null : confirmedTxs.lastEntry().getValue();

      final var itConfirmedTxs = confirmedTxs.values().iterator();
      while (itConfirmedTxs.hasNext()) {
        final var confirmedTx = itConfirmedTxs.next();
        itConfirmedTxs.remove();
        processRemove(senderTxs, confirmedTx.getTransaction(), CONFIRMED);

        metrics.incrementRemoved(confirmedTx.isReceivedFromLocalSource(), "confirmed", name());
        LOG.atTrace()
            .setMessage("Removed confirmed pending transactions {}")
            .addArgument(confirmedTx::toTraceLog)
            .log();
      }

      if (senderTxs.isEmpty()) {
        txsBySender.remove(sender);
      } else {
        internalConfirmed(senderTxs, sender, maxConfirmedNonce, highestNonceRemovedTx);
      }
    }

    promoteTransactions();
  }

  protected abstract void internalConfirmed(
      final NavigableMap<Long, PendingTransaction> senderTxs,
      final Address sender,
      final long maxConfirmedNonce,
      final PendingTransaction highestNonceRemovedTx);

  protected abstract void internalRemove(
      final NavigableMap<Long, PendingTransaction> senderTxs,
      final PendingTransaction pendingTransaction,
      final RemovalReason removalReason);

  protected abstract PendingTransaction getEvictable();

  protected void increaseSpaceUsed(final PendingTransaction pendingTransaction) {
    spaceUsed += pendingTransaction.memorySize();
  }

  protected void decreaseSpaceUsed(final PendingTransaction pendingTransaction) {
    spaceUsed -= pendingTransaction.memorySize();
  }

  protected abstract long cacheFreeSpace();

  protected abstract boolean promotionFilter(PendingTransaction pendingTransaction);

  @Override
  public List<Transaction> getAllLocal() {
    final var localTxs =
        pendingTransactions.values().stream()
            .filter(PendingTransaction::isReceivedFromLocalSource)
            .map(PendingTransaction::getTransaction)
            .collect(Collectors.toCollection(ArrayList::new));
    localTxs.addAll(nextLayer.getAllLocal());
    return localTxs;
  }

  Stream<PendingTransaction> stream(final Address sender) {
    return txsBySender.getOrDefault(sender, EMPTY_SENDER_TXS).values().stream();
  }

  @Override
  public List<PendingTransaction> getAllFor(final Address sender) {
    return Stream.concat(stream(sender), nextLayer.getAllFor(sender).stream()).toList();
  }

  abstract Stream<PendingTransaction> stream();

  @Override
  public int count() {
    return pendingTransactions.size() + nextLayer.count();
  }

  protected void notifyTransactionAdded(final PendingTransaction pendingTransaction) {
    onAddedListeners.forEach(
        listener -> listener.onTransactionAdded(pendingTransaction.getTransaction()));
  }

  protected void notifyTransactionDropped(final PendingTransaction pendingTransaction) {
    onDroppedListeners.forEach(
        listener -> listener.onTransactionDropped(pendingTransaction.getTransaction()));
  }

  @Override
  public long subscribeToAdded(final PendingTransactionAddedListener listener) {
    nextLayerOnAddedListenerId = OptionalLong.of(nextLayer.subscribeToAdded(listener));
    return onAddedListeners.subscribe(listener);
  }

  @Override
  public void unsubscribeFromAdded(final long id) {
    nextLayerOnAddedListenerId.ifPresent(nextLayer::unsubscribeFromAdded);
    onAddedListeners.unsubscribe(id);
  }

  @Override
  public long subscribeToDropped(final PendingTransactionDroppedListener listener) {
    nextLayerOnDroppedListenerId = OptionalLong.of(nextLayer.subscribeToDropped(listener));
    return onDroppedListeners.subscribe(listener);
  }

  @Override
  public void unsubscribeFromDropped(final long id) {
    nextLayerOnDroppedListenerId.ifPresent(nextLayer::unsubscribeFromDropped);
    onDroppedListeners.unsubscribe(id);
  }

  @Override
  public String logStats() {
    return internalLogStats() + " | " + nextLayer.logStats();
  }

  @Override
  public String logSender(final Address sender) {
    final var senderTxs = txsBySender.get(sender);
    return name()
        + "["
        + (Objects.isNull(senderTxs) ? "Empty" : senderTxs.keySet())
        + "] "
        + nextLayer.logSender(sender);
  }

  protected abstract String internalLogStats();

  boolean consistencyCheck(
      final Map<Address, TreeMap<Long, PendingTransaction>> prevLayerTxsBySender) {
    final BinaryOperator<PendingTransaction> noMergeExpected =
        (a, b) -> {
          throw new IllegalArgumentException();
        };
    final var controlTxsBySender =
        pendingTransactions.values().stream()
            .collect(
                Collectors.groupingBy(
                    PendingTransaction::getSender,
                    Collectors.toMap(
                        PendingTransaction::getNonce,
                        Function.identity(),
                        noMergeExpected,
                        TreeMap::new)));

    assert txsBySender.equals(controlTxsBySender)
        : "pendingTransactions and txsBySender do not contain the same txs";

    assert pendingTransactions.values().stream().mapToInt(PendingTransaction::memorySize).sum()
            == spaceUsed
        : "space used does not match";

    internalConsistencyCheck(prevLayerTxsBySender);

    if (nextLayer instanceof AbstractTransactionsLayer) {
      txsBySender.forEach(
          (sender, txsByNonce) ->
              prevLayerTxsBySender
                  .computeIfAbsent(sender, s -> new TreeMap<>())
                  .putAll(txsByNonce));
      return ((AbstractTransactionsLayer) nextLayer).consistencyCheck(prevLayerTxsBySender);
    }
    return true;
  }

  protected abstract void internalConsistencyCheck(
      final Map<Address, TreeMap<Long, PendingTransaction>> prevLayerTxsBySender);
}
