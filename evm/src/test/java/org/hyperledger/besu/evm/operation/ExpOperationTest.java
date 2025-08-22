/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.evm.operation;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import org.junit.jupiter.api.Test;

public class ExpOperationTest extends BaseOperationTest {

  @Test
  void exp_anyToPower0_shouldBeOne() {
    final MessageFrame frame = mock(MessageFrame.class);
    final GasCalculator gasCalculator = mock(GasCalculator.class);

    popStackItemsFromHexString(frame, "0x123456", "0x00");

    when(gasCalculator.expOperationGasCost(0)).thenReturn(10L);
    when(frame.getRemainingGas()).thenReturn(100L);

    final var result = ExpOperation.staticOperation(frame, gasCalculator);

    verifyPushStackItemFromHexString(frame, "0x01");
    assertThat(result.getHaltReason()).isNull();
  }

  @Test
  void exp_zeroToPowerN_shouldBeZero() {
    final MessageFrame frame = mock(MessageFrame.class);
    final GasCalculator gasCalculator = mock(GasCalculator.class);

    popStackItemsFromHexString(frame, "0x00", "0x1234");

    when(gasCalculator.expOperationGasCost(anyInt())).thenReturn(10L);
    when(frame.getRemainingGas()).thenReturn(100L);

    final var result = ExpOperation.staticOperation(frame, gasCalculator);

    verifyPushStackItemFromHexString(frame, "0x00");
    assertThat(result.getHaltReason()).isNull();
  }

  @Test
  void exp_oneToAnyPower_shouldBeOne() {
    final MessageFrame frame = mock(MessageFrame.class);
    final GasCalculator gasCalculator = mock(GasCalculator.class);

    popStackItemsFromHexString(frame, "0x01", "0xffff");

    when(gasCalculator.expOperationGasCost(anyInt())).thenReturn(10L);
    when(frame.getRemainingGas()).thenReturn(100L);

    final var result = ExpOperation.staticOperation(frame, gasCalculator);

    verifyPushStackItemFromHexString(frame, "0x01");
    assertThat(result.getHaltReason()).isNull();
  }

  @Test
  void exp_twoPowThree_shouldBeEight() {
    final MessageFrame frame = mock(MessageFrame.class);
    final GasCalculator gasCalculator = mock(GasCalculator.class);

    popStackItemsFromHexString(frame, "0x02", "0x03");

    when(gasCalculator.expOperationGasCost(anyInt())).thenReturn(10L);
    when(frame.getRemainingGas()).thenReturn(100L);

    final var result = ExpOperation.staticOperation(frame, gasCalculator);

    verifyPushStackItemFromHexString(frame, "0x08");
    assertThat(result.getHaltReason()).isNull();
  }

  @Test
  void exp_largeBaseTruncatesTo256Bits() {
    final MessageFrame frame = mock(MessageFrame.class);
    final GasCalculator gasCalculator = mock(GasCalculator.class);

    popStackItemsFromHexString(
        frame, "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", "0x02");

    when(gasCalculator.expOperationGasCost(anyInt())).thenReturn(10L);
    when(frame.getRemainingGas()).thenReturn(100L);

    final var result = ExpOperation.staticOperation(frame, gasCalculator);

    verifyPushStackItemFromHexString(
        frame, "0x0000000000000000000000000000000000000000000000000000000000000001");
    assertThat(result.getHaltReason()).isNull();
  }

  @Test
  void exp_insufficientGas_shouldHalt() {
    final MessageFrame frame = mock(MessageFrame.class);
    final GasCalculator gasCalculator = mock(GasCalculator.class);

    popStackItemsFromHexString(frame, "0x02", "0xffff");

    when(gasCalculator.expOperationGasCost(anyInt())).thenReturn(1000L);
    when(frame.getRemainingGas()).thenReturn(100L);

    final var result = ExpOperation.staticOperation(frame, gasCalculator);

    verify(frame, never()).pushStackItem(any());
    assertThat(result.getHaltReason()).isNotNull();
  }
}
