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

import java.util.List;
import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PragueGasLimitCalculatorTest {
  // CancunTargetingGasLimitCalculator with Prague numbers
  private final CancunTargetingGasLimitCalculator pragueGasLimitCalculator =
      new CancunTargetingGasLimitCalculator(
          0L, FeeMarket.cancunDefault(0L, Optional.empty()), new PragueGasCalculator());

  private static final long TARGET_BLOB_GAS_PER_BLOCK_PRAGUE = 0xC0000;

  @ParameterizedTest(
      name = "{index} - parent gas {0}, used gas {1}, blob target {2} new excess {3}")
  @MethodSource("blobGasses")
  public void shouldCalculateExcessBlobGasCorrectly(
      final long parentExcess, final long used, final long expected) {
    final long usedBlobGas = pragueGasLimitCalculator.getGasCalculator().blobGasCost(used);
    assertThat(pragueGasLimitCalculator.computeExcessBlobGas(parentExcess, usedBlobGas))
        .isEqualTo(expected);
  }

  Iterable<Arguments> blobGasses() {
    long sixBlobTargetGas = TARGET_BLOB_GAS_PER_BLOCK_PRAGUE;
    long newTargetCount = 6;

    return List.of(
        // New target count
        Arguments.of(0L, 0L, 0L),
        Arguments.of(sixBlobTargetGas, 0L, 0L),
        Arguments.of(newTargetCount, 0L, 0L),
        Arguments.of(0L, newTargetCount, 0L),
        Arguments.of(1L, newTargetCount, 1L),
        Arguments.of(
            pragueGasLimitCalculator.getGasCalculator().blobGasCost(newTargetCount),
            1L,
            pragueGasLimitCalculator.getBlobGasPerBlob()),
        Arguments.of(sixBlobTargetGas, newTargetCount, sixBlobTargetGas));
  }

  @Test
  void dryRunDetector() {
    Assertions.assertThat(true)
        .withFailMessage("This test is here so gradle --dry-run executes this class")
        .isTrue();
  }
}
