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
import java.util.Map;
import java.util.Set;

/**
 * What the untraced loop in EVM.java ({@code runToHaltV2Untraced}) provides to the code copied into
 * it, and so every substitution the copies make. The loop and this class have to agree; the tests
 * that run the loop against EVM v1 check that they do.
 *
 * <p>The loop's locals:
 *
 * <ul>
 *   <li>{@code s}, the frame's stack array, and {@code maxItems}, the number of items it holds;
 *   <li>{@code sp}, {@code pc} and {@code gas}, the frame's stack top, program counter and gas;
 *   <li>{@code code}, the code's bytes, {@code codeObject}, the code, and {@code opcode};
 *   <li>{@code spBase} and {@code pcBase}, the stack top and pc minus the current case's constant;
 *   <li>{@code cost}, {@code step} and {@code result}, which a case sets for the loop to apply;
 *   <li>{@code constantinople} and {@code shanghai}, the fork flags of EVM.
 * </ul>
 *
 * <p>What a copied use of the frame becomes:
 *
 * <pre>
 * frame.stackDataV2()        s
 * frame.stackTopV2()         sp, or (spBase + case constant) before the case has set sp
 * frame.stackHasItemsV2(n)   (sp &gt;= n), sp as above
 * frame.stackHasSpaceV2(n)   (sp + n &lt;= maxItems), sp as above
 * frame.setTopV2(x)          sp = x
 * frame.getPC()              pc, or (pcBase + case constant) before the case has set pc
 * frame.setPC(x)             pc = x
 * frame.getRemainingGas()    gas
 * frame.getCode()            codeObject
 * </pre>
 *
 * Any other use of the frame is copied only after the case has handed the loop's state to the
 * frame, which leaves the operation for the loop to complete as its general path does.
 */
final class UntracedLoop {

  /** The loop's locals, which copied code must not declare a variable of the same name as. */
  static final Set<String> LOCALS =
      Set.of(
          "frame",
          "code",
          "codeObject",
          "s",
          "sp",
          "pc",
          "gas",
          "opcode",
          "entry",
          "bias",
          "spBase",
          "pcBase",
          "cost",
          "step",
          "result",
          "maxItems",
          "constantinople",
          "shanghai");

  /**
   * EVM's fields that {@code executeOperationV2}'s cases read, as the locals that hold them in the
   * loop: a field read in a case is a load C2 moves ahead of the dispatch, for every operation.
   */
  static final Map<String, String> EVM_FIELDS =
      Map.of("enableConstantinople", "constantinople", "enableShanghai", "shanghai");

  /** Hands the loop's state to the frame, before code that uses the frame runs. */
  static final String HAND_STATE_TO_FRAME =
      "frame.setTopV2(sp); frame.setPC(pc); frame.setGasRemaining(gas);";

  /**
   * How far apart the cases' constants are. Further than any offset an operation adds to the stack
   * top or pc, so that no two cases compute the same index.
   */
  static final int CASE_SPACING = 64;

  private UntracedLoop() {}

  /**
   * The copy of a call of one of the frame's methods, made while the loop's state is in its locals,
   * or null for a method the loop has no local for.
   *
   * @param method the name of the frame's method
   * @param arguments the copies of its arguments
   * @param locals what the case reads for the stack top and pc, and whether it has set them
   * @return the copy, or null
   */
  static String frameCall(
      final String method, final List<String> arguments, final CaseLocals locals) {
    return switch (method) {
      case "stackDataV2" -> "s";
      case "stackTopV2" -> locals.stackTop();
      case "stackHasItemsV2" -> "(" + locals.stackTop() + " >= " + arguments.getFirst() + ")";
      case "stackHasSpaceV2" ->
          "(" + locals.stackTop() + " + " + arguments.getFirst() + " <= maxItems)";
      case "setTopV2" -> locals.assignStackTop(arguments.getFirst());
      case "getPC" -> locals.pc();
      case "setPC" -> locals.assignPc(arguments.getFirst());
      case "getRemainingGas" -> "gas";
      case "getCode" -> "codeObject";
      default -> null;
    };
  }
}
