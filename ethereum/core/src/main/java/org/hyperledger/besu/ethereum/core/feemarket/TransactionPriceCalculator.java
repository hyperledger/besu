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

import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;

import java.math.BigInteger;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface TransactionPriceCalculator {
  Wei price(Transaction transaction, Optional<Wei> maybeFee);

  default Wei dataPrice(final Transaction transaction, final ProcessableBlockHeader blockHeader) {
    return Wei.ZERO;
  }

  class Frontier implements TransactionPriceCalculator {
    @Override
    public Wei price(final Transaction transaction, final Optional<Wei> maybeFee) {
      return transaction.getGasPrice().orElse(Wei.ZERO);
    }

    @Override
    public Wei dataPrice(final Transaction transaction, final ProcessableBlockHeader blockHeader) {
      return Wei.ZERO;
    }
  }

  class EIP1559 implements TransactionPriceCalculator {
    @Override
    public Wei price(final Transaction transaction, final Optional<Wei> maybeFee) {
      final Wei baseFee = maybeFee.orElseThrow();
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
    }
  }

  class DataBlob extends EIP1559 {
    private static final Logger LOG = LoggerFactory.getLogger(DataBlob.class);
    private final BigInteger minDataGasPrice;
    private final BigInteger dataGasPriceUpdateFraction;

    public DataBlob(final int minDataGasPrice, final int dataGasPriceUpdateFraction) {
      this.minDataGasPrice = BigInteger.valueOf(minDataGasPrice);
      this.dataGasPriceUpdateFraction = BigInteger.valueOf(dataGasPriceUpdateFraction);
    }

    @Override
    public Wei dataPrice(final Transaction transaction, final ProcessableBlockHeader blockHeader) {
      final var excessDataGas = blockHeader.getExcessDataGas().orElseThrow();

      final var dataGasPrice =
          Wei.of(
              fakeExponential(
                  minDataGasPrice, excessDataGas.toBigInteger(), dataGasPriceUpdateFraction));
      traceLambda(
          LOG,
          "block #{} parentExcessDataGas: {} dataGasPrice: {}",
          blockHeader::getNumber,
          excessDataGas::toShortHexString,
          dataGasPrice::toHexString);

      return dataGasPrice;
    }

    private BigInteger fakeExponential(
        final BigInteger factor, final BigInteger numerator, final BigInteger denominator) {
      BigInteger i = BigInteger.ONE;
      BigInteger output = BigInteger.ZERO;
      BigInteger numeratorAccumulator = factor.multiply(denominator);
      while (numeratorAccumulator.compareTo(BigInteger.ZERO) > 0) {
        output = output.add(numeratorAccumulator);
        numeratorAccumulator =
            (numeratorAccumulator.multiply(numerator)).divide(denominator.multiply(i));
        i = i.add(BigInteger.ONE);
      }
      return output.divide(denominator);
    }
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
}
