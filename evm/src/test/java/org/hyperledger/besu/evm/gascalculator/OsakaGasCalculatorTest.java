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

import org.junit.jupiter.api.Test;

class OsakaGasCalculatorTest {

  @Test
  void testPrecompileSize() {
    OsakaGasCalculator subject = new OsakaGasCalculator();
    assertThat(subject.isPrecompile(Address.precompiled(0x14))).isFalse();
    assertThat(subject.isPrecompile(Address.BLS12_MAP_FP2_TO_G2)).isTrue();
  }

  @Test
  void testNewConstants() {
    CancunGasCalculator cancunGas = new CancunGasCalculator();
    OsakaGasCalculator praugeGasCalculator = new OsakaGasCalculator();

    assertThat(praugeGasCalculator.getMinCalleeGas()).isGreaterThan(cancunGas.getMinCalleeGas());
    assertThat(praugeGasCalculator.getMinRetainedGas())
        .isGreaterThan(cancunGas.getMinRetainedGas());
  }
}
