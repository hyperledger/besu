/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.eth.transactions.sorter;

import static java.util.Comparator.comparing;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.util.number.Percentage;

import java.time.Clock;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Holds the current set of pending transactions with the ability to iterate them based on priority
 * for mining or look-up by hash.
 *
 * <p>This class is safe for use across multiple threads.
 */
public class GasPricePendingTransactionsSorter extends AbstractPendingTransactionsSorter {

  private final NavigableSet<TransactionInfo> prioritizedTransactions =
      new TreeSet<>(
          comparing(TransactionInfo::isReceivedFromLocalSource)
              .thenComparing(TransactionInfo::getGasPrice)
              .thenComparing(TransactionInfo::getSequence)
              .reversed());

  public GasPricePendingTransactionsSorter(
      final int maxTransactionRetentionHours,
      final int maxPendingTransactions,
      final Clock clock,
      final MetricsSystem metricsSystem,
      final Supplier<BlockHeader> chainHeadHeaderSupplier,
      final Percentage priceBump) {
    super(
        maxTransactionRetentionHours,
        maxPendingTransactions,
        clock,
        metricsSystem,
        chainHeadHeaderSupplier,
        priceBump);
  }

  @Override
  public void manageBlockAdded(final Block block) {
    // nothing to do
  }

  @Override
  protected void doRemoveTransaction(final Transaction transaction, final boolean addedToBlock) {
    synchronized (lock) {
      final TransactionInfo removedTransactionInfo =
          pendingTransactions.remove(transaction.getHash());
      if (removedTransactionInfo != null) {
        prioritizedTransactions.remove(removedTransactionInfo);
        removeTransactionTrackedBySenderAndNonce(transaction);
        incrementTransactionRemovedCounter(
            removedTransactionInfo.isReceivedFromLocalSource(), addedToBlock);
      }
    }
  }

  @Override
  protected Iterator<TransactionInfo> prioritizedTransactions() {
    return prioritizedTransactions.iterator();
  }

  @Override
  protected TransactionAddedStatus addTransaction(final TransactionInfo transactionInfo) {
    Optional<Transaction> droppedTransaction = Optional.empty();
    synchronized (lock) {
      if (pendingTransactions.containsKey(transactionInfo.getHash())) {
        return TransactionAddedStatus.ALREADY_KNOWN;
      }

      final TransactionAddedStatus transactionAddedStatus =
          addTransactionForSenderAndNonce(transactionInfo);
      if (!transactionAddedStatus.equals(TransactionAddedStatus.ADDED)) {
        return transactionAddedStatus;
      }
      prioritizedTransactions.add(transactionInfo);
      pendingTransactions.put(transactionInfo.getHash(), transactionInfo);

      if (pendingTransactions.size() > maxPendingTransactions) {
        final TransactionInfo toRemove = prioritizedTransactions.last();
        doRemoveTransaction(toRemove.getTransaction(), false);
        droppedTransaction = Optional.of(toRemove.getTransaction());
      }
    }
    notifyTransactionAdded(transactionInfo.getTransaction());
    droppedTransaction.ifPresent(this::notifyTransactionDropped);
    return TransactionAddedStatus.ADDED;
  }
}
