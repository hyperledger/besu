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

import java.util.Optional;

import org.junit.jupiter.api.Test;

class PragueTargetingGasLimitCalculatorTest {

  @Test
  void currentBlobGasLimitIs9BlobsByDefault() {
    var pragueTargetingGasLimitCalculator =
        new PragueTargetingGasLimitCalculator(0L, FeeMarket.cancun(0L, Optional.empty()));
    assertThat(pragueTargetingGasLimitCalculator.currentBlobGasLimit()).isEqualTo(0x120000);
  }
}
