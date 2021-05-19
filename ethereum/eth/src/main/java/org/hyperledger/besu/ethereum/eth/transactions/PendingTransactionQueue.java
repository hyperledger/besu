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
package org.hyperledger.besu.ethereum.eth.transactions;

import static java.util.Comparator.comparing;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions.TransactionAddedStatus.ADDED;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions.TransactionAddedStatus.ALREADY_KNOWN;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions.TransactionAddedStatus.REJECTED_UNDERPRICED_REPLACEMENT;

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions.TransactionAddedStatus;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions.TransactionInfo;

import java.util.Collection;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public interface PendingTransactionQueue {

  Collection<TransactionInfo> getPendingTransactions();

  Collection<TransactionInfo> getPendingTransactionsByPriority();

  TransactionAddedStatus addTransaction(final TransactionInfo transactionInfo);

  Optional<TransactionInfo> removePendingTransaction(
      final Transaction txn, final Boolean addedToBlock);

  TransactionsForSenderInfo getTransactionsForSender(final Address address);

  void updateBaseFee(final long baseFee);

  boolean containsHash(final Hash txHash);

  TransactionInfo get(final Hash hash);

  int size();

  class BasicPriorityFeeQueue implements PendingTransactionQueue {

    private Optional<Long> baseFee;
    private final int maxPendingTransactions;
    private final Consumer<Transaction> onAddNotifier;

    // Consumer of <removed transaction, boolean added to a block or not>
    private final BiConsumer<TransactionInfo, Boolean> onRemoveNotifier;
    private final TransactionPoolReplacementHandler txnReplacementHandler;

    // transform TransactionInfo into its net miner fee per gas
    Function<TransactionInfo, Long> txPriority =
        txInfo -> {
          Transaction tx = txInfo.getTransaction();
          if (tx.getMaxFeePerGas().isPresent()) {
            // 1559:
            return Math.min(
                tx.getMaxFeePerGas().get().getValue().longValue() - baseFeeValue(),
                tx.getMaxPriorityFeePerGas().get().getValue().longValue());
          } else {
            // Legacy:
            return tx.getGasPrice().toLong() - baseFeeValue();
          }
        };

    Supplier<ConcurrentSkipListSet<TransactionInfo>> setSupplier =
        () ->
            new ConcurrentSkipListSet<>(
                comparing(TransactionInfo::isReceivedFromLocalSource)
                    .thenComparing(txPriority)
                    .thenComparing(TransactionInfo::getSequence)
                    .reversed());

    private ConcurrentSkipListSet<TransactionInfo> prioritizedTransactions = setSupplier.get();

    // read/write lock for the priority queue
    Lock writeLock = new ReentrantLock();

    BasicPriorityFeeQueue(
        final int maxPendingTransactions,
        final Consumer<Transaction> onAddNotifier,
        final BiConsumer<TransactionInfo, Boolean> onRemoveNotifier,
        final TransactionPoolReplacementHandler txnReplacementHandler,
        final Optional<Long> baseFee) {
      this.baseFee = baseFee;
      this.maxPendingTransactions = maxPendingTransactions;
      this.onAddNotifier = onAddNotifier;
      this.onRemoveNotifier = onRemoveNotifier;
      this.txnReplacementHandler = txnReplacementHandler;
    }

    @Override
    public Collection<TransactionInfo> getPendingTransactions() {
      return getPendingTransactionsByPriority();
    }

    @Override
    public Collection<TransactionInfo> getPendingTransactionsByPriority() {
      return prioritizedTransactions.clone();
    }

    @Override
    public Optional<TransactionInfo> removePendingTransaction(
        final Transaction txn, final Boolean addedToBlock) {
      // O(n)
      Optional<TransactionInfo> result =
          prioritizedTransactions.stream()
              .filter(txInfo -> txInfo.getTransaction().equals(txn))
              .findFirst();

      result.ifPresent(
          txi -> {
            doWithWriteLock(() -> prioritizedTransactions.remove(txi));
            onRemoveNotifier.accept(txi, addedToBlock);
          });
      return result;
    }

    @Override
    public TransactionAddedStatus addTransaction(final TransactionInfo transactionInfo) {
      if (containsHash(transactionInfo.getHash())) {
        return ALREADY_KNOWN;
      }

      Optional<TransactionAddedStatus> replaceResult = maybeReplace(transactionInfo);
      if (replaceResult.isPresent()) {
        return replaceResult.get();
      }

      prioritizedTransactions.add(transactionInfo);
      if (size() > maxPendingTransactions) {
        final TransactionInfo toRemove = prioritizedTransactions.last();
        removePendingTransaction(toRemove.getTransaction(), false);
      }

      onAddNotifier.accept(transactionInfo.getTransaction());
      return ADDED;
    }

    private Optional<TransactionAddedStatus> maybeReplace(final TransactionInfo txInfo) {
      final TransactionInfo existingTransaction =
          getTransactionsForSender(txInfo.getSender())
              .getTransactionInfoForNonce(txInfo.getNonce());
      Optional<TransactionAddedStatus> addStatus = Optional.empty();
      if (existingTransaction != null) {
        if (!txnReplacementHandler.shouldReplace(existingTransaction, txInfo, baseFee)) {
          return Optional.of(REJECTED_UNDERPRICED_REPLACEMENT);
        }
        removePendingTransaction(existingTransaction.getTransaction(), false);
      }
      doWithWriteLock(() -> prioritizedTransactions.add(txInfo));
      return addStatus;
    }

    @Override
    public TransactionsForSenderInfo getTransactionsForSender(final Address address) {
      // O(n)
      TransactionsForSenderInfo txsForSender = new TransactionsForSenderInfo();
      prioritizedTransactions.stream()
          .filter(t -> t.getSender().equals(address))
          .sorted(comparing(TransactionInfo::getNonce))
          .forEach(txsForSender::addTransactionToTrack);
      return txsForSender;
    }

    private Long baseFeeValue() {
      return baseFee.orElse(0L);
    }

    @Override
    public void updateBaseFee(final long newBaseFee) {
      if (baseFeeValue() != newBaseFee) {
        // update baseFee
        this.baseFee = Optional.of(newBaseFee);
        // rebuild the prioritized transaction set
        doWithWriteLock(
            () -> {
              NavigableSet<TransactionInfo> oldTransactions = prioritizedTransactions;
              prioritizedTransactions = setSupplier.get();
              prioritizedTransactions.addAll(oldTransactions);
            });
      }
    }

    @Override
    public boolean containsHash(final Hash txHash) {
      return get(txHash) != null;
    }

    @Override
    public TransactionInfo get(final Hash txHash) {
      // O(n)
      return prioritizedTransactions.stream()
          .filter(txInfo -> txInfo.getHash().equals(txHash))
          .findFirst()
          .orElse(null);
    }

    @Override
    public int size() {
      return prioritizedTransactions.size();
    }

    private void doWithWriteLock(final Runnable r) {
      writeLock.lock();
      try {
        r.run();
      } finally {
        writeLock.unlock();
      }
    }
  }
}
