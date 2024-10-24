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
import org.hyperledger.besu.evm.code.CodeSection;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.ReturnStack;

/** The Call F operation. */
public class CallFOperation extends AbstractOperation {

  /** The constant OPCODE. */
  public static final int OPCODE = 0xe3;

  /** The Call F success. */
  static final OperationResult callfSuccess = new OperationResult(5, null);

  static final OperationResult callfStackOverflow =
      new OperationResult(5, ExceptionalHaltReason.TOO_MANY_STACK_ITEMS);

  /**
   * Instantiates a new Call F operation.
   *
   * @param gasCalculator the gas calculator
   */
  public CallFOperation(final GasCalculator gasCalculator) {
    super(OPCODE, "CALLF", 0, 0, gasCalculator);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    Code code = frame.getCode();
    if (code.getEofVersion() == 0) {
      return InvalidOperation.INVALID_RESULT;
    }

    int pc = frame.getPC();
    int section = code.readBigEndianU16(pc + 1);
    CodeSection info = code.getCodeSection(section);
    int operandStackSize = frame.stackSize();
    if (operandStackSize > 1024 - info.getMaxStackHeight() + info.getInputs()) {
      return callfStackOverflow;
    }
    frame.getReturnStack().push(new ReturnStack.ReturnStackItem(frame.getSection(), pc + 2));
    frame.setPC(info.getEntryPoint() - 1); // will be +1ed at end of operations loop
    frame.setSection(section);

    return callfSuccess;
  }
}
