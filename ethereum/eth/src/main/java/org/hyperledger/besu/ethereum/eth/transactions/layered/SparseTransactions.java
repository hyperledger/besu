package org.hyperledger.besu.ethereum.eth.transactions.layered;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class SparseTransactions extends AbstractTransactionsLayer {
  //  private static final Logger LOG = LoggerFactory.getLogger(SparseTransactions.class);
  private final NavigableSet<PendingTransaction> sparseEvictionOrder =
      new TreeSet<>(Comparator.comparing(PendingTransaction::getSequence));
  private final Map<Address, Integer> gapBySender = new HashMap<>();
  private final List<Set<Address>> orderByGap;

  public SparseTransactions(
      final TransactionPoolConfiguration poolConfig,
      final TransactionsLayer nextLayer,
      final TransactionPoolMetrics metrics,
      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
          transactionReplacementTester) {
    super(poolConfig, nextLayer, transactionReplacementTester, metrics);
    orderByGap = new ArrayList<>(poolConfig.getMaxFutureBySender());
    IntStream.range(0, poolConfig.getMaxFutureBySender())
        .forEach(i -> orderByGap.add(new HashSet<>()));
    metrics.initSparseTransactionCount(sparseEvictionOrder::size);
  }

  @Override
  public String name() {
    return "sparse";
  }

  @Override
  protected boolean gapsAllowed() {
    return true;
  }

  @Override
  public void reset() {
    super.reset();
    sparseEvictionOrder.clear();
    gapBySender.clear();
    orderByGap.forEach(senders -> senders.clear());
  }

  @Override
  protected TransactionAddedResult canAdd(
      final PendingTransaction pendingTransaction, final int gap) {
    gapBySender.compute(
        pendingTransaction.getSender(),
        (sender, currGap) -> {
          if (currGap == null) {
            orderByGap.get(gap).add(sender);
            return gap;
          }
          if (gap < currGap) {
            orderByGap.get(currGap).remove(sender);
            orderByGap.get(gap).add(sender);
            return gap;
          }
          return currGap;
        });

    return TransactionAddedResult.ADDED;
  }

  @Override
  protected void internalAdd(final PendingTransaction addedTx) {
    sparseEvictionOrder.add(addedTx);
  }

  @Override
  protected int maxTransactionsNumber() {
    return Integer.MAX_VALUE;
  }

  @Override
  protected void internalReplaced(final PendingTransaction replacedTx) {
    sparseEvictionOrder.remove(replacedTx);
  }

  @Override
  protected void internalBlockAdded(final BlockHeader blockHeader, final FeeMarket feeMarket) {}

  @Override
  public PendingTransaction promote(final Predicate<PendingTransaction> promotionFilter) {
    final PendingTransaction promotedTx =
        orderByGap.get(0).stream()
            .map(readyBySender::get)
            .map(NavigableMap::values)
            .flatMap(Collection::stream)
            .filter(promotionFilter)
            .findFirst()
            .orElse(null);

    if (promotedTx != null) {
      final Address sender = promotedTx.getSender();
      final var senderTxs = readyBySender.get(sender);
      senderTxs.pollFirstEntry();
      processRemove(senderTxs, promotedTx.getTransaction());
      if (senderTxs.isEmpty()) {
        readyBySender.remove(sender);
        orderByGap.get(0).remove(sender);
        gapBySender.remove(sender);
      } else {
        final long firstNonce = senderTxs.firstKey();
        final int newGap = (int) (firstNonce - (promotedTx.getNonce() + 1));
        if (newGap != 0) {
          orderByGap.get(0).remove(sender);
          orderByGap.get(newGap).add(sender);
          gapBySender.put(sender, newGap);
        }
      }
    }

    return promotedTx;
  }

  @Override
  protected void internalConfirmed(
      final NavigableMap<Long, PendingTransaction> senderTxs,
      final Address sender,
      final long maxConfirmedNonce,
      final PendingTransaction highestNonceRemovedTx) {

    if (highestNonceRemovedTx != null) {
      final int currGap = gapBySender.get(sender);
      final int newGap = (int) (senderTxs.firstKey() - (highestNonceRemovedTx.getNonce() + 1));
      if (currGap != newGap) {
        gapBySender.put(sender, newGap);
        orderByGap.get(currGap).remove(sender);
        orderByGap.get(newGap).add(sender);
      }
    } else {
      final int currGap = gapBySender.get(sender);
      final int newGap = (int) (senderTxs.firstKey() - (maxConfirmedNonce + 1));
      if (newGap < currGap) {
        gapBySender.put(sender, newGap);
        orderByGap.get(currGap).remove(sender);
        orderByGap.get(newGap).add(sender);
      }
    }
  }

  @Override
  protected void internalEvict(
      final NavigableMap<Long, PendingTransaction> lessReadySenderTxs,
      final PendingTransaction evictedTx) {
    sparseEvictionOrder.remove(evictedTx);

    if (lessReadySenderTxs.isEmpty()) {
      orderByGap.get(gapBySender.get(evictedTx.getSender())).remove(evictedTx.getSender());
    }
  }

  @Override
  protected void internalRemove(
      final NavigableMap<Long, PendingTransaction> senderTxs, final PendingTransaction removedTx) {

    sparseEvictionOrder.remove(removedTx);

    final Address sender = removedTx.getSender();

    if (senderTxs != null && !senderTxs.isEmpty()) {
      if (senderTxs.firstKey() > removedTx.getNonce()) {
        final int currGap = gapBySender.get(sender);
        final int newGap = (int) (senderTxs.firstKey() - (removedTx.getNonce() + 1));
        if (currGap != newGap) {
          orderByGap.get(currGap).remove(sender);
          orderByGap.get(newGap).add(sender);
          gapBySender.put(sender, newGap);
        }
      }
    } else {
      final int gap = gapBySender.remove(sender);
      orderByGap.get(gap).remove(sender);
    }
  }

  @Override
  protected PendingTransaction getEvictable() {
    return sparseEvictionOrder.first();
  }

  @Override
  protected boolean promotionFilter(final PendingTransaction pendingTransaction) {
    return false;
  }

  @Override
  public Stream<PendingTransaction> stream() {
    return sparseEvictionOrder.descendingSet().stream();
  }

  @Override
  public OptionalLong getNextNonceFor(final Address sender) {
    final Integer gap = gapBySender.get(sender);
    if (gap != null && gap == 0) {
      final var senderTxs = readyBySender.get(sender);
      var currNonce = senderTxs.firstKey();
      for (final var nextNonce : senderTxs.keySet()) {
        if (nextNonce > currNonce + 1) {
          break;
        }
        currNonce = nextNonce;
      }
      return OptionalLong.of(currNonce);
    }
    return OptionalLong.empty();
  }

  //  synchronized int getSparseCount() {
  //    return readyBySender.values().stream().mapToInt(Map::size).sum();
  //  }
  //
  //  void consistencyCheck() {
  //    final int sparseTotal = getSparseCount();
  //    if (sparseTotal != sparseEvictionOrder.size()) {
  //      LOG.error("Sparse != Eviction order ({} != {})", sparseTotal, sparseEvictionOrder.size());
  //    }
  //
  //    sparseEvictionOrder.stream()
  //        .filter(
  //            tx -> {
  //              if (readyBySender.containsKey(tx.getSender())) {
  //                if (readyBySender.get(tx.getSender()).containsKey(tx.getNonce())) {
  //                  if (readyBySender.get(tx.getSender()).get(tx.getNonce()).equals(tx)) {
  //                    return false;
  //                  }
  //                }
  //              }
  //              return true;
  //            })
  //        .forEach(tx -> LOG.error("SparseEvictionOrder tx {} not found in SparseBySender", tx));
  //
  //    sparseEvictionOrder.stream()
  //        .filter(tx -> !pendingTransactions.containsKey(tx.getHash()))
  //        .forEach(tx -> LOG.error("SparseEvictionOrder tx {} not found in PendingTransactions",
  // tx));
  //
  //    readyBySender.values().stream()
  //        .flatMap(senderTxs -> senderTxs.values().stream())
  //        .filter(tx -> !pendingTransactions.containsKey(tx.getHash()))
  //        .forEach(tx -> LOG.error("SparseBySender tx {} not found in PendingTransactions", tx));
  //  }
  //
  //  String logStats() {
  //    return "Space used:  " + spaceUsed + ", Sparse transactions: " + sparseEvictionOrder.size();
  //  }
  //
  //  String toTraceLog() {
  //    return toTraceLog(readyBySender);
  //  }
  //
  //  private String toTraceLog(final Map<Address, NavigableMap<Long, PendingTransaction>>
  // senderTxs) {
  //    return senderTxs.entrySet().stream()
  //        .map(
  //            e ->
  //                e.getKey()
  //                    + "="
  //                    + e.getValue().entrySet().stream()
  //                        .map(etx -> etx.getKey() + ":" + etx.getValue().toTraceLog())
  //                        .collect(Collectors.joining(",", "[", "]")))
  //        .collect(Collectors.joining(";"));
  //  }
}
