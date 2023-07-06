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
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;

import java.util.Comparator;
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
          Comparator.comparing((PendingTransaction pt) -> pt.getTransaction().getMaxGasPrice())
              .thenComparing(PendingTransaction::getSequence));

  public ReadyTransactions(
      final TransactionPoolConfiguration poolConfig,
      final TransactionsLayer nextLayer,
      final TransactionPoolMetrics metrics,
      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
          transactionReplacementTester) {
    super(poolConfig, nextLayer, transactionReplacementTester, metrics);
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
  public PendingTransaction promote(final Predicate<PendingTransaction> promotionFilter) {

    final var maybePromotedTx =
        orderByMaxFee.descendingSet().stream()
            .filter(candidateTx -> promotionFilter.test(candidateTx))
            .findFirst();

    return maybePromotedTx
        .map(
            promotedTx -> {
              final var senderTxs = txsBySender.get(promotedTx.getSender());
              // we always promote the first tx of a sender, so remove the first entry
              senderTxs.pollFirstEntry();
              processRemove(senderTxs, promotedTx.getTransaction(), PROMOTED);

              // now that we have space, promote from the next layer
              promoteTransactions();

              if (senderTxs.isEmpty()) {
                txsBySender.remove(promotedTx.getSender());
              }
              return promotedTx;
            })
        .orElse(null);
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
