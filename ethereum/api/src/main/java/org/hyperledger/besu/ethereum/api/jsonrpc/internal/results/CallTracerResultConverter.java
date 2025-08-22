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
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.CallTracerHelper.shortHex;
import static org.hyperledger.besu.evm.internal.Words.toAddress;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.debug.TraceFrame;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;

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
          finalizePrecompileChild(frame, childBuilder, parentCallInfo);
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

    // Authoritative gas (provided): callee frame start if depth+1, else placeholder value
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

    // --- 3) I/O computation (no callee frame) ---
    if (entryFrame.isPrecompile()) {
      childBuilder.input(entryFrame.getPrecompileInputData().map(Bytes::toHexString).orElse("0x"));
      childBuilder.output(
          entryFrame.getPrecompileOutputData().map(Bytes::toHexString).orElse("0x"));
    }

    // --- 4) Attach immediately — precompiles have no callee frame ---
    if (parentCallInfo != null) {
      parentCallInfo.builder.addCall(childBuilder.build());
    }
  }
}
