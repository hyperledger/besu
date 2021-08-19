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
package org.hyperledger.besu.ethereum.mainnet.feemarket;

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.fees.BaseFee;
import org.hyperledger.besu.ethereum.core.fees.TransactionPriceCalculator;

import java.util.Optional;
import java.util.function.Supplier;

public class LondonFeeMarket implements BaseFeeMarket {
  static final Long DEFAULT_BASEFEE_INITIAL_VALUE = 1000000000L;
  static final Long DEFAULT_BASEFEE_MAX_CHANGE_DENOMINATOR = 8L;
  public static final Long DEFAULT_SLACK_COEFFICIENT = 2L;
  private final TransactionPriceCalculator txPriceCalculator;

  public LondonFeeMarket() {
    this.txPriceCalculator = TransactionPriceCalculator.eip1559();
  }

  @Override
  public long getBasefeeMaxChangeDenominator() {
    return DEFAULT_BASEFEE_MAX_CHANGE_DENOMINATOR;
  }

  @Override
  public long getInitialBasefee() {
    return DEFAULT_BASEFEE_INITIAL_VALUE;
  }

  @Override
  public long getSlackCoefficient() {
    return DEFAULT_SLACK_COEFFICIENT;
  }

  @Override
  public TransactionPriceCalculator getTransactionPriceCalculator() {
    return txPriceCalculator;
  }

  @Override
  public Wei minTransactionPriceInNextBlock(
      final Transaction transaction, final Supplier<Optional<Long>> baseFeeSupplier) {
    final Optional<Long> baseFee = baseFeeSupplier.get();
    Optional<Long> minBaseFeeInNextBlock =
        baseFee.map(bf -> new BaseFee(this, bf).getMinNextValue());

    return this.getTransactionPriceCalculator().price(transaction, minBaseFeeInNextBlock);
  }
}
