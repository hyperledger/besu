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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  private static final Logger LOG = LoggerFactory.getLogger("CallTracerConverter");

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

    var builder =
        CallTracerResult.builder()
            .type(callType)
            .from(transaction.getSender().toHexString())
            .to(toAddress)
            .value(transaction.getValue().toShortHexString())
            .gas(transaction.getGasLimit())
            .gasUsed(calculateCorrectGasUsed(transactionTrace, result))
            .input(transaction.getPayload().toHexString())
            .error(result.isSuccessful() ? null : determineErrorMessage(result))
            .revertReason(result.getRevertReason().map(Bytes::toHexString).orElse(null));
    // Handle output field consistently
    if (shouldIncludeOutput(result, callType)) {
      builder.output(result.getOutput().toHexString());
    }
    return builder.build();
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

    LOG.warn(
        "*** Call Tracer: Type: {}, Number of Frames: {}, Root To Address: {}",
        rootCallType,
        frames.size(),
        rootToAddress);

    CallTracerResult.Builder rootBuilder =
        CallTracerResult.builder()
            .type(rootCallType)
            .from(transaction.getSender().toHexString())
            .to(rootToAddress)
            .value(transaction.getValue().toShortHexString())
            .gas(transaction.getGasLimit())
            .gasUsed(calculateCorrectGasUsed(transactionTrace, result)) // Set gas early
            .input(transaction.getPayload().toHexString())
            .error(result.isSuccessful() ? null : determineErrorMessage(result)) // Set error early
            .revertReason(
                result
                    .getRevertReason()
                    .map(Bytes::toHexString)
                    .orElse(null)); // Set revert reason early

    // Handle output field - only set if there's actual output data
    if (shouldIncludeOutput(result, rootCallType)) {
      rootBuilder.output(result.getOutput().toHexString());
    }

    // For simple transactions (like ETH transfers with just STOP frame),
    // we can return early since all data is already set
    if (frames.size() == 1 && "STOP".equals(frames.get(0).getOpcode())) {
      LOG.warn("*** Call Tracer: Simple transaction with STOP frame, returning early");
      return rootBuilder.build();
    }

    // Use a stack to track nested calls for complex transactions
    Deque<CallTracerResult.Builder> callStack = new ArrayDeque<>();
    callStack.push(rootBuilder);

    // Process each trace frame
    for (int i = 0; i < frames.size(); i++) {
      TraceFrame frame = frames.get(i);
      String opcode = frame.getOpcode();

      LOG.warn(
          "*** Call Tracer: Processing frame {}: opcode={}, depth={}, gas={}",
          i,
          opcode,
          frame.getDepth(),
          frame.getGasRemaining());

      // Handle call-starting opcodes
      if (isCallStartOpcode(opcode)) {
        // Determine addresses
        String toAddress = determineToAddress(frame, opcode);
        String fromAddress = null;

        // Get "from" address from parent call's "to" address
        if (!callStack.isEmpty()) {
          CallTracerResult.Builder parentCall = callStack.peek();
          fromAddress = parentCall.build().getTo();
        }

        // Create new call context
        CallTracerResult.Builder callBuilder =
            CallTracerResult.builder()
                .type(opcode)
                .from(fromAddress)
                .to(toAddress)
                .gas(frame.getGasRemaining())
                .input(frame.getInputData().toHexString());

        // Handle value based on call type
        if ("STATICCALL".equals(opcode)) {
          callBuilder.value("0x0");
        } else {
          callBuilder.value(frame.getValue().toShortHexString());
        }

        LOG.warn("CALL START: {}", callBuilder);

        callStack.push(callBuilder);

      } else if (isCallEndOpcode(opcode)) {
        // Handle call completion
        if (callStack.size() > 1) {
          // This is a nested call completion
          CallTracerResult.Builder completedCall = callStack.pop();

          // Set output and gas used for completed call
          completedCall.gasUsed(calculateGasUsedFromFrame(frame));

          // Only set output if there's actual data
          if (!frame.getOutputData().isEmpty()) {
            completedCall.output(frame.getOutputData().toHexString());
          }

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

        } else {
          // This is the root call ending - only update specific fields from frame data
          LOG.warn("*** Call Tracer: Root call ending with opcode: {}", opcode);

          // Update output only if frame has actual data and we should include it
          if (!frame.getOutputData().isEmpty() && shouldIncludeOutput(result, rootCallType)) {
            rootBuilder.output(frame.getOutputData().toHexString());
          }

          // Don't override gas calculation for root call - we already set it correctly
          // from transaction level data

          // Handle errors for root call from frame data (override transaction-level if more
          // specific)
          if (frame.getExceptionalHaltReason().isPresent()) {
            rootBuilder.error(frame.getExceptionalHaltReason().get().getDescription());
          }

          if (frame.getRevertReason().isPresent()) {
            rootBuilder.revertReason(frame.getRevertReason().get().toHexString());
          }
        }
      } else {
        // Handle other opcodes that might affect the current call context
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

    return rootBuilder.build();
  }

  /**
   * Determines whether to include the output field based on Geth's behavior. Geth omits output for
   * simple EOA-to-EOA transactions.
   */
  private static boolean shouldIncludeOutput(
      final TransactionProcessingResult result, final String callType) {
    // For CREATE transactions, always include output (contract bytecode or empty on failure)
    if ("CREATE".equals(callType)) {
      return true;
    }

    // For CALL transactions, only include output if there's actual return data
    // Simple EOA-to-EOA transfers have empty output and should omit the field
    return !result.getOutput().isEmpty();
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
    // TODO: For now, return null - this needs proper implementation
    // based on CREATE/CREATE2 semantics and available frame data
    return null;
  }

  /** Extracts the refund recipient for SELFDESTRUCT operations. */
  private static String extractSelfDestructRecipient(final TraceFrame frame) {
    // TODO: This needs to be implemented based on how SELFDESTRUCT data is stored in TraceFrame
    // For now, return the recipient
    return frame.getRecipient().toHexString();
  }

  /** Calculates the correct gas used value to match Geth's behavior. */
  private static long calculateCorrectGasUsed(
      final TransactionTrace transactionTrace, final TransactionProcessingResult result) {
    if (result.isSuccessful()) {
      // For successful transactions, use transactionTrace.getGas()
      // which is calculated as: gasLimit - gasRemaining
      LOG.info("*** Using gas from transaction trace: {}", transactionTrace.getGas());
      return transactionTrace.getGas();
    } else {
      // For failed transactions, we need to investigate what Geth reports
      // It might be different from transactionTrace.getGas()
      return result.getEstimateGasUsedByTransaction();
    }
  }
}
