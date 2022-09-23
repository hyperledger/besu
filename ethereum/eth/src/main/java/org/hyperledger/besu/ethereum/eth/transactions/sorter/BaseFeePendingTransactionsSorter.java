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
import static java.util.stream.Collectors.toUnmodifiableList;
import static org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter.TransactionAddedStatus.ADDED;
import static org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter.TransactionAddedStatus.ALREADY_KNOWN;
import static org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter.TransactionAddedStatus.NONCE_TOO_FAR_IN_FUTURE_FOR_SENDER;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionsForSenderInfo;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountState;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.time.Clock;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.google.errorprone.annotations.Keep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the current set of pending transactions with the ability to iterate them based on priority
 * for mining or look-up by hash.
 *
 * <p>This class is safe for use across multiple threads.
 */
public class BaseFeePendingTransactionsSorter extends AbstractPendingTransactionsSorter {

  private static final Logger LOG = LoggerFactory.getLogger(BaseFeePendingTransactionsSorter.class);

  private Optional<Wei> baseFee;

  public BaseFeePendingTransactionsSorter(
      final TransactionPoolConfiguration poolConfig,
      final Clock clock,
      final MetricsSystem metricsSystem,
      final Supplier<BlockHeader> chainHeadHeaderSupplier) {
    super(poolConfig, clock, metricsSystem, chainHeadHeaderSupplier);
    this.baseFee = chainHeadHeaderSupplier.get().getBaseFee();
  }

  /**
   * See this post for an explainer about these data structures:
   * https://hackmd.io/@adietrichs/1559-transaction-sorting
   */
  private final NavigableSet<TransactionInfo> prioritizedTransactionsStaticRange =
      new TreeSet<>(
          comparing(TransactionInfo::isReceivedFromLocalSource)
              .thenComparing(
                  transactionInfo ->
                      transactionInfo
                          .getTransaction()
                          .getMaxPriorityFeePerGas()
                          // just in case we attempt to compare non-1559 transaction
                          .orElse(Wei.ZERO)
                          .getAsBigInteger()
                          .longValue())
              .thenComparing(TransactionInfo::getAddedToPoolAt)
              .thenComparing(TransactionInfo::getSequence)
              .reversed());

  private final NavigableSet<TransactionInfo> prioritizedTransactionsDynamicRange =
      new TreeSet<>(
          comparing(TransactionInfo::isReceivedFromLocalSource)
              .thenComparing(
                  transactionInfo ->
                      transactionInfo
                          .getTransaction()
                          .getMaxFeePerGas()
                          .map(maxFeePerGas -> maxFeePerGas.getAsBigInteger().longValue())
                          .orElse(transactionInfo.getGasPrice().toLong()))
              .thenComparing(TransactionInfo::getAddedToPoolAt)
              .thenComparing(TransactionInfo::getSequence)
              .reversed());

  private final TreeSet<TransactionInfo> transactionsByEvictionOrder =
      new TreeSet<>(
          comparing(TransactionInfo::isReceivedFromLocalSource)
              .reversed()
              .thenComparing(TransactionInfo::getSequence));

  @Override
  public void manageBlockAdded(final Block block) {
    block.getHeader().getBaseFee().ifPresent(this::updateBaseFee);
  }

  @Override
  protected void doRemoveTransaction(final Transaction transaction, final boolean addedToBlock) {
    synchronized (lock) {
      final TransactionInfo removedTransactionInfo =
          pendingTransactions.remove(transaction.getHash());
      if (removedTransactionInfo != null) {
        transactionsByEvictionOrder.remove(removedTransactionInfo);
        if (prioritizedTransactionsDynamicRange.remove(removedTransactionInfo)) {
          traceLambda(
              LOG, "Removed dynamic range transaction {}", removedTransactionInfo::toTraceLog);
        } else {
          removedTransactionInfo
              .getTransaction()
              .getMaxPriorityFeePerGas()
              .ifPresent(
                  __ -> {
                    if (prioritizedTransactionsStaticRange.remove(removedTransactionInfo)) {
                      traceLambda(
                          LOG,
                          "Removed static range transaction {}",
                          removedTransactionInfo::toTraceLog);
                    }
                  });
        }
        removeTransactionTrackedBySenderAndNonce(transaction);
        incrementTransactionRemovedCounter(
            removedTransactionInfo.isReceivedFromLocalSource(), addedToBlock);
      }
    }
  }

  @Override
  protected Iterator<TransactionInfo> prioritizedTransactions() {
    return new Iterator<>() {
      final Iterator<TransactionInfo> staticRangeIterable =
          prioritizedTransactionsStaticRange.iterator();
      final Iterator<TransactionInfo> dynamicRangeIterable =
          prioritizedTransactionsDynamicRange.iterator();

      Optional<TransactionInfo> currentStaticRangeTransaction =
          getNextOptional(staticRangeIterable);
      Optional<TransactionInfo> currentDynamicRangeTransaction =
          getNextOptional(dynamicRangeIterable);

      @Override
      public boolean hasNext() {
        return currentStaticRangeTransaction.isPresent()
            || currentDynamicRangeTransaction.isPresent();
      }

      @Override
      public TransactionInfo next() {
        if (currentStaticRangeTransaction.isEmpty() && currentDynamicRangeTransaction.isEmpty()) {
          throw new NoSuchElementException("Tried to iterate past end of iterator.");
        } else if (currentStaticRangeTransaction.isEmpty()) {
          // only dynamic range txs left
          final TransactionInfo best = currentDynamicRangeTransaction.get();
          currentDynamicRangeTransaction = getNextOptional(dynamicRangeIterable);
          return best;
        } else if (currentDynamicRangeTransaction.isEmpty()) {
          // only static range txs left
          final TransactionInfo best = currentStaticRangeTransaction.get();
          currentStaticRangeTransaction = getNextOptional(staticRangeIterable);
          return best;
        } else {
          // there are both static and dynamic txs remaining, so we need to compare them by their
          // effective priority fees
          final Wei dynamicRangeEffectivePriorityFee =
              currentDynamicRangeTransaction
                  .get()
                  .getTransaction()
                  .getEffectivePriorityFeePerGas(baseFee);
          final Wei staticRangeEffectivePriorityFee =
              currentStaticRangeTransaction
                  .get()
                  .getTransaction()
                  .getEffectivePriorityFeePerGas(baseFee);
          final TransactionInfo best;
          if (dynamicRangeEffectivePriorityFee.compareTo(staticRangeEffectivePriorityFee) > 0) {
            best = currentDynamicRangeTransaction.get();
            currentDynamicRangeTransaction = getNextOptional(dynamicRangeIterable);
          } else {
            best = currentStaticRangeTransaction.get();
            currentStaticRangeTransaction = getNextOptional(staticRangeIterable);
          }
          return best;
        }
      }

      private Optional<TransactionInfo> getNextOptional(
          final Iterator<TransactionInfo> transactionInfoIterator) {
        return transactionInfoIterator.hasNext()
            ? Optional.of(transactionInfoIterator.next())
            : Optional.empty();
      }
    };
  }

  @Override
  protected TransactionAddedStatus addTransaction(
      final TransactionInfo transactionInfo, final Optional<Account> maybeSenderAccount) {
    Optional<Transaction> droppedTransaction = Optional.empty();
    final Transaction transaction = transactionInfo.getTransaction();
    synchronized (lock) {
      if (pendingTransactions.containsKey(transactionInfo.getHash())) {
        traceLambda(LOG, "Already known transaction {}", transactionInfo::toTraceLog);
        return ALREADY_KNOWN;
      }

      if (transaction.getNonce() - maybeSenderAccount.map(AccountState::getNonce).orElse(0L)
          >= poolConfig.getTxPoolMaxFutureTransactionByAccount()) {
        traceLambda(
            LOG,
            "Transaction {} not added because nonce too far in the future for sender {}",
            transaction::toTraceLog,
            maybeSenderAccount::toString);
        return NONCE_TOO_FAR_IN_FUTURE_FOR_SENDER;
      }

      final TransactionAddedStatus transactionAddedStatus =
          addTransactionForSenderAndNonce(transactionInfo, maybeSenderAccount);
      if (!transactionAddedStatus.equals(ADDED)) {
        traceLambda(
            LOG,
            "Not added with status {}, transaction {}",
            transactionAddedStatus::name,
            transactionInfo::toTraceLog);
        return transactionAddedStatus;
      }

      // check if it's in static or dynamic range
      final String kind;
      if (isInStaticRange(transaction, baseFee)) {
        kind = "static";
        prioritizedTransactionsStaticRange.add(transactionInfo);
      } else {
        kind = "dynamic";
        prioritizedTransactionsDynamicRange.add(transactionInfo);
      }
      traceLambda(
          LOG,
          "Adding {} to pending transactions, range type {}",
          transactionInfo::toTraceLog,
          kind::toString);
      pendingTransactions.put(transactionInfo.getHash(), transactionInfo);
      transactionsByEvictionOrder.add(transactionInfo);

      // if we are over txpool limit, select a transaction to evict
      if (pendingTransactions.size() > poolConfig.getTxPoolMaxSize()) {
        LOG.trace(
            "Tx pool size {} over limit {} selecting a transaction to evict",
            pendingTransactions.size(),
            poolConfig.getTxPoolMaxSize());
        droppedTransaction = getTransactionToEvict();

        droppedTransaction.ifPresent(
            toRemove -> {
              doRemoveTransaction(toRemove, false);
              traceLambda(
                  LOG,
                  "Evicted transaction {} due to transaction pool size, effective price {}",
                  toRemove::toTraceLog,
                  () -> toRemove.getEffectivePriorityFeePerGas(baseFee));
            });
      }
    }

    notifyTransactionAdded(transaction);
    droppedTransaction.ifPresent(this::notifyTransactionDropped);
    return ADDED;
  }

  private Optional<Transaction> getTransactionToEvict() {
    // select transaction to drop by lowest sequence and then by max nonce for the sender
    final TransactionInfo firstTransactionInfo = transactionsByEvictionOrder.first();
    final TransactionsForSenderInfo transactionsForSenderInfo =
        transactionsBySender.get(firstTransactionInfo.getSender());
    traceLambda(
        LOG,
        "Oldest transaction info {} will pick transaction with highest nonce for that sender {}",
        firstTransactionInfo::toTraceLog,
        transactionsForSenderInfo::toTraceLog);
    return transactionsForSenderInfo.maybeLastTx().map(TransactionInfo::getTransaction);
  }

  @Keep
  private Optional<Transaction> selectLowestValueTransaction() {
    Optional<Transaction> droppedTransaction;
    final Stream.Builder<TransactionInfo> removalCandidates = Stream.builder();
    if (!prioritizedTransactionsDynamicRange.isEmpty())
      lowestValueTxForRemovalBySender(prioritizedTransactionsDynamicRange)
          .ifPresent(
              tx -> {
                traceLambda(
                    LOG,
                    "Selected for removal dynamic range transaction {} effective price {}",
                    tx::toTraceLog,
                    () ->
                        tx.getTransaction()
                            .getEffectivePriorityFeePerGas(baseFee)
                            .getAsBigInteger());
                removalCandidates.add(tx);
              });
    if (!prioritizedTransactionsStaticRange.isEmpty())
      lowestValueTxForRemovalBySender(prioritizedTransactionsStaticRange)
          .ifPresent(
              tx -> {
                traceLambda(
                    LOG,
                    "Selected for removal static range transaction {} effective price {}",
                    tx::toTraceLog,
                    () ->
                        tx.getTransaction()
                            .getEffectivePriorityFeePerGas(baseFee)
                            .getAsBigInteger());
                removalCandidates.add(tx);
              });

    droppedTransaction =
        removalCandidates
            .build()
            .min(
                Comparator.comparing(
                    txInfo ->
                        txInfo
                            .getTransaction()
                            .getEffectivePriorityFeePerGas(baseFee)
                            .getAsBigInteger()))
            .map(TransactionInfo::getTransaction);
    return droppedTransaction;
  }

  private boolean isInStaticRange(final Transaction transaction, final Optional<Wei> baseFee) {
    return transaction
        .getMaxPriorityFeePerGas()
        .map(
            maxPriorityFeePerGas ->
                transaction.getEffectivePriorityFeePerGas(baseFee).compareTo(maxPriorityFeePerGas)
                    >= 0)
        .orElse(
            // non-eip-1559 txs can't be in static range
            false);
  }

  public void updateBaseFee(final Wei newBaseFee) {
    traceLambda(
        LOG,
        "Updating base fee from {} to {}",
        this.baseFee::toString,
        newBaseFee::toShortHexString);
    if (this.baseFee.orElse(Wei.ZERO).equals(newBaseFee)) {
      return;
    }
    synchronized (lock) {
      final boolean baseFeeIncreased = newBaseFee.compareTo(this.baseFee.orElse(Wei.ZERO)) > 0;
      this.baseFee = Optional.of(newBaseFee);
      if (baseFeeIncreased) {
        // base fee increases can only cause transactions to go from static to dynamic range
        prioritizedTransactionsStaticRange.stream()
            .filter(
                // these are the transactions whose effective priority fee have now dropped
                // below their max priority fee
                transactionInfo1 -> !isInStaticRange(transactionInfo1.getTransaction(), baseFee))
            .collect(toUnmodifiableList())
            .forEach(
                transactionInfo -> {
                  traceLambda(
                      LOG,
                      "Moving {} from static to dynamic gas fee paradigm",
                      transactionInfo::toTraceLog);
                  prioritizedTransactionsStaticRange.remove(transactionInfo);
                  prioritizedTransactionsDynamicRange.add(transactionInfo);
                });
      } else {
        // base fee decreases can only cause transactions to go from dynamic to static range
        prioritizedTransactionsDynamicRange.stream()
            .filter(
                // these are the transactions whose effective priority fee are now above their
                // max priority fee
                transactionInfo1 -> isInStaticRange(transactionInfo1.getTransaction(), baseFee))
            .collect(toUnmodifiableList())
            .forEach(
                transactionInfo -> {
                  traceLambda(
                      LOG,
                      "Moving {} from dynamic to static gas fee paradigm",
                      transactionInfo::toTraceLog);
                  prioritizedTransactionsDynamicRange.remove(transactionInfo);
                  prioritizedTransactionsStaticRange.add(transactionInfo);
                });
      }
    }
  }
}
