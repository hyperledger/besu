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

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
      final Supplier<BlockHeader> chainHeadHeaderSupplier,
      final TransactionsLayer nextLayer,
      final TransactionPoolMetrics metrics,
      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
          transactionReplacementTester,
      final BaseFeeMarket baseFeeMarket) {
    super(poolConfig, nextLayer, metrics, transactionReplacementTester);
    this.nextBlockBaseFee =
        Optional.of(calculateNextBlockBaseFee(baseFeeMarket, chainHeadHeaderSupplier.get()));
  }

  @Override
  protected int compareByFee(final PendingTransaction pt1, final PendingTransaction pt2) {
    return Comparator.comparing(
            (PendingTransaction pendingTransaction) ->
                pendingTransaction.getTransaction().getEffectivePriorityFeePerGas(nextBlockBaseFee))
        .thenComparing(Comparator.comparing(PendingTransaction::getNonce).reversed())
        .thenComparing(PendingTransaction::getSender)
        .compare(pt1, pt2);
  }

  @Override
  public String name() {
    return "prioritized-basefee";
  }

  @Override
  protected void internalBlockAdded(final BlockHeader blockHeader, final FeeMarket feeMarket) {
    final BaseFeeMarket baseFeeMarket = (BaseFeeMarket) feeMarket;
    final Wei newNextBlockBaseFee = calculateNextBlockBaseFee(baseFeeMarket, blockHeader);

    LOG.atTrace()
        .setMessage("Updating base fee from {} to {}")
        .addArgument(nextBlockBaseFee.get()::toHumanReadableString)
        .addArgument(newNextBlockBaseFee::toHumanReadableString)
        .log();

    nextBlockBaseFee = Optional.of(newNextBlockBaseFee);
    orderByFee.clear();
    orderByFee.addAll(pendingTransactions.values());
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
  protected boolean promotionFilter(final PendingTransaction pendingTransaction) {
    return nextBlockBaseFee
        .map(
            baseFee ->
                pendingTransaction
                    .getTransaction()
                    .getEffectiveGasPrice(nextBlockBaseFee)
                    .greaterOrEqualThan(baseFee))
        .orElse(false);
  }

  @Override
  public String logStats() {

    final var baseFeePartition =
        stream()
            .map(PendingTransaction::getTransaction)
            .collect(
                Collectors.partitioningBy(
                    tx -> tx.getMaxGasPrice().greaterOrEqualThan(nextBlockBaseFee.get()),
                    Collectors.counting()));

    if (orderByFee.isEmpty()) {
      return "Empty";
    }

    return "Highest priority tx: [max fee: "
        + orderByFee.last().getTransaction().getMaxGasPrice().toHumanReadableString()
        + ", curr prio fee: "
        + orderByFee
            .last()
            .getTransaction()
            .getEffectivePriorityFeePerGas(nextBlockBaseFee)
            .toHumanReadableString()
        + "], Lowest priority tx: [max fee: "
        + orderByFee.first().getTransaction().getMaxGasPrice().toHumanReadableString()
        + ", curr prio fee: "
        + orderByFee
            .first()
            .getTransaction()
            .getEffectivePriorityFeePerGas(nextBlockBaseFee)
            .toHumanReadableString()
        + "], Next block base fee: "
        + nextBlockBaseFee.get().toHumanReadableString()
        + ", Above next base fee: "
        + baseFeePartition.get(true)
        + ", Below next base fee: "
        + baseFeePartition.get(false);
  }
}
