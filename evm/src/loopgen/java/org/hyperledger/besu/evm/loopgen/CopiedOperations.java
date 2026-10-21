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
package org.hyperledger.besu.evm.loopgen;

import java.util.List;

/**
 * The operations the untraced loop runs as copies of their code, most frequent on mainnet first.
 * Each is named by one opcode of the {@code executeOperationV2} case that runs it, and the copy
 * covers every opcode of that case.
 *
 * <p>Left out on purpose:
 *
 * <ul>
 *   <li>JUMP and JUMPI. Their check of the destination analyses the code the first time, through a
 *       call C2 keeps in the copy, and the loop's locals cannot stay in registers around a call.
 *   <li>EQ, MSTORE, MLOAD, CALLDATALOAD and the operations that touch the world state. {@code
 *       executeOperationV2} runs them through the fork's operation table, not a static method, so
 *       there is no code to copy.
 *   <li>PUSH9 to PUSH32, MUL, DIV, ADDMOD and MULMOD. Each would take the loop past the inlining
 *       budget {@code EvmV2LoopCompilationTest} checks.
 * </ul>
 */
final class CopiedOperations {

  /**
   * An operation the loop copies.
   *
   * @param name the name of its case constant in EVM.java, after {@code COPIED_}
   * @param opcode one opcode of the {@code executeOperationV2} case that runs it
   */
  record Operation(String name, int opcode) {}

  static final List<Operation> ALL =
      List.of(
          new Operation("PUSH1", 0x60),
          new Operation("PUSH2_TO_8", 0x61),
          new Operation("DUP", 0x80),
          new Operation("SWAP", 0x90),
          new Operation("JUMPDEST", 0x5b),
          new Operation("POP", 0x50),
          new Operation("ADD", 0x01),
          new Operation("ISZERO", 0x15),
          new Operation("AND", 0x16),
          new Operation("SUB", 0x03),
          new Operation("LT", 0x10),
          new Operation("SHL", 0x1b),
          new Operation("GT", 0x11),
          new Operation("PUSH0", 0x5f),
          new Operation("SHR", 0x1c),
          new Operation("OR", 0x17),
          new Operation("NOT", 0x19),
          new Operation("SLT", 0x12));

  private CopiedOperations() {}
}
