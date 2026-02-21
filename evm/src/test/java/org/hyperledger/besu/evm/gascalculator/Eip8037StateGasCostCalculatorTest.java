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

import org.junit.jupiter.api.Test;

class Eip8037StateGasCostCalculatorTest {

  private static final long MAINNET_GAS_LIMIT = 36_000_000L;

  private final Eip8037StateGasCostCalculator calculator = new Eip8037StateGasCostCalculator();

  @Test
  void costPerStateByteAtMainnetGasLimit() {
    // raw cpsb = ceil(440.607...) = 441, quantized with offset 9578 → 150
    assertThat(calculator.costPerStateByte(MAINNET_GAS_LIMIT)).isEqualTo(150L);
  }

  @Test
  void costPerStateByteAt60MGasLimit() {
    assertThat(calculator.costPerStateByte(60_000_000L)).isEqualTo(662L);
  }

  @Test
  void costPerStateByteAt100MGasLimit() {
    assertThat(calculator.costPerStateByte(100_000_000L)).isEqualTo(1174L);
  }

  @Test
  void costPerStateByteAtZeroGasLimit() {
    assertThat(calculator.costPerStateByte(0L)).isEqualTo(0L);
  }

  @Test
  void costPerStateByteAtMinimalGasLimits() {
    // gasLimit=1: (1/2)=0 in integer division, numerator=0, raw=0, return 0
    assertThat(calculator.costPerStateByte(1L)).isEqualTo(0L);
    // gasLimit=2: raw=1, shifted=9579, bitLen=14, shift=9,
    // ((9579>>9)<<9) = 18*512 = 9216, 9216-9578 = -362, max(-362, 1) = 1
    assertThat(calculator.costPerStateByte(2L)).isEqualTo(1L);
  }

  @Test
  void createStateGas() {
    // 112 bytes per account * cpsb(150) = 16_800
    assertThat(calculator.createStateGas(MAINNET_GAS_LIMIT)).isEqualTo(112L * 150L);
  }

  @Test
  void storageSetStateGas() {
    // 32 bytes per storage slot * cpsb(150) = 4_800
    assertThat(calculator.storageSetStateGas(MAINNET_GAS_LIMIT)).isEqualTo(32L * 150L);
  }

  @Test
  void codeDepositStateGas() {
    // cpsb * codeSize
    assertThat(calculator.codeDepositStateGas(100, MAINNET_GAS_LIMIT)).isEqualTo(150L * 100L);
    assertThat(calculator.codeDepositStateGas(0, MAINNET_GAS_LIMIT)).isEqualTo(0L);
  }

  @Test
  void codeDepositHashGas() {
    // 6 * ceil(codeSize / 32)
    assertThat(calculator.codeDepositHashGas(0)).isEqualTo(0L);
    assertThat(calculator.codeDepositHashGas(1)).isEqualTo(6L);
    assertThat(calculator.codeDepositHashGas(32)).isEqualTo(6L);
    assertThat(calculator.codeDepositHashGas(33)).isEqualTo(12L);
    assertThat(calculator.codeDepositHashGas(100)).isEqualTo(24L);
  }

  @Test
  void newAccountStateGasMatchesCreate() {
    assertThat(calculator.newAccountStateGas(MAINNET_GAS_LIMIT))
        .isEqualTo(calculator.createStateGas(MAINNET_GAS_LIMIT));
  }

  @Test
  void authBaseStateGas() {
    // 23 bytes per auth * cpsb(150) = 3_450
    assertThat(calculator.authBaseStateGas(MAINNET_GAS_LIMIT)).isEqualTo(23L * 150L);
  }

  @Test
  void emptyAccountDelegationStateGasMatchesCreate() {
    assertThat(calculator.emptyAccountDelegationStateGas(MAINNET_GAS_LIMIT))
        .isEqualTo(calculator.createStateGas(MAINNET_GAS_LIMIT));
  }

  @Test
  void constantRegularGasCosts() {
    assertThat(calculator.storageSetRegularGas()).isEqualTo(2_900L);
    assertThat(calculator.authBaseRegularGas()).isEqualTo(7_500L);
    assertThat(calculator.transactionRegularGasLimit()).isEqualTo(16_777_216L);
  }

  @Test
  void noneImplementationReturnsZeroForAllCosts() {
    final StateGasCostCalculator none = StateGasCostCalculator.NONE;
    assertThat(none.costPerStateByte(MAINNET_GAS_LIMIT)).isEqualTo(0L);
    assertThat(none.createStateGas(MAINNET_GAS_LIMIT)).isEqualTo(0L);
    assertThat(none.storageSetStateGas(MAINNET_GAS_LIMIT)).isEqualTo(0L);
    assertThat(none.codeDepositStateGas(100, MAINNET_GAS_LIMIT)).isEqualTo(0L);
    assertThat(none.codeDepositHashGas(100)).isEqualTo(0L);
    assertThat(none.newAccountStateGas(MAINNET_GAS_LIMIT)).isEqualTo(0L);
    assertThat(none.authBaseStateGas(MAINNET_GAS_LIMIT)).isEqualTo(0L);
    assertThat(none.emptyAccountDelegationStateGas(MAINNET_GAS_LIMIT)).isEqualTo(0L);
    assertThat(none.storageSetRegularGas()).isEqualTo(0L);
    assertThat(none.authBaseRegularGas()).isEqualTo(0L);
    assertThat(none.transactionRegularGasLimit()).isEqualTo(Long.MAX_VALUE);
  }
}
