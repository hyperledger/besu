/*
 * Copyright Hyperledger Besu contributors.
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

public class CancunGasCalculatorTest {

  private final CancunGasCalculator gasCalculator = new CancunGasCalculator();

  @ParameterizedTest(name = "{index} - target gas {0}, excess gas {1}")
  @MethodSource("dataGasses")
  public void shouldCalculateExcessDataGasCorrectly(final long target, final long excess, final long expected) {
    assertThat(gasCalculator.computeExcessDataGas(target, excess)).isEqualTo(expected);
  }

  static Iterable<Arguments> dataGasses() {
    long targetGasPerBlock = 0x60000;
    long excess = 1L;
    return List.of(
            Arguments.of(0L,0L,0L),
            Arguments.of(targetGasPerBlock,0L,0L),
            Arguments.of(0L,targetGasPerBlock,0L),
            Arguments.of(excess,targetGasPerBlock,excess),
            Arguments.of(targetGasPerBlock,excess,excess),
            Arguments.of(targetGasPerBlock,targetGasPerBlock,targetGasPerBlock)
    );

  }
}
