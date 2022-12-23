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
package org.hyperledger.besu.ethereum.eth.transactions.sorter;

import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.cache.NoOpPostponedTransactionsCache;
import org.hyperledger.besu.ethereum.eth.transactions.cache.ReadyTransactionsCache;
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.time.Clock;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the current set of pending transactions with the ability to iterate them based on priority
 * for mining or look-up by hash.
 *
 * <p>This class is safe for use across multiple threads.
 */
public class BaseFeePrioritizedTransactions extends AbstractPrioritizedTransactions {

  private static final Logger LOG = LoggerFactory.getLogger(BaseFeePrioritizedTransactions.class);

  private Optional<Wei> nextBlockBaseFee;

  public BaseFeePrioritizedTransactions(
      final TransactionPoolConfiguration poolConfig,
      final Clock clock,
      final MetricsSystem metricsSystem,
      final Supplier<BlockHeader> chainHeadHeaderSupplier,
      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
          transactionReplacementTester,
      final BaseFeeMarket baseFeeMarket) {
    this(
        poolConfig,
        clock,
        metricsSystem,
        chainHeadHeaderSupplier,
        transactionReplacementTester,
        baseFeeMarket,
        new ReadyTransactionsCache(
            poolConfig, new NoOpPostponedTransactionsCache(), transactionReplacementTester));
  }

  public BaseFeePrioritizedTransactions(
      final TransactionPoolConfiguration poolConfig,
      final Clock clock,
      final MetricsSystem metricsSystem,
      final Supplier<BlockHeader> chainHeadHeaderSupplier,
      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
          transactionReplacementTester,
      final BaseFeeMarket baseFeeMarket,
      final ReadyTransactionsCache readyTransactionsCache) {
    super(poolConfig, clock, metricsSystem, transactionReplacementTester, readyTransactionsCache);
    this.nextBlockBaseFee =
        Optional.of(calculateNextBlockBaseFee(baseFeeMarket, chainHeadHeaderSupplier.get()));
  }

  @Override
  public int compareByFee(final PendingTransaction pt1, final PendingTransaction pt2) {
    return Comparator.comparing(
            (PendingTransaction pendingTransaction) ->
                pendingTransaction.getTransaction().getEffectivePriorityFeePerGas(nextBlockBaseFee))
        .thenComparing(PendingTransaction::getAddedToPoolAt)
        .thenComparing(PendingTransaction::getSequence)
        .compare(pt1, pt2);
  }

  @Override
  protected void manageBlockAdded(final Block block, final FeeMarket feeMarket) {
    final BlockHeader blockHeader = block.getHeader();
    final BaseFeeMarket baseFeeMarket = (BaseFeeMarket) feeMarket;
    final Wei newNextBlockBaseFee = calculateNextBlockBaseFee(baseFeeMarket, blockHeader);

    traceLambda(
        LOG,
        "Updating base fee from {} to {}",
        nextBlockBaseFee.get()::toHumanReadableString,
        newNextBlockBaseFee::toHumanReadableString);

    nextBlockBaseFee = Optional.of(newNextBlockBaseFee);
    orderByFee.clear();
    orderByFee.addAll(prioritizedPendingTransactions.values());
  }

  private Wei calculateNextBlockBaseFee(
      final BaseFeeMarket baseFeeMarket, final BlockHeader blockHeader) {
    return baseFeeMarket.computeBaseFee(
        blockHeader.getNumber() + 1,
        blockHeader.getBaseFee().orElse(Wei.ZERO),
        blockHeader.getGasUsed(),
        baseFeeMarket.targetGasUsed(blockHeader));
  }

  @Override
  protected void removeFromOrderedTransactions(
      final PendingTransaction removedPendingTx, final boolean addedToBlock) {
    // when added to block we can avoid removing from orderByFee since it will be recreated
    if (!addedToBlock) {
      orderByFee.remove(removedPendingTx);
    }
  }

  @Override
  protected Predicate<PendingTransaction> getPromotionFilter() {
    return pendingTransaction ->
        nextBlockBaseFee
            .map(
                baseFee ->
                    pendingTransaction
                        .getTransaction()
                        .getEffectiveGasPrice(nextBlockBaseFee)
                        .greaterOrEqualThan(baseFee))
            .orElse(false);
  }
}
