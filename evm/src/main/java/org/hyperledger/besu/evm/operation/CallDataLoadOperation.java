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
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class CallDataLoadOperation extends AbstractFixedCostOperation {

  public CallDataLoadOperation(final GasCalculator gasCalculator) {
    super(0x35, "CALLDATALOAD", 1, 1, 1, gasCalculator, gasCalculator.getVeryLowTierGasCost());
  }

  @Override
  public Operation.OperationResult executeFixedCostOperation(
      final MessageFrame frame, final EVM evm) {
    final UInt256 startWord = UInt256.fromBytes(frame.popStackItem());

    // If the start index doesn't fit a int, it comes after anything in data, and so the returned
    // word should be zero.
    if (!startWord.fitsInt()) {
      frame.pushStackItem(UInt256.ZERO);
      return successResponse;
    }

    final int offset = startWord.intValue();
    final Bytes data = frame.getInputData();
    final MutableBytes32 res = MutableBytes32.create();
    if (offset < data.size()) {
      final Bytes toCopy = data.slice(offset, Math.min(Bytes32.SIZE, data.size() - offset));
      toCopy.copyTo(res, 0);
    }
    frame.pushStackItem(UInt256.fromBytes(res));

    return successResponse;
  }
}
