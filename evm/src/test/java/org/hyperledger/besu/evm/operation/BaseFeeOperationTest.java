/*
 * Copyright ConsenSys AG.
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

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.BerlinGasCalculator;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;

class BaseFeeOperationTest {
  private final GasCalculator gasCalculator = new BerlinGasCalculator();

  @Test
  void shouldReturnGasCost() {
    final MessageFrame frame = createMessageFrame(100, Optional.of(Wei.of(5L)));
    final Operation operation = new BaseFeeOperation(gasCalculator);
    final OperationResult result = operation.execute(frame, null);
    assertThat(result.getGasCost()).isEqualTo(gasCalculator.getBaseTierGasCost());
    assertSuccessResult(result);
  }

  @Test
  void shouldWriteBaseFeeToStack() {
    final MessageFrame frame = createMessageFrame(100, Optional.of(Wei.of(5L)));
    final Operation operation = new BaseFeeOperation(gasCalculator);
    final OperationResult result = operation.execute(frame, null);
    verify(frame).pushStackItem(UInt256.fromBytes(Bytes32.leftPad(Words.longBytes(5L))));
    assertSuccessResult(result);
  }

  @Test
  void shouldHaltIfNoBaseFeeInBlockHeader() {
    final MessageFrame frame = createMessageFrame(100, Optional.empty());
    final Operation operation = new BaseFeeOperation(gasCalculator);
    final OperationResult result = operation.execute(frame, null);
    assertExceptionalHalt(result, ExceptionalHaltReason.INVALID_OPERATION);
  }

  private void assertSuccessResult(final OperationResult result) {
    assertThat(result).isNotNull();
    assertThat(result.getHaltReason()).isNull();
  }

  private void assertExceptionalHalt(
      final OperationResult result, final ExceptionalHaltReason reason) {
    assertThat(result).isNotNull();
    assertThat(result.getHaltReason()).isEqualTo(reason);
  }

  private MessageFrame createMessageFrame(final long initialGas, final Optional<Wei> baseFee) {
    final MessageFrame frame = mock(MessageFrame.class);
    when(frame.getRemainingGas()).thenReturn(initialGas);
    final BlockValues blockHeader = mock(BlockValues.class);
    when(blockHeader.getBaseFee()).thenReturn(baseFee);
    when(frame.getBlockValues()).thenReturn(blockHeader);
    return frame;
  }
}
