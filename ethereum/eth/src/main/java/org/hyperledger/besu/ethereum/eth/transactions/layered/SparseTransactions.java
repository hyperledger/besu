package org.hyperledger.besu.ethereum.eth.transactions.layered;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ADDED_SPARSE;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ALREADY_KNOWN;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.REJECTED_UNDERPRICED_REPLACEMENT;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.TX_POOL_FULL;

public class SparseTransactions extends AbstractLayeredTransactions {
    private static final Logger LOG = LoggerFactory.getLogger(SparseTransactions.class);

    private final TransactionPoolConfiguration poolConfig;
    private final BiFunction<PendingTransaction, PendingTransaction, Boolean>
            transactionReplacementTester;
    final TransactionPoolMetrics metrics;

//    private final Map<Hash, PendingTransaction> pendingTransactions = new HashMap<>();
//    private final Map<Address, NavigableMap<Long, PendingTransaction>> readyBySender =
//            new HashMap<>();
    private final NavigableSet<PendingTransaction> sparseEvictionOrder =
            new TreeSet<>(Comparator.comparing(PendingTransaction::getSequence));

    private long spaceUsed = 0;

    public SparseTransactions(
            final TransactionPoolConfiguration poolConfig,
            final TransactionPoolMetrics metrics,
            final BiFunction<PendingTransaction, PendingTransaction, Boolean>
                    transactionReplacementTester) {
        this.poolConfig = poolConfig;
        this.transactionReplacementTester = transactionReplacementTester;
        this.metrics = metrics;
        metrics.initSparseTransactionCount(sparseEvictionOrder::size);
    }

    void reset() {
       // readyBySender.clear();
        sparseEvictionOrder.clear();
       // spaceUsed = 0;
    }

    void removeConfirmedTransactions(final Map<Address, Optional<Long>> orderedConfirmedNonceBySender) {

    }

    private List<PendingTransaction> evictSparseTransactions(final long evictSize) {
        final List<PendingTransaction> evictedTxs = new ArrayList<>();
        long postponedSize = 0;
        while (postponedSize < evictSize && !readyBySender.isEmpty()) {
            final var oldestSparse = sparseEvictionOrder.pollFirst();
            pendingTransactions.remove(oldestSparse.getHash());
            evictedTxs.add(oldestSparse);
            decreaseSpaceUsed(oldestSparse);
            postponedSize += oldestSparse.getTransaction().getSize();

            readyBySender.compute(
                    oldestSparse.getSender(),
                    (sender, sparseTxs) -> {
                        sparseTxs.remove(oldestSparse.getNonce());
                        return sparseTxs.isEmpty() ? null : sparseTxs;
                    });
        }
        return evictedTxs;
    }

    private void decreaseSpaceUsed(final PendingTransaction pendingTransaction) {
        decreaseSpaceUsed(pendingTransaction.getTransaction());
    }

    private void decreaseSpaceUsed(final Transaction transaction) {
        spaceUsed -= transaction.getSize();
    }

    void sparseToReady(
            final Address sender,
            final NavigableMap<Long, PendingTransaction> senderReadyTxs,
            final long currLastNonce) {
        final var senderSparseTxs = readyBySender.get(sender);
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

    TransactionAddedResult tryAddToSparse(final PendingTransaction sparseTransaction) {
        // add if fits in cache or there are other sparse txs that we can evict
        // to make space for this one
        if (fitsInCache(sparseTransaction) || !readyBySender.isEmpty()) {
            final var senderSparseTxs =
                    readyBySender.computeIfAbsent(sparseTransaction.getSender(), sender -> new TreeMap<>());
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

    private boolean fitsInCache(final PendingTransaction pendingTransaction) {
        return spaceUsed + pendingTransaction.getTransaction().getSize()
                <= poolConfig.getPendingTransactionsMaxCapacityBytes();
    }

    synchronized int getSparseCount() {
        return readyBySender.values().stream().mapToInt(Map::size).sum();
    }
    void consistencyCheck() {
        final int sparseTotal = getSparseCount();
        if (sparseTotal != sparseEvictionOrder.size()) {
            LOG.error("Sparse != Eviction order ({} != {})", sparseTotal, sparseEvictionOrder.size());
        }

        sparseEvictionOrder.stream()
                .filter(
                        tx -> {
                            if (readyBySender.containsKey(tx.getSender())) {
                                if (readyBySender.get(tx.getSender()).containsKey(tx.getNonce())) {
                                    if (readyBySender.get(tx.getSender()).get(tx.getNonce()).equals(tx)) {
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

        readyBySender.values().stream()
                .flatMap(senderTxs -> senderTxs.values().stream())
                .filter(tx -> !pendingTransactions.containsKey(tx.getHash()))
                .forEach(tx -> LOG.error("SparseBySender tx {} not found in PendingTransactions", tx));
    }

    String logStats() {
        return "Space used:  "
                + spaceUsed
                + ", Sparse transactions: "
                + sparseEvictionOrder.size();
    }

    String toTraceLog() {
        return toTraceLog(readyBySender);
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
}
