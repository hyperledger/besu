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

public class JumpFOperation extends AbstractOperation {

  public static final int OPCODE = 0xb2;
  static final OperationResult jumpfSuccess = new OperationResult(3, null);

  public JumpFOperation(final GasCalculator gasCalculator) {
    super(OPCODE, "JUMPF", 0, 0, gasCalculator);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    final byte[] code = frame.getCode().getCodeBytes(frame.getSection()).toArrayUnsafe();
    return staticOperation(frame, code, frame.getPC());
  }

  public static OperationResult staticOperation(
      final MessageFrame frame, final byte[] code, final int pc) {
    int section = readBigEndianU16(pc + 1, code);
    var exception = frame.jumpFunction(section);
    if (exception == null) {
      return jumpfSuccess;
    } else {
      return new OperationResult(jumpfSuccess.gasCost, exception);
    }
  }
}
