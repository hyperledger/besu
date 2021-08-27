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

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.feemarket.TransactionPriceCalculator;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter.TransactionInfo;
import org.hyperledger.besu.plugin.data.TransactionType;
import org.hyperledger.besu.util.number.Percentage;

import java.util.Optional;

import org.apache.tuweni.units.bigints.UInt256;

public class TransactionReplacementByFeeMarketRule implements TransactionPoolReplacementRule {

  private static final TransactionPriceCalculator FRONTIER_CALCULATOR =
      TransactionPriceCalculator.frontier();
  private static final TransactionPriceCalculator EIP1559_CALCULATOR =
      TransactionPriceCalculator.eip1559();
  private final Percentage priceBump;

  public TransactionReplacementByFeeMarketRule(final Percentage priceBump) {
    this.priceBump = priceBump;
  }

  @Override
  public boolean shouldReplace(
      final TransactionInfo existingTransactionInfo,
      final TransactionInfo newTransactionInfo,
      final Optional<Long> baseFee) {

    // bail early if basefee is absent or neither transaction supports 1559 fee market
    if (baseFee.isEmpty()
        || !(isNotGasPriced(existingTransactionInfo) || isNotGasPriced(newTransactionInfo))) {
      return false;
    }

    Wei newEffPrice = priceOf(newTransactionInfo.getTransaction(), baseFee);
    Long newEffPriority =
        newTransactionInfo.getTransaction().getEffectivePriorityFeePerGas(baseFee);

    // bail early if price is or priority is not strictly positive
    if (!(newEffPrice.toLong() > 0L)) {
      return false;
    }

    Wei curEffPrice = priceOf(existingTransactionInfo.getTransaction(), baseFee);
    Long curEffPriority =
        existingTransactionInfo.getTransaction().getEffectivePriorityFeePerGas(baseFee);

    if (isBumpedBy(curEffPrice.toUInt256(), newEffPrice.toUInt256(), priceBump)) {
      // if effective price is bumped by percent:
      // replace if new effective priority is >= current effective priority
      return newEffPriority >= curEffPriority;
    } else if (curEffPrice.equals(newEffPrice)) {
      // elsif new effective price is equal to current effective price:
      // replace if the new effective priority is bumped by priceBump relative to current priority
      return isBumpedBy(
          UInt256.valueOf(curEffPriority), UInt256.valueOf(newEffPriority), priceBump);
    }
    return false;
  }

  private Wei priceOf(final Transaction transaction, final Optional<Long> baseFee) {
    final TransactionPriceCalculator transactionPriceCalculator =
        transaction.getType().equals(TransactionType.EIP1559)
            ? EIP1559_CALCULATOR
            : FRONTIER_CALCULATOR;
    return transactionPriceCalculator.price(transaction, baseFee);
  }

  private boolean isBumpedBy(final UInt256 val, final UInt256 bumpVal, final Percentage percent) {
    return val.multiply(percent.getValue() + 100L).compareTo(bumpVal.multiply(100L)) < 0;
  }
}
