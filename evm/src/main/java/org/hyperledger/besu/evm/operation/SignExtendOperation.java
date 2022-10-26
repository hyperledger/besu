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

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import org.apache.tuweni.bytes.MutableBytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class SignExtendOperation extends AbstractFixedCostOperation {

  private static final OperationResult signExtendSuccess = new OperationResult(5, null);

  public SignExtendOperation(final GasCalculator gasCalculator) {
    super(0x0B, "SIGNEXTEND", 2, 1, 1, gasCalculator, gasCalculator.getLowTierGasCost());
  }

  @Override
  public Operation.OperationResult executeFixedCostOperation(
      final MessageFrame frame, final EVM evm) {
    return staticOperation(frame);
  }

  public static OperationResult staticOperation(final MessageFrame frame) {
    final UInt256 value0 = UInt256.fromBytes(frame.popStackItem());
    final UInt256 value1 = UInt256.fromBytes(frame.popStackItem());

    final MutableBytes32 result = MutableBytes32.create();

    // Any value >= 31 imply an index <= 0, so no work to do (note that 0 itself is a valid index,
    // but copying the 0th byte to itself is only so useful).
    if (!value0.fitsInt() || value0.intValue() >= 31) {
      frame.pushStackItem(value1);
    } else {
      // This is safe, since other < 31.
      final int byteIndex = 32 - 1 - value0.getInt(32 - 4);
      final byte toSet = value1.get(byteIndex) < 0 ? (byte) 0xFF : 0x00;
      result.mutableSlice(0, byteIndex).fill(toSet);
      value1.slice(byteIndex).copyTo(result, byteIndex);
      frame.pushStackItem(UInt256.fromBytes(result));
    }

    return signExtendSuccess;
  }
}
