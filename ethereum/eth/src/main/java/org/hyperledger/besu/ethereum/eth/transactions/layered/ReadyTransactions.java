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

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;

import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class ReadyTransactions extends AbstractSequentialTransactionsLayer {

  private final NavigableSet<PendingTransaction> orderByMaxFee =
      new TreeSet<>(
          Comparator.comparing((PendingTransaction pt) -> pt.getTransaction().getMaxGasPrice())
              .thenComparing(PendingTransaction::getSequence));

  public ReadyTransactions(
      final TransactionPoolConfiguration poolConfig,
      final TransactionsLayer nextLayer,
      final TransactionPoolMetrics metrics,
      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
          transactionReplacementTester) {
    super(poolConfig, nextLayer, transactionReplacementTester, metrics);
    metrics.initPendingTransactionCount(pendingTransactions::size);
    metrics.initPendingTransactionSpace(this::getUsedSpace);
    metrics.initReadyTransactionCount(this::getReadyCount);
  }

  @Override
  public String name() {
    return "ready";
  }

  @Override
  public void reset() {
    super.reset();
    orderByMaxFee.clear();
  }

  @Override
  protected TransactionAddedResult canAdd(
      final PendingTransaction pendingTransaction, final int gap) {
    final var senderTxs = readyBySender.get(pendingTransaction.getSender());

    if (hasExpectedNonce(senderTxs, pendingTransaction, gap)) {
      return TransactionAddedResult.ADDED;
    }

    return TransactionAddedResult.TRY_NEXT_LAYER;
  }

  private boolean hasExpectedNonce(
      final NavigableMap<Long, PendingTransaction> senderTxs,
      final PendingTransaction pendingTransaction,
      final long gap) {
    if (senderTxs == null) {
      return gap == 0;
    }

    return (senderTxs.lastKey() + 1) == pendingTransaction.getNonce();
  }

  @Override
  protected void internalAdd(final PendingTransaction pendingTransaction) {
    if (readyBySender.get(pendingTransaction.getSender()).firstKey()
        == (pendingTransaction.getNonce())) {
      orderByMaxFee.add(pendingTransaction);
    }
  }

  @Override
  protected int maxTransactionsNumber() {
    return Integer.MAX_VALUE;
  }

  @Override
  protected void internalRemove(
      final NavigableMap<Long, PendingTransaction> senderTxs, final PendingTransaction removedTx) {
    if (senderTxs.isEmpty()) {
      orderByMaxFee.remove(removedTx);
    } else if (senderTxs.firstKey() == removedTx.getNonce() + 1) {
      orderByMaxFee.remove(removedTx);
      orderByMaxFee.add(senderTxs.firstEntry().getValue());
    }
  }

  @Override
  protected void internalReplaced(final PendingTransaction replacedTx) {
    orderByMaxFee.remove(replacedTx);
  }

  @Override
  protected void internalBlockAdded(final BlockHeader blockHeader, final FeeMarket feeMarket) {
    // no-op
  }

  @Override
  protected PendingTransaction getEvictable() {
    return orderByMaxFee.first();
  }

  @Override
  protected boolean promotionFilter(final PendingTransaction pendingTransaction) {
    return true;
  }

  @Override
  public Stream<PendingTransaction> stream() {
    return orderByMaxFee.stream()
        .map(PendingTransaction::getSender)
        .flatMap(sender -> readyBySender.get(sender).values().stream());
  }

  @Override
  public PendingTransaction promote(final Predicate<PendingTransaction> promotionFilter) {
    NavigableMap<Long, PendingTransaction> senderTxs;

    for (var tx : orderByMaxFee.descendingSet()) {
      PendingTransaction promotedTx = null;
      senderTxs = readyBySender.get(tx.getSender());

      if (promotionFilter.test(senderTxs.firstEntry().getValue())) {
        promotedTx = senderTxs.pollFirstEntry().getValue();
      }

      if (promotedTx != null) {
        processRemove(senderTxs, promotedTx.getTransaction());
        if (senderTxs.isEmpty()) {
          readyBySender.remove(promotedTx.getSender());
        }
        return promotedTx;
      }
    }
    return null;
  }
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

  private synchronized int getReadyCount() {
    return readyBySender.values().stream().mapToInt(Map::size).sum();
  }
  /*
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
  //*/
  //  private void prioritizedCheck() {
  //    prioritizedTransactions
  //        .prioritizedTransactions()
  //        .forEachRemaining(
  //            ptx -> {
  //              if (!pendingTransactions.containsKey(ptx.getHash())) {
  //                LOG.error("PrioritizedTransaction {} not found in PendingTransactions", ptx);
  //              }
  //
  //              if (!readyBySender.containsKey(ptx.getSender())
  //                  || !readyBySender.get(ptx.getSender()).containsKey(ptx.getNonce())
  //                  || !readyBySender.get(ptx.getSender()).get(ptx.getNonce()).equals(ptx)) {
  //                LOG.error("PrioritizedTransaction {} not found in ReadyBySender", ptx);
  //              }
  /// *
  //              if (sparseBySender.containsKey(ptx.getSender())
  //                  && sparseBySender.get(ptx.getSender()).containsKey(ptx.getNonce())
  //                  && sparseBySender.get(ptx.getSender()).get(ptx.getNonce()).equals(ptx)) {
  //                LOG.error("PrioritizedTransaction {} found in SparseBySender", ptx);
  //              }
  //
  // */
  //            });
  //
  //    prioritizedTransactions.consistencyCheck();
  //  }
}
