/*
 * Copyright contributors to Besu.
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

import org.hyperledger.besu.config.BlobSchedule;
import org.hyperledger.besu.datatypes.BlobGas;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CancunFeeMarketTest {
  private static final int BLOB_GAS_PER_BLOB = 131072;

  private static Stream<Arguments> provideBlobSchedules() {
    return Stream.of(
        Arguments.of("Cancun", BlobSchedule.CANCUN_DEFAULT),
        Arguments.of("Prague", BlobSchedule.PRAGUE_DEFAULT));
  }

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("provideBlobSchedules")
  void dataPricePerGas(final String name, final BlobSchedule blobSchedule) {
    final BaseFeeMarket feeMarket = FeeMarket.cancun(0, Optional.empty(), blobSchedule);
    // when no excess blob gas, data price per gas is 1
    assertEquals(1, feeMarket.blobGasPricePerGas(BlobGas.ZERO).getAsBigInteger().intValue());

    record BlobGasPricing(long excess, long price) {}
    List<BlobGasPricing> testVector = new ArrayList<>();

    int numBlobs = 1;
    long price = 1;
    while (price <= 1000) {
      price = blobGasPrice(BlobGas.of((long) numBlobs * BLOB_GAS_PER_BLOB), blobSchedule);
      var testCase = new BlobGasPricing((long) numBlobs * BLOB_GAS_PER_BLOB, price);
      testVector.add(testCase);
      numBlobs++;
    }

    testVector.forEach(
        blobGasPricing -> {
          assertEquals(
              blobGasPricing.price,
              feeMarket
                  .blobGasPricePerGas(BlobGas.of(blobGasPricing.excess))
                  .getAsBigInteger()
                  .intValue());
        });
  }

  private long blobGasPrice(final BlobGas excess, final BlobSchedule blobSchedule) {
    double dgufDenominator = blobSchedule.getBaseFeeUpdateFraction();
    double fakeExpo = excess.getValue().longValue() / dgufDenominator;
    return (long) (1 * Math.exp(fakeExpo));
  }
}
