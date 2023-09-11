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
package org.hyperledger.besu.evm.tracing;

import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

/** The interface Operation tracer. */
public interface OperationTracer {

  /** The constant NO_TRACING. */
  OperationTracer NO_TRACING = new OperationTracer() {};

  /**
   * Trace pre execution.
   *
   * @param frame the frame
   */
  default void tracePreExecution(final MessageFrame frame) {}

  /**
   * Trace post execution.
   *
   * @param frame the frame
   * @param operationResult the operation result
   */
  default void tracePostExecution(
      final MessageFrame frame, final OperationResult operationResult) {}

  /**
   * Trace precompile call.
   *
   * @param frame the frame
   * @param gasRequirement the gas requirement
   * @param output the output
   */
  default void tracePrecompileCall(
      final MessageFrame frame, final long gasRequirement, final Bytes output) {}

  /**
   * Trace account creation result.
   *
   * @param frame the frame
   * @param haltReason the halt reason
   */
  default void traceAccountCreationResult(
      final MessageFrame frame, final Optional<ExceptionalHaltReason> haltReason) {}

  /**
   * Trace the start of a transaction.
   *
   * @param transaction the transaction which will be processed
   */
  default void traceStartTransaction(final Transaction transaction) {}

  /**
   * Trace the end of a transaction.
   *
   * @param output the bytes output from the transaction
   * @param gasUsed the gas used by the entire transaction
   * @param timeNs the time in nanoseconds it took to execute the transaction
   */
  default void traceEndTransaction(final Bytes output, final long gasUsed, final long timeNs) {}

  /**
   * Trace the entering of a new context
   *
   * @param frame the frame
   */
  default void traceContextEnter(final MessageFrame frame) {}

  /**
   * Trace the exiting of a context
   *
   * @param frame the frame
   */
  default void traceContextExit(final MessageFrame frame) {}

  /**
   * Returns a boolean indicating whether extended tracing is enabled.
   *
   * @return <code>true</code> if extended tracing is enabled, <code>false</code> otherwise.
   */
  default boolean isExtendedTracing() {
    return false;
  }
}
