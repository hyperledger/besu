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
package org.hyperledger.besu.evm.gascalculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.MessageFrame;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OsakaGasCalculatorTest {

  private static final int MAX_NO_COST_CODE_SIZE = 0x6000; // 24KB
  private static final int COLD_LARGE_CODE_SIZE = MAX_NO_COST_CODE_SIZE + 1;
  private static final int VERY_LARGE_CODE_SIZE = 0x40000; // 256KB
  private static final int MAX_INITCODE_SIZE = 0x80000; // 512KB

  private static final Address ADDRESS = Address.fromHexString("0x01");

  private OsakaGasCalculator gasCalculator;
  private MessageFrame frame;
  private Account account;

  @BeforeEach
  void setUp() {
    gasCalculator = new OsakaGasCalculator();
    frame = mock(MessageFrame.class);
    account = mock(Account.class);
  }

  // Precompile range test
  @Test
  void shouldIdentifyPrecompileAddressesCorrectly() {
    OsakaGasCalculator subject = new OsakaGasCalculator();
    assertThat(subject.isPrecompile(Address.precompiled(0x14))).isFalse();
    assertThat(subject.isPrecompile(Address.BLS12_MAP_FP2_TO_G2)).isTrue();
  }

  // Code access gas (EIP-7907)
  @Test
  void shouldNotChargeGasWhenAccountIsNull() {
    assertThat(gasCalculator.calculateCodeAccessGas(frame, null)).isEqualTo(0L);
  }

  @Test
  void shouldNotChargeGasWhenCodeHashIsEmpty() {
    when(account.getCodeHash()).thenReturn(Hash.EMPTY);
    assertThat(gasCalculator.calculateCodeAccessGas(frame, account)).isEqualTo(0L);
  }

  @Test
  void shouldNotChargeGasWhenCodeSizeIsZero() {
    assertCodeAccessCost(0, false, 0L);
  }

  @Test
  void shouldNotChargeGasWhenCodeSizeIsBelowThreshold() {
    assertCodeAccessCost(MAX_NO_COST_CODE_SIZE - 1, false, 0L);
  }

  @Test
  void shouldNotChargeGasWhenCodeSizeEqualsThreshold() {
    assertCodeAccessCost(MAX_NO_COST_CODE_SIZE, false, 0L);
  }

  @Test
  void shouldNotChargeGasWhenCodeIsAlreadyWarm() {
    assertCodeAccessCost(COLD_LARGE_CODE_SIZE, true, 0L);
  }

  @Test
  void shouldChargeGasWhenCodeIsColdAndExceedsThreshold() {
    assertCodeAccessCost(COLD_LARGE_CODE_SIZE, false, 2L);
  }

  @Test
  void shouldChargeProportionalGasForVeryLargeColdCode() {
    assertCodeAccessCost(VERY_LARGE_CODE_SIZE, false, 14848L);
  }

  // Delegation resolution gas (EIP-7907)
  @Test
  void shouldIncludeExcessCodeAccessGasInDelegationCost() {
    mockAccount(COLD_LARGE_CODE_SIZE, false);
    long result = gasCalculator.calculateCodeDelegationResolutionGas(frame, account);
    assertThat(result).isEqualTo(2L);
  }

  @Test
  void shouldChargeGasWhenInitcodeExceedsThreshold() {
    assertThat(gasCalculator.initcodeCost(COLD_LARGE_CODE_SIZE)).isEqualTo(1538L);
  }

  @Test
  void shouldChargeGasWhenInitcodeEqualsThreshold() {
    assertThat(gasCalculator.initcodeCost(MAX_NO_COST_CODE_SIZE)).isEqualTo(1536L);
  }

  @Test
  void shouldChargeGasWhenInitcodeJustBelowThreshold() {
    assertThat(gasCalculator.initcodeCost(MAX_NO_COST_CODE_SIZE - 1)).isEqualTo(1536L);
  }

  @Test
  void shouldChargeMaximumGasWhenInitcodeIsAtMaximumAllowedSize() {
    assertThat(gasCalculator.initcodeCost(MAX_INITCODE_SIZE)).isEqualTo(32768L);
  }

  private void assertCodeAccessCost(
      final int codeSize, final boolean isWarm, final long expectedCost) {
    mockAccount(codeSize, isWarm);
    long result = gasCalculator.calculateCodeAccessGas(frame, account);
    verify(frame).warmUpCode(ADDRESS);
    assertThat(result).isEqualTo(expectedCost);
  }

  private void mockAccount(final int codeSize, final boolean isWarm) {
    when(account.getCodeHash()).thenReturn(Hash.hash(Bytes.of(1)));
    when(account.getAddress()).thenReturn(ADDRESS);
    when(account.getCodeSize()).thenReturn(codeSize);
    when(frame.warmUpCode(any())).thenReturn(isWarm);
  }
}
