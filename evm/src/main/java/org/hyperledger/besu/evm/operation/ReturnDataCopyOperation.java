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

import java.util.Optional;
import java.util.OptionalLong;

import org.apache.tuweni.bytes.Bytes;

public class ReturnDataCopyOperation extends AbstractOperation {

  protected static final OperationResult INVALID_RETURN_DATA_BUFFER_ACCESS =
      new OperationResult(
          OptionalLong.of(0), Optional.of(ExceptionalHaltReason.INVALID_RETURN_DATA_BUFFER_ACCESS));
  protected static final OperationResult OUT_OF_BOUNDS =
      new OperationResult(OptionalLong.of(0L), Optional.of(ExceptionalHaltReason.OUT_OF_BOUNDS));

  public ReturnDataCopyOperation(final GasCalculator gasCalculator) {
    super(0x3E, "RETURNDATACOPY", 3, 0, 1, gasCalculator);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    final long memOffset = clampedToLong(frame.popStackItem());
    final long sourceOffset = clampedToLong(frame.popStackItem());
    final long numBytes = clampedToLong(frame.popStackItem());
    final Bytes returnData = frame.getReturnData();
    final int returnDataLength = returnData.size();

    try {
      final long end = Math.addExact(sourceOffset, numBytes);
      if (end > returnDataLength) {
        return INVALID_RETURN_DATA_BUFFER_ACCESS;
      }
    } catch (final ArithmeticException ae) {
      return OUT_OF_BOUNDS;
    }

    final long cost = gasCalculator().dataCopyOperationGasCost(frame, memOffset, numBytes);
    if (frame.getRemainingGas() < cost) {
      return new OperationResult(
          OptionalLong.of(cost), Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
    }

    frame.writeMemory(memOffset, sourceOffset, numBytes, returnData, true);

    return new OperationResult(OptionalLong.of(cost), Optional.empty());
  }
}
