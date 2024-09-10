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

import static org.hyperledger.besu.evm.internal.Words.clampedToInt;

import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import org.apache.tuweni.bytes.Bytes;

/** The Data load operation. */
public class DataCopyOperation extends AbstractOperation {

  /**
   * Instantiates a new Data Load operation.
   *
   * @param gasCalculator the gas calculator
   */
  public DataCopyOperation(final GasCalculator gasCalculator) {
    super(0xd3, "DATACOPY", 3, 1, gasCalculator);
  }

  /**
   * Cost of data Copy operation.
   *
   * @param frame the frame
   * @param memOffset the mem offset
   * @param length the length
   * @return the long
   */
  protected long cost(final MessageFrame frame, final long memOffset, final long length) {
    return gasCalculator().getVeryLowTierGasCost()
        + gasCalculator().extCodeCopyOperationGasCost(frame, memOffset, length);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    Code code = frame.getCode();
    if (code.getEofVersion() == 0) {
      return InvalidOperation.INVALID_RESULT;
    }
    final int memOffset = clampedToInt(frame.popStackItem());
    final int sourceOffset = clampedToInt(frame.popStackItem());
    final int length = clampedToInt(frame.popStackItem());
    final long cost = cost(frame, memOffset, length);
    if (cost > frame.getRemainingGas()) {
      return new OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_GAS);
    }

    final Bytes data = code.getData(sourceOffset, length);
    frame.writeMemory(memOffset, length, data);

    return new OperationResult(cost, null);
  }
}
