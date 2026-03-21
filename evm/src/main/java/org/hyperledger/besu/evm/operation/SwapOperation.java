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
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import org.apache.tuweni.bytes.Bytes;

/** The Swap operation. */
public class SwapOperation extends AbstractFixedCostOperation {

  private static final long GAS_COST = 3L;

  /** The constant SWAP_BASE. */
  public static final int SWAP_BASE = 0x8F;

  /** The Swap operation success result. */
  static final OperationResult swapSuccess = new OperationResult(GAS_COST, null);

  private static final OperationResult outOfGasResult =
      new OperationResult(GAS_COST, ExceptionalHaltReason.INSUFFICIENT_GAS);

  private final int index;

  /** The operation result due to underflow. */
  protected final Operation.OperationResult underflowResponse;

  /**
   * Instantiates a new Swap operation.
   *
   * @param index the index
   * @param gasCalculator the gas calculator
   */
  public SwapOperation(final int index, final GasCalculator gasCalculator) {
    super(
        SWAP_BASE + index,
        "SWAP" + index,
        index + 1,
        index + 1,
        gasCalculator,
        GAS_COST);
    this.index = index;
    this.underflowResponse =
        new Operation.OperationResult(gasCost, ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
  }

  @Override
  public Operation.OperationResult executeFixedCostOperation(
      final MessageFrame frame, final EVM evm) {
    return staticOperation(frame, index);
  }

  /**
   * Performs swap operation.
   *
   * @param frame the frame
   * @param index the index
   * @return the operation result
   */
  public static OperationResult staticOperation(final MessageFrame frame, final int index) {
    if (frame.decrementRemainingGas(GAS_COST) < 0) {
      return outOfGasResult;
    }
    final Bytes tmp = frame.getStackItem(0);
    frame.setStackItem(0, frame.getStackItem(index));
    frame.setStackItem(index, tmp);

    return swapSuccess;
  }
}
