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

/** The Exchange operation. */
public class ExchangeOperation extends AbstractFixedCostOperation {

  /** EXCHANGE Opcode 0xe8 */
  public static final int OPCODE = 0xe8;

  /** The Exchange operation success result. */
  static final OperationResult exchangeSuccess = new OperationResult(3, null);

  /**
   * Instantiates a new Exchange operation.
   *
   * @param gasCalculator the gas calculator
   */
  public ExchangeOperation(final GasCalculator gasCalculator) {
    super(OPCODE, "EXCHANGE", 0, 1, gasCalculator, gasCalculator.getVeryLowTierGasCost());
  }

  @Override
  public OperationResult executeFixedCostOperation(final MessageFrame frame, final EVM evm) {
    Code code = frame.getCode();
    if (code.getEofVersion() == 0) {
      return InvalidOperation.INVALID_RESULT;
    }
    int pc = frame.getPC();
    int imm = code.readU8(pc + 1);
    int n = (imm >> 4) + 1;
    int m = (imm & 0x0F) + 1 + n;

    final Bytes tmp = frame.getStackItem(n);
    frame.setStackItem(n, frame.getStackItem(m));
    frame.setStackItem(m, tmp);
    frame.setPC(pc + 1);

    return exchangeSuccess;
  }
}
