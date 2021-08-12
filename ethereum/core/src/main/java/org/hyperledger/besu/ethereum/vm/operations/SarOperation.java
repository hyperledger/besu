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

import org.hyperledger.besu.ethereum.vm.EVM;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class SarOperation extends AbstractFixedCostOperation {

  private static final UInt256 ALL_BITS = UInt256.MAX_VALUE;

  public SarOperation(final GasCalculator gasCalculator) {
    super(0x1d, "SAR", 2, 1, false, 1, gasCalculator, gasCalculator.getVeryLowTierGasCost());
  }

  @Override
  public OperationResult executeFixedCostOperation(final MessageFrame frame, final EVM evm) {
    final UInt256 shiftAmount = frame.popStackItem();
    Bytes32 value = frame.popStackItem();

    final boolean negativeNumber = value.get(0) < 0;

    // short circuit result if we are shifting more than the width of the data.
    if (!shiftAmount.fitsInt() || shiftAmount.intValue() >= 256) {
      final UInt256 overflow = negativeNumber ? ALL_BITS : UInt256.ZERO;
      frame.pushStackItem(overflow);
      return successResponse;
    }

    // first perform standard shift right.
    value = value.shiftRight(shiftAmount.intValue());

    // if a negative number, carry through the sign.
    if (negativeNumber) {
      final Bytes32 significantBits = ALL_BITS.shiftLeft(256 - shiftAmount.intValue());
      value = value.or(significantBits);
    }
    frame.pushStackItem(UInt256.fromBytes(value));

    return successResponse;
  }
}
