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
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedStatus;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.time.Clock;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the current set of pending transactions with the ability to iterate them based on priority
 * for mining or look-up by hash.
 *
 * <p>This class is safe for use across multiple threads.
 */
public class GasPricePendingTransactionsSorter extends AbstractPendingTransactionsSorter {
  private static final Logger LOG =
      LoggerFactory.getLogger(GasPricePendingTransactionsSorter.class);

  private final NavigableSet<PendingTransaction> prioritizedTransactions =
      new TreeSet<>(
          comparing(PendingTransaction::isReceivedFromLocalSource)
              .thenComparing(PendingTransaction::getGasPrice)
              .thenComparing(PendingTransaction::getAddedToPoolAt)
              .thenComparing(PendingTransaction::getSequence)
              .reversed());

  public GasPricePendingTransactionsSorter(
      final TransactionPoolConfiguration poolConfig,
      final Clock clock,
      final MetricsSystem metricsSystem,
      final Supplier<BlockHeader> chainHeadHeaderSupplier) {
    super(poolConfig, clock, metricsSystem, chainHeadHeaderSupplier);
  }

  @Override
  public void manageBlockAdded(final Block block) {
    // nothing to do
  }

  @Override
  protected void doRemoveTransaction(final Transaction transaction, final boolean addedToBlock) {
    synchronized (lock) {
      final PendingTransaction removedPendingTx = pendingTransactions.remove(transaction.getHash());
      if (removedPendingTx != null) {
        prioritizedTransactions.remove(removedPendingTx);
        removePendingTransactionBySenderAndNonce(removedPendingTx);
        incrementTransactionRemovedCounter(
            removedPendingTx.isReceivedFromLocalSource(), addedToBlock);
      }
    }
  }

  @Override
  protected Iterator<PendingTransaction> prioritizedTransactions() {
    return prioritizedTransactions.iterator();
  }

  @Override
  protected TransactionAddedStatus addTransaction(
      final PendingTransaction pendingTransaction, final Optional<Account> maybeSenderAccount) {
    Optional<Transaction> droppedTransaction = Optional.empty();
    synchronized (lock) {
      if (pendingTransactions.containsKey(pendingTransaction.getHash())) {
        return TransactionAddedStatus.ALREADY_KNOWN;
      }

      final TransactionAddedStatus transactionAddedStatus =
          addTransactionForSenderAndNonce(pendingTransaction, maybeSenderAccount);
      if (!transactionAddedStatus.equals(TransactionAddedStatus.ADDED)) {
        return transactionAddedStatus;
      }
      prioritizedTransactions.add(pendingTransaction);
      pendingTransactions.put(pendingTransaction.getHash(), pendingTransaction);

      // check if this sender exceeds the transactions by sender limit:
      var pendingTxsForSender = transactionsBySender.get(pendingTransaction.getSender());
      if (pendingTxsForSender.transactionCount()
          > poolConfig.getTxPoolMaxFutureTransactionByAccount()) {
        droppedTransaction =
            pendingTxsForSender
                .maybeLastPendingTransaction()
                .map(PendingTransaction::getTransaction);
        droppedTransaction.ifPresent(
            tx -> LOG.trace("Evicted {} due to too many transactions from sender", tx));
      } else {
        // else if we are over txpool limit, select the lowest value transaction to evict
        if (pendingTransactions.size() > poolConfig.getTxPoolMaxSize()) {
          droppedTransaction =
              lowestValueTxForRemovalBySender(prioritizedTransactions)
                  .map(PendingTransaction::getTransaction);
        }
      }
      droppedTransaction.ifPresent(tx -> doRemoveTransaction(tx, false));
    }
    notifyTransactionAdded(pendingTransaction.getTransaction());
    droppedTransaction.ifPresent(this::notifyTransactionDropped);
    return TransactionAddedStatus.ADDED;
  }
}
