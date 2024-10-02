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
package org.hyperledger.besu.evm.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.evm.testutils.OperationsTestUtils.mockCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.testutils.TestMessageFrameBuilder;

import org.apache.tuweni.bytes.Bytes;
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
    final Code mockCode = mockCode("00".repeat(3) + "5c" + twosComplementJump);

    when(messageFrame.getCode()).thenReturn(mockCode);
    when(messageFrame.getRemainingGas()).thenReturn(3L);
    when(messageFrame.getPC()).thenReturn(rjumpOperationIndex);

    RelativeJumpOperation rjump = new RelativeJumpOperation(gasCalculator);
    Operation.OperationResult rjumpResult = rjump.execute(messageFrame, null);

    assertThat(rjumpResult.getPcIncrement())
        .isEqualTo(mockCode.getBytes().size() - rjumpOperationIndex + jumpLength);
  }

  @Test
  void rjumpiOperation() {
    final GasCalculator gasCalculator = mock(GasCalculator.class);
    final int rjumpOperationIndex = 3;
    final Code mockCode = mockCode("00".repeat(rjumpOperationIndex) + "5d0004");

    MessageFrame messageFrame =
        new TestMessageFrameBuilder()
            .code(mockCode)
            .pc(rjumpOperationIndex)
            .initialGas(5L)
            .pushStackItem(Bytes.EMPTY)
            .build();

    RelativeJumpIfOperation rjumpi = new RelativeJumpIfOperation(gasCalculator);
    Operation.OperationResult rjumpResult = rjumpi.execute(messageFrame, null);

    assertThat(rjumpResult.getPcIncrement()).isEqualTo(2 + 1);
  }

  @Test
  void rjumpiHitOperation() {
    final GasCalculator gasCalculator = mock(GasCalculator.class);
    final int rjumpOperationIndex = 3;
    final Code mockCode = mockCode("00".repeat(rjumpOperationIndex) + "5dfffc00");

    MessageFrame messageFrame =
        new TestMessageFrameBuilder()
            .code(mockCode)
            .pc(rjumpOperationIndex)
            .initialGas(5L)
            .pushStackItem(Words.intBytes(1))
            .build();

    RelativeJumpIfOperation rjumpi = new RelativeJumpIfOperation(gasCalculator);
    Operation.OperationResult rjumpResult = rjumpi.execute(messageFrame, null);

    assertThat(rjumpResult.getPcIncrement()).isEqualTo(-1);
  }

  @Test
  void rjumpvOperation() {
    final GasCalculator gasCalculator = mock(GasCalculator.class);
    final int rjumpOperationIndex = 3;
    final int jumpVectorSize = 1;
    final int jumpLength = 4;
    final Code mockCode =
        mockCode(
            "00".repeat(rjumpOperationIndex)
                + String.format("e2%02x%04x", jumpVectorSize - 1, jumpLength));

    MessageFrame messageFrame =
        new TestMessageFrameBuilder()
            .code(mockCode)
            .pc(rjumpOperationIndex)
            .initialGas(5L)
            .pushStackItem(Bytes.of(jumpVectorSize))
            .build();

    RelativeJumpVectorOperation rjumpv = new RelativeJumpVectorOperation(gasCalculator);
    Operation.OperationResult rjumpResult = rjumpv.execute(messageFrame, null);

    assertThat(rjumpResult.getPcIncrement()).isEqualTo(1 + 2 * jumpVectorSize + 1);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "0xff",
        "0x5f5f",
        "0xf5f5",
        "0x7fff",
        "0xffff",
        "0x5f5f5f5f",
        "0xf5f5f5f5",
        "0x7fffffff",
        "0xffffffff",
        "0x5f5f5f5f5f5f5f5f",
        "0xf5f5f5f5f5f5f5f5",
        "0x7fffffffffffffff",
        "0xffffffffffffffff",
        "0x5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f",
        "0xf5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5",
        "0x7fffffffffffffffffffffffffffffff",
        "0xffffffffffffffffffffffffffffffff",
        "0x5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f",
        "0xf5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5",
        "0x7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
        "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
      })
  void rjumpvOverflowOperation(final String stackValue) {
    final GasCalculator gasCalculator = mock(GasCalculator.class);
    final int rjumpOperationIndex = 3;
    final int jumpVectorSize = 255;
    final int jumpLength = 400;
    final Code mockCode =
        mockCode(
            "00".repeat(rjumpOperationIndex)
                + String.format("e2%02x", jumpVectorSize - 1)
                + String.format("%04x", jumpLength).repeat(jumpVectorSize));

    RelativeJumpVectorOperation rjumpv = new RelativeJumpVectorOperation(gasCalculator);
    MessageFrame messageFrame =
        new TestMessageFrameBuilder()
            .code(mockCode)
            .pc(rjumpOperationIndex)
            .initialGas(5L)
            .pushStackItem(Bytes.fromHexString(stackValue))
            .build();

    Operation.OperationResult rjumpResult = rjumpv.execute(messageFrame, null);

    assertThat(rjumpResult.getPcIncrement()).isEqualTo(1 + 2 * jumpVectorSize + 1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0x7f", "0xf5", "0x5f", "0xfe"})
  void rjumpvIndexOperation(final String stackValue) {
    final GasCalculator gasCalculator = mock(GasCalculator.class);
    final int rjumpOperationIndex = 3;
    final int jumpVectorSize = 255;
    final int jumpLength = 400;
    final Code mockCode =
        mockCode(
            "00".repeat(rjumpOperationIndex)
                + String.format("e2%02x", jumpVectorSize - 1)
                + String.format("%04x", jumpLength).repeat(jumpVectorSize));

    RelativeJumpVectorOperation rjumpv = new RelativeJumpVectorOperation(gasCalculator);
    MessageFrame messageFrame =
        new TestMessageFrameBuilder()
            .code(mockCode)
            .pc(rjumpOperationIndex)
            .initialGas(5L)
            .pushStackItem(Bytes.fromHexString(stackValue))
            .build();

    Operation.OperationResult rjumpResult = rjumpv.execute(messageFrame, null);

    assertThat(rjumpResult.getPcIncrement()).isEqualTo(2 + 2 * jumpVectorSize + jumpLength);
  }

  @Test
  void rjumpvHitOperation() {
    final GasCalculator gasCalculator = mock(GasCalculator.class);
    final int rjumpOperationIndex = 3;
    final int jumpVectorSize = 2;
    final Code mockCode =
        mockCode("00".repeat(rjumpOperationIndex) + "e2" + "01" + "1234" + "5678");

    MessageFrame messageFrame =
        new TestMessageFrameBuilder()
            .code(mockCode)
            .pc(rjumpOperationIndex)
            .initialGas(5L)
            .pushStackItem(Bytes.of(jumpVectorSize - 1))
            .build();

    RelativeJumpVectorOperation rjumpv = new RelativeJumpVectorOperation(gasCalculator);
    Operation.OperationResult rjumpResult = rjumpv.execute(messageFrame, null);

    assertThat(rjumpResult.getPcIncrement()).isEqualTo(2 + 2 * jumpVectorSize + 0x5678);
  }
}
