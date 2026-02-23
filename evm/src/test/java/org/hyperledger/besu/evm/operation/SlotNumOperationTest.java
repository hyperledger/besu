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
import static org.mockito.Mockito.when;

import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.BerlinGasCalculator;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.testutils.TestMessageFrameBuilder;

import org.junit.jupiter.api.Test;

class SlotNumOperationTest {
  private final GasCalculator gasCalculator = new BerlinGasCalculator();

  @Test
  void shouldReturnGasCost() {
    final MessageFrame frame = createMessageFrame(42L);
    final Operation operation = new SlotNumOperation(gasCalculator);
    final OperationResult result = operation.execute(frame, null);
    assertThat(result.getGasCost()).isEqualTo(gasCalculator.getBaseTierGasCost());
    assertThat(result.getHaltReason()).isNull();
  }

  @Test
  void shouldWriteSlotNumberToStack() {
    final long expectedSlotNumber = 12345L;
    final MessageFrame frame = createMessageFrame(expectedSlotNumber);
    final Operation operation = new SlotNumOperation(gasCalculator);
    operation.execute(frame, null);
    assertThat(frame.getStackItem(0))
        .isEqualTo(org.hyperledger.besu.evm.UInt256.fromLong(expectedSlotNumber));
  }

  @Test
  void shouldHandleZeroSlotNumber() {
    final MessageFrame frame = createMessageFrame(0L);
    final Operation operation = new SlotNumOperation(gasCalculator);
    operation.execute(frame, null);
    assertThat(frame.getStackItem(0)).isEqualTo(org.hyperledger.besu.evm.UInt256.ZERO);
  }

  @Test
  void shouldHandleLargeSlotNumber() {
    final long maxSlotNumber = Long.MAX_VALUE;
    final MessageFrame frame = createMessageFrame(maxSlotNumber);
    final Operation operation = new SlotNumOperation(gasCalculator);
    operation.execute(frame, null);
    assertThat(frame.getStackItem(0))
        .isEqualTo(org.hyperledger.besu.evm.UInt256.fromLong(maxSlotNumber));
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

  private MessageFrame createMessageFrame(final long slotNumber) {
    final BlockValues blockValues = mock(BlockValues.class);
    when(blockValues.getSlotNumber()).thenReturn(slotNumber);
    return new TestMessageFrameBuilder().blockValues(blockValues).build();
  }
}
