/*
 * Copyright contributors to Hyperledger Besu
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
 *
 */
package org.hyperledger.besu.ethereum.vm.operations;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.RelativeJumpIfOperation;
import org.hyperledger.besu.evm.operation.RelativeJumpOperation;
import org.hyperledger.besu.evm.operation.RelativeJumpVectorOperation;

import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

class RelativeJumpOperationTest {

  @ParameterizedTest
  @ValueSource(ints = {1, 0, 9, -4, -5})
  void rjumpOperation(final int jumpLength) {
    final GasCalculator gasCalculator = mock(GasCalculator.class);
    final MessageFrame messageFrame = mock(MessageFrame.class, Mockito.RETURNS_DEEP_STUBS);
    final String twosComplementJump = String.format("%08x", jumpLength).substring(4);
    final int rjumpOperationIndex = 3;
    final Bytes code = Bytes.fromHexString("00".repeat(3) + "5c" + twosComplementJump);

    when(messageFrame.getCode().getCodeBytes(messageFrame.getSection())).thenReturn(code);
    when(messageFrame.getRemainingGas()).thenReturn(3L);
    when(messageFrame.getPC()).thenReturn(rjumpOperationIndex);

    RelativeJumpOperation rjump = new RelativeJumpOperation(gasCalculator);
    Operation.OperationResult rjumpResult = rjump.execute(messageFrame, null);

    Assertions.assertThat(rjumpResult.getPcIncrement())
        .isEqualTo(code.size() - rjumpOperationIndex + jumpLength);
  }

  @Test
  void rjumpiOperation() {
    final GasCalculator gasCalculator = mock(GasCalculator.class);
    final MessageFrame messageFrame = mock(MessageFrame.class, Mockito.RETURNS_DEEP_STUBS);
    final int rjumpOperationIndex = 3;
    final Bytes code = Bytes.fromHexString("00".repeat(rjumpOperationIndex) + "5d0004");

    when(messageFrame.getCode().getCodeBytes(messageFrame.getSection())).thenReturn(code);
    when(messageFrame.getPC()).thenReturn(rjumpOperationIndex);
    when(messageFrame.getRemainingGas()).thenReturn(5L);
    when(messageFrame.popStackItem()).thenReturn(Bytes.EMPTY);

    RelativeJumpIfOperation rjumpi = new RelativeJumpIfOperation(gasCalculator);
    Operation.OperationResult rjumpResult = rjumpi.execute(messageFrame, null);

    Assertions.assertThat(rjumpResult.getPcIncrement()).isEqualTo(2 + 1);
  }

  @Test
  void rjumpvOperation() {
    final GasCalculator gasCalculator = mock(GasCalculator.class);
    final MessageFrame messageFrame = mock(MessageFrame.class, Mockito.RETURNS_DEEP_STUBS);
    final int rjumpOperationIndex = 3;
    final int jumpVectorSize = 1;
    final int jumpLength = 4;
    final Bytes code =
        Bytes.fromHexString(
            "00".repeat(rjumpOperationIndex)
                + String.format("5e%02x%04x", jumpVectorSize, jumpLength));

    when(messageFrame.getCode().getCodeBytes(messageFrame.getSection())).thenReturn(code);
    when(messageFrame.getPC()).thenReturn(rjumpOperationIndex);
    when(messageFrame.getRemainingGas()).thenReturn(5L);
    when(messageFrame.popStackItem()).thenReturn(Bytes.of(jumpVectorSize));

    RelativeJumpVectorOperation rjumpv = new RelativeJumpVectorOperation(gasCalculator);
    Operation.OperationResult rjumpResult = rjumpv.execute(messageFrame, null);

    Assertions.assertThat(rjumpResult.getPcIncrement()).isEqualTo(1 + 2 * jumpVectorSize + 1);
  }
}
