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

import static java.lang.Math.max;

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.feemarket.BaseFee;
import org.hyperledger.besu.ethereum.core.feemarket.TransactionPriceCalculator;

import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LondonFeeMarket implements BaseFeeMarket {
  static final long DEFAULT_BASEFEE_INITIAL_VALUE = 1000000000L;
  static final long DEFAULT_BASEFEE_MAX_CHANGE_DENOMINATOR = 8L;
  static final long DEFAULT_SLACK_COEFFICIENT = 2L;
  private static final Logger LOG = LogManager.getLogger();

  private final long londonForkBlockNumber;
  private final TransactionPriceCalculator txPriceCalculator;

  public LondonFeeMarket(final long londonForkBlockNumber) {
    this.txPriceCalculator = TransactionPriceCalculator.eip1559();
    this.londonForkBlockNumber = londonForkBlockNumber;
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

  @Override
  public long computeBaseFee(
      final long blockNumber,
      final long parentBaseFee,
      final long parentBlockGasUsed,
      final long targetGasUsed) {
    if (londonForkBlockNumber == blockNumber) {
      return getInitialBasefee();
    }

    long gasDelta, feeDelta, baseFee;
    if (parentBlockGasUsed == targetGasUsed) {
      return parentBaseFee;
    } else if (parentBlockGasUsed > targetGasUsed) {
      gasDelta = parentBlockGasUsed - targetGasUsed;
      final BigInteger pBaseFee = BigInteger.valueOf(parentBaseFee);
      final BigInteger gDelta = BigInteger.valueOf(gasDelta);
      final BigInteger target = BigInteger.valueOf(targetGasUsed);
      final BigInteger denominator = BigInteger.valueOf(getBasefeeMaxChangeDenominator());
      feeDelta = max(pBaseFee.multiply(gDelta).divide(target).divide(denominator).longValue(), 1);
      baseFee = parentBaseFee + feeDelta;
    } else {
      gasDelta = targetGasUsed - parentBlockGasUsed;
      final BigInteger pBaseFee = BigInteger.valueOf(parentBaseFee);
      final BigInteger gDelta = BigInteger.valueOf(gasDelta);
      final BigInteger target = BigInteger.valueOf(targetGasUsed);
      final BigInteger denominator = BigInteger.valueOf(getBasefeeMaxChangeDenominator());
      feeDelta = pBaseFee.multiply(gDelta).divide(target).divide(denominator).longValue();
      baseFee = parentBaseFee - feeDelta;
    }
    LOG.trace(
        "block #{} parentBaseFee: {} parentGasUsed: {} parentGasTarget: {} baseFee: {}",
        blockNumber,
        parentBaseFee,
        parentBlockGasUsed,
        targetGasUsed,
        baseFee);
    return baseFee;
  }

  @Override
  public boolean isForkBlock(final long blockNumber) {
    return londonForkBlockNumber == blockNumber;
  }

  @Override
  public boolean isBeforeForkBlock(final long blockNumber) {
    return londonForkBlockNumber > blockNumber;
  }
}
