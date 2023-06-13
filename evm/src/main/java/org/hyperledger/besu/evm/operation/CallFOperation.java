/*
 * Copyright contributors to Hyperledger Besu
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

import static org.hyperledger.besu.evm.internal.Words.readBigEndianU16;

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

/** The Call F operation. */
public class CallFOperation extends AbstractOperation {

  /** The constant OPCODE. */
  public static final int OPCODE = 0xe3;
  /** The Call F success. */
  static final OperationResult callfSuccess = new OperationResult(5, null);

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
    final byte[] code = frame.getCode().getBytes().toArrayUnsafe();
    return staticOperation(frame, code, frame.getPC());
  }

  /**
   * Performs Call F operation.
   *
   * @param frame the frame
   * @param code the code
   * @param pc the pc
   * @return the successful operation result
   */
  public static OperationResult staticOperation(
      final MessageFrame frame, final byte[] code, final int pc) {
    int section = readBigEndianU16(pc + 1, code);
    var exception = frame.callFunction(section);
    if (exception == null) {
      return callfSuccess;
    } else {
      return new OperationResult(callfSuccess.gasCost, exception);
    }
  }
}
