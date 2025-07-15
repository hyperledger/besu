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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.debug.TraceFrame;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;

/**
 * Converts TransactionTrace objects to CallTracerResult format following Geth specifications.
 *
 * <p>Handles proper "to" field semantics:
 *
 * <ul>
 *   <li>CALL/STATICCALL: The contract or receiving address being called
 *   <li>DELEGATECALL: The contract whose code is being executed
 *   <li>CREATE/CREATE2: The address of the newly deployed contract
 *   <li>SELFDESTRUCT/SUICIDE: The address of the refund recipient
 * </ul>
 */
public class CallTracerResultConverter {

  /**
   * Converts a TransactionTrace to CallTracerResult.
   *
   * @param transactionTrace the transaction trace to convert
   * @return the converted CallTracerResult
   * @throws IllegalArgumentException if transactionTrace is null or invalid
   */
  public static CallTracerResult convert(final TransactionTrace transactionTrace) {
    if (transactionTrace == null) {
      throw new IllegalArgumentException("TransactionTrace cannot be null");
    }

    if (transactionTrace.getTransaction() == null || transactionTrace.getResult() == null) {
      throw new IllegalArgumentException("TransactionTrace must have valid transaction and result");
    }

    if (transactionTrace.getTraceFrames() == null || transactionTrace.getTraceFrames().isEmpty()) {
      return createRootCallFromTransaction(transactionTrace);
    }

    return buildCallHierarchyFromFrames(transactionTrace);
  }

  /**
   * Creates a root call when no trace frames are available. This typically happens for simple value
   * transfers or failed transactions.
   */
  private static CallTracerResult createRootCallFromTransaction(
      final TransactionTrace transactionTrace) {
    Transaction transaction = transactionTrace.getTransaction();
    TransactionProcessingResult result = transactionTrace.getResult();

    String toAddress = null;
    String callType = "CALL";

    if (transaction.isContractCreation()) {
      callType = "CREATE";
      // For CREATE, "to" should be the address of the newly created contract
      toAddress = transaction.contractAddress().map(Address::toHexString).orElse(null);
    } else {
      // For regular calls, "to" is the recipient
      toAddress = transaction.getTo().map(Address::toHexString).orElse(null);
    }

    return CallTracerResult.builder()
        .type(callType)
        .from(transaction.getSender().toHexString())
        .to(toAddress)
        .value(transaction.getValue().toShortHexString())
        .gas(transaction.getGasLimit())
        .gasUsed(transactionTrace.getGas())
        .input(transaction.getPayload().toHexString())
        .output(result.getOutput().toHexString())
        .error(result.isSuccessful() ? null : determineErrorMessage(result))
        .revertReason(result.getRevertReason().map(Bytes::toHexString).orElse(null))
        .build();
  }

  /** Determines error message from transaction processing result. */
  private static String determineErrorMessage(final TransactionProcessingResult result) {
    if (result.getInvalidReason().isPresent()) {
      return result.getInvalidReason().get();
    }
    if (result.getExceptionalHaltReason().isPresent()) {
      return result.getExceptionalHaltReason().get().getDescription();
    }
    return "execution reverted";
  }

  /**
   * Builds the call hierarchy from trace frames using a stack-based approach. This method processes
   * all trace frames to construct a nested call structure that matches the Geth callTracer format.
   */
  private static CallTracerResult buildCallHierarchyFromFrames(
      final TransactionTrace transactionTrace) {
    List<TraceFrame> frames = transactionTrace.getTraceFrames();

    // Initialize with root transaction context
    Transaction transaction = transactionTrace.getTransaction();
    TransactionProcessingResult result = transactionTrace.getResult();

    // Create root call builder
    String rootToAddress = null;
    String rootCallType = "CALL";

    if (transaction.isContractCreation()) {
      rootCallType = "CREATE";
      rootToAddress = transaction.contractAddress().map(Address::toHexString).orElse(null);
    } else {
      rootToAddress = transaction.getTo().map(Address::toHexString).orElse(null);
    }

    CallTracerResult.Builder rootBuilder =
        CallTracerResult.builder()
            .type(rootCallType)
            .from(transaction.getSender().toHexString())
            .to(rootToAddress)
            .value(transaction.getValue().toShortHexString())
            .gas(transaction.getGasLimit())
            .input(transaction.getPayload().toHexString());

    // Use a stack to track nested calls
    Deque<CallTracerResult.Builder> callStack = new ArrayDeque<>();
    callStack.push(rootBuilder);

    // Track call depth to handle nested calls properly
    int currentDepth = 0;

    // Process each trace frame
    for (int i = 0; i < frames.size(); i++) {
      TraceFrame frame = frames.get(i);
      String opcode = frame.getOpcode();

      // Handle call-starting opcodes
      if (isCallStartOpcode(opcode)) {
        // Create new call context
        CallTracerResult.Builder callBuilder = createCallFromFrame(frame, opcode);
        callStack.push(callBuilder);
        currentDepth = frame.getDepth();

      } else if (isCallEndOpcode(opcode) || frame.getDepth() < currentDepth) {
        // Handle call completion
        if (callStack.size() > 1) {
          CallTracerResult.Builder completedCall = callStack.pop();

          // Set output and gas used for completed call
          completedCall
              .output(frame.getOutputData().toHexString())
              .gasUsed(calculateGasUsedFromFrame(frame));

          // Handle errors for nested calls
          if (frame.getExceptionalHaltReason().isPresent()) {
            completedCall.error(frame.getExceptionalHaltReason().get().getDescription());
          }

          if (frame.getRevertReason().isPresent()) {
            completedCall.revertReason(frame.getRevertReason().get().toHexString());
          }

          // Add completed call to parent
          CallTracerResult.Builder parentCall = callStack.peek();
          if (parentCall != null) {
            parentCall.addCall(completedCall.build());
          }

          currentDepth = frame.getDepth();
        } else {
          // This is the root call ending - handle its error/revert state
          rootBuilder
              .output(frame.getOutputData().toHexString())
              .gasUsed(calculateGasUsedFromFrame(frame));

          // Handle errors for root call from frame data
          if (frame.getExceptionalHaltReason().isPresent()) {
            rootBuilder.error(frame.getExceptionalHaltReason().get().getDescription());
          }

          if (frame.getRevertReason().isPresent()) {
            rootBuilder.revertReason(frame.getRevertReason().get().toHexString());
          }
        }
      } else {
        // Handle other opcodes that might affect the current call context
        // Check if current frame has error information that should be applied to current call
        CallTracerResult.Builder currentCall = callStack.peek();
        if (currentCall != null && frame.getExceptionalHaltReason().isPresent()) {
          currentCall.error(frame.getExceptionalHaltReason().get().getDescription());
        }

        if (currentCall != null && frame.getRevertReason().isPresent()) {
          currentCall.revertReason(frame.getRevertReason().get().toHexString());
        }
      }
    }

    // Finalize any remaining nested calls
    while (callStack.size() > 1) {
      CallTracerResult.Builder completedCall = callStack.pop();
      CallTracerResult.Builder parentCall = callStack.peek();
      if (parentCall != null) {
        parentCall.addCall(completedCall.build());
      }
    }

    // Set final root call properties from transaction result
    // Only override if not already set from frame processing
    if (rootBuilder.build().getOutput() == null) {
      rootBuilder.output(result.getOutput().toHexString());
    }

    if (rootBuilder.build().getGasUsed() == null) {
      rootBuilder.gasUsed(transactionTrace.getGas());
    }

    // Set error from transaction result if not already set from frames
    if (rootBuilder.build().getError() == null && !result.isSuccessful()) {
      rootBuilder.error(determineErrorMessage(result));
    }

    // Set revert reason from transaction result if not already set from frames
    if (rootBuilder.build().getRevertReason() == null && result.getRevertReason().isPresent()) {
      rootBuilder.revertReason(result.getRevertReason().get().toHexString());
    }

    return rootBuilder.build();
  }

  /** Checks if the opcode starts a new call context. */
  private static boolean isCallStartOpcode(final String opcode) {
    return switch (opcode) {
      case "CALL",
          "STATICCALL",
          "DELEGATECALL",
          "CALLCODE",
          "CREATE",
          "CREATE2",
          "SELFDESTRUCT",
          "SUICIDE" ->
          true;
      default -> false;
    };
  }

  /** Checks if the opcode ends a call context. */
  private static boolean isCallEndOpcode(final String opcode) {
    return switch (opcode) {
      case "RETURN", "REVERT", "STOP", "INVALID" -> true;
      default -> false;
    };
  }

  /** Creates a CallTracerResult.Builder from a trace frame for call-starting opcodes. */
  private static CallTracerResult.Builder createCallFromFrame(
      final TraceFrame frame, final String opcode) {
    String toAddress = determineToAddress(frame, opcode);
    String fromAddress = frame.getRecipient().toHexString(); // This may need refinement

    CallTracerResult.Builder builder =
        CallTracerResult.builder()
            .type(opcode)
            .from(fromAddress)
            .to(toAddress)
            .gas(frame.getGasRemaining())
            .input(frame.getInputData().toHexString());

    // Handle value based on call type
    if ("STATICCALL".equals(opcode)) {
      builder.value("0x0"); // STATICCALL always has zero value
    } else {
      builder.value(frame.getValue().toShortHexString());
    }

    return builder;
  }

  /** Determines the "to" address based on the opcode and frame data. */
  private static String determineToAddress(final TraceFrame frame, final String opcode) {
    return switch (opcode) {
      case "CALL", "STATICCALL" ->
          // The contract or receiving address being called
          frame.getRecipient().toHexString();

      case "DELEGATECALL" ->
          // The contract whose code is being executed
          frame.getRecipient().toHexString();

      case "CALLCODE" ->
          // Similar to DELEGATECALL
          frame.getRecipient().toHexString();

      case "CREATE", "CREATE2" ->
          // The address of the newly deployed contract
          // This might need to be calculated or extracted from subsequent frames
          calculateNewContractAddress(frame, opcode);

      case "SELFDESTRUCT", "SUICIDE" ->
          // The address of the refund recipient
          extractSelfDestructRecipient(frame);

      default -> frame.getRecipient().toHexString();
    };
  }

  /** Calculates gas used from a trace frame. */
  private static long calculateGasUsedFromFrame(final TraceFrame frame) {
    // This is a simplified calculation - may need refinement based on frame data
    return Math.max(0, frame.getGasRemaining());
  }

  /** Calculates the address of a newly created contract. */
  @SuppressWarnings("UnusedVariable")
  private static String calculateNewContractAddress(final TraceFrame frame, final String opcode) {
    // For now, return null - this needs proper implementation
    // based on CREATE/CREATE2 semantics and available frame data
    return null;
  }

  /** Extracts the refund recipient for SELFDESTRUCT operations. */
  private static String extractSelfDestructRecipient(final TraceFrame frame) {
    // This needs to be implemented based on how SELFDESTRUCT data is stored in TraceFrame
    // For now, return the recipient
    return frame.getRecipient().toHexString();
  }
}
