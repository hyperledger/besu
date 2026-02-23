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

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

/** The JUMPI operation. */
public class JumpiOperation extends AbstractFixedCostOperation {

  private static final OperationResult invalidJumpResponse =
      new Operation.OperationResult(10L, ExceptionalHaltReason.INVALID_JUMP_DESTINATION);
  private static final OperationResult jumpiResponse = new OperationResult(10L, null, 0);
  private static final OperationResult nojumpResponse = new OperationResult(10L, null);

  private static final JumpService jumpService = new JumpService();

  /**
   * Instantiates a new JUMPI operation.
   *
   * @param gasCalculator the gas calculator
   */
  public JumpiOperation(final GasCalculator gasCalculator) {
    super(0x57, "JUMPI", 2, 0, gasCalculator, gasCalculator.getHighTierGasCost());
  }

  @Override
  public OperationResult executeFixedCostOperation(final MessageFrame frame, final EVM evm) {
    return staticOperation(frame, frame.stackData());
  }

  /**
   * Performs Jump operation.
   *
   * @param frame the frame
   * @param s the stack data array
   * @return the operation result
   */
  public static OperationResult staticOperation(final MessageFrame frame, final long[] s) {
    if (!frame.stackHasItems(2)) return UNDERFLOW_RESPONSE;
    final int top = frame.stackTop();
    final int destOff = (top - 1) << 2;
    final int condOff = (top - 2) << 2;
    frame.setTop(top - 2);

    // If condition is zero (false), no jump will be performed.
    if (s[condOff] == 0 && s[condOff + 1] == 0 && s[condOff + 2] == 0 && s[condOff + 3] == 0) {
      return nojumpResponse;
    }

    return jumpService.performJump(frame, s, destOff, jumpiResponse, invalidJumpResponse);
  }
}
