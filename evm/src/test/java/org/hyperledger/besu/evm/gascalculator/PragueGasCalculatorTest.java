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
package org.hyperledger.besu.evm.gascalculator;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.units.bigints.UInt64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PragueGasCalculatorTest {

  private final PragueGasCalculator pragueGasCalculator = new PragueGasCalculator();

  @Test
  void testPrecompileSize() {
    PragueGasCalculator subject = new PragueGasCalculator();
    assertThat(subject.isPrecompile(Address.precompiled(0x14))).isFalse();
    assertThat(subject.isPrecompile(Address.BLS12_MAP_FP2_TO_G2)).isTrue();
  }

  @ParameterizedTest(
      name = "{index} - parent gas {0}, used gas {1}, blob target {2} new excess {3}")
  @MethodSource("blobGasses")
  public void shouldCalculateExcessBlobGasCorrectly(
      final long parentExcess, final long used, final long target, final long expected) {
    final long usedBlobGas = pragueGasCalculator.blobGasCost(used);
    assertThat(
            pragueGasCalculator.computeExcessBlobGas(
                parentExcess, usedBlobGas, Optional.of(UInt64.valueOf(target))))
        .isEqualTo(expected);
  }

  Iterable<Arguments> blobGasses() {
    long threeBlobTargetGas = CancunGasCalculator.TARGET_BLOB_GAS_PER_BLOCK;
    long cancunTargetCount = 3;
    long newTargetCount = 4;

    return List.of(
        // If blob target count remains at 3
        Arguments.of(0L, 0L, cancunTargetCount, 0L),
        Arguments.of(threeBlobTargetGas, 0L, cancunTargetCount, 0L),
        Arguments.of(0L, cancunTargetCount, cancunTargetCount, 0L),
        Arguments.of(1L, cancunTargetCount, cancunTargetCount, 1L),
        Arguments.of(
            threeBlobTargetGas, 1L, cancunTargetCount, pragueGasCalculator.getBlobGasPerBlob()),
        Arguments.of(threeBlobTargetGas, 3L, cancunTargetCount, threeBlobTargetGas),
        // New target count
        Arguments.of(0L, 0L, newTargetCount, 0L),
        Arguments.of(threeBlobTargetGas, 0L, newTargetCount, 0L),
        Arguments.of(newTargetCount, 0L, newTargetCount, 0L),
        Arguments.of(0L, newTargetCount, newTargetCount, 0L),
        Arguments.of(1L, newTargetCount, newTargetCount, 1L),
        Arguments.of(
            pragueGasCalculator.blobGasCost(newTargetCount),
            1L,
            newTargetCount,
            pragueGasCalculator.getBlobGasPerBlob()),
        Arguments.of(threeBlobTargetGas, newTargetCount, newTargetCount, threeBlobTargetGas));
  }
}
