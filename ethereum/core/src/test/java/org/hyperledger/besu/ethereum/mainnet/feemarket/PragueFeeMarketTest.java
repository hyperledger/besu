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

import org.hyperledger.besu.datatypes.BlobGas;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class PragueFeeMarketTest {

  private static final int BLOB_GAS_PER_BLOB = 131072;

  /**
   * from: https://eips.ethereum.org/EIPS/eip-7691 The BLOB_BASE_FEE_UPDATE_FRACTION_PRAGUE value in
   * this EIP is chosen as the mid-point between keeping the responsiveness to full blobs and no
   * blobs constant:
   *
   * <p>full blobs: basefee increases by ~8.2% no blobs: basefee decreases by ~14.5%
   */
  @Test
  void dataPricePerGas() {
    PragueFeeMarket pragueFeeMarket = new PragueFeeMarket(0, Optional.empty());
    // when no excess blob gas, data price per gas is 1
    assertEquals(1, pragueFeeMarket.blobGasPricePerGas(BlobGas.ZERO).getAsBigInteger().intValue());

    record BlobGasPricing(long excess, long price) {}
    List<BlobGasPricing> testVector = new ArrayList<>();

    int numBlobs = 1;
    long price = 1;
    while (price <= 1000) {
      price = blobGasPrice(BlobGas.of(numBlobs * BLOB_GAS_PER_BLOB));
      var testCase = new BlobGasPricing(numBlobs * BLOB_GAS_PER_BLOB, price);
      testVector.add(testCase);
      numBlobs++;
    }

    testVector.stream()
        .forEach(
            blobGasPricing -> {
              assertEquals(
                  blobGasPricing.price,
                  pragueFeeMarket
                      .blobGasPricePerGas(BlobGas.of(blobGasPricing.excess))
                      .getAsBigInteger()
                      .intValue());
            });
  }

  private long blobGasPrice(final BlobGas excess) {
    double dgufDenominator = 5007716;
    double fakeExpo = excess.getValue().longValue() / dgufDenominator;
    return (long) (1 * Math.exp(fakeExpo));
  }
}
