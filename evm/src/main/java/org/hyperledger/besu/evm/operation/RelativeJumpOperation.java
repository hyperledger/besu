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

import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

/** The type Relative jump operation. */
public class RelativeJumpOperation extends AbstractFixedCostOperation {

  /** The constant OPCODE. */
  public static final int OPCODE = 0xe0;

  /**
   * Instantiates a new Relative jump operation.
   *
   * @param gasCalculator the gas calculator
   */
  public RelativeJumpOperation(final GasCalculator gasCalculator) {
    this(OPCODE, "RJUMP", 0, 0, gasCalculator, gasCalculator.getBaseTierGasCost());
  }

  /**
   * Instantiates a new Relative jump operation.
   *
   * @param opcode the opcode
   * @param name the name
   * @param stackItemsConsumed the stack items consumed
   * @param stackItemsProduced the stack items produced
   * @param gasCalculator the gas calculator
   * @param fixedCost the fixed cost
   */
  protected RelativeJumpOperation(
      final int opcode,
      final String name,
      final int stackItemsConsumed,
      final int stackItemsProduced,
      final GasCalculator gasCalculator,
      final long fixedCost) {
    super(opcode, name, stackItemsConsumed, stackItemsProduced, gasCalculator, fixedCost);
  }

  @Override
  protected OperationResult executeFixedCostOperation(final MessageFrame frame, final EVM evm) {
    Code code = frame.getCode();
    if (code.getEofVersion() == 0) {
      return InvalidOperation.INVALID_RESULT;
    }
    final int pcPostInstruction = frame.getPC() + 1;
    return new OperationResult(gasCost, null, 2 + code.readBigEndianI16(pcPostInstruction) + 1);
  }
}
