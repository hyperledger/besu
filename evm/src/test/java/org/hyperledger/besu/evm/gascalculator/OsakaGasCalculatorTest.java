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

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class OsakaGasCalculatorTest {

  @Test
  void testPrecompileSize() {
    OsakaGasCalculator subject = new OsakaGasCalculator();
    // past last l1 precompile address
    assertThat(subject.isPrecompile(Address.precompiled(0x12))).isFalse();
    // past last l2 precompile address
    assertThat(subject.isPrecompile(Address.precompiled(0x0101))).isFalse();
    assertThat(subject.isPrecompile(Address.P256_VERIFY)).isTrue();
    assertThat(subject.isPrecompile(Address.BLS12_MAP_FP2_TO_G2)).isTrue();
    assertThat(subject.isPrecompile(Address.precompiled(0x00))).isFalse();
  }

  @Test
  void testZeroLengthBaseModCost() {
    OsakaGasCalculator subject = new OsakaGasCalculator();
    Bytes input =
        Bytes.fromHexString(
            "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000000660bfd6246b7f481d4a9955bb2bd9ee970e21063193ae484c413aea066fc949d");
    assertThat(subject.modExpGasCost(input)).isEqualTo(4064L);
  }
}
