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
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

/** The type Jump F operation. */
public class JumpFOperation extends AbstractOperation {

  /** The constant OPCODE. */
  public static final int OPCODE = 0xe5;

  /** The Jump F success operation result. */
  static final OperationResult jumpfSuccess = new OperationResult(5, null);

  static final OperationResult jumpfStackOverflow =
      new OperationResult(5, ExceptionalHaltReason.TOO_MANY_STACK_ITEMS);

  /**
   * Instantiates a new Jump F operation.
   *
   * @param gasCalculator the gas calculator
   */
  public JumpFOperation(final GasCalculator gasCalculator) {
    super(OPCODE, "JUMPF", 0, 0, gasCalculator);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    Code code = frame.getCode();
    if (code.getEofVersion() == 0) {
      return InvalidOperation.INVALID_RESULT;
    }
    int pc = frame.getPC();
    int section = code.readBigEndianU16(pc + 1);
    var info = code.getCodeSection(section);
    int operandStackSize = frame.stackSize();
    if (operandStackSize > 1024 - info.getMaxStackHeight() + info.getInputs()) {
      return jumpfStackOverflow;
    }
    frame.setPC(info.getEntryPoint() - 1); // will be +1ed at end of operations loop
    frame.setSection(section);
    return jumpfSuccess;
  }
}
