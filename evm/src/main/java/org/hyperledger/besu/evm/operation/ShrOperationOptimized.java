/*
 * Copyright contributors to Besu.
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
import org.hyperledger.besu.evm.UInt256;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

/** The optimized SHR (Shift Right Logical) operation. */
public class ShrOperationOptimized extends AbstractFixedCostOperation {

  /** The Shr operation success result. */
  static final OperationResult shrSuccess = new OperationResult(3, null);

  /**
   * Instantiates a new optimized Shr operation.
   *
   * @param gasCalculator the gas calculator
   */
  public ShrOperationOptimized(final GasCalculator gasCalculator) {
    super(0x1c, "SHR", 2, 1, gasCalculator, gasCalculator.getVeryLowTierGasCost());
  }

  @Override
  public Operation.OperationResult executeFixedCostOperation(
      final MessageFrame frame, final EVM evm) {
    return staticOperation(frame);
  }

  /**
   * Performs optimized Shift Right Logical operation.
   *
   * @param frame the frame
   * @return the operation result
   */
  public static OperationResult staticOperation(final MessageFrame frame) {
    final UInt256 shiftAmount = frame.popStackItem();
    final UInt256 value = frame.popStackItem();

    if (value.isZero()
        || !shiftAmount.isUInt64()
        || Long.compareUnsigned(shiftAmount.longValue(), 256) >= 0) {
      frame.pushStackItem(UInt256.ZERO);
    } else {
      frame.pushStackItem(value.shr((int) shiftAmount.longValue()));
    }
    return shrSuccess;
  }
}
