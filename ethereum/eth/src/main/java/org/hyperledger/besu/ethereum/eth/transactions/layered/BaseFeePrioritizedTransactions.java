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

import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;

import java.time.Clock;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.BiFunction;
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
      final Supplier<BlockHeader> chainHeadHeaderSupplier,
      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
          transactionReplacementTester,
      final BaseFeeMarket baseFeeMarket) {
    super(poolConfig, clock, transactionReplacementTester);
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
  public void manageBlockAdded(final BlockHeader blockHeader, final FeeMarket feeMarket) {
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
  public boolean isPromotable(final PendingTransaction pendingTransaction) {
    return super.isPromotable(pendingTransaction)
        && nextBlockBaseFee
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
    int aboveBaseFee = 0;
    int belowBaseFee = 0;
    final var txs = prioritizedTransactions();
    while (txs.hasNext()) {
      if (txs.next().getTransaction().getMaxGasPrice().greaterOrEqualThan(nextBlockBaseFee.get())) {
        aboveBaseFee++;
      } else {
        belowBaseFee++;
      }
    }

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
        + aboveBaseFee
        + ", Below next base fee: "
        + belowBaseFee;
  }
}
