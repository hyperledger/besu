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

import org.apache.tuweni.bytes.MutableBytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class ByteOperation extends AbstractFixedCostOperation {

  public ByteOperation(final GasCalculator gasCalculator) {
    super(0x1A, "BYTE", 2, 1, 1, gasCalculator, gasCalculator.getVeryLowTierGasCost());
  }

  private UInt256 getByte(final UInt256 seq, final UInt256 offset) {
    if (!offset.fitsInt()) {
      return UInt256.ZERO;
    }

    final int index = offset.intValue();
    if (index >= 32) {
      return UInt256.ZERO;
    }

    final byte b = seq.get(index);
    final MutableBytes32 res = MutableBytes32.create();
    res.set(31, b);
    return UInt256.fromBytes(res);
  }

  @Override
  public Operation.OperationResult executeFixedCostOperation(
      final MessageFrame frame, final EVM evm) {
    final UInt256 value0 = UInt256.fromBytes(frame.popStackItem());
    final UInt256 value1 = UInt256.fromBytes(frame.popStackItem());

    // Stack items are reversed for the BYTE operation.
    final UInt256 result = getByte(value1, value0);

    frame.pushStackItem(result);

    return successResponse;
  }
}
