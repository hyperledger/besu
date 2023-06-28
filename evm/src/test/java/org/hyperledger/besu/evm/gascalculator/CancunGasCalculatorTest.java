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

public class CancunGasCalculatorTest {

  private final GasCalculator gasCalculator = new CancunGasCalculator();

  @Test
  public void shouldCalculateExcessDataGasCorrectly() {

    long target = CancunGasCalculator.getTargetDataGasPerBlock();
    long excess = 1L;

    assertThat(gasCalculator.computeExcessDataGas(0L, 0L)).isEqualTo(0);

    assertThat(gasCalculator.computeExcessDataGas(target, 0L)).isEqualTo(0);

    assertThat(gasCalculator.computeExcessDataGas(0L, target)).isEqualTo(0);

    assertThat(gasCalculator.computeExcessDataGas(excess, target)).isEqualTo(excess);

    assertThat(gasCalculator.computeExcessDataGas(target, excess)).isEqualTo(excess);

    assertThat(gasCalculator.computeExcessDataGas(target, target)).isEqualTo(target);
  }
}
