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

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.fees.TransactionPriceCalculator;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions.TransactionInfo;
import org.hyperledger.besu.util.number.Percentage;

import java.util.Optional;

public class TransactionReplacementByPriceRule implements TransactionPoolReplacementRule {
  private static final TransactionPriceCalculator FRONTIER_CALCULATOR =
      TransactionPriceCalculator.frontier();
  private static final TransactionPriceCalculator EIP1559_CALCULATOR =
      TransactionPriceCalculator.eip1559();
  private final Percentage priceBump;

  public TransactionReplacementByPriceRule(final Percentage priceBump) {
    this.priceBump = priceBump;
  }

  @Override
  public boolean shouldReplace(
      final TransactionInfo existingTransactionInfo,
      final TransactionInfo newTransactionInfo,
      final Optional<Long> baseFee) {
    assert existingTransactionInfo.getTransaction() != null
        && newTransactionInfo.getTransaction() != null;
    final Wei replacementThreshold =
        priceOf(existingTransactionInfo.getTransaction(), baseFee)
            .multiply(100 + priceBump.getValue())
            .divide(100);
    return priceOf(newTransactionInfo.getTransaction(), baseFee).compareTo(replacementThreshold)
        > 0;
  }

  private Wei priceOf(final Transaction transaction, final Optional<Long> baseFee) {
    final TransactionPriceCalculator transactionPriceCalculator =
        transaction.isEIP1559Transaction() ? EIP1559_CALCULATOR : FRONTIER_CALCULATOR;
    return transactionPriceCalculator.price(transaction, baseFee);
  }
}
