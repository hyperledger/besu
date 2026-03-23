/*
 * Copyright ConsenSys AG.
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

/** The Jump dest operation. */
public class JumpDestOperation extends AbstractFixedCostOperation {

  private static final long GAS_COST = 1L;

  /** constant for a successful jumpdest * */
  public static final OperationResult JUMPDEST_SUCCESS = new OperationResult(GAS_COST, null);

  private static final OperationResult outOfGasResult =
      new OperationResult(GAS_COST, ExceptionalHaltReason.INSUFFICIENT_GAS);

  /** The constant OPCODE. */
  public static final int OPCODE = 0x5B;

  /**
   * Instantiates a new Jump dest operation.
   *
   * @param gasCalculator the gas calculator
   */
  public JumpDestOperation(final GasCalculator gasCalculator) {
    super(OPCODE, "JUMPDEST", 0, 0, gasCalculator, GAS_COST);
  }

  @Override
  public Operation.OperationResult executeFixedCostOperation(
      final MessageFrame frame, final EVM evm) {
    return successResponse;
  }

  /**
   * Performs JumpDest operation with gas self-deduction.
   *
   * @param frame the frame
   * @return the operation result
   */
  public static OperationResult staticOperation(final MessageFrame frame) {
    if (frame.decrementRemainingGas(GAS_COST) < 0) {
      return outOfGasResult;
    }
    return JUMPDEST_SUCCESS;
  }
}
