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
package org.hyperledger.besu.ethereum.vm.operations;

import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.vm.AbstractOperation;
import org.hyperledger.besu.ethereum.vm.EVM;
import org.hyperledger.besu.ethereum.vm.ExceptionalHaltReason;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;

import java.util.EnumSet;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class ReturnDataCopyOperation extends AbstractOperation {

  public ReturnDataCopyOperation(final GasCalculator gasCalculator) {
    super(0x3E, "RETURNDATACOPY", 3, 0, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    final UInt256 offset = UInt256.fromBytes(frame.getStackItem(0));
    final UInt256 length = UInt256.fromBytes(frame.getStackItem(2));

    return gasCalculator().dataCopyOperationGasCost(frame, offset, length);
  }

  @Override
  public void execute(final MessageFrame frame) {
    final Bytes returnData = frame.getReturnData();

    final UInt256 memOffset = UInt256.fromBytes(frame.popStackItem());
    final UInt256 sourceOffset = UInt256.fromBytes(frame.popStackItem());
    final UInt256 numBytes = UInt256.fromBytes(frame.popStackItem());

    frame.writeMemory(memOffset, sourceOffset, numBytes, returnData, true);
  }

  @Override
  public Optional<ExceptionalHaltReason> exceptionalHaltCondition(
      final MessageFrame frame,
      final EnumSet<ExceptionalHaltReason> previousReasons,
      final EVM evm) {
    final Bytes returnData = frame.getReturnData();

    final UInt256 start = UInt256.fromBytes(frame.getStackItem(1));
    final UInt256 length = UInt256.fromBytes(frame.getStackItem(2));
    final UInt256 returnDataLength = UInt256.valueOf(returnData.size());

    if (!start.fitsInt()
        || !length.fitsInt()
        || start.add(length).compareTo(returnDataLength) > 0) {
      return Optional.of(ExceptionalHaltReason.INVALID_RETURN_DATA_BUFFER_ACCESS);
    } else {
      return Optional.empty();
    }
  }
}
