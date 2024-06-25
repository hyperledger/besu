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

/** The SwapN operation. */
public class SwapNOperation extends AbstractFixedCostOperation {

  /** SWAPN Opcode 0xe7 */
  public static final int OPCODE = 0xe7;

  /** The Swap operation success result. */
  static final OperationResult swapSuccess = new OperationResult(3, null);

  /**
   * Instantiates a new SwapN operation.
   *
   * @param gasCalculator the gas calculator
   */
  public SwapNOperation(final GasCalculator gasCalculator) {
    super(OPCODE, "SWAPN", 0, 1, gasCalculator, gasCalculator.getVeryLowTierGasCost());
  }

  @Override
  public Operation.OperationResult executeFixedCostOperation(
      final MessageFrame frame, final EVM evm) {
    Code code = frame.getCode();
    if (code.getEofVersion() == 0) {
      return InvalidOperation.INVALID_RESULT;
    }
    int pc = frame.getPC();
    int index = code.readU8(pc + 1);

    final Bytes tmp = frame.getStackItem(0);
    frame.setStackItem(0, frame.getStackItem(index + 1));
    frame.setStackItem(index + 1, tmp);
    frame.setPC(pc + 1);

    return swapSuccess;
  }
}
