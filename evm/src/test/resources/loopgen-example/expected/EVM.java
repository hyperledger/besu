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
package org.hyperledger.besu.evm;

import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.v2.operation.ExampleOperationV2;

/**
 * The parts of EVM the loop generator reads and writes, for EvmV2LoopGeneratorTest: the generated
 * regions, one step of the untraced loop with the locals the copies use, and the general path the
 * copies are taken from. It does not import ExceptionalHaltReason, so the copies name it in full.
 */
public class EVM {

  private final boolean enableShanghai = true;

  // BEGIN GENERATED case table for the copies of the operations' code in the untraced loop
  // below; do not edit, run ./gradlew :evm:generateEvmV2Loop
  private static final int COPIED_POP = 1;
  private static final int COPIED_PUSH1 = 2;
  private static final int COPIED_JUMP = 3;
  private static final int COPIED_PUSH0 = 4;

  // Per opcode: its case in the low byte, and above it the distance of the case's
  // constant from the stack top and pc that its copied code reads
  private static final int[] DISPATCH = new int[256];

  static {
    dispatch(COPIED_POP, 0x50, 0x50);
    dispatch(COPIED_PUSH1, 0x60, 0x61);
    dispatch(COPIED_JUMP, 0x56, 0x56);
    dispatch(COPIED_PUSH0, 0x5f, 0x5f);
  }

  private static void dispatch(final int copiedCase, final int first, final int last) {
    for (int op = first; op <= last; op++) {
      DISPATCH[op] = ((copiedCase * 64) << 8) | copiedCase;
    }
  }

  // copied from ExampleOperationV2.BAD_JUMP
  private static final OperationResult EXAMPLE_OPERATION_V2_BAD_JUMP =
      new OperationResult(
          8, org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INVALID_JUMP_DESTINATION);

  // copied from ExampleOperationV2.setLong
  private static void exampleOperationV2SetLong(
      final long[] stack, final int top, final long value) {
    final int offset = top << 2;
    stack[offset] = 0L;
    stack[offset + 1] = 0L;
    stack[offset + 2] = 0L;
    stack[offset + 3] = value;
  }

  // END GENERATED case table

  private void runToHaltV2Untraced(final MessageFrame frame) {
    final Code codeObject = frame.getCode();
    final byte[] code = codeObject.getBytes().toArrayUnsafe();
    final boolean shanghai = enableShanghai;
    final long[] s = frame.stackDataV2();
    final int maxItems = s.length >> 2;
    int sp = frame.stackTopV2();
    int pc = frame.getPC();
    long gas = frame.getRemainingGas();

    final int opcode = code[pc] & 0xff;
    long cost = -1;
    int step = 0;
    OperationResult result = null;
    final int entry = DISPATCH[opcode];
    final int bias = entry >> 8;
    final int spBase = sp - bias;
    final int pcBase = pc - bias;
    switch (entry & 0xff) {
      // BEGIN GENERATED cases: copies of the operations' code;
      // edit those, then run ./gradlew :evm:generateEvmV2Loop
      // BEGIN copied from ExampleOperationV2.pop
      case COPIED_POP -> {
        if (!((spBase + 64) >= 1)) {
          frame.setTopV2(sp);
          frame.setPC(pc);
          frame.setGasRemaining(gas);
          result = ExampleOperationV2.UNDERFLOW;
          break;
        }
        sp = (spBase + 64) - 1;
        cost = 2L;
        step = 1;
      }
      // END copied from ExampleOperationV2.pop
      // BEGIN copied from ExampleOperationV2.push
      case COPIED_PUSH1 -> {
        int size = 1;
        if (!((spBase + 128) + 1 <= maxItems)) {
          frame.setTopV2(sp);
          frame.setPC(pc);
          frame.setGasRemaining(gas);
          result = ExampleOperationV2.OVERFLOW;
          break;
        }
        final int top = (spBase + 128);
        exampleOperationV2SetLong(s, top, size == 0 ? 0L : code[(pcBase + 128) + 1] & 0xffL);
        sp = top + 1;
        pc = (pcBase + 128) + size;
        cost = 3L;
        step = 1;
      }
      // END copied from ExampleOperationV2.push
      // BEGIN copied from ExampleOperationV2.jump
      case COPIED_JUMP -> {
        final int top = (spBase + 192);
        sp = top - 1;
        {
          final int destination = (int) s[((top - 1) << 2) + 3];
          if (codeObject.isJumpDestInvalid(destination)) {
            frame.setTopV2(sp);
            frame.setPC(pc);
            frame.setGasRemaining(gas);
            result = EXAMPLE_OPERATION_V2_BAD_JUMP;
            break;
          }
          pc = destination;
          cost = 8L;
          step = 0;
        }
      }
      // END copied from ExampleOperationV2.jump
      // BEGIN copied from ExampleOperationV2.push
      case COPIED_PUSH0 -> {
        if (shanghai) {
          {
            int size = 0;
            if (!((spBase + 256) + 1 <= maxItems)) {
              frame.setTopV2(sp);
              frame.setPC(pc);
              frame.setGasRemaining(gas);
              result = ExampleOperationV2.OVERFLOW;
              break;
            }
            final int top = (spBase + 256);
            exampleOperationV2SetLong(s, top, size == 0 ? 0L : code[(pcBase + 256) + 1] & 0xffL);
            sp = top + 1;
            pc = (pcBase + 256) + size;
            cost = 3L;
            step = 1;
          }
        } else {
          {
            frame.setTopV2(sp);
            frame.setPC(pc);
            frame.setGasRemaining(gas);
            result =
                new OperationResult(
                    0,
                    org.hyperledger.besu.evm.frame.ExceptionalHaltReason.newInvalidOperation(
                        opcode));
          }
        }
      }
      // END copied from ExampleOperationV2.push
      // END GENERATED cases
      default -> {}
    }
  }

  private OperationResult executeOperationV2(
      final MessageFrame frame, final byte[] code, final int pc, final int opcode) {
    return switch (opcode) {
      case 0x50 -> ExampleOperationV2.pop(frame);
      case 0x54 -> ExampleOperationV2.storage(frame);
      case 0x56 -> ExampleOperationV2.jump(frame);
      case 0x5f ->
          enableShanghai
              ? ExampleOperationV2.push(frame, code, pc, 0)
              : ExampleOperationV2.invalid(opcode);
      case 0x60, 0x61 -> ExampleOperationV2.push(frame, code, pc, 1);
      default -> null;
    };
  }
}
