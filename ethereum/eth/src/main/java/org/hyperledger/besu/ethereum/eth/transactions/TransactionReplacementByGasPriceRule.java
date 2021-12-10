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
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter.TransactionInfo;
import org.hyperledger.besu.util.number.Percentage;

import java.util.Optional;

public class TransactionReplacementByGasPriceRule implements TransactionPoolReplacementRule {
  private final Percentage priceBump;

  public TransactionReplacementByGasPriceRule(final Percentage priceBump) {
    this.priceBump = priceBump;
  }

  @Override
  public boolean shouldReplace(
      final TransactionInfo existingTransactionInfo,
      final TransactionInfo newTransactionInfo,
      final Optional<Wei> baseFee) {
    assert existingTransactionInfo.getTransaction() != null
        && newTransactionInfo.getTransaction() != null;

    // return false if either transaction supports 1559 fee market
    if (isNotGasPriced(existingTransactionInfo) || isNotGasPriced(newTransactionInfo)) {
      return false;
    }

    final Wei replacementThreshold =
        existingTransactionInfo.getGasPrice().multiply(100 + priceBump.getValue()).divide(100);
    return newTransactionInfo.getGasPrice().compareTo(replacementThreshold) > 0;
  }
}
