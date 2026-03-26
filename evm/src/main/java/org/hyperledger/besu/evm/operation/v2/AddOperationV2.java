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
package org.hyperledger.besu.evm.operation.v2;

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Operation;

/**
 * EVM v2 ADD operation using long[] stack representation.
 *
 * <p>Each 256-bit word is stored as four longs: index 0 = most significant 64 bits, index 3 = least
 * significant 64 bits. Addition is performed with carry propagation from least to most significant
 * word, with overflow silently truncated (mod 2^256).
 */
public class AddOperationV2 extends AbstractFixedCostOperationV2 {

  private static final Operation.OperationResult ADD_SUCCESS =
      new Operation.OperationResult(3, null);

  /**
   * Instantiates a new Add operation.
   *
   * @param gasCalculator the gas calculator
   */
  public AddOperationV2(final GasCalculator gasCalculator) {
    super(0x01, "ADD", 2, 1, gasCalculator, gasCalculator.getVeryLowTierGasCost());
  }

  @Override
  public Operation.OperationResult executeFixedCostOperation(
      final MessageFrame frame, final EVM evm) {
    return staticOperation(frame, frame.stackDataV2());
  }

  /**
   * Execute the ADD opcode on the v2 long[] stack.
   *
   * @param frame the message frame
   * @return the operation result
   */
  public static Operation.OperationResult staticOperation(
      final MessageFrame frame, final long[] s) {
    if (!frame.stackHasItems(2)) return UNDERFLOW_RESPONSE;
    frame.setTopV2(add(s, frame.stackTopV2()));
    return ADD_SUCCESS;
  }

  /** ADD: s[top-2] = s[top-1] + s[top-2], return top-1. */
  private static int add(final long[] s, final int top) {
    final int a = (top - 1) << 2;
    final int b = (top - 2) << 2;
    // Fast path: both values fit in a single limb (common case)
    if ((s[a] | s[a + 1] | s[a + 2] | s[b] | s[b + 1] | s[b + 2]) == 0) {
      long z = s[a + 3] + s[b + 3];
      s[b + 2] = ((s[a + 3] & s[b + 3]) | ((s[a + 3] | s[b + 3]) & ~z)) >>> 63;
      s[b + 3] = z;
      return top - 1;
    }
    long a0 = s[a + 3], a1 = s[a + 2], a2 = s[a + 1], a3 = s[a];
    long b0 = s[b + 3], b1 = s[b + 2], b2 = s[b + 1], b3 = s[b];
    long z0 = a0 + b0;
    long c = ((a0 & b0) | ((a0 | b0) & ~z0)) >>> 63;
    long t1 = a1 + b1;
    long c1 = ((a1 & b1) | ((a1 | b1) & ~t1)) >>> 63;
    long z1 = t1 + c;
    c = c1 | (((t1 & c) | ((t1 | c) & ~z1)) >>> 63);
    long t2 = a2 + b2;
    long c2 = ((a2 & b2) | ((a2 | b2) & ~t2)) >>> 63;
    long z2 = t2 + c;
    c = c2 | (((t2 & c) | ((t2 | c) & ~z2)) >>> 63);
    long z3 = a3 + b3 + c;
    s[b] = z3;
    s[b + 1] = z2;
    s[b + 2] = z1;
    s[b + 3] = z0;
    return top - 1;
  }
}
