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

  // BEGIN GENERATED case table
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
      // BEGIN GENERATED cases
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
