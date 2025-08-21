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
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.CallTracerHelper.isPrecompileAddress;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.CallTracerHelper.parsePrecompileIdFromTo;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.CallTracerHelper.shortHex;
import static org.hyperledger.besu.evm.internal.Words.toAddress;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.debug.TraceFrame;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

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
 *   <li>Return operations (RETURN, REVERT, STOP, SELFDESTRUCT)
 * </ul>
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
        "Building call hierarchy from {} trace frames", transactionTrace.getTraceFrames().size());
    return buildCallHierarchyFromFrames(transactionTrace);
  }

  private static CallTracerResult buildCallHierarchyFromFrames(final TransactionTrace trace) {
    final List<TraceFrame> frames = trace.getTraceFrames();
    final Transaction tx = trace.getTransaction();

    // Track calls by depth
    final Map<Integer, CallInfo> depthToCallInfo = new HashMap<>();

    // Initialize the root call
    final CallTracerResult.Builder rootBuilder = initializeRootBuilder(tx);

    int currentDepth = 0;
    final CallInfo rootInfo = new CallInfo(rootBuilder, null);
    depthToCallInfo.put(0, rootInfo);

    // Process all frames
    for (int i = 0; i < frames.size(); i++) {
      final TraceFrame nextTrace = i < frames.size() - 1 ? frames.get(i + 1) : null;
      final TraceFrame frame = frames.get(i);
      final String opcode = frame.getOpcode();
      final int frameDepth = frame.getDepth();

      // Process call operations that create a new context (or a precompile effect)
      if (isCallOp(opcode) || isCreateOp(opcode)) {
        currentDepth = frameDepth;

        // Get parent call info
        final CallInfo parentCallInfo = depthToCallInfo.get(currentDepth);

        // Build the prospective child
        final CallTracerResult.Builder childBuilder =
            createCallBuilder(frame, nextTrace, opcode, parentCallInfo);

        if (LOG.isTraceEnabled()) {
          final Bytes fIn = frame.getInputData();
          LOG.trace(
              " child draft: type={} from={} to={} gas(provided)={} input[{}]={}",
              childBuilder.getType(),
              childBuilder.getFrom(),
              childBuilder.getTo(),
              (childBuilder.getGas() == null ? "null" : hexN(childBuilder.getGas().longValue())),
              (fIn == null ? 0 : fIn.size()),
              (fIn == null ? "null" : fIn.toShortHexString()));
        }

        // Derive 'to' if missing and we have a stack
        String toForCheck = childBuilder.getTo();
        if (toForCheck == null) {
          frame
              .getStack()
              .ifPresent(
                  stack -> {
                    if (stack.length > 1) {
                      String derived = toAddress(stack[stack.length - 2]).toHexString();
                      childBuilder.to(derived);
                    }
                  });
          toForCheck = childBuilder.getTo();
        }

        // Robust precompile detection – show which signal fired
        final boolean byTo = toForCheck != null && isPrecompileAddress(toForCheck);
        final boolean byCost = frame.getPrecompiledGasCost().isPresent();
        if (LOG.isTraceEnabled() && isCallOp(opcode)) {
          LOG.trace(
              " precompile? byTo={} byCost={} to={} precompileGasCost={}",
              byTo,
              byCost,
              toForCheck,
              byCost ? frame.getPrecompiledGasCost().getAsLong() : "-");
        }

        // PRECOMPILE FAST-PATH: finalize immediately (no callee frame at depth+1)
        if (isCallOp(opcode) && (byTo || byCost)) {
          LOG.trace("Precompile fast-path for to={}", toForCheck);
          finalizePrecompileChild(frame, nextTrace, childBuilder, parentCallInfo);
          // Do not store this child in depth tracking; it's already attached
          continue;
        }

        // If we did not enter a callee (no depth+1) and it's not a precompile, skip creating a
        // phantom child.
        if (nextTrace == null || nextTrace.getDepth() <= frameDepth) {
          if (LOG.isTraceEnabled()) {
            LOG.trace(
                " skip: no callee frame opened (nextDepth={} <= frameDepth={})",
                (nextTrace == null ? null : nextTrace.getDepth()),
                frameDepth);
          }
          // Mirrors geth callTracer behavior: do not emit children for non-executed calls.
          continue;
        }

        if (LOG.isTraceEnabled()) {
          LOG.trace(" enqueue child: depth={} (waiting for RETURN/REVERT/STOP)", currentDepth + 1);
        }
        // Normal call path: track child until its RETURN/REVERT/HALT
        final CallInfo childCallInfo = new CallInfo(childBuilder, frame);

        // Store call info for this depth
        depthToCallInfo.put(currentDepth + 1, childCallInfo);
      }
      // Process return operations that exit a context
      else if (isReturnOp(opcode) || isRevertOp(opcode) || isHaltOp(opcode)) {
        currentDepth = frameDepth;

        // Get child call info
        final CallInfo childCallInfo = depthToCallInfo.get(currentDepth);
        if (LOG.isTraceEnabled() && childCallInfo != null) {
          LOG.trace(
              " return: depth={} type={} -> computing gasUsed",
              currentDepth,
              childCallInfo.builder.getType());
        }
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
    }

    // Process any remaining calls that didn't have explicit return frames
    processRemainingCalls(depthToCallInfo);

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

      frame
          .getRevertReason()
          .ifPresent(
              reason -> {
                // Try to decode the revert reason as a human-readable string
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

    // Authoritative gas (provided): callee frame start if depth+1, else derive (covers precompiles)
    final long gasProvided = computeGasProvided(frame, nextTrace, opcode);

    // Detect precompile up front so we can defer input/output
    final boolean looksLikePrecompile =
        isCallOp(opcode)
            && (isPrecompileAddress(toAddress) || frame.getPrecompiledGasCost().isPresent());

    if (LOG.isTraceEnabled()) {
      LOG.trace(
          "createCallBuilder: op={} from={} to={} depth={} nextDepth={} gas(prov)={} stackPresent={} precompile={}",
          opcode,
          fromAddress,
          toAddress,
          frame.getDepth(),
          (nextTrace == null ? "-" : nextTrace.getDepth()),
          hexN(gasProvided),
          frame.getStack().isPresent(),
          looksLikePrecompile);
    }

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
    } else {
      // Precompiles: defer both input & output — finalizePrecompileChild(...) will set them once
      LOG.trace("  precompile detected at createCallBuilder -> deferring input/output");
      builder.input("0x");
    }

    return builder;
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
    if ("STATICCALL".equals(opcode)) {
      return null; // omit field to match Geth
    }
    // For DELEGATECALL, value is always 0, and geth includes the field
    if ("DELEGATECALL".equals(opcode)) {
      return "0x0";
    }
    return frame.getValue().toShortHexString(); // CALL, CALLCODE, CREATE, CREATE2
  }

  private static long calculateGasUsed(
      final CallInfo callInfo, final TraceFrame entryFrame, final TraceFrame exitFrame) {
    if (LOG.isTraceEnabled()) {
      LOG.trace(
          "calculateGasUsed: exitDepth={} entryGasRem={} exitGasRem={} precompileCost={} gasCost={}",
          exitFrame.getDepth(),
          entryFrame.getGasRemaining(),
          exitFrame.getGasRemaining(),
          entryFrame.getPrecompiledGasCost().isPresent()
              ? entryFrame.getPrecompiledGasCost().getAsLong()
              : "-",
          entryFrame.getGasCost().isPresent() ? entryFrame.getGasCost().getAsLong() : "-");
    }

    // For root transaction
    if (exitFrame.getDepth() == 0) {
      // Root transaction: simply calculate difference and account for refunds
      long gasUsed = entryFrame.getGasRemaining() - exitFrame.getGasRemaining();
      long gasRefund = exitFrame.getGasRefund();
      return Math.max(0, gasUsed - gasRefund);
    }

    // For nested calls with a real callee frame
    if (exitFrame.getGasRemaining() >= 0) {
      // Normal case: gas before call - gas after call = gas used
      return callInfo.builder.getGas().longValue() - exitFrame.getGasRemaining();
    }
    // For precompiled contracts (no callee frame => use precompiled cost if present)
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
    /*
     * Stack layouts (TOS on the right):
     *   CALL/CALLCODE:     gas, to, value, inOffset, inSize, outOffset, outSize
     *   DELEGATECALL:      gas, to, inOffset, inSize, outOffset, outSize
     *   STATICCALL:        gas, to, inOffset, inSize, outOffset, outSize
     *
     * For all of the above, the callee "to" address is the same stack position: -2 from TOS.
     * (i.e., stack[stack.length - 2]).
     *
     * Note:
     * - For DELEGATECALL, do NOT use frame.getRecipient() — that is the *current* contract (proxy).
     *   Geth’s callTracer reports the *target implementation* taken from the stack.
     * - For CREATE/CREATE2, the "to" is not known at call-site (computed later), so we return null.
     */

    if ("CREATE".equals(opcode) || "CREATE2".equals(opcode)) {
      return null;
    }
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
    if (nextTrace != null
        && nextTrace.getDepth() == frame.getDepth() + 1
        && nextTrace.getInputData() != null
        && !nextTrace.getInputData().isEmpty()) {
      LOG.trace(
          "  using callee-frame input (authoritative) [{}]={}",
          nextTrace.getInputData().size(),
          shortHex(nextTrace.getInputData(), 16));
      return nextTrace.getInputData();
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
  private record CallInfo(CallTracerResult.Builder builder, TraceFrame entryFrame) {}

  /** Compute the gas provided to the child call. */
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
              : "using stack gas (precompile/non-entered)"));
    }

    // If we actually enter the callee (depth+1), that frame's starting gas is authoritative.
    if (hasCalleeStart) {
      final long g = nextTrace.getGasRemaining();
      return (g >= 0) ? g : 0L;
    }

    // Otherwise (precompiles / non-executed call), pull the call gas from the stack.
    if (isCallOp(opcode)) {
      final Long fromStack = gasFromStack(frame, opcode);
      return (fromStack != null && fromStack > 0L) ? fromStack : 0L;
    }

    // CREATE/CREATE2 normally have a callee frame; if not, we have no better signal.
    return 0L;
  }

  /** Read gas argument for a call-like op from the stack. */
  private static Long gasFromStack(final TraceFrame frame, final String opcode) {
    return frame
        .getStack()
        .map(
            stack -> {
              final boolean callOrCallCode = "CALL".equals(opcode) || "CALLCODE".equals(opcode);
              final int required = callOrCallCode ? 7 : 6;
              if (stack.length < required) return null;

              // Primary: assume TOS at the END (tail)
              final int gasIdxTail =
                  stack.length - required; // e.g. STATIC/DELEGATE: len-6; CALL: len-7
              // Alternate: assume TOS at index 0
              final int gasIdxHead = required - 1; // e.g. STATIC/DELEGATE: 5; CALL: 6

              java.math.BigInteger candidate = java.math.BigInteger.ZERO;

              try {
                if (gasIdxTail >= 0 && gasIdxTail < stack.length && stack[gasIdxTail] != null) {
                  candidate = stack[gasIdxTail].toUnsignedBigInteger();
                }
              } catch (Throwable __ignore) {
                // ignore
              }

              if (candidate.signum() == 0) {
                try {
                  if (gasIdxHead >= 0 && gasIdxHead < stack.length && stack[gasIdxHead] != null) {
                    candidate = stack[gasIdxHead].toUnsignedBigInteger();
                  }
                } catch (Throwable __ignore) {
                  // ignore
                }
              }

              // Last resort: probe a few plausible positions
              if (candidate.signum() == 0) {
                final int n = stack.length;
                final int[] probe = new int[] {n - 6, n - 7, n - 5, 5, 6, 7};
                for (int idx : probe) {
                  if (idx >= 0 && idx < n && stack[idx] != null) {
                    try {
                      final java.math.BigInteger bi = stack[idx].toUnsignedBigInteger();
                      if (bi.signum() > 0) {
                        candidate = bi;
                        break;
                      }
                    } catch (Throwable __ignore) {
                      // ignore
                    }
                  }
                }
              }

              if (candidate.signum() == 0) return null;
              if (candidate.compareTo(java.math.BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                return Long.MAX_VALUE;
              }
              return candidate.longValue();
            })
        .orElse(null);
  }

  /**
   * Finalize a precompile "child" call: set gas/gasUsed, compute input/output from memory, and
   * attach immediately to the parent (no depth+1 callee frame will arrive).
   *
   * <p>Behavior:
   *
   * <ul>
   *   <li><b>Execution model:</b> Precompiles execute inline; no separate callee frame is emitted.
   *   <li><b>Gas (provided):</b> Forwarded gas per EIP-150: forwarded = min(gasArg, (gasRemaining -
   *       opCost) * 63/64). If gasArg can’t be read (or is zero), fall back to the cap.
   *   <li><b>Gas used:</b> Prefer entryFrame.getPrecompiledGasCost(); fallback to opcode gasCost.
   *   <li><b>Input:</b> Pre-call memory slice using (inOffset, inSize) from the caller’s stack.
   *   <li><b>Output:</b>
   *       <ul>
   *         <li>Identity (0x04): returndata equals input (match Geth callTracer).
   *         <li>Fixed-size precompiles: use expected length (e.g. 32 or 64) clamped by outSize.
   *         <li>ModExp (0x05): expected length = modulus length from header (3rd length word).
   *         <li>Otherwise: conservative fallback min(inSize, outSize).
   *       </ul>
   * </ul>
   */
  private static void finalizePrecompileChild(
      final TraceFrame entryFrame,
      final TraceFrame nextTrace,
      final CallTracerResult.Builder childBuilder,
      final CallInfo parentCallInfo) {

    LOG.trace(
        "finalizePrecompileChild: to={} type={} precompileGasCost={} gasCost={}",
        childBuilder.getTo(),
        childBuilder.getType(),
        entryFrame.getPrecompiledGasCost().isPresent()
            ? entryFrame.getPrecompiledGasCost().getAsLong()
            : "-",
        entryFrame.getGasCost().isPresent() ? entryFrame.getGasCost().getAsLong() : "-");

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

    final int precompileId = parsePrecompileIdFromTo(childBuilder.getTo());

    // --- 3) I/O computation (no callee frame) ---
    entryFrame
        .getStack()
        .ifPresent(
            stack -> {
              if (stack.length < 4) return;
              logStackDebug(stack);

              final Bytes[] preMem = entryFrame.getMemory().orElse(null);
              final Bytes[] postMem =
                  (nextTrace != null && nextTrace.getMemory().isPresent())
                      ? nextTrace.getMemory().get()
                      : null;

              // 3a) Success flag (orientation-agnostic)
              boolean success = true;
              if (nextTrace != null && nextTrace.getStack().isPresent()) {
                final Bytes[] postStack = nextTrace.getStack().get();
                if (postStack.length >= 1) {
                  final int tail = bytesToInt(postStack[postStack.length - 1]);
                  final int head = bytesToInt(postStack[0]);
                  if (tail == 0 || tail == 1) success = (tail != 0);
                  else if (head == 0 || head == 1) success = (head != 0);
                }
              }

              // 3b) Build both IO-candidates without using gas or 'to'
              final int n = stack.length;
              final CallTracerHelper.IoCoords tail =
                  new CallTracerHelper.IoCoords(
                      bytesToInt(stack[n - 4]), // inOffset
                      bytesToInt(stack[n - 3]), // inSize
                      bytesToInt(stack[n - 2]), // outOffset
                      bytesToInt(stack[n - 1])); // outSize

              final CallTracerHelper.IoCoords head =
                  new CallTracerHelper.IoCoords(
                      bytesToInt(stack[3]), // inOffset
                      bytesToInt(stack[2]), // inSize
                      bytesToInt(stack[1]), // outOffset
                      bytesToInt(stack[0])); // outSize

              // 3c) Score by memory+spec (gas-agnostic): input must fit preMem; output must fit
              // postMem
              final int preBytes = (preMem == null) ? Integer.MAX_VALUE : preMem.length * 32;
              final int postBytes = (postMem == null) ? Integer.MAX_VALUE : postMem.length * 32;

              final boolean effSuccess = success;
              java.util.function.ToIntFunction<CallTracerHelper.IoCoords> score =
                  c -> {
                    // input must be sane and fit
                    if (c.inOffset() < 0 || c.inSize() < 0) return Integer.MIN_VALUE;
                    long inEnd = (long) c.inOffset() + (long) c.inSize();
                    if (inEnd > preBytes) return Integer.MIN_VALUE;

                    // expected output length per precompile (identity=inSize, sha256=32, etc.)
                    int expected =
                        effSuccess
                            ? expectedReturndataLenForPrecompile(
                                precompileId, preMem, c.inOffset(), c.inSize())
                            : 0;
                    if (expected < 0) expected = c.inSize(); // conservative fallback when unknown
                    final int take = Math.max(0, Math.min(c.outSize(), expected));

                    // if we can see post-mem, output slice must also fit
                    if (postMem != null) {
                      if (c.outOffset() < 0) return Integer.MIN_VALUE;
                      long outEnd = (long) c.outOffset() + (long) take;
                      if (outEnd > postBytes) return Integer.MIN_VALUE;
                    }

                    // soft preferences
                    int s = 0;
                    if ((c.inOffset() & 31) == 0) s += 2;
                    if ((c.outOffset() & 31) == 0) s += 1;
                    if (c.inSize() <= 4096) s += 1;
                    if (postMem != null) s += 3; // we could verify output fits
                    return s;
                  };

              final int st = score.applyAsInt(tail);
              final int sh = score.applyAsInt(head);
              final CallTracerHelper.IoCoords io =
                  (sh > st) ? head : (st > sh ? tail : head); // prefer HEAD on tie

              LOG.trace(
                  "  IO coords (mem+spec): inOffset={} inSize={} outOffset={} outSize={}",
                  io.inOffset(),
                  io.inSize(),
                  io.outOffset(),
                  io.outSize());

              // 3d) INPUT from pre-call memory
              String inputHex = "0x";
              if (preMem != null) {
                final Bytes in = extractCallDataFromMemory(preMem, io.inOffset(), io.inSize());
                inputHex = in.toHexString();
              }
              childBuilder.input(inputHex);

              // 3e) OUTPUT
              if (precompileId == 0x04) {
                // Identity: echo input exactly like Geth
                childBuilder.output(inputHex);
              } else if (postMem != null) {
                int expected =
                    success
                        ? expectedReturndataLenForPrecompile(
                            precompileId, preMem, io.inOffset(), io.inSize())
                        : 0;
                if (expected < 0) expected = io.inSize(); // conservative fallback
                final int take = Math.max(0, Math.min(io.outSize(), expected));
                final Bytes out =
                    (take == 0)
                        ? Bytes.EMPTY
                        : extractCallDataFromMemory(postMem, io.outOffset(), take);
                childBuilder.output(out.toHexString());
              } else if (nextTrace != null
                  && nextTrace.getOutputData() != null
                  && !nextTrace.getOutputData().isEmpty()) {
                // Fallback: clamp raw returndata if post-mem not exposed (rare)
                int expected =
                    success
                        ? expectedReturndataLenForPrecompile(
                            precompileId, preMem, io.inOffset(), io.inSize())
                        : 0;
                if (expected < 0) expected = io.inSize();
                final int take = Math.max(0, Math.min(io.outSize(), expected));
                final Bytes rd = nextTrace.getOutputData();
                final Bytes out = rd.slice(0, Math.min(take, rd.size()));
                childBuilder.output(out.toHexString());
              } else {
                childBuilder.output("0x");
              }
            });

    // --- 4) Attach immediately — precompiles have no callee frame ---
    if (parentCallInfo != null) {
      parentCallInfo.builder.addCall(childBuilder.build());
    }
  }

  private static void logStackDebug(final Bytes[] stack) {
    LOG.trace(
        " . Stack debug - length: {}, contents: {}",
        stack.length,
        Arrays.stream(stack).map(Bytes::toHexString).collect(Collectors.toList()));
    LOG.trace(" . Stack[0] (potential TOS-head): {}", stack[0].toHexString());
    LOG.trace(" . Stack[length-1] (potential TOS-tail): {}", stack[stack.length - 1].toHexString());

    // For STATICCALL, show the specific indices we're accessing
    if (stack.length >= 6) {
      LOG.trace(" . STATICCALL stack positions (if tail-TOS):");
      LOG.trace(" .   gas [-6]: {}", stack[stack.length - 6].toHexString());
      LOG.trace(" .   to [-5]: {}", stack[stack.length - 5].toHexString());
      LOG.trace(" .   inOffset [-4]: {}", stack[stack.length - 4].toHexString());
      LOG.trace(" .   inSize [-3]: {}", stack[stack.length - 3].toHexString());
      LOG.trace(" .   outOffset [-2]: {}", stack[stack.length - 2].toHexString());
      LOG.trace(" .   outSize [-1]: {}", stack[stack.length - 1].toHexString());

      LOG.trace(" . STATICCALL stack positions (if head-TOS):");
      LOG.trace(" .   gas [5]: {}", stack[5].toHexString());
      LOG.trace(" .   to [4]: {}", stack[4].toHexString());
      LOG.trace(" .   inOffset [3]: {}", stack[3].toHexString());
      LOG.trace(" .   inSize [2]: {}", stack[2].toHexString());
      LOG.trace(" .   outOffset [1]: {}", stack[1].toHexString());
      LOG.trace(" .   outSize [0]: {}", stack[0].toHexString());
    }
  }

  /**
   * Expected returndata size by precompile. -1 if unknown. - Identity (0x04): inSize - ModExp
   * (0x05): modulusLen from header (third 32-byte length field at inOffset+64) - Fixed-size
   * returns: 32 bytes: ecrecover(0x01), sha256(0x02), ripemd160(0x03 padded), pairing(0x08),
   * kzg(0x0a) 64 bytes: bn128 add(0x06), bn128 mul(0x07), blake2f(0x09)
   */
  private static int expectedReturndataLenForPrecompile(
      final int precompileId, final Bytes[] preCallMemory, final int inOffset, final int inSize) {

    switch (precompileId) {
      // 32-byte returns
      case 0x01: // ecrecover
      case 0x02: // sha256
      case 0x03: // ripemd160 (padded to 32)
      case 0x08: // bn128 pairing
      case 0x0a: // kzg point evaluation
        return 32;

      // 64-byte returns
      case 0x06: // bn128 add
      case 0x07: // bn128 mul
      case 0x09: // blake2f compression
        return 64;

      // identity: echo input
      case 0x04:
        return Math.max(0, inSize);

      // modexp: output len = modulusLen (third 32-byte length field)
      case 0x05:
        if (preCallMemory == null || inSize < 96 || inOffset < 0) return -1;
        try {
          final Bytes aLenWord = extractCallDataFromMemory(preCallMemory, inOffset + 64, 32);
          final java.math.BigInteger bi = aLenWord.toUnsignedBigInteger();
          if (bi.signum() < 0) return 0;
          final java.math.BigInteger cap = java.math.BigInteger.valueOf(Integer.MAX_VALUE);
          return (bi.compareTo(cap) > 0) ? Integer.MAX_VALUE : bi.intValue();
        } catch (Exception ignored) {
          return -1;
        }

      default:
        return -1;
    }
  }
}
