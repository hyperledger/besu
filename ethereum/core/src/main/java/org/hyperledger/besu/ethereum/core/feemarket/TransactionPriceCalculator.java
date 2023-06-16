/*
 * Copyright Hyperledger Besu contributors.
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
package org.hyperledger.besu.ethereum.core.feemarket;

import org.hyperledger.besu.datatypes.DataGas;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;

import java.math.BigInteger;
import java.util.Optional;

@FunctionalInterface
public interface TransactionPriceCalculator {
  Wei price(Transaction transaction, Optional<Wei> baseFee);

  static TransactionPriceCalculator frontier() {
    return (transaction, baseFee) -> transaction.getGasPrice().orElse(Wei.ZERO);
  }

  static TransactionPriceCalculator eip1559() {
    return (transaction, maybeBaseFee) -> {
      final Wei baseFee = maybeBaseFee.orElseThrow();
      if (!transaction.getType().supports1559FeeMarket()) {
        return transaction.getGasPrice().orElse(Wei.ZERO);
      }
      final Wei maxPriorityFeePerGas = transaction.getMaxPriorityFeePerGas().orElseThrow();
      final Wei maxFeePerGas = transaction.getMaxFeePerGas().orElseThrow();
      Wei price = maxPriorityFeePerGas.add(baseFee);
      if (price.compareTo(maxFeePerGas) > 0) {
        price = maxFeePerGas;
      }
      return price;
    };
  }

  // curiously named as in the spec
  // https://eips.ethereum.org/EIPS/eip-4844#cryptographic-helpers
  private static BigInteger fakeExponential(
      final BigInteger factor, final BigInteger numerator, final BigInteger denominator) {
    int i = 1;
    BigInteger output = BigInteger.ZERO;
    BigInteger numeratorAccumulator = factor.multiply(denominator);
    while (numeratorAccumulator.signum() > 0) {
      output = output.add(numeratorAccumulator);
      numeratorAccumulator =
          (numeratorAccumulator.multiply(numerator))
              .divide(denominator.multiply(BigInteger.valueOf(i)));
      ++i;
    }
    return output.divide(denominator);
  }

  static TransactionPriceCalculator dataGas(
      final int minDataGasPrice,
      final int dataGasPriceUpdateFraction,
      final DataGas excessDataGas) {
    return ((transaction, baseFee) -> {
      final var dataGasPrice =
          Wei.of(
              fakeExponential(
                  BigInteger.valueOf(minDataGasPrice),
                  excessDataGas.toBigInteger(),
                  BigInteger.valueOf(dataGasPriceUpdateFraction)));
      return dataGasPrice;
    });
  }
}
