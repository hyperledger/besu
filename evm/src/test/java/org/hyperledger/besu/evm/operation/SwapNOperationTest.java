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

import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.PragueGasCalculator;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.testutils.TestMessageFrameBuilder;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Unit tests for {@link SwapNOperation}. */
class SwapNOperationTest {

  private final SwapNOperation operation = new SwapNOperation(new PragueGasCalculator());

  @Test
  void testDecodeSingle_validLowRange() {
    // x = 0 -> n = 17
    assertThat(SwapNOperation.decodeSingle(0)).isEqualTo(17);
    // x = 90 -> n = 107
    assertThat(SwapNOperation.decodeSingle(90)).isEqualTo(107);
  }

  @Test
  void testDecodeSingle_validHighRange() {
    // x = 128 -> n = 108
    assertThat(SwapNOperation.decodeSingle(128)).isEqualTo(108);
    // x = 255 -> n = 235
    assertThat(SwapNOperation.decodeSingle(255)).isEqualTo(235);
  }

  @ParameterizedTest
  @ValueSource(ints = {91, 100, 110, 127})
  void testDecodeSingle_invalidRange(final int imm) {
    assertThat(SwapNOperation.decodeSingle(imm)).isEqualTo(-1);
  }

  @Test
  void testSwapN_basicOperation() {
    // SWAPN with immediate 0 -> n=17, swaps top with 18th item (index 17)
    final Bytes code = Bytes.of(0xe7, 0x00); // SWAPN 17
    final TestMessageFrameBuilder builder =
        new TestMessageFrameBuilder().code(new Code(code)).pc(0);

    // Push 18 items: bottom=18, ..., top=1
    for (int i = 18; i >= 1; i--) {
      builder.pushStackItem(Bytes.of(i));
    }
    final MessageFrame frame = builder.build();

    // Before: stack[0]=1, stack[17]=18
    assertThat(frame.getStackItem(0)).isEqualTo(Bytes.of(1));
    assertThat(frame.getStackItem(17)).isEqualTo(Bytes.of(18));

    final OperationResult result = operation.execute(frame, null);

    assertThat(result.getHaltReason()).isNull();
    assertThat(result.getGasCost()).isEqualTo(3);
    assertThat(result.getPcIncrement()).isEqualTo(2);

    // After: stack[0]=18, stack[17]=1
    assertThat(frame.getStackItem(0)).isEqualTo(Bytes.of(18));
    assertThat(frame.getStackItem(17)).isEqualTo(Bytes.of(1));
    // Stack size unchanged
    assertThat(frame.stackSize()).isEqualTo(18);
  }

  @Test
  void testSwapN_immediate128() {
    // SWAPN with immediate 128 (0x80) -> n=108, swaps top with 109th item
    final Bytes code = Bytes.fromHexString("e780"); // SWAPN 108
    final TestMessageFrameBuilder builder =
        new TestMessageFrameBuilder().code(new Code(code)).pc(0);

    // Push 109 items using Bytes.ofUnsignedInt to avoid byte range issues
    for (int i = 109; i >= 1; i--) {
      builder.pushStackItem(Bytes.ofUnsignedInt(i));
    }
    final MessageFrame frame = builder.build();

    // Before: stack[0]=1, stack[108]=109
    assertThat(frame.getStackItem(0).toInt()).isEqualTo(1);
    assertThat(frame.getStackItem(108).toInt()).isEqualTo(109);

    final OperationResult result = operation.execute(frame, null);

    assertThat(result.getHaltReason()).isNull();

    // After: swapped
    assertThat(frame.getStackItem(0).toInt()).isEqualTo(109);
    assertThat(frame.getStackItem(108).toInt()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(ints = {91, 0x5b, 0x60, 0x7f, 100, 127})
  void testSwapN_invalidImmediate(final int imm) {
    final Bytes code = Bytes.wrap(new byte[] {(byte) 0xe7, (byte) imm});
    final TestMessageFrameBuilder builder =
        new TestMessageFrameBuilder().code(new Code(code)).pc(0);

    for (int i = 0; i < 250; i++) {
      builder.pushStackItem(Bytes.ofUnsignedInt(i));
    }
    final MessageFrame frame = builder.build();

    final OperationResult result = operation.execute(frame, null);

    assertThat(result.getHaltReason()).isEqualTo(ExceptionalHaltReason.INVALID_OPERATION);
    assertThat(result.getPcIncrement()).isEqualTo(2);
  }

  @Test
  void testSwapN_stackUnderflow() {
    // SWAPN with immediate 0 -> n=17, needs 18 items but only 10
    final Bytes code = Bytes.of(0xe7, 0x00);
    final TestMessageFrameBuilder builder =
        new TestMessageFrameBuilder().code(new Code(code)).pc(0);

    for (int i = 0; i < 10; i++) {
      builder.pushStackItem(Bytes.of(i));
    }
    final MessageFrame frame = builder.build();

    final OperationResult result = operation.execute(frame, null);

    assertThat(result.getHaltReason()).isEqualTo(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
    assertThat(result.getPcIncrement()).isEqualTo(2);
  }

  @Test
  void testSwapN_endOfCode() {
    // Code ends right after opcode
    final Bytes code = Bytes.of(0xe7);
    final TestMessageFrameBuilder builder =
        new TestMessageFrameBuilder().code(new Code(code)).pc(0);

    for (int i = 18; i >= 1; i--) {
      builder.pushStackItem(Bytes.of(i));
    }
    final MessageFrame frame = builder.build();

    final OperationResult result = operation.execute(frame, null);

    // Immediate 0 is valid
    assertThat(result.getHaltReason()).isNull();
    // Verify swap happened
    assertThat(frame.getStackItem(0)).isEqualTo(Bytes.of(18));
    assertThat(frame.getStackItem(17)).isEqualTo(Bytes.of(1));
  }

  @Test
  void testSwapN_gasCost() {
    final Bytes code = Bytes.of(0xe7, 0x00);
    final TestMessageFrameBuilder builder =
        new TestMessageFrameBuilder().code(new Code(code)).pc(0).initialGas(100);

    for (int i = 18; i >= 1; i--) {
      builder.pushStackItem(Bytes.of(i));
    }
    final MessageFrame frame = builder.build();

    final OperationResult result = operation.execute(frame, null);

    assertThat(result.getGasCost()).isEqualTo(3);
  }
}
