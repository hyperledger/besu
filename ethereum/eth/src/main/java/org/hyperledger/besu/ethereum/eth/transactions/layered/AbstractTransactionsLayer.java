package org.hyperledger.besu.ethereum.eth.transactions.layered;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactionDroppedListener;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactionListener;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.util.Subscribers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.maxBy;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ALREADY_KNOWN;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.REJECTED_UNDERPRICED_REPLACEMENT;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.TX_POOL_FULL;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.INTERNAL_ERROR;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;

public abstract class AbstractTransactionsLayer {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractTransactionsLayer.class);

    protected final TransactionPoolConfiguration poolConfig;
    protected final AbstractTransactionsLayer nextLayer;
    protected final BiFunction<PendingTransaction, PendingTransaction, Boolean>
            transactionReplacementTester;
    protected final TransactionPoolMetrics metrics;


    protected final Map<Hash, PendingTransaction> pendingTransactions = new HashMap<>();
    protected final Map<Address, NavigableMap<Long, PendingTransaction>> readyBySender =
            new HashMap<>();
    protected long spaceUsed = 0;
    private final Subscribers<PendingTransactionListener> pendingTxsAddedSubscribers =
            Subscribers.create();
    private final Subscribers<PendingTransactionDroppedListener> pendingTxsDroppedListeners =
            Subscribers.create();

    public AbstractTransactionsLayer(final TransactionPoolConfiguration poolConfig, final AbstractTransactionsLayer nextLayer, final BiFunction<PendingTransaction, PendingTransaction, Boolean> transactionReplacementTester, final TransactionPoolMetrics metrics) {
        this.poolConfig = poolConfig;
        this.nextLayer = nextLayer;
        this.transactionReplacementTester = transactionReplacementTester;
        this.metrics = metrics;
    }

    public abstract String name();

    synchronized void reset() {
        pendingTransactions.clear();
        readyBySender.clear();
        spaceUsed = 0;
    }

    Optional<Transaction> getTransactionByHash(final Hash transactionHash) {
        return Optional.ofNullable(pendingTransactions.get(transactionHash))
                .map(PendingTransaction::getTransaction);
    }

    boolean containsTransaction(final Transaction transaction) {
      return pendingTransactions.containsKey(transaction.getHash());
    }

    synchronized Set<PendingTransaction> getPendingTransactions() {
      return new HashSet<>(pendingTransactions.values());
    }

    synchronized long getUsedSpace() {
      return spaceUsed;
    }

    TransactionAddedResult add(
            final PendingTransaction pendingTransaction, final long senderNonce) {

        // is replacing an existing one?
        TransactionAddedResult addStatus = maybeReplaceTransaction(pendingTransaction);
        if (addStatus != null) {
            addStatus.maybeReplacedTransaction().ifPresent(this::replaced);
        } else {
            addStatus = internalAdd(pendingTransaction, senderNonce);
        }

        if (addStatus.isSuccess()) {
            pendingTransactions.put(pendingTransaction.getHash(), pendingTransaction);
            readyBySender.computeIfAbsent(
                    pendingTransaction.getSender(), s -> new TreeMap<>())
                    .put(pendingTransaction.getNonce(), pendingTransaction);
            increaseSpaceUsed(pendingTransaction);

            // ToDo: evict by count, evict last

            var cacheFreeSpace = cacheFreeSpace();
            if (cacheFreeSpace < 0) {
                LOG.trace("Space full, need to free space {}", cacheFreeSpace);

                // free some space moving trying first to evict older sparse txs,
                // then less valuable ready to postponed
                final List<PendingTransaction> evictedReadyTxs = evict(-cacheFreeSpace);
                metrics.incrementEvicted("ready", evictedReadyTxs.size());
                evictedReadyTxs.forEach(this::notifyTransactionDropped);
                if (evictedReadyTxs.contains(pendingTransaction)) {
                    // in case the just added transaction is evicted to free space
                    // change the returned result
                    return TX_POOL_FULL;
                }
            }
            notifyTransactionAdded(pendingTransaction);

        }
        updateMetrics(pendingTransaction, addStatus);
        return addStatus;
    }

    protected List<PendingTransaction> evict(final long spaceToFree) {
        final var lessReadySender = getEvictable().getSender();
        final var lessReadySenderTxs = readyBySender.get(lessReadySender);

        final List<PendingTransaction> evictedTxs = new ArrayList<>();
        long evictedSize = 0;
        PendingTransaction lastTx = null;
        // lastTx must never be null, because the sender have at least the lessReadyTx
        while (evictedSize < spaceToFree && !lessReadySenderTxs.isEmpty()) {
            lastTx = lessReadySenderTxs.pollLastEntry().getValue();
            remove(lastTx);
         //   pendingTransactions.remove(lastTx.getHash());
            evictedTxs.add(lastTx);
         //   decreaseSpaceUsed(lastTx);
            evictedSize += lastTx.getTransaction().getSize();
        }

        if (lessReadySenderTxs.isEmpty()) {
            readyBySender.remove(lessReadySender);
            // at this point lastTx was the first for the sender, then remove it from eviction order too
            internalRemove(lastTx);

            if (!readyBySender.isEmpty()) {
                // try next less valuable sender
                evictedTxs.addAll(evict(spaceToFree - evictedSize));
            }
        }
        return evictedTxs;
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
                                        name());
                                metrics.incrementRemoved(replacedTx.isReceivedFromLocalSource(), "replaced", name());
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

    Optional<PendingTransaction> remove(final Transaction transaction) {
        final PendingTransaction removed = pendingTransactions.remove(transaction.getHash());
        if(removed != null) {
            decreaseSpaceUsed(transaction);
        }
        return Optional.ofNullable(removed);
    }
    void remove(final PendingTransaction pendingTransaction) {
        remove(pendingTransaction.getTransaction());
        // remove the transaction and move to the next layer any returned transaction
        nextLayer.fromPrevLayer(internalRemove(pendingTransaction));
    }

    void replaced(final PendingTransaction replacedTx) {
        remove(replacedTx.getTransaction());
        internalReplace(replacedTx);
    }

    protected abstract void internalReplace(final PendingTransaction replacedTx);

    final protected TransactionAddedResult maybeReplaceTransaction(final PendingTransaction incomingTx) {

        final var existingTxs = readyBySender.get(incomingTx.getSender());

        if(existingTxs != null) {
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

    void manageBlockAdded(
            final BlockHeader blockHeader,
            final List<Transaction> confirmedTransactions,
            final FeeMarket feeMarket) {
        debugLambda(LOG, "Managing new added block {}", blockHeader::toLogString);

        confirmed(confirmedTransactions);
        prioritizedTransactions.manageBlockAdded(blockHeader, feeMarket);
        prioritizeReadyTransactions();
    }

    private void confirmed(final Transaction confirmedTx) {
            remove(confirmedTx).ifPresentOrElse(confirmedPendingTx -> {
                metrics.incrementRemoved(confirmedPendingTx.isReceivedFromLocalSource(), "confirmed", name());
                innerConfirmed(confirmedPendingTx);
            }, () -> nextLayer.confirmed(confirmedTx));

        final var orderedConfirmedNonceBySender = maxConfirmedNonceBySender(confirmedTransactions);
        final var removedTransactions = removeConfirmed(orderedConfirmedNonceBySender);
        traceLambda(
                LOG,
                "Confirmed nonce by sender {}, removed pending transactions {}",
                orderedConfirmedNonceBySender::toString,
                () ->
                        removedTransactions.stream()
                                .map(PendingTransaction::toTraceLog)
                                .collect(Collectors.joining(", ")));

        removedTransactions.stream()
                .peek(pt -> metrics.incrementRemoved(pt.isReceivedFromLocalSource(), "confirmed", name()))
                .map(PendingTransaction::getHash)
                .forEach(pendingTransactions::remove);

        //sparseTransactions.removeConfirmedTransactions(orderedConfirmedNonceBySender);
        nextLayer.removeConfirmed(orderedConfirmedNonceBySender);

        innerRemoveConfirmed(orderedConfirmedNonceBySender, removedTransactions);
    }

    protected abstract void innerConfirmed(final PendingTransaction confirmedPendingTx);

    private Map<Address, Optional<Long>> maxConfirmedNonceBySender(
            final List<Transaction> confirmedTransactions) {
        return confirmedTransactions.stream()
                .collect(
                        groupingBy(
                                Transaction::getSender, mapping(Transaction::getNonce, maxBy(Long::compare))));
    }

    List<PendingTransaction> removeConfirmed(
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

    abstract void fromPrevLayer(final List<PendingTransaction> pendingTransactions);

    abstract void removeInvalid(final List<PendingTransaction> invalidTransactions);

    protected abstract List<PendingTransaction> internalRemove(final PendingTransaction pendingTransaction);
    protected abstract List<PendingTransaction> removeConfirmed(final Transaction transaction);

    protected abstract TransactionAddedResult internalAdd(final PendingTransaction pendingTransaction, final long senderNonce);

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

    private void notifyTransactionAdded(final PendingTransaction pendingTransaction) {
        pendingTxsAddedSubscribers.forEach(
                listener -> listener.onTransactionAdded(pendingTransaction.getTransaction()));
    }

    private void notifyTransactionDropped(final PendingTransaction pendingTransaction) {
        pendingTxsDroppedListeners.forEach(
                listener -> listener.onTransactionDropped(pendingTransaction.getTransaction()));
    }
}
