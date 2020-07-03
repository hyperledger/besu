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
package org.hyperledger.besu.ethereum.vm;

import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.vm.PreAllocatedOperandStack.OverflowException;
import org.hyperledger.besu.ethereum.vm.PreAllocatedOperandStack.UnderflowException;

import java.util.Optional;

public interface Operation {

  class OperationResult {
    final Optional<Gas> gasCost;
    final Optional<ExceptionalHaltReason> haltReason;

    public OperationResult(
        final Optional<Gas> gasCost, final Optional<ExceptionalHaltReason> haltReason) {
      this.gasCost = gasCost;
      this.haltReason = haltReason;
    }

    Optional<Gas> getGasCost() {
      return gasCost;
    }

    public Optional<ExceptionalHaltReason> getHaltReason() {
      return haltReason;
    }
  }

  default OperationResult execute(final MessageFrame frame, final EVM evm) {
    try {
      final Optional<ExceptionalHaltReason> haltReason = exceptionalHaltCondition(frame, evm);
      if (haltReason.isEmpty()) {
        final Gas cost = cost(frame);
        final Optional<Gas> optionalCost = Optional.ofNullable(cost);
        if (cost != null) {
          if (frame.getRemainingGas().compareTo(cost) < 0) {
            return new OperationResult(
                optionalCost, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
          }
          execute(frame);
        }
        return new OperationResult(optionalCost, haltReason);
      } else {
        return new OperationResult(Optional.empty(), haltReason);
      }
    } catch (final UnderflowException ue) {
      return new OperationResult(
          Optional.empty(), Optional.of(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS));
    } catch (final OverflowException oe) {
      return new OperationResult(
          Optional.empty(), Optional.of(ExceptionalHaltReason.TOO_MANY_STACK_ITEMS));
    }
  }

  /**
   * Gas cost of this operation, in context of the provided frame.
   *
   * @param frame The frame for execution of this operation.
   * @return The gas cost associated with executing this operation given the current {@link
   *     MessageFrame}.
   */
  Gas cost(final MessageFrame frame);

  /**
   * Executes the logic behind this operation.
   *
   * @param frame The frame for execution of this operation.
   */
  void execute(final MessageFrame frame);

  /**
   * Check if an exceptional halt condition should apply
   *
   * @param frame the current frame
   * @param evm the currently executing EVM
   * @return an {@link Optional} containing the {@link ExceptionalHaltReason} that applies or empty
   *     if no exceptional halt condition applies.
   */
  default Optional<ExceptionalHaltReason> exceptionalHaltCondition(
      final MessageFrame frame, final EVM evm) {
    return Optional.empty();
  }

  int getOpcode();

  String getName();

  int getStackItemsConsumed();

  int getStackItemsProduced();

  default int getStackSizeChange() {
    return getStackItemsProduced() - getStackItemsConsumed();
  }

  boolean getUpdatesProgramCounter();

  int getOpSize();

  /**
   * Determines whether or not this operation has been virtually added to the contract code. For
   * instance if the contract is not ended by a STOP opcode the {@link EVM} adds an explicit end of
   * script stop which can be considered as virtual.
   *
   * @return a boolean indicating if the operation is virtual.
   */
  default boolean isVirtualOperation() {
    return false;
  }
}
