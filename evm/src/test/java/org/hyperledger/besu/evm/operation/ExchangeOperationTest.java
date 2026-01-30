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

/** Unit tests for {@link ExchangeOperation}. */
class ExchangeOperationTest {

  private final ExchangeOperation operation = new ExchangeOperation(new PragueGasCalculator());

  @Test
  void testDecodePair_immediate0x12() {
    // From EIP test vector: e812 is [EXCHANGE 2 3]
    // x=0x12=18, k=18, q=18/16=1, r=18%16=2
    // q < r, so return (q+1, r+1) = (2, 3)
    int[] result = ExchangeOperation.decodePair(0x12);
    assertThat(result).isNotNull();
    assertThat(result[0]).isEqualTo(2);
    assertThat(result[1]).isEqualTo(3);
  }

  @Test
  void testDecodePair_immediate0xd0() {
    // From EIP test vector: e8d0 is [EXCHANGE 1 19]
    // x=0xd0=208, k=208-48=160, q=160/16=10, r=160%16=0
    // q >= r, so return (r+1, 29-q) = (1, 19)
    int[] result = ExchangeOperation.decodePair(0xd0);
    assertThat(result).isNotNull();
    assertThat(result[0]).isEqualTo(1);
    assertThat(result[1]).isEqualTo(19);
  }

  @Test
  void testDecodePair_immediate0() {
    // x=0, k=0, q=0, r=0
    // q >= r (both 0), so return (r+1, 29-q) = (1, 29)
    int[] result = ExchangeOperation.decodePair(0);
    assertThat(result).isNotNull();
    assertThat(result[0]).isEqualTo(1);
    assertThat(result[1]).isEqualTo(29);
  }

  @Test
  void testDecodePair_immediate79() {
    // x=79, k=79, q=79/16=4, r=79%16=15
    // q < r, so return (q+1, r+1) = (5, 16)
    int[] result = ExchangeOperation.decodePair(79);
    assertThat(result).isNotNull();
    assertThat(result[0]).isEqualTo(5);
    assertThat(result[1]).isEqualTo(16);
  }

  @ParameterizedTest
  @ValueSource(ints = {80, 0x50, 91, 100, 127})
  void testDecodePair_invalidRange(final int imm) {
    // Invalid range: 80-127
    assertThat(ExchangeOperation.decodePair(imm)).isNull();
  }

  @Test
  void testExchange_basicOperation() {
    // EXCHANGE with immediate 0x12 -> (n=2, m=3)
    // Swaps stack[2] with stack[3]
    final Bytes code = Bytes.of(0xe8, 0x12);
    final TestMessageFrameBuilder builder =
        new TestMessageFrameBuilder().code(new Code(code)).pc(0);

    // Push 4 items: stack[0]=1, stack[1]=2, stack[2]=3, stack[3]=4
    builder.pushStackItem(Bytes.of(4)); // becomes stack[3]
    builder.pushStackItem(Bytes.of(3)); // becomes stack[2]
    builder.pushStackItem(Bytes.of(2)); // becomes stack[1]
    builder.pushStackItem(Bytes.of(1)); // becomes stack[0]
    final MessageFrame frame = builder.build();

    // Before
    assertThat(frame.getStackItem(0)).isEqualTo(Bytes.of(1));
    assertThat(frame.getStackItem(1)).isEqualTo(Bytes.of(2));
    assertThat(frame.getStackItem(2)).isEqualTo(Bytes.of(3));
    assertThat(frame.getStackItem(3)).isEqualTo(Bytes.of(4));

    final OperationResult result = operation.execute(frame, null);

    assertThat(result.getHaltReason()).isNull();
    assertThat(result.getGasCost()).isEqualTo(3);
    assertThat(result.getPcIncrement()).isEqualTo(2);

    // After: stack[2] and stack[3] swapped
    assertThat(frame.getStackItem(0)).isEqualTo(Bytes.of(1));
    assertThat(frame.getStackItem(1)).isEqualTo(Bytes.of(2));
    assertThat(frame.getStackItem(2)).isEqualTo(Bytes.of(4)); // was 3
    assertThat(frame.getStackItem(3)).isEqualTo(Bytes.of(3)); // was 4
    assertThat(frame.stackSize()).isEqualTo(4);
  }

  @Test
  void testExchange_immediate0xd0() {
    // EXCHANGE with immediate 0xd0 -> (n=1, m=19)
    // Swaps stack[1] with stack[19]
    final Bytes code = Bytes.fromHexString("e8d0");
    final TestMessageFrameBuilder builder =
        new TestMessageFrameBuilder().code(new Code(code)).pc(0);

    // Push 20 items
    for (int i = 20; i >= 1; i--) {
      builder.pushStackItem(Bytes.ofUnsignedInt(i));
    }
    final MessageFrame frame = builder.build();

    // Before: stack[1]=2, stack[19]=20
    assertThat(frame.getStackItem(1).toInt()).isEqualTo(2);
    assertThat(frame.getStackItem(19).toInt()).isEqualTo(20);

    final OperationResult result = operation.execute(frame, null);

    assertThat(result.getHaltReason()).isNull();

    // After: swapped
    assertThat(frame.getStackItem(1).toInt()).isEqualTo(20);
    assertThat(frame.getStackItem(19).toInt()).isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(ints = {80, 0x50, 91, 0x5b, 0x60, 0x7f, 100, 127})
  void testExchange_invalidImmediate(final int imm) {
    final Bytes code = Bytes.wrap(new byte[] {(byte) 0xe8, (byte) imm});
    final TestMessageFrameBuilder builder =
        new TestMessageFrameBuilder().code(new Code(code)).pc(0);

    for (int i = 0; i < 50; i++) {
      builder.pushStackItem(Bytes.ofUnsignedInt(i));
    }
    final MessageFrame frame = builder.build();

    final OperationResult result = operation.execute(frame, null);

    assertThat(result.getHaltReason()).isEqualTo(ExceptionalHaltReason.INVALID_OPERATION);
    assertThat(result.getPcIncrement()).isEqualTo(2);
  }

  @Test
  void testExchange_stackUnderflow() {
    // EXCHANGE with immediate 0x12 -> (n=2, m=3), needs 4 items but only 2
    final Bytes code = Bytes.of(0xe8, 0x12);
    final TestMessageFrameBuilder builder =
        new TestMessageFrameBuilder().code(new Code(code)).pc(0);

    builder.pushStackItem(Bytes.of(1));
    builder.pushStackItem(Bytes.of(2));
    final MessageFrame frame = builder.build();

    final OperationResult result = operation.execute(frame, null);

    assertThat(result.getHaltReason()).isEqualTo(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
    assertThat(result.getPcIncrement()).isEqualTo(2);
  }

  @Test
  void testExchange_endOfCode() {
    // Code ends right after opcode, immediate treated as 0
    // x=0 -> (n=1, m=29), needs 30 items
    final Bytes code = Bytes.of(0xe8);
    final TestMessageFrameBuilder builder =
        new TestMessageFrameBuilder().code(new Code(code)).pc(0);

    for (int i = 30; i >= 1; i--) {
      builder.pushStackItem(Bytes.of(i));
    }
    final MessageFrame frame = builder.build();

    // Before: stack[1]=2, stack[29]=30
    assertThat(frame.getStackItem(1)).isEqualTo(Bytes.of(2));
    assertThat(frame.getStackItem(29)).isEqualTo(Bytes.of(30));

    final OperationResult result = operation.execute(frame, null);

    assertThat(result.getHaltReason()).isNull();
    // Verify swap
    assertThat(frame.getStackItem(1)).isEqualTo(Bytes.of(30));
    assertThat(frame.getStackItem(29)).isEqualTo(Bytes.of(2));
  }

  @Test
  void testExchange_gasCost() {
    final Bytes code = Bytes.of(0xe8, 0x12);
    final TestMessageFrameBuilder builder =
        new TestMessageFrameBuilder().code(new Code(code)).pc(0).initialGas(100);

    for (int i = 0; i < 10; i++) {
      builder.pushStackItem(Bytes.of(i));
    }
    final MessageFrame frame = builder.build();

    final OperationResult result = operation.execute(frame, null);

    assertThat(result.getGasCost()).isEqualTo(3);
  }

  // ==================== EIP-8024 Spec Test Vectors ====================

  /**
   * Spec test vector: EXCHANGE with immediate 0x01. Bytecode sequence: 5f60016002e801 (PUSH0, PUSH1
   * 1, PUSH1 2, EXCHANGE 01) Stack before: [2, 1, 0] (top to bottom) Expected after: [2, 0, 1]
   * (swaps stack[1] with stack[2])
   *
   * <p>For immediate 0x01: k=1, q=0, r=1, q < r so (n,m) = (1, 2)
   */
  @Test
  void testSpecVector_exchange_e801() {
    final Bytes code = Bytes.of(0xe8, 0x01); // EXCHANGE 01
    final TestMessageFrameBuilder builder =
        new TestMessageFrameBuilder().code(new Code(code)).pc(0);

    // Stack setup: PUSH0, PUSH1 1, PUSH1 2 -> [2, 1, 0]
    builder.pushStackItem(Bytes.of(0)); // stack[2] = 0
    builder.pushStackItem(Bytes.of(1)); // stack[1] = 1
    builder.pushStackItem(Bytes.of(2)); // stack[0] = 2
    final MessageFrame frame = builder.build();

    // Verify initial state
    assertThat(frame.stackSize()).isEqualTo(3);
    assertThat(frame.getStackItem(0)).isEqualTo(Bytes.of(2)); // top
    assertThat(frame.getStackItem(1)).isEqualTo(Bytes.of(1));
    assertThat(frame.getStackItem(2)).isEqualTo(Bytes.of(0)); // bottom

    final OperationResult result = operation.execute(frame, null);

    assertThat(result.getHaltReason()).isNull();
    assertThat(result.getPcIncrement()).isEqualTo(2);

    // After EXCHANGE 01: stack[1] and stack[2] swapped
    assertThat(frame.stackSize()).isEqualTo(3); // size unchanged
    assertThat(frame.getStackItem(0)).isEqualTo(Bytes.of(2)); // unchanged
    assertThat(frame.getStackItem(1)).isEqualTo(Bytes.of(0)); // swapped
    assertThat(frame.getStackItem(2)).isEqualTo(Bytes.of(1)); // swapped
  }

  /**
   * Spec test vector: EXCHANGE with 30 items on stack. Uses immediate 0x00 which decodes to (n=1,
   * m=29), swapping stack[1] with stack[29].
   */
  @Test
  void testSpecVector_exchange30Items() {
    final Bytes code = Bytes.of(0xe8, 0x00); // EXCHANGE 00 -> (1, 29)
    final TestMessageFrameBuilder builder =
        new TestMessageFrameBuilder().code(new Code(code)).pc(0);

    // Push 30 items: stack[29]=1, stack[28..1]=values, stack[0]=2
    // Using distinct values at positions we care about
    builder.pushStackItem(Bytes.of(1)); // stack[29] = 1 (bottom)
    for (int i = 28; i >= 2; i--) {
      builder.pushStackItem(Bytes.of(0)); // stack[28..2] = 0
    }
    builder.pushStackItem(Bytes.of(99)); // stack[1] = 99
    builder.pushStackItem(Bytes.of(2)); // stack[0] = 2 (top)
    final MessageFrame frame = builder.build();

    assertThat(frame.stackSize()).isEqualTo(30);
    assertThat(frame.getStackItem(0)).isEqualTo(Bytes.of(2)); // top
    assertThat(frame.getStackItem(1)).isEqualTo(Bytes.of(99));
    assertThat(frame.getStackItem(29)).isEqualTo(Bytes.of(1)); // bottom

    final OperationResult result = operation.execute(frame, null);

    assertThat(result.getHaltReason()).isNull();

    // After EXCHANGE: stack[1] and stack[29] swapped
    assertThat(frame.stackSize()).isEqualTo(30);
    assertThat(frame.getStackItem(0)).isEqualTo(Bytes.of(2)); // unchanged
    assertThat(frame.getStackItem(1)).isEqualTo(Bytes.of(1)); // was 99, now 1
    assertThat(frame.getStackItem(29)).isEqualTo(Bytes.of(99)); // was 1, now 99
  }

  /**
   * Spec test vector: Invalid immediate 0x50 (POP opcode). Bytecode: e850 0x50 (80) is in invalid
   * range 80-127, should return INVALID_OPERATION.
   */
  @Test
  void testSpecVector_invalidImmediate0x50() {
    final Bytes code = Bytes.of(0xe8, 0x50); // EXCHANGE with invalid immediate 0x50
    final TestMessageFrameBuilder builder =
        new TestMessageFrameBuilder().code(new Code(code)).pc(0);

    // Add sufficient stack items
    for (int i = 0; i < 50; i++) {
      builder.pushStackItem(Bytes.ofUnsignedInt(i));
    }
    final MessageFrame frame = builder.build();

    final OperationResult result = operation.execute(frame, null);

    assertThat(result.getHaltReason()).isEqualTo(ExceptionalHaltReason.INVALID_OPERATION);
    assertThat(result.getPcIncrement()).isEqualTo(2);
  }
}
