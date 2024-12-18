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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CancunGasCalculatorTest {

  private static final long TARGET_BLOB_GAS_PER_BLOCK_CANCUN = 0x60000;
  private final CancunGasCalculator cancunGasCalculator = new CancunGasCalculator();

  @ParameterizedTest(name = "{index} - parent gas {0}, used gas {1}, new excess {2}")
  @MethodSource("blobGasses")
  public void shouldCalculateExcessBlobGasCorrectly(
      final long parentExcess, final long used, final long expected) {
    final long usedBlobGas = cancunGasCalculator.blobGasCost(used);
    assertThat(cancunGasCalculator.computeExcessBlobGas(parentExcess, usedBlobGas))
        .isEqualTo(expected);
  }

  Iterable<Arguments> blobGasses() {
    long targetGasPerBlock = TARGET_BLOB_GAS_PER_BLOCK_CANCUN;
    return List.of(
        Arguments.of(0L, 0L, 0L),
        Arguments.of(targetGasPerBlock, 0L, 0L),
        Arguments.of(0L, 3, 0L),
        Arguments.of(1, 3, 1),
        Arguments.of(targetGasPerBlock, 1, cancunGasCalculator.getBlobGasPerBlob()),
        Arguments.of(targetGasPerBlock, 3, targetGasPerBlock));
  }

  @Test
  void dryRunDetector() {
    assertThat(true)
        .withFailMessage("This test is here so gradle --dry-run executes this class")
        .isTrue();
  }
}
