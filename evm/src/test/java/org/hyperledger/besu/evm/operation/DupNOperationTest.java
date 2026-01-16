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

/** Unit tests for {@link DupNOperation}. */
class DupNOperationTest {

  private final DupNOperation operation = new DupNOperation(new PragueGasCalculator());

  @Test
  void testDecodeSingle_validLowRange() {
    // x = 0 -> n = 17
    assertThat(DupNOperation.decodeSingle(0)).isEqualTo(17);
    // x = 90 -> n = 107
    assertThat(DupNOperation.decodeSingle(90)).isEqualTo(107);
    // x = 45 -> n = 62
    assertThat(DupNOperation.decodeSingle(45)).isEqualTo(62);
  }

  @Test
  void testDecodeSingle_validHighRange() {
    // x = 128 -> n = 108
    assertThat(DupNOperation.decodeSingle(128)).isEqualTo(108);
    // x = 255 -> n = 235
    assertThat(DupNOperation.decodeSingle(255)).isEqualTo(235);
    // x = 200 -> n = 180
    assertThat(DupNOperation.decodeSingle(200)).isEqualTo(180);
  }

  @ParameterizedTest
  @ValueSource(ints = {91, 100, 110, 127})
  void testDecodeSingle_invalidRange(final int imm) {
    // Invalid range: 91-127
    assertThat(DupNOperation.decodeSingle(imm)).isEqualTo(-1);
  }

  @Test
  void testDupN_basicOperation() {
    // DUPN with immediate 0 -> n=17, duplicates 17th item
    // Set up stack with 17 items, verify the 17th (bottom) item is duplicated
    final Bytes code = Bytes.of(0xe6, 0x00); // DUPN 17
    final TestMessageFrameBuilder builder =
        new TestMessageFrameBuilder().code(new Code(code)).pc(0);

    // Push 17 items onto stack (first pushed = bottom = item 17)
    for (int i = 17; i >= 1; i--) {
      builder.pushStackItem(Bytes.of(i));
    }
    final MessageFrame frame = builder.build();

    final OperationResult result = operation.execute(frame, null);

    assertThat(result.getHaltReason()).isNull();
    assertThat(result.getGasCost()).isEqualTo(3);
    assertThat(result.getPcIncrement()).isEqualTo(2);
    // Top of stack should now be a copy of item 17 (value 17)
    assertThat(frame.getStackItem(0)).isEqualTo(Bytes.of(17));
    // Stack should have 18 items now
    assertThat(frame.stackSize()).isEqualTo(18);
  }

  @Test
  void testDupN_immediate90() {
    // DUPN with immediate 90 -> n=107
    // Set up minimal test to verify decode works correctly
    final Bytes code = Bytes.of(0xe6, 90); // DUPN 107
    final TestMessageFrameBuilder builder =
        new TestMessageFrameBuilder().code(new Code(code)).pc(0);

    // Push 107 items
    for (int i = 107; i >= 1; i--) {
      builder.pushStackItem(Bytes.of((byte) (i & 0xFF)));
    }
    final MessageFrame frame = builder.build();

    final OperationResult result = operation.execute(frame, null);

    assertThat(result.getHaltReason()).isNull();
    assertThat(result.getPcIncrement()).isEqualTo(2);
    // Should duplicate item 107 (value 107)
    assertThat(frame.getStackItem(0).toInt()).isEqualTo(107);
  }

  @Test
  void testDupN_immediate128() {
    // DUPN with immediate 128 (0x80) -> n=108
    final Bytes code = Bytes.fromHexString("e680"); // DUPN 108
    final TestMessageFrameBuilder builder =
        new TestMessageFrameBuilder().code(new Code(code)).pc(0);

    // Push 108 items using 32-byte values to avoid byte range issues
    for (int i = 108; i >= 1; i--) {
      builder.pushStackItem(Bytes.ofUnsignedInt(i));
    }
    final MessageFrame frame = builder.build();

    final OperationResult result = operation.execute(frame, null);

    assertThat(result.getHaltReason()).isNull();
    assertThat(frame.getStackItem(0).toInt()).isEqualTo(108);
  }

  @ParameterizedTest
  @ValueSource(ints = {91, 0x5b, 0x60, 0x7f, 100, 127})
  void testDupN_invalidImmediate(final int imm) {
    // Invalid immediate range: 91-127 (includes JUMPDEST 0x5b and PUSH ops 0x60-0x7f)
    final Bytes code = Bytes.wrap(new byte[] {(byte) 0xe6, (byte) imm});
    final TestMessageFrameBuilder builder =
        new TestMessageFrameBuilder().code(new Code(code)).pc(0);

    // Add enough stack items that underflow won't be the issue
    for (int i = 0; i < 250; i++) {
      builder.pushStackItem(Bytes.ofUnsignedInt(i));
    }
    final MessageFrame frame = builder.build();

    final OperationResult result = operation.execute(frame, null);

    assertThat(result.getHaltReason()).isEqualTo(ExceptionalHaltReason.INVALID_OPERATION);
    assertThat(result.getPcIncrement()).isEqualTo(2);
  }

  @Test
  void testDupN_stackUnderflow() {
    // DUPN with immediate 0 -> n=17, but only 10 items on stack
    final Bytes code = Bytes.of(0xe6, 0x00); // DUPN 17
    final TestMessageFrameBuilder builder =
        new TestMessageFrameBuilder().code(new Code(code)).pc(0);

    // Push only 10 items (need 17)
    for (int i = 0; i < 10; i++) {
      builder.pushStackItem(Bytes.of(i));
    }
    final MessageFrame frame = builder.build();

    final OperationResult result = operation.execute(frame, null);

    assertThat(result.getHaltReason()).isEqualTo(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
    assertThat(result.getPcIncrement()).isEqualTo(2);
  }

  @Test
  void testDupN_endOfCode() {
    // Code ends right after opcode (no immediate byte)
    // Immediate should be treated as 0 -> n=17
    final Bytes code = Bytes.of(0xe6); // Just DUPN, no immediate
    final TestMessageFrameBuilder builder =
        new TestMessageFrameBuilder().code(new Code(code)).pc(0);

    // Push 17 items
    for (int i = 17; i >= 1; i--) {
      builder.pushStackItem(Bytes.of(i));
    }
    final MessageFrame frame = builder.build();

    final OperationResult result = operation.execute(frame, null);

    // Immediate 0 is valid, should succeed
    assertThat(result.getHaltReason()).isNull();
    assertThat(frame.getStackItem(0)).isEqualTo(Bytes.of(17));
  }

  @Test
  void testDupN_gasCost() {
    final Bytes code = Bytes.of(0xe6, 0x00);
    final TestMessageFrameBuilder builder =
        new TestMessageFrameBuilder().code(new Code(code)).pc(0).initialGas(100);

    for (int i = 17; i >= 1; i--) {
      builder.pushStackItem(Bytes.of(i));
    }
    final MessageFrame frame = builder.build();

    final OperationResult result = operation.execute(frame, null);

    assertThat(result.getGasCost()).isEqualTo(3);
  }

  @Test
  void testDupN_stackOverflow() {
    // Fill stack to maximum capacity (1024 items), then try to duplicate
    final Bytes code = Bytes.of(0xe6, 0x00); // DUPN 17
    final TestMessageFrameBuilder builder =
        new TestMessageFrameBuilder().code(new Code(code)).pc(0);

    // Push 1024 items (max stack size)
    for (int i = 0; i < 1024; i++) {
      builder.pushStackItem(Bytes.ofUnsignedInt(i));
    }
    final MessageFrame frame = builder.build();

    assertThat(frame.stackSize()).isEqualTo(1024);

    final OperationResult result = operation.execute(frame, null);

    // Should fail with stack overflow since duplicating would exceed 1024
    assertThat(result.getHaltReason()).isEqualTo(ExceptionalHaltReason.TOO_MANY_STACK_ITEMS);
    assertThat(result.getPcIncrement()).isEqualTo(2);
  }
}
