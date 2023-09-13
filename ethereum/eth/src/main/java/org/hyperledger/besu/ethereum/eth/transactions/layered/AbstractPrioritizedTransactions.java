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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;

import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;

public abstract class AbstractPrioritizedTransactions extends AbstractSequentialTransactionsLayer {
  protected final TreeSet<PendingTransaction> orderByFee;

  public AbstractPrioritizedTransactions(
      final TransactionPoolConfiguration poolConfig,
      final TransactionsLayer prioritizedTransactions,
      final TransactionPoolMetrics metrics,
      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
          transactionReplacementTester) {
    super(poolConfig, prioritizedTransactions, transactionReplacementTester, metrics);
    this.orderByFee = new TreeSet<>(this::compareByFee);
  }

  @Override
  public void reset() {
    super.reset();
    orderByFee.clear();
  }

  @Override
  public String name() {
    return "prioritized";
  }

  @Override
  protected TransactionAddedResult canAdd(
      final PendingTransaction pendingTransaction, final int gap) {
    final var senderTxs = txsBySender.get(pendingTransaction.getSender());

    if (hasExpectedNonce(senderTxs, pendingTransaction, gap) && hasPriority(pendingTransaction)) {

      return TransactionAddedResult.ADDED;
    }

    return TransactionAddedResult.TRY_NEXT_LAYER;
  }

  @Override
  protected void internalAdd(
      final NavigableMap<Long, PendingTransaction> senderTxs, final PendingTransaction addedTx) {
    orderByFee.add(addedTx);
  }

  @Override
  protected void internalReplaced(final PendingTransaction replacedTx) {
    orderByFee.remove(replacedTx);
  }

  private boolean hasPriority(final PendingTransaction pendingTransaction) {
    if (orderByFee.size() < poolConfig.getMaxPrioritizedTransactions()) {
      return true;
    }
    return compareByFee(pendingTransaction, orderByFee.first()) > 0;
  }

  @Override
  protected int maxTransactionsNumber() {
    return poolConfig.getMaxPrioritizedTransactions();
  }

  @Override
  protected PendingTransaction getEvictable() {
    return orderByFee.first();
  }

  protected abstract int compareByFee(final PendingTransaction pt1, final PendingTransaction pt2);

  @Override
  protected void internalRemove(
      final NavigableMap<Long, PendingTransaction> senderTxs,
      final PendingTransaction removedTx,
      final RemovalReason removalReason) {
    orderByFee.remove(removedTx);
  }

  @Override
  public List<PendingTransaction> promote(
      final Predicate<PendingTransaction> promotionFilter, final long l, final int freeSlots) {
    return List.of();
  }

  @Override
  public Stream<PendingTransaction> stream() {
    return orderByFee.descendingSet().stream();
  }

  @Override
  protected long cacheFreeSpace() {
    return Integer.MAX_VALUE;
  }

  @Override
  protected void internalConsistencyCheck(
      final Map<Address, TreeMap<Long, PendingTransaction>> prevLayerTxsBySender) {
    super.internalConsistencyCheck(prevLayerTxsBySender);

    final var controlOrderByFee = new TreeSet<>(this::compareByFee);
    controlOrderByFee.addAll(pendingTransactions.values());

    final var itControl = controlOrderByFee.iterator();
    final var itCurrent = orderByFee.iterator();

    while (itControl.hasNext()) {
      assert itControl.next().equals(itCurrent.next())
          : "orderByFee does not match pendingTransactions";
    }

    assert itCurrent.hasNext() == false : "orderByFee has more elements that pendingTransactions";
  }
}
