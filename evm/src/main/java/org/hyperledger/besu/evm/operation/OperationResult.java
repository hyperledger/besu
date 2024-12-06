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

import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;

/** The Operation result. */
public class OperationResult {
  /** The Gas cost. */
  private final long gasCost;

  /** The Halt reason. */
  private final ExceptionalHaltReason haltReason;

  /** The increment. */
  private final int pcIncrement;

  /**
   * Instantiates a new Operation result when due to an ExceptionalHaltReason.
   *
   * @param haltReason the halt reason
   */
  private OperationResult(final ExceptionalHaltReason haltReason) {
    this(0L, haltReason);
  }

  /**
   * Instantiates a new Operation result when due to an ExceptionalHaltReason with an associated
   * gasCost increment. Only InsufficientGas has a gas cost increment associated with the reason for
   * halting, all other halt reasons should use this constructor.
   *
   * @param gasCost the gas cost increment that caused the execution to halt
   * @param haltReason the halt reason
   */
  private OperationResult(final long gasCost, final ExceptionalHaltReason haltReason) {
    this.gasCost = gasCost;
    this.pcIncrement = 0;
    this.haltReason = haltReason;
  }

  /**
   * Instantiates a new Operation result.
   *
   * @param gasCost the gas cost
   */
  public OperationResult(final long gasCost) {
    this(gasCost, 1);
  }

  /**
   * Instantiates a new Operation result.
   *
   * @param gasCost the gas cost
   * @param pcIncrement the increment
   */
  public OperationResult(final long gasCost, final int pcIncrement) {
    this.gasCost = gasCost;
    this.pcIncrement = pcIncrement;
    this.haltReason = null;
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
   * Returns a halted OperationResult due to ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS.
   *
   * @return halted OperationResult
   */
  public static OperationResult underFlow() {
    return new OperationResult(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
  }

  /**
   * Returns a halted OperationResult due to ExceptionalHaltReason.TOO_MANY_STACK_ITEMS.
   *
   * @return halted OperationResult
   */
  public static OperationResult overFlow() {
    return new OperationResult(ExceptionalHaltReason.TOO_MANY_STACK_ITEMS);
  }

  /**
   * Returns a halted OperationResult due to ExceptionalHaltReason.INSUFFICIENT_GAS.
   *
   * @param overshotGasCost gas cost that exceeded the remaining gas during execution
   * @return halted OperationResult
   */
  public static OperationResult insufficientGas(final long overshotGasCost) {
    return new OperationResult(overshotGasCost, ExceptionalHaltReason.INSUFFICIENT_GAS);
  }

  /**
   * Returns a halted OperationResult due to ExceptionalHaltReason.ILLEGAL_STATE_CHANGE.
   *
   * @return halted OperationResult
   */
  public static OperationResult illegalStateChange() {
    return new OperationResult(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE);
  }

  /**
   * Returns a halted OperationResult due to ExceptionalHaltReason.INVALID_OPERATION.
   *
   * @return halted OperationResult
   */
  public static OperationResult invalidOperation() {
    return new OperationResult(ExceptionalHaltReason.INVALID_OPERATION);
  }

  /**
   * Returns a halted OperationResult due to ExceptionalHaltReason.INVALID_JUMP_DESTINATION.
   *
   * @return halted OperationResult
   */
  public static OperationResult invalidJumpDestination() {
    return new OperationResult(ExceptionalHaltReason.INVALID_JUMP_DESTINATION);
  }

  /**
   * Returns a halted OperationResult due to
   * ExceptionalHaltReason.INVALID_RETURN_DATA_BUFFER_ACCESS.
   *
   * @return halted OperationResult
   */
  public static OperationResult invalidReturnDataBufferAccess() {
    return new OperationResult(ExceptionalHaltReason.INVALID_RETURN_DATA_BUFFER_ACCESS);
  }

  /**
   * Returns a halted OperationResult due to ExceptionalHaltReason.OUT_OF_BOUNDS.
   *
   * @return halted OperationResult
   */
  public static OperationResult outOfBounds() {
    return new OperationResult(ExceptionalHaltReason.OUT_OF_BOUNDS);
  }

  /**
   * Returns a halted OperationResult due to ExceptionalHaltReason.CODE_TOO_LARGE.
   *
   * @return halted OperationResult
   */
  public static OperationResult codeTooLarge() {
    return new OperationResult(ExceptionalHaltReason.CODE_TOO_LARGE);
  }

  /**
   * Returns a halted OperationResult due to ExceptionalHaltReason.INVALID_CODE.
   *
   * @return halted OperationResult
   */
  public static OperationResult invalidCode() {
    return new OperationResult(ExceptionalHaltReason.INVALID_CODE);
  }

  /**
   * Returns a halted OperationResult due to ExceptionalHaltReason.NONEXISTENT_CONTAINER.
   *
   * @return halted OperationResult
   */
  public static OperationResult nonExistentContainer() {
    return new OperationResult(ExceptionalHaltReason.NONEXISTENT_CONTAINER);
  }

  /**
   * Returns a halted OperationResult due to ExceptionalHaltReason.INVALID_CONTAINER.
   *
   * @return halted OperationResult
   */
  public static OperationResult invalidContainer() {
    return new OperationResult(ExceptionalHaltReason.INVALID_CONTAINER);
  }

  /**
   * Returns a halted OperationResult due to ExceptionalHaltReason.DATA_TOO_SMALL.
   *
   * @return halted OperationResult
   */
  public static OperationResult dataTooSmall() {
    return new OperationResult(ExceptionalHaltReason.DATA_TOO_SMALL);
  }

  /**
   * Returns a halted OperationResult due to ExceptionalHaltReason.ADDRESS_OUT_OF_RANGE.
   *
   * @return halted OperationResult
   */
  public static OperationResult addressOutOfRange() {
    return new OperationResult(ExceptionalHaltReason.ADDRESS_OUT_OF_RANGE);
  }
}
