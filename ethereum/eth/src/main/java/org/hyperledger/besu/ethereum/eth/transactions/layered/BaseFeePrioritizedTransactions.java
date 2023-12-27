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

import static org.hyperledger.besu.ethereum.eth.transactions.layered.TransactionsLayer.RemovalReason.BELOW_BASE_FEE;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.BlobCache;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
      final FeeMarket feeMarket,
      final BlobCache blobCache) {
    super(poolConfig, nextLayer, metrics, transactionReplacementTester, blobCache);
    this.nextBlockBaseFee =
        Optional.of(calculateNextBlockBaseFee(feeMarket, chainHeadHeaderSupplier.get()));
  }

  @Override
  protected int compareByFee(final PendingTransaction pt1, final PendingTransaction pt2) {
    return Comparator.comparing(PendingTransaction::hasPriority)
        .thenComparing(
            (PendingTransaction pendingTransaction) ->
                pendingTransaction.getTransaction().getEffectivePriorityFeePerGas(nextBlockBaseFee))
        .thenComparing(
            (PendingTransaction pendingTransaction) ->
                pendingTransaction.getTransaction().getMaxGasPrice())
        .thenComparing(Comparator.comparing(PendingTransaction::getNonce).reversed())
        .thenComparing(PendingTransaction::getSequence)
        .compare(pt1, pt2);
  }

  /**
   * On base fee markets when a new block is added we can calculate the base fee for the next block
   * and use it to keep only pending transactions willing to pay at least that fee in the
   * prioritized layer, since only these transactions are executable, while all the other can be
   * demoted to the next layer.
   *
   * @param blockHeader the header of the added block
   * @param feeMarket the fee market
   */
  @Override
  protected void internalBlockAdded(final BlockHeader blockHeader, final FeeMarket feeMarket) {
    final Wei newNextBlockBaseFee = calculateNextBlockBaseFee(feeMarket, blockHeader);

    LOG.atTrace()
        .setMessage("Updating base fee from {} to {}")
        .addArgument(nextBlockBaseFee.get()::toHumanReadableString)
        .addArgument(newNextBlockBaseFee::toHumanReadableString)
        .log();

    nextBlockBaseFee = Optional.of(newNextBlockBaseFee);
    orderByFee.clear();

    final var itTxsBySender = txsBySender.entrySet().iterator();
    while (itTxsBySender.hasNext()) {
      final var senderTxs = itTxsBySender.next().getValue();

      Optional<Long> maybeFirstUnderpricedNonce = Optional.empty();

      for (final var e : senderTxs.entrySet()) {
        final PendingTransaction tx = e.getValue();
        // it must pass the promotion filter to be prioritized
        if (promotionFilter(tx)) {
          orderByFee.add(tx);
        } else {
          // otherwise sender txs starting from this nonce need to be demoted to next layer,
          // and we can go to next sender
          maybeFirstUnderpricedNonce = Optional.of(e.getKey());
          break;
        }
      }

      maybeFirstUnderpricedNonce.ifPresent(
          nonce -> {
            // demote all txs after the first underpriced to the next layer, because none of them is
            // executable now, and we can avoid sorting them until they are candidate for execution
            // again
            final var demoteTxs = senderTxs.tailMap(nonce, true);
            while (!demoteTxs.isEmpty()) {
              final PendingTransaction demoteTx = demoteTxs.pollLastEntry().getValue();
              LOG.atTrace()
                  .setMessage("Demoting tx {} with max gas price below next block base fee {}")
                  .addArgument(demoteTx::toTraceLog)
                  .addArgument(newNextBlockBaseFee::toHumanReadableString)
                  .log();
              processEvict(senderTxs, demoteTx, BELOW_BASE_FEE);
              addToNextLayer(senderTxs, demoteTx, 0);
            }
          });

      if (senderTxs.isEmpty()) {
        itTxsBySender.remove();
      }
    }
  }

  private Wei calculateNextBlockBaseFee(final FeeMarket feeMarket, final BlockHeader blockHeader) {
    if (feeMarket.implementsBaseFee()) {
      final var baseFeeMarket = (BaseFeeMarket) feeMarket;
      return baseFeeMarket.computeBaseFee(
          blockHeader.getNumber() + 1,
          blockHeader.getBaseFee().orElse(Wei.ZERO),
          blockHeader.getGasUsed(),
          baseFeeMarket.targetGasUsed(blockHeader));
    }
    return Wei.ZERO;
  }

  @Override
  protected boolean promotionFilter(final PendingTransaction pendingTransaction) {
    return nextBlockBaseFee
        .map(
            baseFee ->
                pendingTransaction.getTransaction().getMaxGasPrice().greaterOrEqualThan(baseFee))
        .orElse(false);
  }

  @Override
  protected String internalLogStats() {

    if (orderByFee.isEmpty()) {
      return "Basefee Prioritized: Empty";
    }

    final Transaction highest = orderByFee.last().getTransaction();
    final Transaction lowest = orderByFee.first().getTransaction();

    return "Basefee Prioritized: "
        + "count: "
        + pendingTransactions.size()
        + ", space used: "
        + spaceUsed
        + ", unique senders: "
        + txsBySender.size()
        + ", highest priority tx: [max fee: "
        + highest.getMaxGasPrice().toHumanReadableString()
        + ", curr prio fee: "
        + highest.getEffectivePriorityFeePerGas(nextBlockBaseFee).toHumanReadableString()
        + ", hash: "
        + highest.getHash()
        + "], lowest priority tx: [max fee: "
        + lowest.getMaxGasPrice().toHumanReadableString()
        + ", curr prio fee: "
        + lowest.getEffectivePriorityFeePerGas(nextBlockBaseFee).toHumanReadableString()
        + ", hash: "
        + lowest.getHash()
        + "], next block base fee: "
        + nextBlockBaseFee.get().toHumanReadableString();
  }
}
