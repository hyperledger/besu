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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.calltrace;

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.calltrace.OpcodeCategory.isCreateOp;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.CallTracerResult;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.tracing.TraceFrame;

import java.nio.charset.StandardCharsets;

/**
 * Handles error conditions and revert reasons for call tracing.
 *
 * <p>This class consolidates the error handling logic used throughout the call tracer, including:
 *
 * <ul>
 *   <li>Exceptional halt reason extraction
 *   <li>Revert reason decoding
 *   <li>Hard and soft failure handling
 *   <li>Precompile error handling
 * </ul>
 */
public final class CallTracerErrorHandler {

  /** Standard error message for reverted transactions */
  public static final String EXECUTION_REVERTED = "execution reverted";

  /** Standard error message for failed precompile calls */
  public static final String PRECOMPILE_FAILED = "precompile failed";

  private CallTracerErrorHandler() {
    // Utility class - prevent instantiation
  }

  /**
   * Extracts the error description from a trace frame.
   *
   * @param frame the trace frame that may contain an exceptional halt reason
   * @return the error description, or EXECUTION_REVERTED as default
   */
  public static String getErrorDescription(final TraceFrame frame) {
    return frame
        .getExceptionalHaltReason()
        .map(ExceptionalHaltReason::getDescription)
        .orElse(EXECUTION_REVERTED);
  }

  /**
   * Extracts the error description from a transaction processing result.
   *
   * @param result the transaction processing result
   * @return the error description, or EXECUTION_REVERTED as default
   */
  public static String getErrorDescription(final TransactionProcessingResult result) {
    return result
        .getExceptionalHaltReason()
        .map(ExceptionalHaltReason::getDescription)
        .orElse(EXECUTION_REVERTED);
  }

  /**
   * Handles a hard failure (exceptional halt) that prevented a call from entering.
   *
   * <p>This method sets the appropriate error message and gas used for the failed call. For CREATE
   * operations, it ensures the 'to' field is null.
   *
   * @param frame the trace frame where the failure occurred
   * @param opcode the opcode that failed
   * @param builder the builder to update with error information
   */
  public static void handleHardFailure(
      final TraceFrame frame, final String opcode, final CallTracerResult.Builder builder) {

    String errorMessage = getErrorDescription(frame);
    builder.error(errorMessage);

    // For failed CREATE, ensure 'to' is null
    if (isCreateOp(opcode)) {
      builder.to(null);
    }

    // Set gas used based on failure type
    long gasUsed = CallTracerGasCalculator.calculateHardFailureGasUsed(frame);
    builder.gasUsed(gasUsed);
  }

  /**
   * Handles a soft failure (e.g., insufficient balance) that prevented a call from entering.
   *
   * <p>Soft failures don't consume gas for the child call itself.
   *
   * @param frame the trace frame where the failure occurred
   * @param opcode the opcode that failed
   * @param builder the builder to update with error information
   */
  public static void handleSoftFailure(
      final TraceFrame frame, final String opcode, final CallTracerResult.Builder builder) {

    // Soft failures don't consume gas for the child call itself
    builder.gasUsed(0L);

    // For failed CREATE, ensure 'to' is null
    if (isCreateOp(opcode)) {
      builder.to(null);
    }

    // Set error message based on soft failure reason
    frame.getSoftFailureReason().ifPresent(reason -> builder.error(reason.getDescription()));
  }

  /**
   * Handles errors in precompile execution.
   *
   * @param entryFrame the trace frame containing precompile information
   * @param builder the builder to update with error information
   */
  public static void handlePrecompileError(
      final TraceFrame entryFrame, final CallTracerResult.Builder builder) {

    String errorMessage =
        entryFrame
            .getExceptionalHaltReason()
            .map(ExceptionalHaltReason::getDescription)
            .orElse(PRECOMPILE_FAILED);

    // Check for revert reason with actual error message
    if (entryFrame.getRevertReason().isPresent()) {
      errorMessage =
          new String(entryFrame.getRevertReason().get().toArrayUnsafe(), StandardCharsets.UTF_8);
    }

    builder.error(errorMessage);
  }

  /**
   * Handles errors for the root transaction.
   *
   * @param builder the builder to update with error information
   * @param tx the transaction
   * @param result the transaction processing result
   */
  public static void handleRootError(
      final CallTracerResult.Builder builder,
      final Transaction tx,
      final TransactionProcessingResult result) {

    builder.error(getErrorDescription(result));

    if (tx.isContractCreation()) {
      builder.to(null);
      result.getRevertReason().ifPresent(builder::revertReason);
    } else if (result.getExceptionalHaltReason().isEmpty()
        && result.getRevertReason().isPresent()) {
      // For regular calls that reverted, set the output to the revert reason
      builder.output(result.getRevertReason().get().toHexString());
    }
  }

  /**
   * Sets the output data and error status for a completed call.
   *
   * @param builder the builder to update
   * @param frame the trace frame of the return operation
   * @param opcode the return opcode (RETURN, REVERT, STOP)
   */
  public static void setOutputAndErrorStatus(
      final CallTracerResult.Builder builder, final TraceFrame frame, final String opcode) {

    // Set output data if present
    if (frame.getOutputData() != null && !frame.getOutputData().isEmpty()) {
      builder.output(frame.getOutputData().toHexString());
    }

    // Handle errors
    if (frame.getExceptionalHaltReason().isPresent()) {
      handleExceptionalHalt(builder, frame);
    } else if (OpcodeCategory.isRevertOp(opcode)) {
      builder.error(EXECUTION_REVERTED);
      frame.getRevertReason().ifPresent(builder::revertReason);
    }
  }

  /**
   * Handles an exceptional halt during execution.
   *
   * @param builder the builder to update
   * @param frame the trace frame with the exceptional halt
   */
  public static void handleExceptionalHalt(
      final CallTracerResult.Builder builder, final TraceFrame frame) {

    String errorMessage = getErrorDescription(frame);
    builder.error(errorMessage);

    // For failed CREATE, set 'to' to null and use code as input
    if (isCreateOp(builder.getType())) {
      builder.to(null);
      frame
          .getMaybeCode()
          .map(Code::getBytes)
          .ifPresent(codeBytes -> builder.input(codeBytes.toHexString()));
    }

    frame.getRevertReason().ifPresent(builder::revertReason);
  }
}
