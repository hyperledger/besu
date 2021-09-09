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

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class ReturnDataCopyOperation extends AbstractOperation {

  protected static final OperationResult INVALID_RETURN_DATA_BUFFER_ACCESS =
      new OperationResult(
          Optional.empty(), Optional.of(ExceptionalHaltReason.INVALID_RETURN_DATA_BUFFER_ACCESS));

  public ReturnDataCopyOperation(final GasCalculator gasCalculator) {
    super(0x3E, "RETURNDATACOPY", 3, 0, false, 1, gasCalculator);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    final UInt256 memOffset = UInt256.fromBytes(frame.popStackItem());
    final UInt256 sourceOffset = UInt256.fromBytes(frame.popStackItem());
    final UInt256 numBytes = UInt256.fromBytes(frame.popStackItem());
    final Bytes returnData = frame.getReturnData();
    final UInt256 returnDataLength = UInt256.valueOf(returnData.size());

    if (!sourceOffset.fitsInt()
        || !numBytes.fitsInt()
        || sourceOffset.add(numBytes).compareTo(returnDataLength) > 0) {
      return INVALID_RETURN_DATA_BUFFER_ACCESS;
    }

    final Gas cost = gasCalculator().dataCopyOperationGasCost(frame, memOffset, numBytes);
    final Optional<Gas> optionalCost = Optional.of(cost);
    if (frame.getRemainingGas().compareTo(cost) < 0) {
      return new OperationResult(optionalCost, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
    }

    frame.writeMemory(memOffset, sourceOffset, numBytes, returnData, true);

    return new OperationResult(optionalCost, Optional.empty());
  }
}
