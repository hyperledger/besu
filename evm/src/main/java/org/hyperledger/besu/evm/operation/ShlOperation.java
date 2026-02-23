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
import org.hyperledger.besu.evm.UInt256;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

/** The Shl (Shift Left) operation. */
public class ShlOperation extends AbstractFixedCostOperation {

  /** The Shl operation success result. */
  static final OperationResult shlSuccess = new OperationResult(3, null);

  /**
   * Instantiates a new Shl operation.
   *
   * @param gasCalculator the gas calculator
   */
  public ShlOperation(final GasCalculator gasCalculator) {
    super(0x1b, "SHL", 2, 1, gasCalculator, gasCalculator.getVeryLowTierGasCost());
  }

  @Override
  public Operation.OperationResult executeFixedCostOperation(
      final MessageFrame frame, final EVM evm) {
    return staticOperation(frame);
  }

  /**
   * Performs Shift Left operation.
   *
   * @param frame the frame
   * @return the operation result
   */
  public static OperationResult staticOperation(final MessageFrame frame) {
    if (!frame.stackHasItems(2)) return UNDERFLOW_RESPONSE;
    final UInt256 shiftAmount = frame.peekStackItemUnsafe(0);
    final UInt256 value = frame.peekStackItemUnsafe(1);
    frame.shrinkStackUnsafe(1);

    if (value.isZero()
        || !shiftAmount.isUInt64()
        || Long.compareUnsigned(shiftAmount.longValue(), 256) >= 0) {
      frame.overwriteStackItemUnsafe(0, UInt256.ZERO);
    } else {
      frame.overwriteStackItemUnsafe(0, value.shl((int) shiftAmount.longValue()));
    }
    return shlSuccess;
  }
}
