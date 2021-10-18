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
 *
 */

package org.hyperledger.besu.evm.operation;

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.FixedStack.OverflowException;
import org.hyperledger.besu.evm.internal.FixedStack.UnderflowException;

import java.util.Optional;

abstract class AbstractFixedCostOperation extends AbstractOperation {

  protected final OperationResult successResponse;
  protected final OperationResult outOfGasResponse;
  private final OperationResult underflowResponse;
  private final OperationResult overflowResponse;
  protected final Gas gasCost;

  protected AbstractFixedCostOperation(
      final int opcode,
      final String name,
      final int stackItemsConsumed,
      final int stackItemsProduced,
      final boolean updatesProgramCounter,
      final int opSize,
      final GasCalculator gasCalculator,
      final Gas fixedCost) {
    super(
        opcode,
        name,
        stackItemsConsumed,
        stackItemsProduced,
        opSize,
        gasCalculator);
    gasCost = fixedCost;
    successResponse = new OperationResult(Optional.of(gasCost), Optional.empty());
    outOfGasResponse =
        new OperationResult(
            Optional.of(gasCost), Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
    underflowResponse =
        new OperationResult(
            Optional.of(gasCost), Optional.of(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS));
    overflowResponse =
        new OperationResult(
            Optional.of(gasCost), Optional.of(ExceptionalHaltReason.TOO_MANY_STACK_ITEMS));
  }

  @Override
  public final OperationResult execute(final MessageFrame frame, final EVM evm) {
    try {
      if (frame.getRemainingGas().compareTo(gasCost) < 0) {
        return outOfGasResponse;
      } else {
        return executeFixedCostOperation(frame, evm);
      }
    } catch (final UnderflowException ufe) {
      return underflowResponse;
    } catch (final OverflowException ofe) {
      return overflowResponse;
    }
  }

  protected abstract OperationResult executeFixedCostOperation(MessageFrame frame, EVM evm);
}
