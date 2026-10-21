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
package org.hyperledger.besu.evm.v2.operation;

import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;

/** Operations that each use what one rule of the loop generator handles. */
public final class ExampleOperationV2 {

  /** A halt EVM can name. */
  public static final OperationResult UNDERFLOW =
      new OperationResult(0, ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);

  /** A halt EVM can name. */
  public static final OperationResult OVERFLOW =
      new OperationResult(0, ExceptionalHaltReason.TOO_MANY_STACK_ITEMS);

  private static final OperationResult POP_SUCCESS = new OperationResult(2, null);
  private static final OperationResult PUSH_SUCCESS = new OperationResult(3, null);
  private static final OperationResult JUMP_SUCCESS = new OperationResult(8, null, 0);
  private static final OperationResult BAD_JUMP =
      new OperationResult(8, ExceptionalHaltReason.INVALID_JUMP_DESTINATION);

  private ExampleOperationV2() {}

  /** The frame's stack top, a halt EVM can name, and a success. */
  public static OperationResult pop(final MessageFrame frame) {
    if (!frame.stackHasItemsV2(1)) {
      return UNDERFLOW;
    }
    frame.setTopV2(frame.stackTopV2() - 1);
    return POP_SUCCESS;
  }

  /** The stack array, a helper EVM cannot call, a literal argument and a pc that moves. */
  public static OperationResult push(
      final MessageFrame frame, final byte[] code, final int pc, final int size) {
    if (!frame.stackHasSpaceV2(1)) {
      return OVERFLOW;
    }
    final int top = frame.stackTopV2();
    setLong(frame.stackDataV2(), top, size == 0 ? 0L : code[pc + 1] & 0xffL);
    frame.setTopV2(top + 1);
    frame.setPC(pc + size);
    return PUSH_SUCCESS;
  }

  /** A call whose result is returned, which is passed the frame, a value and a halt. */
  public static OperationResult jump(final MessageFrame frame) {
    final int top = frame.stackTopV2();
    frame.setTopV2(top - 1);
    return jumpTo(frame, (int) frame.stackDataV2()[((top - 1) << 2) + 3], BAD_JUMP);
  }

  static OperationResult jumpTo(
      final MessageFrame frame, final int destination, final OperationResult invalid) {
    if (frame.getCode().isJumpDestInvalid(destination)) {
      return invalid;
    }
    frame.setPC(destination);
    return JUMP_SUCCESS;
  }

  /** A result that is not a constant. */
  public static OperationResult invalid(final int opcode) {
    return new OperationResult(0, ExceptionalHaltReason.newInvalidOperation(opcode));
  }

  /** A use of the frame the loop has no local for. */
  public static OperationResult storage(final MessageFrame frame) {
    frame.getWorldUpdater();
    return POP_SUCCESS;
  }

  static void setLong(final long[] stack, final int top, final long value) {
    final int offset = top << 2;
    stack[offset] = 0L;
    stack[offset + 1] = 0L;
    stack[offset + 2] = 0L;
    stack[offset + 3] = value;
  }
}
