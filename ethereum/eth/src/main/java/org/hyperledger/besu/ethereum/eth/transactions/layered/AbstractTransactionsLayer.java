package org.hyperledger.besu.ethereum.eth.transactions.layered;

import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ALREADY_KNOWN;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.REJECTED_UNDERPRICED_REPLACEMENT;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.TRY_NEXT_LAYER;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.INTERNAL_ERROR;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractTransactionsLayer extends BaseTransactionsLayer {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractTransactionsLayer.class);
  private static final NavigableMap<Long, PendingTransaction> EMPTY_SENDER_TXS = new TreeMap<>();
  protected final TransactionPoolConfiguration poolConfig;
  protected final TransactionsLayer nextLayer;
  protected final BiFunction<PendingTransaction, PendingTransaction, Boolean>
      transactionReplacementTester;
  protected final TransactionPoolMetrics metrics;
  protected final Map<Hash, PendingTransaction> pendingTransactions = new HashMap<>();
  protected final Map<Address, NavigableMap<Long, PendingTransaction>> readyBySender =
      new HashMap<>();
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
    readyBySender.clear();
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

  protected long getUsedSpace() {
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

      final long cacheFreeSpace = cacheFreeSpace();
      final int overflowTxsCount = pendingTransactions.size() - maxTransactionsNumber();
      if (cacheFreeSpace < 0 || overflowTxsCount > 0) {
        debugLambda(
            LOG,
            "Layer full: {}",
            () ->
                cacheFreeSpace < 0
                    ? "need to free " + (-cacheFreeSpace) + " space"
                    : "need to evict " + overflowTxsCount + " transaction(s)");

        evict(-cacheFreeSpace, overflowTxsCount);
      }

      notifyTransactionAdded(pendingTransaction);
    }
    updateMetrics(pendingTransaction, addStatus);
    return addStatus;
  }

  private TransactionAddedResult addToNextLayer(
      final PendingTransaction pendingTransaction, final int distance) {
    final var senderTxs = readyBySender.get(pendingTransaction.getSender());
    final int nextLayerDistance;
    if (senderTxs != null) {
      nextLayerDistance = (int) (pendingTransaction.getNonce() - (senderTxs.lastKey() + 1));
    } else {
      nextLayerDistance = distance;
    }
    return nextLayer.add(pendingTransaction, nextLayerDistance);
  }

  private void processAdded(final PendingTransaction addedTx) {
    pendingTransactions.put(addedTx.getHash(), addedTx);
    readyBySender
        .computeIfAbsent(addedTx.getSender(), s -> new TreeMap<>())
        .put(addedTx.getNonce(), addedTx);
    increaseSpaceUsed(addedTx);
    internalAdd(addedTx);
  }

  protected abstract void internalAdd(final PendingTransaction addedTx);

  protected abstract int maxTransactionsNumber();

  private void evict(final long spaceToFree, final int txsToEvict) {
    final var evictableTx = getEvictable();
    if (evictableTx != null) {
      final var lessReadySender = evictableTx.getSender();
      final var lessReadySenderTxs = readyBySender.get(lessReadySender);

      final List<PendingTransaction> evictedTxs = new ArrayList<>();
      long evictedSize = 0;
      PendingTransaction lastTx;
      // lastTx must never be null, because the sender have at least the lessReadyTx
      while ((evictedSize < spaceToFree || txsToEvict > evictedTxs.size())
          && !lessReadySenderTxs.isEmpty()) {
        lastTx = lessReadySenderTxs.pollLastEntry().getValue();
        evictLast(lessReadySenderTxs, lastTx);
        evictedTxs.add(lastTx);
        evictedSize += lastTx.getTransaction().getSize();

        // evicted can always be added to the next layer
        addToNextLayer(lastTx, 0);

        metrics.incrementEvicted(name(), 1);
      }

      if (lessReadySenderTxs.isEmpty()) {
        readyBySender.remove(lessReadySender);
      }

      final long newSpaceToFree = spaceToFree - evictedSize;
      final int newTxsToEvict = txsToEvict - evictedTxs.size();

      if ((newSpaceToFree > 0 || newTxsToEvict > 0) && !readyBySender.isEmpty()) {
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
      traceLambda(
          LOG,
          "Transaction {} rejected reason {}",
          pendingTransaction::toTraceLog,
          rejectReason::toString);
    }
  }

  private void replaced(final PendingTransaction replacedTx) {
    pendingTransactions.remove(replacedTx.getHash());
    decreaseSpaceUsed(replacedTx);
    internalReplaced(replacedTx);
  }

  protected abstract void internalReplaced(final PendingTransaction replacedTx);

  private TransactionAddedResult maybeReplaceTransaction(final PendingTransaction incomingTx) {

    final var existingTxs = readyBySender.get(incomingTx.getSender());

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

  private void evictLast(
      final NavigableMap<Long, PendingTransaction> senderTxs, final PendingTransaction evictedTx) {
    internalEvict(senderTxs, evictedTx);
    nextLayer.add(evictedTx, 0);
  }

  protected abstract void internalEvict(
      final NavigableMap<Long, PendingTransaction> lessReadySenderTxs,
      final PendingTransaction evictedTx);

  // ToDo: find a more efficient way to handle remove and gaps
  @Override
  public void remove(final PendingTransaction pendingTransaction) {
    final var senderTxs = readyBySender.get(pendingTransaction.getSender());
    if (senderTxs != null) {
      if (gapsAllowed()) {
        // if gaps are allowed then just remove
        processRemove(senderTxs, pendingTransaction.getTransaction());
      } else {
        // on sequential layer we need to remove and push to next layer all the following txs
        final var txsToRemove = senderTxs.tailMap(pendingTransaction.getNonce(), true);
        final var followingTxs =
            txsToRemove.values().stream()
                .peek(pt -> processRemove(senderTxs, pt.getTransaction()))
                .toList();

        txsToRemove.clear();

        final int distance;
        if (senderTxs.isEmpty()) {
          distance = 0;
        } else {
          distance = (int) (pendingTransaction.getNonce() - senderTxs.lastKey());
        }

        followingTxs.forEach(followingTx -> nextLayer.add(followingTx, distance));
      }

      if (senderTxs.isEmpty()) {
        readyBySender.remove(pendingTransaction.getSender());
      }
    }

    nextLayer.remove(pendingTransaction);
  }

  @Override
  public void removeConfirmed(final Map<Address, Long> maxConfirmedNonceBySender) {
    nextLayer.removeConfirmed(maxConfirmedNonceBySender);
    maxConfirmedNonceBySender.forEach(this::confirmed);
  }

  @Override
  public final void blockAdded(final BlockHeader blockHeader, final FeeMarket feeMarket) {
    debugLambda(LOG, "Managing new added block {}", blockHeader::toLogString);

    nextLayer.blockAdded(blockHeader, feeMarket);

    internalBlockAdded(blockHeader, feeMarket);
    promoteTransactions();
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
        return;
      }
    }
  }

  private void confirmed(final Address sender, final long maxConfirmedNonce) {
    final var senderTxs = readyBySender.get(sender);
    if (senderTxs != null) {
      final var confirmedTxs = senderTxs.headMap(maxConfirmedNonce, true);
      confirmedTxs.values().stream()
          .map(pt -> processRemove(senderTxs, pt.getTransaction()))
          .forEach(
              removedTx -> {
                traceLambda(
                    LOG, "Removed confirmed pending transactions {}", removedTx::toTraceLog);
                metrics.incrementRemoved(
                    removedTx.isReceivedFromLocalSource(), "confirmed", name());
              });

      confirmedTxs.clear();

      if (senderTxs.isEmpty()) {
        readyBySender.remove(sender);
      }
      internalConfirmed(senderTxs, sender, maxConfirmedNonce);
    }
  }

  protected abstract void internalConfirmed(
      final NavigableMap<Long, PendingTransaction> senderTxs,
      final Address sender,
      final long maxConfirmedNonce);

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

  protected long cacheFreeSpace() {
    return poolConfig.getPendingTransactionsMaxCapacityBytes() - getUsedSpace();
  }

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
    return readyBySender.getOrDefault(sender, EMPTY_SENDER_TXS).values().stream();
  }

  abstract Stream<PendingTransaction> stream();

  @Override
  public int count() {
    return pendingTransactions.size() + nextLayer.count();
  }
}
