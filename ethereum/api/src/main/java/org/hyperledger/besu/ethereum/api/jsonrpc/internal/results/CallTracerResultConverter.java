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
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.CallTracerHelper.hexN;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.CallTracerHelper.mapExceptionalHaltToError;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.CallTracerHelper.shortHex;
import static org.hyperledger.besu.evm.internal.Words.toAddress;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.tracing.TraceFrame;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *
 * <p>For precompiled contracts, the converter uses dedicated fields in TraceFrame to capture the
 * actual input/output data, as precompiles execute atomically without creating child frames.
 *
 * <p>For each call, it extracts relevant information such as addresses, values transferred, input
 * data, output data, gas usage, and error states.
 */
public class CallTracerResultConverter {
  private static final Logger LOG = LoggerFactory.getLogger(CallTracerResultConverter.class);

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
      LOG.trace("No trace frames available, creating root call from transaction");
      return createRootCallFromTransaction(transactionTrace);
    }
    LOG.trace(
        "** Building call hierarchy for tx: {} from {} trace frames",
        transactionTrace.getTransaction().getHash(),
        transactionTrace.getTraceFrames().size());
    return buildCallHierarchyFromFrames(transactionTrace);
  }

  private static CallTracerResult buildCallHierarchyFromFrames(final TransactionTrace trace) {
    // debug log all opcodes
    for (TraceFrame traceFrame : trace.getTraceFrames()) {
      String opcodeLog =
          String.format(
              "OpCode: %s, Depth: %d, GasRem: %d, GasCost: %s, Exceptional Halt: %s",
              traceFrame.getOpcode(),
              traceFrame.getDepth(),
              traceFrame.getGasRemaining(),
              traceFrame.getGasCost().isPresent() ? traceFrame.getGasCost().getAsLong() : "N/A",
              traceFrame
                  .getExceptionalHaltReason()
                  .map(ExceptionalHaltReason::name)
                  .orElse("None"));

      // Add stack details for CALL/CALLCODE
      if (("CALL".equals(traceFrame.getOpcode()) || "CALLCODE".equals(traceFrame.getOpcode()))
          && traceFrame.getStack().isPresent()) {
        Bytes[] stack = traceFrame.getStack().get();
        if (stack.length >= 3) {
          Bytes valueBytes = stack[stack.length - 3];
          Wei value = Wei.wrap(valueBytes);
          opcodeLog += String.format(", Value from stack: %s", value.toShortHexString());
        }
      }

      LOG.trace(opcodeLog);
    }
    LOG.trace("-----------------------------------");

    // Initialize the root call
    final CallTracerResult.Builder rootBuilder = initializeRootBuilder(trace);
    if (!trace.getResult().isSuccessful()) {
      // If the transaction failed, return only the root call with error info
      return rootBuilder.build();
    }
    final List<TraceFrame> frames = trace.getTraceFrames();

    // Track calls by depth
    final Map<Integer, CallInfo> depthToCallInfo = new HashMap<>();

    int currentDepth = 0;
    final CallInfo rootInfo = new CallInfo(rootBuilder, null);
    depthToCallInfo.put(0, rootInfo);

    // Track previous frame to detect implicit returns
    TraceFrame previousFrame = null;

    // Process all frames
    for (int i = 0; i < frames.size(); i++) {
      final TraceFrame nextTrace = i < frames.size() - 1 ? frames.get(i + 1) : null;
      final TraceFrame frame = frames.get(i);
      final String opcode = frame.getOpcode();
      final int frameDepth = frame.getDepth();

      // Check if depth decreased without a proper return (implicit return due to failure)
      if (previousFrame != null && frameDepth < previousFrame.getDepth()) {
        // Check if the previous frame was NOT a return/revert/stop
        if (!isReturnOp(previousFrame.getOpcode())
            && !isRevertOp(previousFrame.getOpcode())
            && !isHaltOp(previousFrame.getOpcode())) {

          LOG.trace(
              "Detected implicit return from depth {} to {}", previousFrame.getDepth(), frameDepth);
          handleImplicitReturn(previousFrame, depthToCallInfo);
        }
      }

      // Process call operations that create a new context (or a precompile effect)
      if (isCallOp(opcode) || isCreateOp(opcode)) {
        currentDepth = frameDepth;
        // Get parent call info
        final CallInfo parentCallInfo = depthToCallInfo.get(currentDepth);
        // Build the prospective child
        final CallTracerResult.Builder childBuilder =
            createCallBuilder(frame, nextTrace, opcode, parentCallInfo);

        // PRECOMPILE FAST-PATH: finalize immediately (no callee frame at depth+1)
        if (frame.isPrecompile()) {
          finalizePrecompileChild(frame, childBuilder, parentCallInfo);
          previousFrame = frame;
          continue;
        }

        // Check if we entered a callee (depth increased)
        final boolean calleeEntered = (nextTrace != null && nextTrace.getDepth() > frameDepth);
        LOG.trace(
            "*** calleeEntered={} nextDepth={}",
            calleeEntered,
            (nextTrace == null ? "-" : nextTrace.getDepth()));

        if (!calleeEntered) {
          handleNonEnteredCall(frame, opcode, childBuilder, parentCallInfo);
          previousFrame = frame;
          continue;
        }

        // Normal call path: track child until its RETURN/REVERT/HALT
        final CallInfo childCallInfo = new CallInfo(childBuilder, frame);
        depthToCallInfo.put(currentDepth + 1, childCallInfo);
      }
      // Handle SELFDESTRUCT specifically
      else if (isSelfDestructOp(opcode)) {
        final CallInfo currentCallInfo = depthToCallInfo.get(frameDepth);
        handleSelfDestruct(frame, currentCallInfo);
      }
      // Process return operations that exit a context
      else if (isReturnOp(opcode) || isRevertOp(opcode) || isHaltOp(opcode)) {
        currentDepth = frameDepth;

        // Get child call info
        final CallInfo childCallInfo = depthToCallInfo.get(currentDepth);

        if (childCallInfo == null) {
          LOG.debug(">> Orphaned return at depth {} - no matching call entry", currentDepth);
          previousFrame = frame;
          continue;
        }
        LOG.trace(
            " >> return: opcode={}, depth={} type={}",
            opcode,
            currentDepth,
            childCallInfo.builder.getType());

        // Get entry frame and calculate gas used
        final TraceFrame entryFrame = childCallInfo.entryFrame;
        if (entryFrame != null) {
          if (isCreateOp(childCallInfo.builder.getType()) && frame.getDepth() > 0) {
            // For successful CREATE, set the created contract address
            // This might be available in frame.getRecipient() or frame.getContractAddress()
            if (childCallInfo.builder.getTo() == null) {
              childCallInfo.builder.to(frame.getRecipient().toHexString());
            }
          }
          // Set output data and error status
          setOutputAndErrorStatus(childCallInfo.builder, frame, opcode);

          // Calculate gas used
          final long gasUsed = calculateGasUsed(childCallInfo, entryFrame, frame);
          childCallInfo.builder.gasUsed(gasUsed);
          if (LOG.isTraceEnabled()) {
            LOG.trace(
                " gasUsed={} (provided={} - exitGasRem={})",
                gasUsed,
                (childCallInfo.builder.getGas() == null ? "null" : childCallInfo.builder.getGas()),
                frame.getGasRemaining());
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

      // Update previous frame at the end of the loop
      previousFrame = frame;
    }

    // Process any remaining calls that didn't have explicit return frames
    processRemainingCalls(depthToCallInfo);

    return rootInfo.builder.build();
  }

  private static void handleNonEnteredCall(
      final TraceFrame frame,
      final String opcode,
      final CallTracerResult.Builder childBuilder,
      final CallInfo parentCallInfo) {
    // Call didn't enter - could be: failed call, call to EOA, insufficient balance, etc.
    // For now, add all non-entered calls to trace with available data

    // Set error if there's an exceptional halt
    frame
        .getExceptionalHaltReason()
        .ifPresent(
            haltReason -> {
              String errorMessage = mapExceptionalHaltToError(haltReason);
              childBuilder.error(errorMessage);
            });

    // For failed CREATE, ensure 'to' is null
    if (isCreateOp(opcode)) {
      childBuilder.to(null); // No contract was created
    }

    // Set gas used (0 for most failed calls)
    if (frame.getExceptionalHaltReason().isPresent()) {
      // Some exceptional halts consume gas
      ExceptionalHaltReason reason = frame.getExceptionalHaltReason().get();
      if ("INSUFFICIENT_GAS".equals(reason.name())) {
        childBuilder.gasUsed(frame.getGasCost().orElse(0L)); // Uses all available
      } else {
        childBuilder.gasUsed(0L); // Most other failures use no gas
      }
    } else {
      childBuilder.gasUsed(0L); // Non-exceptional failures (like insufficient balance)
    }

    // TODO: We need a way to determine if this has happened due to insufficient funds.
    //  Not very obvious from data from TraceFrame at the moment. Insufficient funds doesn't
    //  raise exceptional halt reason.

    // Add to parent immediately (like precompiles)
    if (parentCallInfo != null) {
      parentCallInfo.builder.addCall(childBuilder.build());
    }
  }

  private static void setOutputAndErrorStatus(
      final CallTracerResult.Builder builder, final TraceFrame frame, final String opcode) {
    // Set output data if present
    if (frame.getOutputData() != null && !frame.getOutputData().isEmpty()) {
      builder.output(frame.getOutputData().toHexString());
    }

    // Set error information
    if (frame.getExceptionalHaltReason().isPresent()) {
      // Use the specific halt reason description
      String errorMessage =
          frame
              .getExceptionalHaltReason()
              .map(ExceptionalHaltReason::getDescription)
              .orElse("execution reverted");
      builder.error(errorMessage);

      // For failed CREATE/CREATE2, ensure 'to' is null
      if ("CREATE".equals(builder.getType()) || "CREATE2".equals(builder.getType())) {
        builder.to(null);
        // also set code as input for failed contract creation
        if (frame.getMaybeCode().isPresent()) {
          // set code to input
          final Bytes codeBytes = frame.getMaybeCode().get().getBytes();
          if (codeBytes != null) {
            builder.input(codeBytes.toHexString());
          }
        }
      }

      // Add revert reason if available
      frame
          .getRevertReason()
          .ifPresent(
              reason -> {
                String decodedReason = decodeRevertReason(reason);
                builder.revertReason(decodedReason);
              });
    } else if ("REVERT".equals(opcode)) {
      builder.error("execution reverted");
      frame
          .getRevertReason()
          .ifPresent(
              reason -> {
                String decodedReason = decodeRevertReason(reason);
                builder.revertReason(decodedReason);
              });
    }
  }

  /**
   * Decodes the revert reason bytes into a human-readable string.
   *
   * @param reason The raw revert reason bytes
   * @return A human-readable revert reason string
   */
  private static String decodeRevertReason(final Bytes reason) {
    // Check for empty reason
    if (reason == null || reason.isEmpty()) {
      return null;
    }

    try {
      // Check for Error(string) format - should start with selector 0x08c379a0
      if (reason.size() >= 4
          && reason.get(0) == (byte) 0x08
          && reason.get(1) == (byte) 0xc3
          && reason.get(2) == (byte) 0x79
          && reason.get(3) == (byte) 0xa0) {

        // Reason has format: 0x08c379a0 + 32 bytes offset + 32 bytes length + string data
        // The offset is usually 0x20 (32) from the selector
        int strLenOffset = 4 + 32; // Skip selector and offset word

        if (reason.size() >= strLenOffset + 32) { // Must have at least the length word
          // Extract the string length from the length word
          int strLen = 0;
          for (int i = 0; i < 32; i++) {
            strLen = (strLen << 8) | (reason.get(strLenOffset + i) & 0xFF);
          }

          // Sanity check on length
          if (strLen > 0 && strLen < 1000 && strLenOffset + 32 + strLen <= reason.size()) {
            // Extract the string data
            final Bytes strData = reason.slice(strLenOffset + 32, strLen);
            // Convert to a string
            return new String(strData.toArrayUnsafe(), StandardCharsets.UTF_8);
          }
        }
      }

      // If it's not in the standard format or couldn't be decoded,
      // return a compact hex representation
      return toCompactHex(reason, true);
    } catch (final Exception e) {
      LOG.warn("Failed to decode revert reason", e);
      // Fall back to hex representation on any error
      return reason.toHexString();
    }
  }

  /**
   * Converts bytes to a compact hex representation, removing leading zeros. Based on the
   * implementation in StructLog.
   */
  private static String toCompactHex(final Bytes bytes, final boolean prefix) {
    if (bytes.isEmpty()) {
      return prefix ? "0x0" : "0";
    }

    byte[] byteArray = bytes.toArrayUnsafe();
    final int size = byteArray.length;
    final StringBuilder result = new StringBuilder(prefix ? (size * 2) + 2 : size * 2);

    if (prefix) {
      result.append("0x");
    }

    boolean leadingZero = true;
    for (int i = 0; i < size; i++) {
      byte b = byteArray[i];
      int highNibble = (b >> 4) & 0xF;
      if (!leadingZero || highNibble != 0) {
        result.append(Character.forDigit(highNibble, 16));
        leadingZero = false;
      }
      int lowNibble = b & 0xF;
      if (!leadingZero || lowNibble != 0 || i == size - 1) {
        result.append(Character.forDigit(lowNibble, 16));
        leadingZero = false;
      }
    }
    return result.toString();
  }

  private static void processRemainingCalls(final Map<Integer, CallInfo> depthToCallInfo) {
    // Walk whatever depths we actually stored, deepest first
    final TreeSet<Integer> depths = new TreeSet<>(depthToCallInfo.keySet());
    for (final Integer depth : depths.descendingSet()) {
      if (depth == 0) continue; // skip root
      final CallInfo child = depthToCallInfo.get(depth);
      if (child == null) continue;
      final CallInfo parent = depthToCallInfo.get(depth - 1);
      if (parent != null) {
        parent.builder.addCall(child.builder.build());
      }
      depthToCallInfo.remove(depth);
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

    // Authoritative gas (provided): callee frame start if depth+1, else placeholder value
    final long gasProvided = computeGasProvided(frame, nextTrace, opcode);

    // Detect precompile up front so we can defer input/output
    final boolean looksLikePrecompile = frame.isPrecompile();

    LOG.trace(
        "*** createCallBuilder, TraceFrame: {}, Exceptional Halt: {}",
        frame,
        frame.getExceptionalHaltReason().orElse(null));
    final CallTracerResult.Builder builder =
        CallTracerResult.builder()
            .type(opcode)
            .from(fromAddress)
            .to(toAddress)
            .value(getCallValue(frame, opcode))
            .gas(gasProvided);

    if (!looksLikePrecompile) {
      // Normal calls/creates: set input now (resolveInputData may use callee frame if we entered)
      final Bytes inputData = resolveInputData(frame, nextTrace, opcode);
      builder.input(inputData.toHexString());
    }

    return builder;
  }

  private static CallTracerResult.Builder initializeRootBuilder(final TransactionTrace trace) {
    final Transaction tx = trace.getTransaction();
    final TransactionProcessingResult result = trace.getResult();

    final CallTracerResult.Builder builder =
        CallTracerResult.builder()
            .type(tx.isContractCreation() ? "CREATE" : "CALL")
            .from(tx.getSender().toHexString())
            .to(
                tx.isContractCreation()
                    ? tx.contractAddress().map(Address::toHexString).orElse(null)
                    : tx.getTo().map(Address::toHexString).orElse(null))
            .value(tx.getValue().toShortHexString())
            .gas(tx.getGasLimit())
            .input(tx.getPayload().toHexString());

    // Set total gas used
    final long totalGasUsed = tx.getGasLimit() - result.getGasRemaining();
    builder.gasUsed(totalGasUsed);

    // Set output if present
    if (result.getOutput() != null && !result.getOutput().isEmpty()) {
      builder.output(result.getOutput().toHexString());
    }

    // Set error if transaction failed
    if (!result.isSuccessful()) {
      builder.error(
          result
              .getExceptionalHaltReason()
              .map(ExceptionalHaltReason::getDescription)
              .orElse("execution reverted"));

      if (tx.isContractCreation()) {
        // set TO as null for failed contract creation
        builder.to(null);
        builder.revertReason(result.getRevertReason().map(Bytes::toHexString).orElse(null));
      } else {
        // for failed calls, if we don't have an exceptional halt, and revert reason exists, it
        // needs to set "output" with revert reason
        if (result.getExceptionalHaltReason().isEmpty() && result.getRevertReason().isPresent()) {
          builder.output(result.getRevertReason().get().toHexString());
        }
      }
    }
    return builder;
  }

  private static String getCallValue(final TraceFrame frame, final String opcode) {
    if ("STATICCALL".equals(opcode)) {
      return null; // omit field to match Geth
    }

    if ("DELEGATECALL".equals(opcode)) {
      return "0x0"; // DELEGATECALL never transfers value
    }

    // For CALL/CALLCODE, extract value from stack, NOT from frame.getValue()
    if ("CALL".equals(opcode) || "CALLCODE".equals(opcode)) {
      return frame
          .getStack()
          .filter(stack -> stack.length >= 3)
          .map(
              stack -> {
                // CALL stack: gas, to, value, inOffset, inSize, outOffset, outSize
                // Value is at stack[length - 3]
                Bytes valueBytes = stack[stack.length - 3];
                Wei value = Wei.wrap(valueBytes);
                return value.toShortHexString();
              })
          .orElse("0x0");
    }

    // For CREATE/CREATE2, use frame.getValue()
    return frame.getValue().toShortHexString();
  }

  private static long calculateGasUsed(
      final CallInfo callInfo, final TraceFrame entryFrame, final TraceFrame exitFrame) {

    // For root transaction
    if (exitFrame.getDepth() == 0) {
      long gasUsed = entryFrame.getGasRemaining() - exitFrame.getGasRemaining();
      long gasRefund = exitFrame.getGasRefund();
      return Math.max(0, gasUsed - gasRefund);
    }

    // Get base gas calculation
    long gasProvided = callInfo.builder.getGas().longValue();
    long baseGasUsed = 0;

    // Check if this was a SELFDESTRUCT exit
    if ("SELFDESTRUCT".equals(exitFrame.getOpcode())) {
      long selfDestructCost = exitFrame.getGasCost().orElse(5000L);
      long gasBeforeSelfDestruct = exitFrame.getGasRemaining();
      baseGasUsed = gasProvided - gasBeforeSelfDestruct + selfDestructCost;
    }
    // Normal return path
    else if (exitFrame.getGasRemaining() >= 0) {
      baseGasUsed = gasProvided - exitFrame.getGasRemaining();
    }
    // Precompiled contracts
    else if (entryFrame.getPrecompiledGasCost().isPresent()) {
      baseGasUsed = entryFrame.getPrecompiledGasCost().getAsLong();
    }
    // Fallback
    else if (entryFrame.getGasCost().isPresent()) {
      baseGasUsed = entryFrame.getGasCost().getAsLong();
    }

    // Add code deposit cost for successful CREATE/CREATE2
    String callType = callInfo.builder.getType();
    if (("CREATE".equals(callType) || "CREATE2".equals(callType))
        && exitFrame.getOutputData() != null
        && !exitFrame.getOutputData().isEmpty()
        && exitFrame.getExceptionalHaltReason().isEmpty()) {

      // Code deposit cost is 200 gas per byte of deployed code
      long codeDepositCost = exitFrame.getOutputData().size() * 200L;
      baseGasUsed += codeDepositCost;

      LOG.trace(
          "Adding code deposit cost for {}: {} bytes * 200 = {} gas",
          callType,
          exitFrame.getOutputData().size(),
          codeDepositCost);
    }

    return baseGasUsed;
  }

  private static String resolveToAddress(final TraceFrame frame, final String opcode) {
    // For CREATE/CREATE2, "to" is not known at call-site
    if ("CREATE".equals(opcode) || "CREATE2".equals(opcode)) {
      return null;
    }

    // For precompiles, use the stored precompile recipient if available
    if (frame.isPrecompile() && frame.getPrecompileRecipient().isPresent()) {
      return frame.getPrecompileRecipient().get().toHexString();
    }
    // For regular calls, extract from stack
    if ("CALL".equals(opcode)
        || "CALLCODE".equals(opcode)
        || "STATICCALL".equals(opcode)
        || "DELEGATECALL".equals(opcode)) {
      return frame
          .getStack()
          .filter(stack -> stack.length > 1 && stack[stack.length - 2] != null)
          .map(stack -> toAddress(stack[stack.length - 2]).toHexString())
          .orElse(null);
    }

    // Unknown/other opcodes: no callee address.
    return null;
  }

  /**
   * Returns the callee's calldata (for CALL/DELEGATECALL/STATICCALL) or the init code (for
   * CREATE/CREATE2), matching geth's callTracer behavior.
   *
   * <p>Strategy: 1) Prefer the immediate callee frame's inputData when the very next trace frame is
   * at depth+1. This is the authoritative view of what the callee actually received.
   *
   * <p>2) Otherwise, reconstruct from the caller's memory using the op-specific stack operands
   * (inOffset, inSize / offset, size). Indices below are counted from the top-of-stack (TOS), i.e.
   * stack[stack.length - 1] is TOS.
   *
   * <p>Notes: - Precompiles (and some edge cases) may not emit a depth+1 frame; hence the
   * memory-slice fallback. - For CREATE/CREATE2, "input" here refers to the init code (which runs
   * once to produce the deployed runtime code), not any constructor arguments encoding semantics.
   *
   * <p>Stack layouts (TOS on the right): CALL/CALLCODE: gas, to, value, inOffset, inSize,
   * outOffset, outSize DELEGATECALL: gas, to, inOffset, inSize, outOffset, outSize STATICCALL: gas,
   * to, inOffset, inSize, outOffset, outSize CREATE: value, offset, size CREATE2: value, offset,
   * size, salt
   *
   * <p>Therefore: CALL/DELEGATECALL/STATICCALL -> inOffset = -4, inSize = -3 CREATE -> offset = -2,
   * size = -1 CREATE2 -> offset = -3, size = -2
   */
  private static Bytes resolveInputData(
      final TraceFrame frame, final TraceFrame nextTrace, final String opcode) {

    LOG.trace(
        "resolveInputData: op={} depth={} hasNextCalleeFrame={}",
        opcode,
        frame.getDepth(),
        (nextTrace != null && nextTrace.getDepth() == frame.getDepth() + 1));

    // Prefer the callee frame when we actually enter it (authoritative).
    if (nextTrace != null && nextTrace.getDepth() == frame.getDepth() + 1) {
      Bytes calleeInput = nextTrace.getInputData();
      if (calleeInput == null) {
        calleeInput = Bytes.EMPTY; // Normalize null to empty
      }
      LOG.trace(
          "  using callee-frame input (authoritative) [{}]={}",
          calleeInput.size(),
          shortHex(calleeInput, 16));
      return calleeInput;
    }

    // CALL-like: [..., gas, to, inOffset, inSize, outOffset, outSize]^TOS
    // Tail (TOS at end) mapping for calldata:
    //   inOffset = stack[-4], inSize = stack[-3]
    if (isCallOp(opcode)) {
      return frame
          .getStack()
          .map(
              stack -> {
                if (stack.length < 4) {
                  return frame.getInputData();
                }

                final int inOffset = bytesToInt(stack[stack.length - 4]);
                final int inSize = bytesToInt(stack[stack.length - 3]);

                LOG.trace(
                    "  CALL-like fallback slice: inOffset={} inSize={} memPresent={}",
                    inOffset,
                    inSize,
                    frame.getMemory().isPresent());

                return frame
                    .getMemory()
                    .map(memory -> extractCallDataFromMemory(memory, inOffset, inSize))
                    .orElse(frame.getInputData());
              })
          .orElse(frame.getInputData());
    }

    // CREATE/CREATE2 have different (offset,size) slots.
    if (isCreateOp(opcode)) {
      return frame
          .getStack()
          .map(
              stack -> {
                if ("CREATE".equals(opcode)) { // CREATE(init_offset, init_size)
                  if (stack.length < 2) return frame.getInputData();
                  final int offset = bytesToInt(stack[stack.length - 2]); // [-2]
                  final int length = bytesToInt(stack[stack.length - 1]); // [-1]
                  LOG.trace(
                      "  CREATE-init slice: offset={} length={} memPresent={}",
                      offset,
                      length,
                      frame.getMemory().isPresent());

                  return frame
                      .getMemory()
                      .map(memory -> extractCallDataFromMemory(memory, offset, length))
                      .orElse(frame.getInputData());
                } else { // CREATE2(init_offset, init_size, salt)
                  if (stack.length < 3) return frame.getInputData();
                  final int offset = bytesToInt(stack[stack.length - 3]); // [-3]
                  final int length = bytesToInt(stack[stack.length - 2]); // [-2]
                  LOG.trace(
                      "  CREATE2-init slice: offset={} length={} memPresent={}",
                      offset,
                      length,
                      frame.getMemory().isPresent());
                  return frame
                      .getMemory()
                      .map(memory -> extractCallDataFromMemory(memory, offset, length))
                      .orElse(frame.getInputData());
                }
              })
          .orElse(frame.getInputData());
    }

    // Default (unchanged)
    return frame.getInputData();
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

  private static boolean isSelfDestructOp(final String opcode) {
    return "SELFDESTRUCT".equals(opcode);
  }

  private static boolean isHaltOp(final String opcode) {
    return "STOP".equals(opcode);
  }

  private static CallTracerResult createRootCallFromTransaction(final TransactionTrace trace) {
    final Transaction tx = trace.getTransaction();
    final TransactionProcessingResult result = trace.getResult();
    final CallTracerResult.Builder rootBuilder = initializeRootBuilder(trace);

    // Set gas used
    rootBuilder.gasUsed(tx.getGasLimit() - result.getGasRemaining());

    return rootBuilder.build();
  }

  /** Helper class to track call information during trace processing. */
  private record CallInfo(CallTracerResult.Builder builder, TraceFrame entryFrame) {}

  /**
   * Compute the gas provided to the child call.
   *
   * <p>For calls that enter a child frame (depth+1), returns the child's starting gas. For
   * precompiles and non-entered calls, returns a placeholder value since:
   *
   * <ul>
   *   <li>Precompiles: Gas will be recalculated in finalizePrecompileChild
   *   <li>Non-entered calls: Won't create a child (skipped in main loop)
   * </ul>
   *
   * @param frame The current trace frame
   * @param nextTrace The next trace frame (if any)
   * @param opcode The operation code
   * @return The gas provided to the call, or 0 as placeholder
   */
  private static long computeGasProvided(
      final TraceFrame frame, final TraceFrame nextTrace, final String opcode) {

    final boolean hasCalleeStart =
        (nextTrace != null && nextTrace.getDepth() == frame.getDepth() + 1);

    if (LOG.isTraceEnabled()) {
      LOG.trace(
          "computeGasProvided: op={} depth={} nextDepth={} -> {}",
          opcode,
          frame.getDepth(),
          (nextTrace == null ? "-" : nextTrace.getDepth()),
          (hasCalleeStart
              ? "using callee start gas=" + hexN(Math.max(0L, nextTrace.getGasRemaining()))
              : "placeholder for precompile/non-entered"));
    }

    // If we actually enter the callee (depth+1), that frame's starting gas is authoritative.
    if (hasCalleeStart) {
      final long g = nextTrace.getGasRemaining();
      return (g >= 0) ? g : 0L;
    }

    // For precompiles and non-executed calls, return a placeholder
    // Precompiles: The actual gas will be calculated in finalizePrecompileChild
    // Non-executed calls: Won't create a child anyway (skipped in main loop)
    return 0L; // Placeholder value
  }

  /**
   * Finalizes a precompile child call by setting gas, gas used, input/output data, and attaching it
   * to the parent call.
   *
   * <p>Behavior:
   *
   * <ul>
   *   <li><b>Execution model:</b> Precompiles execute inline; no separate callee frame is emitted.
   *   <li><b>Gas (provided):</b> Calculated using Geth-style display: (post - warmAccess) * 63/64
   *       where warmAccess = 100 for post-Berlin, 0 for pre-Berlin.
   *   <li><b>Gas used:</b> Uses the precompiled gas cost stored in the TraceFrame.
   *   <li><b>Input:</b> Uses the precompile input data stored in TraceFrame by
   *       DebugOperationTracer.
   *   <li><b>Output:</b> Uses the precompile output data stored in TraceFrame by
   *       DebugOperationTracer.
   * </ul>
   *
   * @param entryFrame The trace frame containing the precompile call
   * @param childBuilder The builder for the precompile call result
   * @param parentCallInfo The parent call information
   */
  private static void finalizePrecompileChild(
      final TraceFrame entryFrame,
      final CallTracerResult.Builder childBuilder,
      final CallInfo parentCallInfo) {

    LOG.trace(
        "finalizePrecompileChild: to={} type={} precompileGasCost={} gasCost={}, isPrecompile={}, precompileOutput={}",
        childBuilder.getTo(),
        childBuilder.getType(),
        entryFrame.getPrecompiledGasCost().isPresent()
            ? entryFrame.getPrecompiledGasCost().getAsLong()
            : "-",
        entryFrame.getGasCost().isPresent() ? entryFrame.getGasCost().getAsLong() : "-",
        entryFrame.isPrecompile(),
        entryFrame
            .getPrecompileOutputData()
            .map(Bytes::toHexString)
            .orElse("Precompile Output Not Available"));

    // --- 1) Child gas (Geth-style display): (post - warm) * 63/64 ---
    final long post = Math.max(0L, entryFrame.getGasRemaining());
    final long warmAccess = 100L; // 0 pre-Berlin
    final long base = post > warmAccess ? post - warmAccess : 0L;
    final long cap = base - (base / 64L);
    childBuilder.gas(cap);
    LOG.trace(
        "  precompile gas(display): post={} warmAccess={} base={} cap={}",
        hexN(post),
        hexN(warmAccess),
        hexN(base),
        hexN(cap));

    // --- 2) gasUsed: prefer precompiledGasCost, else opcode gasCost ---
    entryFrame
        .getPrecompiledGasCost()
        .ifPresentOrElse(
            childBuilder::gasUsed, () -> entryFrame.getGasCost().ifPresent(childBuilder::gasUsed));

    // Check if precompile failed
    if (entryFrame.getExceptionalHaltReason().isPresent()) {
      String errorMessage =
          entryFrame
              .getExceptionalHaltReason()
              .map(ExceptionalHaltReason::getDescription)
              .orElse("precompile failed");
      childBuilder.error(errorMessage);

      // revert reason may contain the actual reason for precompile failure
      if (entryFrame.getRevertReason().isPresent()) {
        childBuilder.error(
            new String(entryFrame.getRevertReason().get().toArrayUnsafe(), StandardCharsets.UTF_8));
      }
      LOG.trace("  Precompile failed: {}", errorMessage);

      // TODO: Verify from reviewers - Geth sets "Gas Cost <= gas" in case of error,
      //  Besu should do same?
      childBuilder.gasUsed(cap);
    }

    // --- 3) I/O computation (no callee frame) ---
    childBuilder.input(entryFrame.getPrecompileInputData().map(Bytes::toHexString).orElse(null));
    childBuilder.output(entryFrame.getPrecompileOutputData().map(Bytes::toHexString).orElse(null));

    // --- 4) Attach immediately — precompiles have no callee frame ---
    if (parentCallInfo != null) {
      parentCallInfo.builder.addCall(childBuilder.build());
    }
  }

  private static void handleSelfDestruct(final TraceFrame frame, final CallInfo currentCallInfo) {
    if (currentCallInfo == null || frame.getStack().isEmpty()) {
      return;
    }

    // Check for exceptional halt first
    if (frame.getExceptionalHaltReason().isPresent()) {
      LOG.trace("SELFDESTRUCT failed with halt reason: {}", frame.getExceptionalHaltReason().get());
      return; // Don't create SELFDESTRUCT entry for failed operation
    }

    final Bytes[] stack = frame.getStack().get();
    if (stack.length == 0) {
      return;
    }

    // Extract beneficiary from top of stack
    final Address beneficiary = toAddress(stack[stack.length - 1]);
    final String beneficiaryHex = beneficiary.toHexString();
    final String from = frame.getRecipient().toHexString();

    // Get the contract's balance from refunds map
    // The refund is keyed by beneficiary address and contains the originator's balance
    String value = "0x0"; // Default if not found
    if (frame.getMaybeRefunds().isPresent()) {
      final Map<Address, Wei> refunds = frame.getMaybeRefunds().get();
      final Wei balance = refunds.get(beneficiary); // Get the refund for this beneficiary
      if (balance != null) {
        value = balance.toShortHexString();
        LOG.trace("SELFDESTRUCT balance from refunds[{}]: {}", beneficiaryHex, value);
      } else {
        LOG.warn("SELFDESTRUCT: No refund found for beneficiary {}", beneficiaryHex);
      }
    } else {
      LOG.warn("SELFDESTRUCT: No refunds map available");
    }

    // Create SELFDESTRUCT entry matching Geth's format
    final CallTracerResult selfDestructCall =
        CallTracerResult.builder()
            .type("SELFDESTRUCT")
            .from(from)
            .to(beneficiaryHex)
            .gas(0L)
            .gasUsed(0L)
            .value(value)
            .input("0x")
            .build();

    currentCallInfo.builder.addCall(selfDestructCall);

    LOG.trace("SELFDESTRUCT: {} -> {} (value: {})", from, beneficiaryHex, value);
  }

  private static void handleImplicitReturn(
      final TraceFrame lastFrameAtDepth, final Map<Integer, CallInfo> depthToCallInfo) {

    final int failedDepth = lastFrameAtDepth.getDepth();
    final CallInfo childCallInfo = depthToCallInfo.get(failedDepth);

    if (childCallInfo == null) {
      LOG.debug("No matching call info for implicit return at depth {}", failedDepth);
      return;
    }

    LOG.trace(
        "Handling implicit return at depth {} after {} with halt reason: {}",
        failedDepth,
        lastFrameAtDepth.getOpcode(),
        lastFrameAtDepth.getExceptionalHaltReason().orElse(null));

    // Set error if there was an exceptional halt
    if (lastFrameAtDepth.getExceptionalHaltReason().isPresent()) {
      String errorMessage =
          mapExceptionalHaltToError(lastFrameAtDepth.getExceptionalHaltReason().get());
      childCallInfo.builder.error(errorMessage);
    }

    // Calculate gas used
    if (childCallInfo.entryFrame != null) {
      long gasProvided = childCallInfo.builder.getGas().longValue();

      // If INSUFFICIENT_GAS, all gas was consumed
      if (lastFrameAtDepth
          .getExceptionalHaltReason()
          .map(r -> "INSUFFICIENT_GAS".equals(r.name()))
          .orElse(false)) {
        childCallInfo.builder.gasUsed(gasProvided);
      }
      // Special handling for SELFDESTRUCT
      else if ("SELFDESTRUCT".equals(lastFrameAtDepth.getOpcode())) {
        // For SELFDESTRUCT, we need to calculate based on the gas before SELFDESTRUCT
        // and the gas after (which would be in the parent frame)
        // SELFDESTRUCT costs 5000 gas (or 0 if balance is 0 and beneficiary exists)

        // Get the gas cost from the SELFDESTRUCT operation itself
        long selfDestructCost = lastFrameAtDepth.getGasCost().orElse(5000L);
        long gasBeforeSelfDestruct = lastFrameAtDepth.getGasRemaining();

        // Calculate total gas used: provided - remaining before + cost of SELFDESTRUCT
        long gasUsed = gasProvided - gasBeforeSelfDestruct + selfDestructCost;

        LOG.trace(
            "SELFDESTRUCT gas calculation: provided={}, beforeSD={}, SDcost={}, used={}",
            gasProvided,
            gasBeforeSelfDestruct,
            selfDestructCost,
            gasUsed);

        childCallInfo.builder.gasUsed(gasUsed);
      } else {
        // Normal calculation for other opcodes
        long gasRemaining = lastFrameAtDepth.getGasRemaining();
        childCallInfo.builder.gasUsed(Math.max(0, gasProvided - gasRemaining));
      }
    }

    // Add to parent
    final CallInfo parentCallInfo = depthToCallInfo.get(failedDepth - 1);
    if (parentCallInfo != null) {
      parentCallInfo.builder.addCall(childCallInfo.builder.build());
    }

    // Remove from tracking
    depthToCallInfo.remove(failedDepth);
  }
}
