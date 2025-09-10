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
import org.hyperledger.besu.evm.frame.SoftFailureReason;

import java.util.Optional;
import java.util.OptionalLong;

/** The interface Operation. */
public interface Operation {

  /** The Operation result. */
  class OperationResult {
    /** The Gas cost. */
    final long gasCost;

    /** The Halt reason. */
    final ExceptionalHaltReason haltReason;

    /** The increment. */
    final int pcIncrement;

    /** Soft Failure Reason. Mainly used to provide visibility to tracers. */
    final SoftFailureReason softFailureReason;

    /** Gas allocated for child call, if applicable. Used in conjunction to soft failure reason. */
    private final OptionalLong gasAvailableForChildCall;

    /**
     * Instantiates a new Operation result.
     *
     * @param gasCost the gas cost
     * @param haltReason the halt reason
     */
    public OperationResult(final long gasCost, final ExceptionalHaltReason haltReason) {
      this(gasCost, haltReason, 1);
    }

    /**
     * Instantiates a new Operation result.
     *
     * @param gasCost the gas cost
     * @param haltReason the halt reason
     * @param pcIncrement the increment
     */
    public OperationResult(
        final long gasCost, final ExceptionalHaltReason haltReason, final int pcIncrement) {
      this.gasCost = gasCost;
      this.haltReason = haltReason;
      this.pcIncrement = pcIncrement;
      this.softFailureReason = null;
      this.gasAvailableForChildCall = OptionalLong.empty();
    }

    /**
     * Instantiates a new Operation result for a soft failure.
     *
     * @param gasCost the gas cost
     * @param pcIncrement the increment
     * @param softFailureReason the soft failure reason
     * @param gasAvailableForChildCall the gas available for child call
     */
    public OperationResult(
        final long gasCost,
        final int pcIncrement,
        final SoftFailureReason softFailureReason,
        final long gasAvailableForChildCall) {
      this.gasCost = gasCost;
      this.haltReason = null;
      this.pcIncrement = pcIncrement;
      this.softFailureReason = softFailureReason;
      this.gasAvailableForChildCall = OptionalLong.of(gasAvailableForChildCall);
    }

    /**
     * Gets gas cost.
     *
     * @return the gas cost
     */
    public long getGasCost() {
      return gasCost;
    }

    /**
     * Gets halt reason.
     *
     * @return the halt reason
     */
    public ExceptionalHaltReason getHaltReason() {
      return haltReason;
    }

    /**
     * Gets increment.
     *
     * @return the increment
     */
    public int getPcIncrement() {
      return pcIncrement;
    }

    /**
     * Returns optional Soft Failure Reason.
     *
     * @return Optional Soft Failure Reason
     */
    public Optional<SoftFailureReason> getSoftFailureReason() {
      return Optional.ofNullable(this.softFailureReason);
    }

    /**
     * If this operation result is associated with a soft failure that involves a child call,
     * returns the gas allocated for that child call.
     *
     * @return an OptionalLong containing the gas allocated for the child call, or empty if not
     *     applicable.
     */
    public OptionalLong getGasAvailableForChildCall() {
      return gasAvailableForChildCall;
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

  /**
   * Gets opcode.
   *
   * @return the opcode
   */
  int getOpcode();

  /**
   * Gets name.
   *
   * @return the name
   */
  String getName();

  /**
   * Gets stack items consumed.
   *
   * @return the stack items consumed
   */
  int getStackItemsConsumed();

  /**
   * Gets stack items produced.
   *
   * @return the stack items produced
   */
  int getStackItemsProduced();

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
