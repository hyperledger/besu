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
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.CallTracerHelper.bytesToInt;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.CallTracerHelper.extractCallDataFromMemory;
import static org.hyperledger.besu.evm.internal.Words.toAddress;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.tracing.TraceFrame;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

import org.apache.tuweni.bytes.Bytes;

/**
 * Converts Ethereum transaction traces into a hierarchical call tracer format.
 *
 * <p>This class transforms the flat sequence of trace frames produced during transaction execution
 * into a structured call tree similar to Geth's callTracer output. It captures the relationships
 * between calls, their inputs, outputs, gas usage, and error states.
 *
 * <p>The converter handles various EVM operations including:
 *
 * <ul>
 *   <li>Contract calls (CALL, STATICCALL, DELEGATECALL, CALLCODE)
 *   <li>Contract creation (CREATE, CREATE2)
 *   <li>Precompiled contracts (addresses 0x01-0x0a)
 *   <li>Return operations (RETURN, REVERT, STOP, SELFDESTRUCT)
 * </ul>
 */
public class CallTracerResultConverter {
  // Gas calculation constants
  private static final long WARM_ACCESS_GAS = 100L;
  private static final long CODE_DEPOSIT_GAS_PER_BYTE = 200L;
  private static final long DEFAULT_SELFDESTRUCT_COST = 5000L;

  // Stack position constants for different operations
  private static final int CALL_STACK_VALUE_OFFSET = 3;
  private static final int CALL_STACK_TO_OFFSET = 2;
  private static final int CALL_STACK_IN_OFFSET = 4;
  private static final int CALL_STACK_IN_SIZE_OFFSET = 3;

  // CREATE operation stack positions
  private static final int CREATE_STACK_OFFSET_POS = 2;
  private static final int CREATE_STACK_SIZE_POS = 1;
  private static final int CREATE2_STACK_OFFSET_POS = 3;
  private static final int CREATE2_STACK_SIZE_POS = 2;

  // Operation types
  private static final String CALL_TYPE = "CALL";
  private static final String CALLCODE_TYPE = "CALLCODE";
  private static final String DELEGATECALL_TYPE = "DELEGATECALL";
  private static final String STATICCALL_TYPE = "STATICCALL";
  private static final String CREATE_TYPE = "CREATE";
  private static final String CREATE2_TYPE = "CREATE2";
  private static final String SELFDESTRUCT_TYPE = "SELFDESTRUCT";
  private static final String RETURN_TYPE = "RETURN";
  private static final String REVERT_TYPE = "REVERT";
  private static final String STOP_TYPE = "STOP";

  // Error messages
  private static final String EXECUTION_REVERTED = "execution reverted";
  private static final String PRECOMPILE_FAILED = "precompile failed";

  private static final String ZERO_VALUE = "0x0";

  /**
   * Converts a transaction trace to a call tracer result.
   *
   * @param transactionTrace The transaction trace to convert
   * @return A call tracer result representing the transaction's call hierarchy
   * @throws NullPointerException if transactionTrace or its components are null
   */
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
    final CallTracerResult.Builder rootBuilder = initializeRootBuilder(trace);

    if (!trace.getResult().isSuccessful()) {
      return rootBuilder.build();
    }

    final List<TraceFrame> frames = trace.getTraceFrames();
    final Map<Integer, CallInfo> depthToCallInfo = new HashMap<>();

    depthToCallInfo.put(0, new CallInfo(rootBuilder, null));

    TraceFrame previousFrame = null;

    for (int i = 0; i < frames.size(); i++) {
      final TraceFrame frame = frames.get(i);
      final TraceFrame nextTrace = (i < frames.size() - 1) ? frames.get(i + 1) : null;

      // Handle implicit returns when depth decreases
      if (shouldHandleImplicitReturn(previousFrame, frame)) {
        handleImplicitReturn(previousFrame, depthToCallInfo);
      }

      final String opcode = frame.getOpcode();

      if (isCallOp(opcode) || isCreateOp(opcode)) {
        processCallOrCreate(frame, nextTrace, opcode, depthToCallInfo);
      } else if (isSelfDestructOp(opcode)) {
        handleSelfDestruct(frame, depthToCallInfo.get(frame.getDepth()));
      } else if (isReturnOp(opcode) || isRevertOp(opcode) || isHaltOp(opcode)) {
        processReturn(frame, opcode, depthToCallInfo);
      }

      previousFrame = frame;
    }

    processRemainingCalls(depthToCallInfo);
    return depthToCallInfo.get(0).builder.build();
  }

  private static boolean shouldHandleImplicitReturn(
      final TraceFrame previousFrame, final TraceFrame currentFrame) {
    return previousFrame != null
        && currentFrame.getDepth() < previousFrame.getDepth()
        && !isReturnOp(previousFrame.getOpcode())
        && !isRevertOp(previousFrame.getOpcode())
        && !isHaltOp(previousFrame.getOpcode());
  }

  private static void processCallOrCreate(
      final TraceFrame frame,
      final TraceFrame nextTrace,
      final String opcode,
      final Map<Integer, CallInfo> depthToCallInfo) {

    final CallInfo parentCallInfo = depthToCallInfo.get(frame.getDepth());
    final CallTracerResult.Builder childBuilder =
        createCallBuilder(frame, nextTrace, opcode, parentCallInfo);

    // Handle precompiles
    if (frame.isPrecompile()) {
      finalizePrecompileChild(frame, childBuilder, parentCallInfo);
      return;
    }

    // Check if call entered (depth increased)
    final boolean calleeEntered = nextTrace != null && nextTrace.getDepth() > frame.getDepth();

    if (!calleeEntered) {
      handleNonEnteredCall(frame, opcode, childBuilder, parentCallInfo);
      return;
    }

    // Track child for later processing
    depthToCallInfo.put(frame.getDepth() + 1, new CallInfo(childBuilder, frame));
  }

  private static void processReturn(
      final TraceFrame frame, final String opcode, final Map<Integer, CallInfo> depthToCallInfo) {

    final CallInfo childCallInfo = depthToCallInfo.get(frame.getDepth());
    if (childCallInfo == null || childCallInfo.entryFrame == null) {
      return;
    }

    // Handle CREATE contract address
    if (isCreateOp(childCallInfo.builder.getType())
        && frame.getDepth() > 0
        && childCallInfo.builder.getTo() == null) {
      childCallInfo.builder.to(frame.getRecipient().toHexString());
    }

    // Set output and error status
    setOutputAndErrorStatus(childCallInfo.builder, frame, opcode);

    // Calculate and set gas used
    final long gasUsed = calculateGasUsed(childCallInfo, childCallInfo.entryFrame, frame);
    childCallInfo.builder.gasUsed(gasUsed);

    // Add to parent's calls
    final CallInfo parentCallInfo = depthToCallInfo.get(frame.getDepth() - 1);
    if (parentCallInfo != null) {
      parentCallInfo.builder.addCall(childCallInfo.builder.build());
    }

    depthToCallInfo.remove(frame.getDepth());
  }

  private static void handleNonEnteredCall(
      final TraceFrame frame,
      final String opcode,
      final CallTracerResult.Builder childBuilder,
      final CallInfo parentCallInfo) {

    // Check for exceptional halt (hard failures)
    if (frame.getExceptionalHaltReason().isPresent()) {
      handleHardFailure(frame, opcode, childBuilder);
    } else {
      // Soft failure (e.g., insufficient balance, call depth exceeded)
      handleSoftFailure(frame, opcode, childBuilder);
    }

    // Add to parent immediately
    if (parentCallInfo != null) {
      parentCallInfo.builder.addCall(childBuilder.build());
    }
  }

  private static void handleHardFailure(
      final TraceFrame frame, final String opcode, final CallTracerResult.Builder childBuilder) {

    String errorMessage =
        frame
            .getExceptionalHaltReason()
            .map(ExceptionalHaltReason::getDescription)
            .orElse(EXECUTION_REVERTED);
    childBuilder.error(errorMessage);

    // For failed CREATE, ensure 'to' is null
    if (isCreateOp(opcode)) {
      childBuilder.to(null);
    }

    // Set gas used based on failure type
    long gasUsed = 0L;
    if (frame
        .getExceptionalHaltReason()
        .map(r -> Objects.equals(r, ExceptionalHaltReason.INSUFFICIENT_GAS))
        .orElse(false)) {
      gasUsed = frame.getGasCost().orElse(0L);
    }
    childBuilder.gasUsed(gasUsed);
  }

  private static void handleSoftFailure(
      final TraceFrame frame, final String opcode, final CallTracerResult.Builder childBuilder) {

    // Soft failures typically don't consume gas for the child call itself
    // They only consume the base operation cost at the parent level
    // The child shows gasUsed: 0 since it never executed
    childBuilder.gasUsed(0L);

    // For failed CREATE, ensure 'to' is null
    if (isCreateOp(opcode)) {
      childBuilder.to(null);
    }

    // TODO: Set error messages for soft failures to match Geth behavior.
    // Once Besu provides soft failure context in TraceFrame, implement:
    //
    // Error messages that Geth sets for soft failures:
    // - "insufficient balance for transfer" - when caller lacks funds for value transfer
    // - "max call depth exceeded" - when call depth > 1024
    // - "invalid input length" - for certain precompile failures
    //
    // Example implementation when available:
    // if (frame.getSoftFailureReason().isPresent()) {
    //   switch (frame.getSoftFailureReason().get()) {
    //     case INSUFFICIENT_BALANCE:
    //       childBuilder.error("insufficient balance for transfer");
    //       break;
    //     case CALL_DEPTH_EXCEEDED:
    //       childBuilder.error("max call depth exceeded");
    //       break;
    //     default:
    //       // No error for other soft failures
    //   }
    // }
    //
    // For now, we don't set errors for soft failures as Besu doesn't provide
    // the necessary context to distinguish between different soft failure types.
  }

  private static void setOutputAndErrorStatus(
      final CallTracerResult.Builder builder, final TraceFrame frame, final String opcode) {

    // Set output data
    if (frame.getOutputData() != null && !frame.getOutputData().isEmpty()) {
      builder.output(frame.getOutputData().toHexString());
    }

    // Handle errors
    if (frame.getExceptionalHaltReason().isPresent()) {
      handleExceptionalHalt(builder, frame);
    } else if (REVERT_TYPE.equals(opcode)) {
      builder.error(EXECUTION_REVERTED);
      frame.getRevertReason().ifPresent(builder::revertReason);
    }
  }

  private static void handleExceptionalHalt(
      final CallTracerResult.Builder builder, final TraceFrame frame) {

    String errorMessage =
        frame
            .getExceptionalHaltReason()
            .map(ExceptionalHaltReason::getDescription)
            .orElse(EXECUTION_REVERTED);
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

  private static void processRemainingCalls(final Map<Integer, CallInfo> depthToCallInfo) {
    new TreeSet<>(depthToCallInfo.keySet())
        .descendingSet().stream()
            .filter(depth -> depth > 0)
            .forEach(
                depth -> {
                  CallInfo child = depthToCallInfo.get(depth);
                  CallInfo parent = depthToCallInfo.get(depth - 1);
                  if (child != null && parent != null) {
                    parent.builder.addCall(child.builder.build());
                  }
                  depthToCallInfo.remove(depth);
                });
  }

  private static CallTracerResult.Builder createCallBuilder(
      final TraceFrame frame,
      final TraceFrame nextTrace,
      final String opcode,
      final CallInfo parentCallInfo) {

    final String fromAddress =
        (parentCallInfo != null && parentCallInfo.builder != null)
            ? parentCallInfo.builder.build().getTo()
            : null;

    final String toAddress = resolveToAddress(frame, opcode);
    final long gasProvided = computeGasProvided(frame, nextTrace);
    final boolean isPrecompile = frame.isPrecompile();

    final CallTracerResult.Builder builder =
        CallTracerResult.builder()
            .type(opcode)
            .from(fromAddress)
            .to(toAddress)
            .value(getCallValue(frame, opcode))
            .gas(gasProvided);

    if (!isPrecompile) {
      builder.input(resolveInputData(frame, nextTrace, opcode).toHexString());
    }

    return builder;
  }

  private static CallTracerResult.Builder initializeRootBuilder(final TransactionTrace trace) {
    final Transaction tx = trace.getTransaction();
    final TransactionProcessingResult result = trace.getResult();

    final CallTracerResult.Builder builder =
        CallTracerResult.builder()
            .type(tx.isContractCreation() ? CREATE_TYPE : CALL_TYPE)
            .from(tx.getSender().toHexString())
            .to(
                tx.isContractCreation()
                    ? tx.contractAddress().map(Address::toHexString).orElse(null)
                    : tx.getTo().map(Address::toHexString).orElse(null))
            .value(tx.getValue().toShortHexString())
            .gas(tx.getGasLimit())
            .input(tx.getPayload().toHexString())
            .gasUsed(tx.getGasLimit() - result.getGasRemaining());

    // Set output if present
    if (result.getOutput() != null && !result.getOutput().isEmpty()) {
      builder.output(result.getOutput().toHexString());
    }

    // Handle errors
    if (!result.isSuccessful()) {
      handleRootError(builder, tx, result);
    }

    return builder;
  }

  private static void handleRootError(
      final CallTracerResult.Builder builder,
      final Transaction tx,
      final TransactionProcessingResult result) {

    builder.error(
        result
            .getExceptionalHaltReason()
            .map(ExceptionalHaltReason::getDescription)
            .orElse(EXECUTION_REVERTED));

    if (tx.isContractCreation()) {
      builder.to(null);
      result.getRevertReason().ifPresent(builder::revertReason);
    } else if (result.getExceptionalHaltReason().isEmpty()
        && result.getRevertReason().isPresent()) {
      builder.output(result.getRevertReason().get().toHexString());
    }
  }

  private static String getCallValue(final TraceFrame frame, final String opcode) {
    if (STATICCALL_TYPE.equals(opcode)) {
      return null;
    }

    if (DELEGATECALL_TYPE.equals(opcode)) {
      return ZERO_VALUE;
    }

    if (CALL_TYPE.equals(opcode) || CALLCODE_TYPE.equals(opcode)) {
      return extractValueFromStack(frame);
    }

    return frame.getValue().toShortHexString();
  }

  private static String extractValueFromStack(final TraceFrame frame) {
    return frame
        .getStack()
        .filter(stack -> stack.length >= CALL_STACK_VALUE_OFFSET)
        .map(stack -> Wei.wrap(stack[stack.length - CALL_STACK_VALUE_OFFSET]).toShortHexString())
        .orElse(ZERO_VALUE);
  }

  private static long calculateGasUsed(
      final CallInfo callInfo, final TraceFrame entryFrame, final TraceFrame exitFrame) {

    // Root transaction
    if (exitFrame.getDepth() == 0) {
      return Math.max(
          0, entryFrame.getGasRemaining() - exitFrame.getGasRemaining() - exitFrame.getGasRefund());
    }

    long gasProvided = callInfo.builder.getGas().longValue();
    long baseGasUsed = calculateBaseGasUsed(exitFrame, entryFrame, gasProvided);

    // Add code deposit cost for successful CREATE
    if (shouldAddCodeDepositCost(callInfo.builder.getType(), exitFrame)) {
      baseGasUsed += exitFrame.getOutputData().size() * CODE_DEPOSIT_GAS_PER_BYTE;
    }

    return baseGasUsed;
  }

  private static long calculateBaseGasUsed(
      final TraceFrame exitFrame, final TraceFrame entryFrame, final long gasProvided) {

    if (SELFDESTRUCT_TYPE.equals(exitFrame.getOpcode())) {
      long selfDestructCost = exitFrame.getGasCost().orElse(DEFAULT_SELFDESTRUCT_COST);
      return gasProvided - exitFrame.getGasRemaining() + selfDestructCost;
    }

    if (exitFrame.getGasRemaining() >= 0) {
      return gasProvided - exitFrame.getGasRemaining();
    }

    if (entryFrame.getPrecompiledGasCost().isPresent()) {
      return entryFrame.getPrecompiledGasCost().getAsLong();
    }

    return entryFrame.getGasCost().orElse(0L);
  }

  private static boolean shouldAddCodeDepositCost(
      final String callType, final TraceFrame exitFrame) {
    return (CREATE_TYPE.equals(callType) || CREATE2_TYPE.equals(callType))
        && exitFrame.getOutputData() != null
        && !exitFrame.getOutputData().isEmpty()
        && exitFrame.getExceptionalHaltReason().isEmpty();
  }

  private static String resolveToAddress(final TraceFrame frame, final String opcode) {
    if (isCreateOp(opcode)) {
      return null;
    }

    if (frame.isPrecompile() && frame.getPrecompileRecipient().isPresent()) {
      return frame.getPrecompileRecipient().get().toHexString();
    }

    if (isCallOp(opcode)) {
      return extractToAddressFromStack(frame);
    }

    return null;
  }

  private static String extractToAddressFromStack(final TraceFrame frame) {
    return frame
        .getStack()
        .filter(stack -> stack.length > 1 && stack[stack.length - CALL_STACK_TO_OFFSET] != null)
        .map(stack -> toAddress(stack[stack.length - CALL_STACK_TO_OFFSET]).toHexString())
        .orElse(null);
  }

  private static long computeGasProvided(final TraceFrame frame, final TraceFrame nextTrace) {
    boolean hasCalleeStart = nextTrace != null && nextTrace.getDepth() == frame.getDepth() + 1;

    if (hasCalleeStart) {
      return Math.max(0L, nextTrace.getGasRemaining());
    }

    // For non-entered calls (including soft failures), calculate the gas that would
    // have been provided using the 63/64 rule
    long remainingGas = frame.getGasRemaining();

    // Subtract the base cost of the operation itself
    long gasCost = frame.getGasCost().orElse(0L);
    long gasAfterCost = Math.max(0L, remainingGas - gasCost);

    // Apply 63/64 rule for CALL operations
    // For most calls, child gets min(gasAfterCost, gasAfterCost * 63/64)
    // Since we're calculating what WOULD have been provided, use the 63/64 rule
    long gasProvided = gasAfterCost - (gasAfterCost / 64L);

    return Math.max(0L, gasProvided);
  }

  private static Bytes resolveInputData(
      final TraceFrame frame, final TraceFrame nextTrace, final String opcode) {

    if (isCreateOp(opcode)) {
      return resolveCreateInputData(frame, opcode);
    }

    // Prefer callee frame for calls
    if (nextTrace != null && nextTrace.getDepth() == frame.getDepth() + 1) {
      return nextTrace.getInputData() != null ? nextTrace.getInputData() : Bytes.EMPTY;
    }

    // Fallback to memory extraction for calls
    if (isCallOp(opcode)) {
      return extractCallInputFromMemory(frame);
    }

    return frame.getInputData();
  }

  private static Bytes resolveCreateInputData(final TraceFrame frame, final String opcode) {
    // Try getMaybeCode() first
    if (frame.getMaybeCode().isPresent()) {
      return frame.getMaybeCode().get().getBytes();
    }

    // Fallback to memory extraction
    return frame
        .getStack()
        .map(stack -> extractCreateInitCode(frame, stack, opcode))
        .orElse(Bytes.EMPTY);
  }

  private static Bytes extractCreateInitCode(
      final TraceFrame frame, final Bytes[] stack, final String opcode) {

    if (CREATE_TYPE.equals(opcode)) {
      if (stack.length < CREATE_STACK_OFFSET_POS) return Bytes.EMPTY;

      int offset = bytesToInt(stack[stack.length - CREATE_STACK_OFFSET_POS]);
      int length = bytesToInt(stack[stack.length - CREATE_STACK_SIZE_POS]);

      return extractFromMemory(frame, offset, length);
    } else { // CREATE2
      if (stack.length < CREATE2_STACK_OFFSET_POS) return Bytes.EMPTY;

      int offset = bytesToInt(stack[stack.length - CREATE2_STACK_OFFSET_POS]);
      int length = bytesToInt(stack[stack.length - CREATE2_STACK_SIZE_POS]);

      return extractFromMemory(frame, offset, length);
    }
  }

  private static Bytes extractCallInputFromMemory(final TraceFrame frame) {
    return frame
        .getStack()
        .filter(stack -> stack.length >= CALL_STACK_IN_OFFSET)
        .map(
            stack -> {
              int inOffset = bytesToInt(stack[stack.length - CALL_STACK_IN_OFFSET]);
              int inSize = bytesToInt(stack[stack.length - CALL_STACK_IN_SIZE_OFFSET]);
              return extractFromMemory(frame, inOffset, inSize);
            })
        .orElse(frame.getInputData());
  }

  private static Bytes extractFromMemory(
      final TraceFrame frame, final int offset, final int length) {
    if (length == 0) return Bytes.EMPTY;

    return frame
        .getMemory()
        .map(memory -> extractCallDataFromMemory(memory, offset, length))
        .orElse(Bytes.EMPTY);
  }

  private static void finalizePrecompileChild(
      final TraceFrame entryFrame,
      final CallTracerResult.Builder childBuilder,
      final CallInfo parentCallInfo) {

    // Calculate gas for precompile (Geth-style)
    final long post = Math.max(0L, entryFrame.getGasRemaining());
    final long base = post > WARM_ACCESS_GAS ? post - WARM_ACCESS_GAS : 0L;
    final long cap = base - (base / 64L);
    childBuilder.gas(cap);

    // Set gas used
    long gasUsed =
        entryFrame.getPrecompiledGasCost().orElseGet(() -> entryFrame.getGasCost().orElse(0L));

    // Handle precompile failure
    if (entryFrame.getExceptionalHaltReason().isPresent()) {
      handlePrecompileError(entryFrame, childBuilder);
      gasUsed = cap; // Failed precompiles consume all gas
    }

    childBuilder.gasUsed(gasUsed);

    // Set I/O data
    childBuilder.input(entryFrame.getPrecompileInputData().map(Bytes::toHexString).orElse(null));
    childBuilder.output(entryFrame.getPrecompileOutputData().map(Bytes::toHexString).orElse(null));

    // Add to parent
    if (parentCallInfo != null) {
      parentCallInfo.builder.addCall(childBuilder.build());
    }
  }

  private static void handlePrecompileError(
      final TraceFrame entryFrame, final CallTracerResult.Builder childBuilder) {

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

    childBuilder.error(errorMessage);
  }

  private static void handleSelfDestruct(final TraceFrame frame, final CallInfo currentCallInfo) {
    if (currentCallInfo == null
        || frame.getStack().isEmpty()
        || frame.getExceptionalHaltReason().isPresent()) {
      return;
    }

    frame
        .getStack()
        .ifPresent(
            stack -> {
              if (stack.length == 0) return;

              final Address beneficiary = toAddress(stack[stack.length - 1]);
              final String from = frame.getRecipient().toHexString();
              final String value = extractSelfDestructValue(frame, beneficiary);

              final CallTracerResult selfDestructCall =
                  CallTracerResult.builder()
                      .type(SELFDESTRUCT_TYPE)
                      .from(from)
                      .to(beneficiary.toHexString())
                      .gas(0L)
                      .gasUsed(0L)
                      .value(value)
                      .input("0x")
                      .build();

              currentCallInfo.builder.addCall(selfDestructCall);
            });
  }

  private static String extractSelfDestructValue(
      final TraceFrame frame, final Address beneficiary) {
    return frame
        .getMaybeRefunds()
        .map(refunds -> refunds.get(beneficiary))
        .map(Wei::toShortHexString)
        .orElse(ZERO_VALUE);
  }

  private static void handleImplicitReturn(
      final TraceFrame lastFrameAtDepth, final Map<Integer, CallInfo> depthToCallInfo) {

    final int failedDepth = lastFrameAtDepth.getDepth();
    final CallInfo childCallInfo = depthToCallInfo.get(failedDepth);

    if (childCallInfo == null) return;

    // Set error if exceptional halt
    lastFrameAtDepth
        .getExceptionalHaltReason()
        .ifPresent(reason -> childCallInfo.builder.error(reason.getDescription()));

    // Calculate gas used
    if (childCallInfo.entryFrame != null) {
      long gasUsed = calculateImplicitReturnGasUsed(lastFrameAtDepth, childCallInfo);
      childCallInfo.builder.gasUsed(gasUsed);
    }

    // Add to parent and remove from tracking
    CallInfo parentCallInfo = depthToCallInfo.get(failedDepth - 1);
    if (parentCallInfo != null) {
      parentCallInfo.builder.addCall(childCallInfo.builder.build());
    }

    depthToCallInfo.remove(failedDepth);
  }

  private static long calculateImplicitReturnGasUsed(
      final TraceFrame lastFrame, final CallInfo childCallInfo) {

    long gasProvided = childCallInfo.builder.getGas().longValue();

    // All gas consumed for insufficient gas
    if (lastFrame
        .getExceptionalHaltReason()
        .map(r -> Objects.equals(r, ExceptionalHaltReason.INSUFFICIENT_GAS))
        .orElse(false)) {
      return gasProvided;
    }

    // Special handling for SELFDESTRUCT
    if (SELFDESTRUCT_TYPE.equals(lastFrame.getOpcode())) {
      long selfDestructCost = lastFrame.getGasCost().orElse(DEFAULT_SELFDESTRUCT_COST);
      return gasProvided - lastFrame.getGasRemaining() + selfDestructCost;
    }

    // Normal calculation
    return Math.max(0, gasProvided - lastFrame.getGasRemaining());
  }

  private static CallTracerResult createRootCallFromTransaction(final TransactionTrace trace) {
    final Transaction tx = trace.getTransaction();
    final TransactionProcessingResult result = trace.getResult();

    return initializeRootBuilder(trace)
        .gasUsed(tx.getGasLimit() - result.getGasRemaining())
        .build();
  }

  // Helper utilities
  private static boolean isCallOp(final String opcode) {
    return CALL_TYPE.equals(opcode)
        || CALLCODE_TYPE.equals(opcode)
        || DELEGATECALL_TYPE.equals(opcode)
        || STATICCALL_TYPE.equals(opcode);
  }

  private static boolean isCreateOp(final String opcode) {
    return CREATE_TYPE.equals(opcode) || CREATE2_TYPE.equals(opcode);
  }

  private static boolean isReturnOp(final String opcode) {
    return RETURN_TYPE.equals(opcode);
  }

  private static boolean isRevertOp(final String opcode) {
    return REVERT_TYPE.equals(opcode);
  }

  private static boolean isSelfDestructOp(final String opcode) {
    return SELFDESTRUCT_TYPE.equals(opcode);
  }

  private static boolean isHaltOp(final String opcode) {
    return STOP_TYPE.equals(opcode);
  }

  /** Helper class to track call information during trace processing. */
  private record CallInfo(CallTracerResult.Builder builder, TraceFrame entryFrame) {}
}
