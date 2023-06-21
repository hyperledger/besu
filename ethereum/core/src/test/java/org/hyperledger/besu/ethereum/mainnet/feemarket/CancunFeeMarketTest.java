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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.hyperledger.besu.datatypes.DataGas;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class CancunFeeMarketTest {

  private static final int DATA_GAS_PER_BLOB = 131072;

  @Test
  void dataPricePerGas() {
    CancunFeeMarket cancunFeeMarket = new CancunFeeMarket(0, Optional.empty());
    // when no excess data gas, data price per gas is 1
    assertEquals(1, cancunFeeMarket.dataPricePerGas(DataGas.ZERO).getAsBigInteger().intValue());

    record DataGasPricing(long excess, long price) {}
    List<DataGasPricing> testVector = new ArrayList<>();

    int numBlobs = 1;
    long price = 1;
    while (price <= 1000) {
      price = dataGasPrice(DataGas.of(numBlobs * DATA_GAS_PER_BLOB));
      var testCase = new DataGasPricing(numBlobs * DATA_GAS_PER_BLOB, price);
      testVector.add(testCase);
      numBlobs++;
    }

    testVector.stream()
        .forEach(
            dataGasPricing -> {
              assertEquals(
                  dataGasPricing.price,
                  cancunFeeMarket
                      .dataPricePerGas(DataGas.of(dataGasPricing.excess))
                      .getAsBigInteger()
                      .intValue());
            });
  }

  private long dataGasPrice(final DataGas excess) {
    double dgufDenominator = 3338477;
    double fakeExpo = excess.getValue().longValue() / dgufDenominator;
    return (long) (1 * Math.exp(fakeExpo));
  }
}
