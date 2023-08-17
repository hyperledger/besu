/*
 * Copyright contributors to Hyperledger Besu.
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

import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.datatypes.Wei;

import java.math.BigInteger;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CancunFeeMarket extends LondonFeeMarket {
  private static final Logger LOG = LoggerFactory.getLogger(CancunFeeMarket.class);
  private static final BigInteger BLOB_GAS_PRICE = BigInteger.ONE;
  private static final BigInteger BLOB_GAS_PRICE_UPDATE_FRACTION = BigInteger.valueOf(3338477);

  public CancunFeeMarket(
      final long londonForkBlockNumber, final Optional<Wei> baseFeePerGasOverride) {
    super(londonForkBlockNumber, baseFeePerGasOverride);
  }

  @Override
  public boolean implementsDataFee() {
    return true;
  }

  @Override
  public Wei dataPricePerGas(final BlobGas excessBlobGas) {
    final var blobGasPrice =
        Wei.of(
            fakeExponential(
                    BLOB_GAS_PRICE, excessBlobGas.toBigInteger(), BLOB_GAS_PRICE_UPDATE_FRACTION));
    LOG.atTrace()
        .setMessage("parentExcessBlobGas: {} blobGasPrice: {}")
        .addArgument(excessBlobGas::toShortHexString)
        .addArgument(blobGasPrice::toHexString)
        .log();

    return blobGasPrice;
  }

  private BigInteger fakeExponential(
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
