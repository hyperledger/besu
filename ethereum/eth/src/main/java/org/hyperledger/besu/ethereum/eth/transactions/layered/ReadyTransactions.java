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

import static org.hyperledger.besu.ethereum.eth.transactions.layered.TransactionsLayer.RemovalReason.PROMOTED;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.BlobCache;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ReadyTransactions extends AbstractSequentialTransactionsLayer {

  private final NavigableSet<PendingTransaction> orderByMaxFee =
      new TreeSet<>(
          Comparator.comparing(PendingTransaction::hasPriority)
              .thenComparing((PendingTransaction pt) -> pt.getTransaction().getMaxGasPrice())
              .thenComparing(PendingTransaction::getSequence));

  public ReadyTransactions(
      final TransactionPoolConfiguration poolConfig,
      final TransactionsLayer nextLayer,
      final TransactionPoolMetrics metrics,
      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
          transactionReplacementTester,
      final BlobCache blobCache) {
    super(poolConfig, nextLayer, transactionReplacementTester, metrics, blobCache);
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
  protected long cacheFreeSpace() {
    return poolConfig.getPendingTransactionsLayerMaxCapacityBytes() - getLayerSpaceUsed();
  }

  @Override
  protected TransactionAddedResult canAdd(
      final PendingTransaction pendingTransaction, final int gap) {
    final var senderTxs = txsBySender.get(pendingTransaction.getSender());

    if (hasExpectedNonce(senderTxs, pendingTransaction, gap)) {
      return TransactionAddedResult.ADDED;
    }

    return TransactionAddedResult.TRY_NEXT_LAYER;
  }

  @Override
  protected void internalAdd(
      final NavigableMap<Long, PendingTransaction> senderTxs,
      final PendingTransaction pendingTransaction) {
    if (senderTxs.firstKey() == pendingTransaction.getNonce()) {
      // replace previous if exists
      if (senderTxs.size() > 1) {
        final PendingTransaction secondTx = senderTxs.get(pendingTransaction.getNonce() + 1);
        orderByMaxFee.remove(secondTx);
      }
      orderByMaxFee.add(pendingTransaction);
    }
  }

  @Override
  protected int maxTransactionsNumber() {
    return Integer.MAX_VALUE;
  }

  @Override
  protected void internalRemove(
      final NavigableMap<Long, PendingTransaction> senderTxs,
      final PendingTransaction removedTx,
      final RemovalReason removalReason) {
    orderByMaxFee.remove(removedTx);
    if (!senderTxs.isEmpty()) {
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
    return orderByMaxFee.descendingSet().stream()
        .map(PendingTransaction::getSender)
        .flatMap(sender -> txsBySender.get(sender).values().stream());
  }

  @Override
  public List<PendingTransaction> promote(
      final Predicate<PendingTransaction> promotionFilter,
      final long freeSpace,
      final int freeSlots) {
    long accumulatedSpace = 0;
    final List<PendingTransaction> promotedTxs = new ArrayList<>();

    // first find all txs that can be promoted
    search:
    for (final var senderFirstTx : orderByMaxFee.descendingSet()) {
      final var senderTxs = txsBySender.get(senderFirstTx.getSender());
      for (final var candidateTx : senderTxs.values()) {
        if (promotionFilter.test(candidateTx)) {
          accumulatedSpace += candidateTx.memorySize();
          if (promotedTxs.size() < freeSlots && accumulatedSpace <= freeSpace) {
            promotedTxs.add(candidateTx);
          } else {
            // no room for more txs the search is over exit the loops
            break search;
          }
        } else {
          // skip remaining txs for this sender to avoid gaps
          break;
        }
      }
    }

    // then remove promoted txs from this layer
    promotedTxs.forEach(
        promotedTx -> {
          final var sender = promotedTx.getSender();
          final var senderTxs = txsBySender.get(sender);
          senderTxs.remove(promotedTx.getNonce());
          processRemove(senderTxs, promotedTx.getTransaction(), PROMOTED);
          if (senderTxs.isEmpty()) {
            txsBySender.remove(sender);
          }
        });

    if (!promotedTxs.isEmpty()) {
      // since we removed some txs we can try to promote from next layer
      promoteTransactions();
    }

    return promotedTxs;
  }

  @Override
  public String internalLogStats() {
    if (orderByMaxFee.isEmpty()) {
      return "Ready: Empty";
    }

    final Transaction top = orderByMaxFee.last().getTransaction();
    final Transaction last = orderByMaxFee.first().getTransaction();

    return "Ready: "
        + "count="
        + pendingTransactions.size()
        + ", space used: "
        + spaceUsed
        + ", unique senders: "
        + txsBySender.size()
        + ", top by max fee[max fee:"
        + top.getMaxGasPrice().toHumanReadableString()
        + ", hash: "
        + top.getHash()
        + "], last by max fee [max fee: "
        + last.getMaxGasPrice().toHumanReadableString()
        + ", hash: "
        + last.getHash()
        + "]";
  }

  @Override
  protected void internalConsistencyCheck(
      final Map<Address, TreeMap<Long, PendingTransaction>> prevLayerTxsBySender) {
    super.internalConsistencyCheck(prevLayerTxsBySender);

    final var minNonceBySender =
        pendingTransactions.values().stream()
            .collect(
                Collectors.groupingBy(
                    PendingTransaction::getSender,
                    Collectors.minBy(Comparator.comparingLong(PendingTransaction::getNonce))));

    final var controlOrderByMaxFee = new TreeSet<>(orderByMaxFee.comparator());
    controlOrderByMaxFee.addAll(minNonceBySender.values().stream().map(Optional::get).toList());

    final var itControl = controlOrderByMaxFee.iterator();
    final var itCurrent = orderByMaxFee.iterator();

    while (itControl.hasNext()) {
      assert itControl.next().equals(itCurrent.next())
          : "orderByMaxFee does not match pendingTransactions";
    }

    assert itCurrent.hasNext() == false
        : "orderByMaxFee has more elements than pendingTransactions";
  }
}
