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

/** The Dup operation. */
public class DupOperation extends AbstractFixedCostOperation {

  private static final long GAS_COST = 3L;

  /** The constant DUP_BASE. */
  public static final int DUP_BASE = 0x7F;

  /** The Dup success operation result. */
  static final OperationResult dupSuccess = new OperationResult(GAS_COST, null);

  private static final OperationResult outOfGasResult =
      new OperationResult(GAS_COST, ExceptionalHaltReason.INSUFFICIENT_GAS);

  /** The Underflow response. */
  protected final Operation.OperationResult underflowResponse;

  private final int index;

  /**
   * Instantiates a new Dup operation.
   *
   * @param index the index
   * @param gasCalculator the gas calculator
   */
  public DupOperation(final int index, final GasCalculator gasCalculator) {
    super(
        0x80 + index - 1,
        "DUP" + index,
        index,
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
   * Performs Dup operation.
   *
   * @param frame the frame
   * @param index the index
   * @return the operation result
   */
  public static OperationResult staticOperation(final MessageFrame frame, final int index) {
    if (frame.decrementRemainingGas(GAS_COST) < 0) {
      return outOfGasResult;
    }
    frame.pushStackItem(frame.getStackItem(index - 1));

    return dupSuccess;
  }
}
