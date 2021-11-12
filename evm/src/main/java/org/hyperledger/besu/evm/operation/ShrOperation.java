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

import static org.apache.tuweni.bytes.Bytes32.leftPad;

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class ShrOperation extends AbstractFixedCostOperation {

  public ShrOperation(final GasCalculator gasCalculator) {
    super(0x1c, "SHR", 2, 1, 1, gasCalculator, gasCalculator.getVeryLowTierGasCost());
  }

  @Override
  public Operation.OperationResult executeFixedCostOperation(
      final MessageFrame frame, final EVM evm) {
    Bytes shiftAmount = frame.popStackItem();
    if (shiftAmount.size() > 4 && (shiftAmount = shiftAmount.trimLeadingZeros()).size() > 4) {
      frame.popStackItem();
      frame.pushStackItem(UInt256.ZERO);
    } else {
      final int shiftAmountInt = shiftAmount.toInt();
      final Bytes value = leftPad(frame.popStackItem());

      if (shiftAmountInt >= 256 || shiftAmountInt < 0) {
        frame.pushStackItem(UInt256.ZERO);
      } else {
        frame.pushStackItem(value.shiftRight(shiftAmountInt));
      }
    }
    return successResponse;
  }
}
