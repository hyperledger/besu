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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OsakaGasCalculatorTest {

  private static final long TARGET_BLOB_GAS_PER_BLOCK_OSAKA = 0x120000;
  private final OsakaGasCalculator osakaGasCalculator = new OsakaGasCalculator();

  @Test
  void testPrecompileSize() {
    OsakaGasCalculator subject = new OsakaGasCalculator();
    assertThat(subject.isPrecompile(Address.precompiled(0x14))).isFalse();
    assertThat(subject.isPrecompile(Address.BLS12_MAP_FP2_TO_G2)).isTrue();
  }

  @Test
  void testNewConstants() {
    CancunGasCalculator cancunGas = new CancunGasCalculator();
    assertThat(osakaGasCalculator.getMinCalleeGas()).isGreaterThan(cancunGas.getMinCalleeGas());
    assertThat(osakaGasCalculator.getMinRetainedGas()).isGreaterThan(cancunGas.getMinRetainedGas());
  }

  @ParameterizedTest(
      name = "{index} - parent gas {0}, used gas {1}, blob target {2} new excess {3}")
  @MethodSource("blobGasses")
  public void shouldCalculateExcessBlobGasCorrectly(
      final long parentExcess, final long used, final long expected) {
    final long usedBlobGas = osakaGasCalculator.blobGasCost(used);
    assertThat(osakaGasCalculator.computeExcessBlobGas(parentExcess, usedBlobGas))
        .isEqualTo(expected);
  }

  Iterable<Arguments> blobGasses() {
    long nineBlobTargetGas = TARGET_BLOB_GAS_PER_BLOCK_OSAKA;
    long newTargetCount = 9;

    return List.of(
        // New target count
        Arguments.of(0L, 0L, 0L),
        Arguments.of(nineBlobTargetGas, 0L, 0L),
        Arguments.of(newTargetCount, 0L, 0L),
        Arguments.of(0L, newTargetCount, 0L),
        Arguments.of(1L, newTargetCount, 1L),
        Arguments.of(
            osakaGasCalculator.blobGasCost(newTargetCount),
            1L,
            osakaGasCalculator.getBlobGasPerBlob()),
        Arguments.of(nineBlobTargetGas, newTargetCount, nineBlobTargetGas));
  }
}
