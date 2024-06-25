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

import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import org.apache.tuweni.bytes.Bytes;

/** The Return data copy operation. */
public class ReturnDataCopyOperation extends AbstractOperation {

  /** The constant INVALID_RETURN_DATA_BUFFER_ACCESS. */
  protected static final OperationResult INVALID_RETURN_DATA_BUFFER_ACCESS =
      new OperationResult(0L, ExceptionalHaltReason.INVALID_RETURN_DATA_BUFFER_ACCESS);

  /** The constant OUT_OF_BOUNDS. */
  protected static final OperationResult OUT_OF_BOUNDS =
      new OperationResult(0L, ExceptionalHaltReason.OUT_OF_BOUNDS);

  /**
   * Instantiates a new Return data copy operation.
   *
   * @param gasCalculator the gas calculator
   */
  public ReturnDataCopyOperation(final GasCalculator gasCalculator) {
    super(0x3E, "RETURNDATACOPY", 3, 0, gasCalculator);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    final long memOffset = clampedToLong(frame.popStackItem());
    final long sourceOffset = clampedToLong(frame.popStackItem());
    final long numBytes = clampedToLong(frame.popStackItem());
    final Bytes returnData = frame.getReturnData();
    final int returnDataLength = returnData.size();

    if (frame.getCode().getEofVersion() < 1) {
      try {
        final long end = Math.addExact(sourceOffset, numBytes);
        if (end > returnDataLength) {
          return INVALID_RETURN_DATA_BUFFER_ACCESS;
        }
      } catch (final ArithmeticException ae) {
        return OUT_OF_BOUNDS;
      }
    }

    final long cost = gasCalculator().dataCopyOperationGasCost(frame, memOffset, numBytes);
    if (frame.getRemainingGas() < cost) {
      return new OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_GAS);
    }

    frame.writeMemory(memOffset, sourceOffset, numBytes, returnData, true);

    return new OperationResult(cost, null);
  }
}
