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
package org.hyperledger.besu.evm.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.BerlinGasCalculator;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;

import org.junit.jupiter.api.Test;

class SlotNumOperationTest {
  private final GasCalculator gasCalculator = new BerlinGasCalculator();

  @Test
  void shouldReturnGasCost() {
    final MessageFrame frame = createMessageFrame(100L, 42L);
    final Operation operation = new SlotNumOperation(gasCalculator);
    final OperationResult result = operation.execute(frame, null);
    assertThat(result.getGasCost()).isEqualTo(gasCalculator.getBaseTierGasCost());
    assertSuccessResult(result);
  }

  @Test
  void shouldWriteSlotNumberToStack() {
    final long expectedSlotNumber = 12345L;
    final MessageFrame frame = createMessageFrame(100L, expectedSlotNumber);
    final Operation operation = new SlotNumOperation(gasCalculator);
    final OperationResult result = operation.execute(frame, null);
    verify(frame).pushStackItem(Words.longBytes(expectedSlotNumber));
    assertSuccessResult(result);
  }

  @Test
  void shouldHandleZeroSlotNumber() {
    final MessageFrame frame = createMessageFrame(100L, 0L);
    final Operation operation = new SlotNumOperation(gasCalculator);
    final OperationResult result = operation.execute(frame, null);
    verify(frame).pushStackItem(Words.longBytes(0L));
    assertSuccessResult(result);
  }

  @Test
  void shouldHandleLargeSlotNumber() {
    final long maxSlotNumber = Long.MAX_VALUE;
    final MessageFrame frame = createMessageFrame(100L, maxSlotNumber);
    final Operation operation = new SlotNumOperation(gasCalculator);
    final OperationResult result = operation.execute(frame, null);
    verify(frame).pushStackItem(Words.longBytes(maxSlotNumber));
    assertSuccessResult(result);
  }

  @Test
  void shouldHaveCorrectOpcode() {
    final Operation operation = new SlotNumOperation(gasCalculator);
    assertThat(operation.getOpcode()).isEqualTo(0x4b);
  }

  @Test
  void shouldHaveCorrectName() {
    final Operation operation = new SlotNumOperation(gasCalculator);
    assertThat(operation.getName()).isEqualTo("SLOTNUM");
  }

  @Test
  void shouldHaveCorrectStackBehavior() {
    final Operation operation = new SlotNumOperation(gasCalculator);
    assertThat(operation.getStackItemsConsumed()).isZero();
    assertThat(operation.getStackItemsProduced()).isEqualTo(1);
  }

  private void assertSuccessResult(final OperationResult result) {
    assertThat(result).isNotNull();
    assertThat(result.getHaltReason()).isNull();
  }

  private MessageFrame createMessageFrame(final long initialGas, final long slotNumber) {
    final MessageFrame frame = mock(MessageFrame.class);
    when(frame.getRemainingGas()).thenReturn(initialGas);
    final BlockValues blockValues = mock(BlockValues.class);
    when(blockValues.getSlotNumber()).thenReturn(slotNumber);
    when(frame.getBlockValues()).thenReturn(blockValues);
    return frame;
  }
}
