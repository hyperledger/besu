/*
 * Copyright contributors to Hyperledger Besu.
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

/** The Abstract fixed cost operation. */
abstract class AbstractFixedCostOperation extends AbstractOperation {

  /** The Success response. */
  protected final OperationResult successResponse;

  /** The Gas cost. */
  protected final long gasCost;

  /**
   * Instantiates a new Abstract fixed cost operation.
   *
   * @param opcode the opcode
   * @param name the name
   * @param stackItemsConsumed the stack items consumed
   * @param stackItemsProduced the stack items produced
   * @param gasCalculator the gas calculator
   * @param fixedCost the fixed cost
   */
  protected AbstractFixedCostOperation(
      final int opcode,
      final String name,
      final int stackItemsConsumed,
      final int stackItemsProduced,
      final GasCalculator gasCalculator,
      final long fixedCost) {
    super(opcode, name, stackItemsConsumed, stackItemsProduced, gasCalculator);
    gasCost = fixedCost;
    successResponse = new OperationResult(gasCost);
  }

  @Override
  public final OperationResult execute(final MessageFrame frame, final EVM evm) {
    if (frame.getRemainingGas() < gasCost) {
      return OperationResult.insufficientGas(gasCost);
    } else {
      return executeFixedCostOperation(frame, evm);
    }
  }

  /**
   * Execute fixed cost operation.
   *
   * @param frame the frame
   * @param evm the evm
   * @return the operation result
   */
  protected abstract OperationResult executeFixedCostOperation(MessageFrame frame, EVM evm);
}
