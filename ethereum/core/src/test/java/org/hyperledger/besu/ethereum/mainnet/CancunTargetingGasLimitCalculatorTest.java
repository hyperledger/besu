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
package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.evm.gascalculator.CancunGasCalculator;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class CancunTargetingGasLimitCalculatorTest {

  @Test
  void currentBlobGasLimitIs6Blobs() {
    var cancunTargetingGasLimitCalculator =
        new org.hyperledger.besu.ethereum.mainnet.CancunTargetingGasLimitCalculator(
            0L, FeeMarket.cancun(0L, Optional.empty()), new CancunGasCalculator());
    assertThat(cancunTargetingGasLimitCalculator.currentBlobGasLimit()).isEqualTo(0xC0000);
  }

  @Test
  void shouldUseCancunCalculatorBlobGasPerBlob() {
    // should use CancunGasCalculator's blob gas per blob to calculate the gas limit
    final long blobGasPerBlob = new CancunGasCalculator().getBlobGasPerBlob();
    assertThat(blobGasPerBlob).isEqualTo(131072);
    int maxBlobs = 10;
    var cancunTargetingGasLimitCalculator =
        new CancunTargetingGasLimitCalculator(
            0L, FeeMarket.cancun(0L, Optional.empty()), new CancunGasCalculator(), maxBlobs);
    // if maxBlobs = 10, then the gas limit would be 131072 * 10 = 1310720
    assertThat(cancunTargetingGasLimitCalculator.currentBlobGasLimit())
        .isEqualTo(blobGasPerBlob * maxBlobs);
    assertThat(cancunTargetingGasLimitCalculator.currentBlobGasLimit()).isEqualTo(1310720);
  }

  @Test
  void shouldUseFutureForkCalculatorBlobGasPerBlob() {
    // if a future fork changes the blob gas per blob
    // even if we still use the CancunTargetingGasLimitCalculator
    // it should use TestFutureForkCalculator's blob gas per blob to calculate the blob gas limit
    final long blobGasPerBlob = new TestFutureGasCalculator().getBlobGasPerBlob();
    assertThat(blobGasPerBlob).isEqualTo(262144);
    int maxBlobs = 10;
    var cancunTargetingGasLimitCalculator =
        new CancunTargetingGasLimitCalculator(
            0L, FeeMarket.cancun(0L, Optional.empty()), new TestFutureGasCalculator(), maxBlobs);
    // if maxBlobs = 10, then the gas limit would be 262144 * 10 = 2621440
    assertThat(cancunTargetingGasLimitCalculator.currentBlobGasLimit())
        .isEqualTo(blobGasPerBlob * maxBlobs);
    assertThat(cancunTargetingGasLimitCalculator.currentBlobGasLimit()).isEqualTo(2621440);
  }

  private static class TestFutureGasCalculator extends CancunGasCalculator {
    private static final long TEST_BLOB_GAS_PER_BLOB_FUTURE = 262144;

    public TestFutureGasCalculator() {
      super(0, 7);
    }

    @Override
    public long getBlobGasPerBlob() {
      return TEST_BLOB_GAS_PER_BLOB_FUTURE;
    }
  }
}
