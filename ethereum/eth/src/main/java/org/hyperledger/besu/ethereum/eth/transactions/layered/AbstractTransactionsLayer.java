package org.hyperledger.besu.ethereum.eth.transactions.layered;

import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ADDED;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ALREADY_KNOWN;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.REJECTED_UNDERPRICED_REPLACEMENT;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.TRY_NEXT_LAYER;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.INTERNAL_ERROR;

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
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;
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
    final var hereTx = pendingTransactions.get(transactionHash);
    if (hereTx == null) {
      return nextLayer.getByHash(transactionHash);
    }
    return Optional.of(hereTx.getTransaction());
  }

  @Override
  public boolean contains(final Transaction transaction) {
    return pendingTransactions.containsKey(transaction.getHash())
        || nextLayer.contains(transaction);
  }

  @Override
  public Set<PendingTransaction> getAll() {
    final var allTxs = nextLayer.getAll();
    allTxs.addAll(pendingTransactions.values());
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
    }
    updateMetrics(pendingTransaction, addStatus);
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
          nextLayer.promote(pendingTransaction.getSender(), pendingTransaction.getNonce());
      if (promotedTx != null) {
        processAdded(promotedTx);
        if (!maybeFull()) {
          tryFillGap(ADDED, promotedTx);
        }
      }
    }
  }

  @Override
  public PendingTransaction promote(final Address sender, final long nonce) {
    final var senderTxs = txsBySender.get(sender);
    if (senderTxs != null) {
      long expectedNonce = nonce + 1;
      if (senderTxs.firstKey() == expectedNonce) {
        final PendingTransaction promotedTx = senderTxs.pollFirstEntry().getValue();
        processRemove(senderTxs, promotedTx.getTransaction());

        if (senderTxs.isEmpty()) {
          txsBySender.remove(sender);
        }
        return promotedTx;
      }
    }
    return nextLayer.promote(sender, nonce);
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
    if (!senderTxs.isEmpty()) {
      nextLayerDistance = (int) (pendingTransaction.getNonce() - (senderTxs.lastKey() + 1));
    } else {
      nextLayerDistance = distance;
    }
    return nextLayer.add(pendingTransaction, nextLayerDistance);
  }

  private void processAdded(final PendingTransaction addedTx) {
    pendingTransactions.put(addedTx.getHash(), addedTx);
    final var senderTxs = txsBySender.computeIfAbsent(addedTx.getSender(), s -> new TreeMap<>());
    senderTxs.put(addedTx.getNonce(), addedTx);
    increaseSpaceUsed(addedTx);
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
        evictedSize += lastTx.getTransaction().getSize();

        // evicted can always be added to the next layer
        addToNextLayer(lessReadySenderTxs, lastTx, 0);
      }

      if (lessReadySenderTxs.isEmpty()) {
        txsBySender.remove(lessReadySender);
      }

      metrics.incrementEvicted(name(), evictedCount);

      final long newSpaceToFree = spaceToFree - evictedSize;
      final int newTxsToEvict = txsToEvict - evictedCount;

      if ((newSpaceToFree > 0 || newTxsToEvict > 0) && !txsBySender.isEmpty()) {
        // try next less valuable sender
        evict(newSpaceToFree, newTxsToEvict);
      }
    }
  }

  private void updateMetrics(
      final PendingTransaction pendingTransaction, final TransactionAddedResult result) {
    if (result.isSuccess()) {
      result
          .maybeReplacedTransaction()
          .ifPresent(
              replacedTx -> {
                metrics.incrementReplaced(replacedTx.isReceivedFromLocalSource(), name());
                metrics.incrementRemoved(
                    replacedTx.isReceivedFromLocalSource(), "replaced", name());
              });
      metrics.incrementAdded(pendingTransaction.isReceivedFromLocalSource(), name());
    } else {
      final var rejectReason =
          result
              .maybeInvalidReason()
              .orElseGet(
                  () -> {
                    LOG.warn("Missing invalid reason for status {}", result);
                    return INTERNAL_ERROR;
                  });
      metrics.incrementRejected(false, rejectReason, name());
      LOG.atTrace()
          .setMessage("Transaction {} rejected reason {}")
          .addArgument(pendingTransaction::toTraceLog)
          .addArgument(rejectReason)
          .log();
    }
  }

  private void replaced(final PendingTransaction replacedTx) {
    pendingTransactions.remove(replacedTx.getHash());
    decreaseSpaceUsed(replacedTx);
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
      final NavigableMap<Long, PendingTransaction> senderTxs, final Transaction transaction) {
    final PendingTransaction removedTx = pendingTransactions.remove(transaction.getHash());
    if (removedTx != null) {
      decreaseSpaceUsed(transaction);
      internalRemove(senderTxs, removedTx);
    }
    return removedTx;
  }

  protected PendingTransaction processEvict(
      final NavigableMap<Long, PendingTransaction> senderTxs, final PendingTransaction evictedTx) {
    final PendingTransaction removedTx = pendingTransactions.remove(evictedTx.getHash());
    if (removedTx != null) {
      decreaseSpaceUsed(evictedTx);
      internalEvict(senderTxs, removedTx);
    }
    return removedTx;
  }

  protected abstract void internalEvict(
      final NavigableMap<Long, PendingTransaction> lessReadySenderTxs,
      final PendingTransaction evictedTx);

  // ToDo: find a more efficient way to handle remove and gaps
  @Override
  public void remove(final PendingTransaction pendingTransaction) {
    nextLayer.remove(pendingTransaction);

    final var senderTxs = txsBySender.get(pendingTransaction.getSender());
    if (senderTxs != null) {
      if (gapsAllowed()) {
        // if gaps are allowed then just remove
        senderTxs.remove(pendingTransaction.getNonce());
        processRemove(senderTxs, pendingTransaction.getTransaction());
      } else {
        // on sequential layer we need to remove and push to next layer all the following txs
        final List<PendingTransaction> txsToRemove =
            new ArrayList<>(senderTxs.tailMap(pendingTransaction.getNonce(), true).values());
        final boolean skipFirst =
            !txsToRemove.isEmpty() && txsToRemove.get(0).equals(pendingTransaction);
        txsToRemove.stream()
            .peek(
                txToRemove -> {
                  senderTxs.remove(txToRemove.getNonce());
                  processRemove(senderTxs, txToRemove.getTransaction());
                })
            .skip(skipFirst ? 1 : 0)
            .forEach(followingTx -> nextLayer.add(followingTx, 1));
      }

      if (senderTxs.isEmpty()) {
        txsBySender.remove(pendingTransaction.getSender());
      }
    }
  }

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
        processRemove(senderTxs, confirmedTx.getTransaction());

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
      final PendingTransaction pendingTransaction);

  protected abstract PendingTransaction getEvictable();

  protected void increaseSpaceUsed(final PendingTransaction pendingTransaction) {
    spaceUsed += pendingTransaction.getTransaction().getSize();
  }

  protected void decreaseSpaceUsed(final PendingTransaction pendingTransaction) {
    decreaseSpaceUsed(pendingTransaction.getTransaction());
  }

  protected void decreaseSpaceUsed(final Transaction transaction) {
    spaceUsed -= transaction.getSize();
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

  protected abstract String internalLogStats();
}
