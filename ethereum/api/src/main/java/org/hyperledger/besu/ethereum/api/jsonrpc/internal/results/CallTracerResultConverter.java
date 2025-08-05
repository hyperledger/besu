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

import static com.google.common.base.Preconditions.checkNotNull;
import static org.hyperledger.besu.evm.internal.Words.toAddress;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.debug.TraceFrame;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tuweni.bytes.Bytes;

public class CallTracerResultConverter {
  public static CallTracerResult convert(final TransactionTrace transactionTrace) {
    checkNotNull(
        transactionTrace, "CallTracerResultConverter requires a non-null TransactionTrace");
    checkNotNull(
        transactionTrace.getTransaction(),
        "CallTracerResultConverter requires non-null Transaction");
    checkNotNull(
        transactionTrace.getResult(), "CallTracerResultConverter requires non-null Result");

    if (transactionTrace.getTraceFrames() == null || transactionTrace.getTraceFrames().isEmpty()) {
      return createRootCallFromTransaction(transactionTrace);
    }
    return buildCallHierarchyFromFrames(transactionTrace);
  }

  private static CallTracerResult buildCallHierarchyFromFrames(final TransactionTrace trace) {
    final List<TraceFrame> frames = trace.getTraceFrames();
    final Transaction tx = trace.getTransaction();

    // Track calls by depth
    final Map<Integer, CallInfo> depthToCallInfo = new HashMap<>();

    // Initialize the root call
    final CallTracerResult.Builder rootBuilder = initializeRootBuilder(tx);

    // Initialize depth-based tracking
    int maxDepth = 0;
    int currentDepth = 0;
    final CallInfo rootInfo = new CallInfo(rootBuilder, null);
    depthToCallInfo.put(0, rootInfo);
    // Process all frames
    for (int i = 0; i < frames.size(); i++) {
      final TraceFrame nextTrace = i < frames.size() - 1 ? frames.get(i + 1) : null;
      final TraceFrame frame = frames.get(i);
      final String opcode = frame.getOpcode();
      final int frameDepth = frame.getDepth();

      // Update max depth encountered
      maxDepth = Math.max(maxDepth, frameDepth);

      // Process call operations that create a new context
      if (isCallOp(opcode) || isCreateOp(opcode)) {
        currentDepth = frameDepth;

        // Get parent call info
        final CallInfo parentCallInfo = depthToCallInfo.get(currentDepth);

        // Create new call for the next depth level
        final CallTracerResult.Builder childBuilder =
            createCallBuilder(frame, nextTrace, opcode, parentCallInfo);
        final CallInfo childCallInfo = new CallInfo(childBuilder, frame);

        // Store call info for this depth
        depthToCallInfo.put(currentDepth + 1, childCallInfo);
      }
      // Process return operations that exit a context
      else if (isReturnOp(opcode) || isRevertOp(opcode) || isHaltOp(opcode)) {
        currentDepth = frameDepth;

        // Get child call info
        final CallInfo childCallInfo = depthToCallInfo.get(currentDepth);
        if (childCallInfo == null) {
          continue;
        }

        // Get entry frame and calculate gas used
        final TraceFrame entryFrame = childCallInfo.entryFrame;
        if (entryFrame != null) {
          // Set output data and error status
          setOutputAndErrorStatus(childCallInfo.builder, frame, opcode);

          // Calculate gas used
          final long gasUsed = calculateGasUsed(childCallInfo, entryFrame, frame);
          childCallInfo.builder.gasUsed(gasUsed);

          // Add code deposit cost for CREATE operations
          if (isCreateOp(childCallInfo.builder.getType()) && frame.getOutputData() != null) {
            // 200 gas per byte of deployed code
            long codeDepositCost = frame.getOutputData().size() * 200L;
            childCallInfo.incGasUsed(codeDepositCost);
          }

          // Find parent and add this call to parent's calls
          final CallInfo parentCallInfo = depthToCallInfo.get(currentDepth - 1);
          if (parentCallInfo != null) {
            final CallTracerResult childResult = childCallInfo.builder.build();
            parentCallInfo.builder.addCall(childResult);
          }

          // Remove this call from tracking as it's now complete
          depthToCallInfo.remove(currentDepth);
        }
      }
    }

    // Process any remaining calls that didn't have explicit return frames
    processRemainingCalls(depthToCallInfo, maxDepth);

    // Add transaction result information to root
    finalizeRoot(rootInfo.builder, trace);

    return rootInfo.builder.build();
  }

  private static void setOutputAndErrorStatus(
      final CallTracerResult.Builder builder, final TraceFrame frame, final String opcode) {

    // Set output data if present
    if (frame.getOutputData() != null && !frame.getOutputData().isEmpty()) {
      builder.output(frame.getOutputData().toHexString());
    }

    // Set error information
    if (frame.getExceptionalHaltReason().isPresent() || "REVERT".equals(opcode)) {
      builder.error("execution reverted");
      frame.getRevertReason().ifPresent(reason -> builder.revertReason(reason.toHexString()));
    }
  }

  private static void processRemainingCalls(
      final Map<Integer, CallInfo> depthToCallInfo, final int maxDepth) {
    // Process any calls that didn't have explicit return frames, starting from the deepest
    for (int depth = maxDepth; depth > 0; depth--) {
      final CallInfo callInfo = depthToCallInfo.get(depth);
      if (callInfo != null) {
        final CallInfo parentCallInfo = depthToCallInfo.get(depth - 1);
        if (parentCallInfo != null) {
          final CallTracerResult childResult = callInfo.builder.build();
          parentCallInfo.builder.addCall(childResult);
        }
        depthToCallInfo.remove(depth);
      }
    }
  }

  private static void finalizeRoot(
      final CallTracerResult.Builder rootBuilder, final TransactionTrace trace) {
    final TransactionProcessingResult result = trace.getResult();
    final Transaction tx = trace.getTransaction();

    // Set total gas used
    final long totalGasUsed = tx.getGasLimit() - result.getGasRemaining();
    rootBuilder.gasUsed(totalGasUsed);

    // Set error if transaction failed
    if (!result.isSuccessful()) {
      rootBuilder.error("execution reverted");
      result.getRevertReason().ifPresent(reason -> rootBuilder.revertReason(reason.toHexString()));
    }

    // Set output if present
    if (result.getOutput() != null && !result.getOutput().isEmpty()) {
      rootBuilder.output(result.getOutput().toHexString());
    }
  }

  private static CallTracerResult.Builder createCallBuilder(
      final TraceFrame frame,
      final TraceFrame nextTrace,
      final String opcode,
      final CallInfo parentCallInfo) {

    String fromAddress = null;
    if (parentCallInfo != null && parentCallInfo.builder != null) {
      fromAddress = parentCallInfo.builder.build().getTo();
    }

    final String toAddress = resolveToAddress(frame, opcode);
    final Bytes inputData = resolveInputData(frame, opcode);

    final long gasProvided = nextTrace != null ? nextTrace.getGasRemaining() : 0;

    return CallTracerResult.builder()
        .type(opcode)
        .from(fromAddress)
        .to(toAddress)
        .value(getCallValue(frame, opcode))
        .gas(gasProvided) // Use the calculated gas value
        .input(inputData.toHexString());
  }

  private static CallTracerResult.Builder initializeRootBuilder(final Transaction tx) {
    return CallTracerResult.builder()
        .type(tx.isContractCreation() ? "CREATE" : "CALL")
        .from(tx.getSender().toHexString())
        .to(
            tx.isContractCreation()
                ? tx.contractAddress().map(Address::toHexString).orElse(null)
                : tx.getTo().map(Address::toHexString).orElse(null))
        .value(tx.getValue().toShortHexString())
        .gas(tx.getGasLimit())
        .input(tx.getPayload().toHexString());
  }

  private static String getCallValue(final TraceFrame frame, final String opcode) {
    // STATICCALL and DELEGATECALL don't transfer value
    if ("STATICCALL".equals(opcode) || "DELEGATECALL".equals(opcode)) {
      return "0x0";
    }
    return frame.getValue().toShortHexString();
  }

  private static long calculateGasUsed(
      final CallInfo callInfo, final TraceFrame entryFrame, final TraceFrame exitFrame) {
    // For root transaction
    if (exitFrame.getDepth() == 0) {
      // Root transaction: simply calculate difference and account for refunds
      long gasUsed = entryFrame.getGasRemaining() - exitFrame.getGasRemaining();
      long gasRefund = exitFrame.getGasRefund();
      return Math.max(0, gasUsed - gasRefund);
    }

    // For nested calls
    if (exitFrame.getGasRemaining() >= 0) {
      // Normal case: gas before call - gas after call = gas used
      return callInfo.builder.getGas().longValue() - exitFrame.getGasRemaining();
    }
    // For precompiled contracts
    else if (entryFrame.getPrecompiledGasCost().isPresent()) {
      return entryFrame.getPrecompiledGasCost().getAsLong();
    }
    // Fallback to operation gas cost
    else if (entryFrame.getGasCost().isPresent()) {
      return entryFrame.getGasCost().getAsLong();
    }

    return 0;
  }

  private static String resolveToAddress(final TraceFrame frame, final String opcode) {
    if ("CREATE".equals(opcode) || "CREATE2".equals(opcode)) {
      // For contract creation, we'd need to compute the new contract address
      // This is typically available in a later frame when the creation completes
      return null;
    } else if ("CALL".equals(opcode) || "STATICCALL".equals(opcode) || "CALLCODE".equals(opcode)) {
      return frame
          .getStack()
          .filter(s -> s.length > 1)
          .map(s -> toAddress(s[s.length - 2]).toHexString())
          .orElse(null);
    } else if ("DELEGATECALL".equals(opcode)) {
      return frame.getRecipient().toHexString();
    }
    return null;
  }

  private static Bytes resolveInputData(final TraceFrame frame, final String opcode) {
    if (isCallOp(opcode)) {

      // Check if stack is present
      if (frame.getStack().isEmpty()) {
        return frame.getInputData();
      }

      // Try to extract call data from stack and memory
      return frame
          .getStack()
          .filter(stack -> stack.length >= 5)
          .map(
              stack -> {
                // For CALL operations, extract offset and length from stack
                final int offset = bytesToInt(stack[stack.length - 4]);
                final int length = bytesToInt(stack[stack.length - 5]);

                // Check if memory is present
                if (frame.getMemory().isEmpty()) {
                  return frame.getInputData();
                }

                return frame
                    .getMemory()
                    .map(memory -> extractCallDataFromMemory(memory, offset, length))
                    .orElseGet(frame::getInputData);
              })
          .orElseGet(frame::getInputData);
    } else if (isCreateOp(opcode)) {
      // For create operations, extract initialization code from memory
      return frame
          .getStack()
          .filter(stack -> stack.length >= 3)
          .map(
              stack -> {
                final int offset = bytesToInt(stack[stack.length - 2]);
                final int length = bytesToInt(stack[stack.length - 3]);
                return frame
                    .getMemory()
                    .map(memory -> extractCallDataFromMemory(memory, offset, length))
                    .orElse(frame.getInputData());
              })
          .orElse(frame.getInputData());
    }

    return frame.getInputData();
  }

  private static Bytes extractCallDataFromMemory(
      final Bytes[] memory, final int offset, final int length) {
    // Ensure parameters are valid
    if (offset < 0 || length < 0 || memory == null || memory.length == 0) {
      return Bytes.EMPTY;
    }

    // Calculate memory word indices
    final int startWord = offset / 32;
    final int endWord = (offset + length + 31) / 32; // Ceiling division

    // Check if within bounds
    if (startWord >= memory.length) {
      return Bytes.EMPTY;
    }

    final int boundedEndWord = Math.min(endWord, memory.length);

    // Extract and concatenate memory words
    Bytes result = Bytes.EMPTY;
    for (int i = startWord; i < boundedEndWord; i++) {
      result = Bytes.concatenate(result, memory[i]);
    }

    // Trim to exact offset and length
    final int startByteInWord = offset % 32;
    return result.slice(startByteInWord, Math.min(length, result.size() - startByteInWord));
  }

  private static boolean isCallOp(final String opcode) {
    return "CALL".equals(opcode)
        || "CALLCODE".equals(opcode)
        || "DELEGATECALL".equals(opcode)
        || "STATICCALL".equals(opcode);
  }

  private static boolean isCreateOp(final String opcode) {
    return "CREATE".equals(opcode) || "CREATE2".equals(opcode);
  }

  private static boolean isReturnOp(final String opcode) {
    return "RETURN".equals(opcode);
  }

  private static boolean isRevertOp(final String opcode) {
    return "REVERT".equals(opcode);
  }

  private static boolean isHaltOp(final String opcode) {
    return "STOP".equals(opcode) || "SELFDESTRUCT".equals(opcode);
  }

  private static CallTracerResult createRootCallFromTransaction(final TransactionTrace trace) {
    final Transaction tx = trace.getTransaction();
    final TransactionProcessingResult result = trace.getResult();
    final CallTracerResult.Builder rootBuilder = initializeRootBuilder(tx);

    // Set gas used
    rootBuilder.gasUsed(tx.getGasLimit() - result.getGasRemaining());

    // Set output if present
    if (result.getOutput() != null && !result.getOutput().isEmpty()) {
      rootBuilder.output(result.getOutput().toHexString());
    }

    // Set error if transaction failed
    if (!result.isSuccessful()) {
      rootBuilder.error("execution reverted");
      result.getRevertReason().ifPresent(reason -> rootBuilder.revertReason(reason.toHexString()));
    }

    return rootBuilder.build();
  }

  /** Helper class to track call information during trace processing. */
  private record CallInfo(CallTracerResult.Builder builder, TraceFrame entryFrame) {

    void incGasUsed(final long gas) {
      long currentGasUsed = builder.getGasUsed().longValueExact();
      builder.gasUsed(currentGasUsed + gas);
    }
  }

  /** Converts Bytes to integer safely using the built-in toBigInteger method */
  private static int bytesToInt(final Bytes bytes) {
    try {
      // Use the built-in toBigInteger method and convert to int
      return bytes.toBigInteger().intValue();
    } catch (final Exception e) {
      return 0;
    }
  }
}
