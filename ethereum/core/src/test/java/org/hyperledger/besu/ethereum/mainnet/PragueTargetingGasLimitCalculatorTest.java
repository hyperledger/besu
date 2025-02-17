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
import org.hyperledger.besu.evm.gascalculator.PragueGasCalculator;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class PragueTargetingGasLimitCalculatorTest {

  @Test
  void currentBlobGasLimitIs9BlobsByDefault() {
    var pragueTargetingGasLimitCalculator =
        new PragueTargetingGasLimitCalculator(
            0L, FeeMarket.cancun(0L, Optional.empty()), new PragueGasCalculator());
    assertThat(pragueTargetingGasLimitCalculator.currentBlobGasLimit()).isEqualTo(0x120000);
  }

  @Test
  void shouldUsePragueCalculatorBlobGasPerBlob() {
    // should use PragueGasCalculator's blob gas per blob to calculate the gas limit
    final long blobGasPerBlob = new PragueGasCalculator().getBlobGasPerBlob();
    assertThat(blobGasPerBlob).isEqualTo(131072); // same as Cancun
    int maxBlobs = 10;
    var pragueTargetingGasLimitCalculator =
        new PragueTargetingGasLimitCalculator(
            0L, FeeMarket.cancun(0L, Optional.empty()), new PragueGasCalculator(), maxBlobs);
    // if maxBlobs = 10, then the gas limit would be 131072 * 10 = 1310720
    assertThat(pragueTargetingGasLimitCalculator.currentBlobGasLimit())
        .isEqualTo(blobGasPerBlob * maxBlobs);
    assertThat(pragueTargetingGasLimitCalculator.currentBlobGasLimit()).isEqualTo(1310720);
  }
}
