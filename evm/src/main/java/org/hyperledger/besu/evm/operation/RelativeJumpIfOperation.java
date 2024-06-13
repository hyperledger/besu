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

import org.apache.tuweni.bytes.Bytes;

/** The type Relative jump If operation. */
public class RelativeJumpIfOperation extends AbstractFixedCostOperation {

  /** The constant OPCODE. */
  public static final int OPCODE = 0xe1;

  /**
   * Instantiates a new Relative jump If operation.
   *
   * @param gasCalculator the gas calculator
   */
  public RelativeJumpIfOperation(final GasCalculator gasCalculator) {
    super(OPCODE, "RJUMPI", 0, 0, gasCalculator, 4L);
  }

  @Override
  protected OperationResult executeFixedCostOperation(final MessageFrame frame, final EVM evm) {
    Code code = frame.getCode();
    if (code.getEofVersion() == 0) {
      return InvalidOperation.INVALID_RESULT;
    }
    final Bytes condition = frame.popStackItem();
    if (!condition.isZero()) {
      final int pcPostInstruction = frame.getPC() + 1;
      return new OperationResult(gasCost, null, 2 + code.readBigEndianI16(pcPostInstruction) + 1);
    } else {
      return new OperationResult(gasCost, null, 2 + 1);
    }
  }
}
