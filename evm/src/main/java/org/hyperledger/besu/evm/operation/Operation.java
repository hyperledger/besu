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

import java.util.Optional;
import java.util.OptionalLong;

public interface Operation {

  class OperationResult {
    final OptionalLong gasCost;
    final Optional<ExceptionalHaltReason> haltReason;
    final int pcIncrement;

    public OperationResult(
        final OptionalLong gasCost, final Optional<ExceptionalHaltReason> haltReason) {
      this(gasCost, haltReason, 1);
    }

    public OperationResult(
        final OptionalLong gasCost,
        final Optional<ExceptionalHaltReason> haltReason,
        final int pcIncrement) {
      this.gasCost = gasCost;
      this.haltReason = haltReason;
      this.pcIncrement = pcIncrement;
    }

    public OptionalLong getGasCost() {
      return gasCost;
    }

    public Optional<ExceptionalHaltReason> getHaltReason() {
      return haltReason;
    }

    public int getPcIncrement() {
      return pcIncrement;
    }
  }

  /**
   * Executes the logic behind this operation.
   *
   * <p>Implementors are responsible for calculating gas cost, checking Out-of-gas conditions,
   * applying gas cost to the MessageFrame, executing the operation including all side effects, and
   * checking for all operation related exceptional halts such as OutOfGas, InvalidJumpDestination,
   * Stack overflow/underflow, etc., and storing the halt in the MessageFrame
   *
   * @param frame The frame for execution of this operation.
   * @param evm The EVM for execution of this operation.
   * @return the gas cost and any exceptional halt reasons of the operation.
   */
  OperationResult execute(final MessageFrame frame, final EVM evm);

  int getOpcode();

  String getName();

  int getStackItemsConsumed();

  int getStackItemsProduced();

  int getOpSize();

  /**
   * Determines whether this operation has been virtually added to the contract code. For instance
   * if the contract is not ended by a STOP opcode the {@link EVM} adds an explicit end of script
   * stop which can be considered as virtual.
   *
   * @return a boolean indicating if the operation is virtual.
   */
  default boolean isVirtualOperation() {
    return false;
  }
}
