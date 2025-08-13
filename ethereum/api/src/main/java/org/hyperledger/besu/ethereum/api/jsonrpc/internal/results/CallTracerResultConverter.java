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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
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

      if (LOG.isTraceEnabled()) {
        LOG.trace(
            "frame[{}]: op={} depth={} nextDepth={}",
            i,
            opcode,
            frameDepth,
            (nextTrace == null ? "-" : nextTrace.getDepth()));
      }

      // Update max depth encountered
      maxDepth = Math.max(maxDepth, frameDepth);

      // Process call operations that create a new context (or a precompile effect)
      if (isCallOp(opcode) || isCreateOp(opcode)) {
        currentDepth = frameDepth;

        // Get parent call info
        final CallInfo parentCallInfo = depthToCallInfo.get(currentDepth);

        // Build the prospective child
        final CallTracerResult.Builder childBuilder =
            createCallBuilder(frame, nextTrace, opcode, parentCallInfo);

        if (LOG.isTraceEnabled()) {
          LOG.trace(
              " child draft: type={} from={} to={} gas(provided)={} input[{}]={}",
              childBuilder.getType(),
              childBuilder.getFrom(),
              childBuilder.getTo(),
              (childBuilder.getGas() == null
                  ? "null"
                  : Long.toHexString(childBuilder.getGas().longValue())),
              (frame.getInputData() == null ? 0 : frame.getInputData().size()),
              frame.getInputData().toShortHexString());
        }

        // Robust precompile detection – show which signal fired
        final boolean byTo =
            (childBuilder.getTo() != null && isPrecompileAddress(childBuilder.getTo()));
        final boolean byCost = frame.getPrecompiledGasCost().isPresent();
        if (LOG.isTraceEnabled() && isCallOp(opcode)) {
          LOG.trace(
              " precompile? byTo={} byCost={} to={} precompileGasCost={}",
              byTo,
              byCost,
              childBuilder.getTo(),
              frame.getPrecompiledGasCost().isPresent()
                  ? frame.getPrecompiledGasCost().getAsLong()
                  : "-");
        }
        // PRECOMPILE FAST-PATH: finalize immediately (no callee frame at depth+1)
        if (isCallOp(opcode) && isPrecompileAddress(childBuilder.getTo())) {
          LOG.trace("Precompile fast-path for to={}", childBuilder.getTo());
          finalizePrecompileChild(frame, nextTrace, childBuilder, parentCallInfo);
          // Do not store this child in depth tracking; it's already attached
          continue;
        }

        // If we did not enter a callee (no depth+1) and it's not a precompile, skip creating a
        // phantom child.
        if (nextTrace == null || nextTrace.getDepth() <= frameDepth) {
          // Mirrors geth callTracer behavior: do not emit children for non-executed calls.
          continue;
        }

        if (LOG.isTraceEnabled()) {
          LOG.trace(" enqueue child: depth={} (waiting for RETURN/REVERT/STOP)", currentDepth + 1);
        }
        // Normal call path: track child until its RETURN/REVERT/HALT
        final CallInfo childCallInfo = new CallInfo(childBuilder, frame);
        depthToCallInfo.put(currentDepth + 1, childCallInfo);
        // Keep maxDepth correct even if a callee frame never appears
        maxDepth = Math.max(maxDepth, currentDepth + 1);
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
    final Bytes inputData = resolveInputData(frame, nextTrace, opcode);

    // Authoritative gas (provided): callee frame start if depth+1, else derive from stack (covers
    // precompiles)
    final long gasProvided = computeGasProvided(frame, nextTrace, opcode);

    return CallTracerResult.builder()
        .type(opcode)
        .from(fromAddress)
        .to(toAddress)
        .value(getCallValue(frame, opcode))
        .gas(gasProvided)
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
    // For root transaction
    if (exitFrame.getDepth() == 0) {
      long gasUsed = entryFrame.getGasRemaining() - exitFrame.getGasRemaining();
      long gasRefund = exitFrame.getGasRefund();
      return Math.max(0, gasUsed - gasRefund);
    }

    // For nested calls with a real callee frame
    if (exitFrame.getGasRemaining() >= 0) {
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
      // Contract address is determined on successful creation; not available at the call site.
      return null;
    }

    if ("CALL".equals(opcode)
        || "CALLCODE".equals(opcode)
        || "STATICCALL".equals(opcode)
        || "DELEGATECALL".equals(opcode)) {

      return frame
          .getStack()
          .filter(stack -> stack.length > 1) // need at least two items to read stack[-2]
          .map(stack -> toAddress(stack[stack.length - 2]).toHexString())
          .orElse(null); // defensive: missing stack info
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

    // Prefer the callee frame's inputData when the next frame is the callee at depth+1.
    if (nextTrace != null
        && nextTrace.getDepth() == frame.getDepth() + 1
        && nextTrace.getInputData() != null
        && !nextTrace.getInputData().isEmpty()) {
      return nextTrace.getInputData();
    }

    if (isCallOp(opcode)) {
      // Reconstruct calldata from caller memory using inOffset/inSize from the stack.
      return frame
          .getStack()
          .map(
              stack -> {
                // CALL/CALLCODE/DELEGATECALL/STATICCALL: inOffset = -4, inSize = -3
                if (stack.length < 4) {
                  return frame.getInputData();
                }
                final int inOffset = bytesToInt(stack[stack.length - 4]);
                final int inSize = bytesToInt(stack[stack.length - 3]);
                return frame
                    .getMemory()
                    .map(memory -> extractCallDataFromMemory(memory, inOffset, inSize))
                    .orElse(frame.getInputData());
              })
          .orElse(frame.getInputData());

    } else if (isCreateOp(opcode)) {
      // For CREATE/CREATE2, "input" is the init code slice from memory (not constructor args).
      return frame
          .getStack()
          .map(
              stack -> {
                if ("CREATE".equals(opcode)) {
                  // CREATE: ..., value, offset, size  -> offset = -2, size = -1
                  if (stack.length < 2) {
                    return frame.getInputData();
                  }
                  final int offset = bytesToInt(stack[stack.length - 2]);
                  final int length = bytesToInt(stack[stack.length - 1]);
                  return frame
                      .getMemory()
                      .map(memory -> extractCallDataFromMemory(memory, offset, length))
                      .orElse(frame.getInputData());
                } else {
                  // CREATE2: ..., value, offset, size, salt -> offset = -3, size = -2
                  if (stack.length < 3) {
                    return frame.getInputData();
                  }
                  final int offset = bytesToInt(stack[stack.length - 3]);
                  final int length = bytesToInt(stack[stack.length - 2]);
                  return frame
                      .getMemory()
                      .map(memory -> extractCallDataFromMemory(memory, offset, length))
                      .orElse(frame.getInputData());
                }
              })
          .orElse(frame.getInputData());
    }

    // Default: preserve the current frame's input as a last resort.
    return frame.getInputData();
  }

  private static Bytes extractCallDataFromMemory(
      final Bytes[] memory, final int offset, final int length) {
    if (offset < 0 || length <= 0) return Bytes.EMPTY;

    // Compute word window covering [offset, offset+length)
    final int startWord = offset >>> 5; // offset / 32
    final int endWord = (offset + length + 31) >>> 5; // ceil((off+len)/32)
    final int wordCount = Math.max(0, endWord - startWord);
    if (wordCount == 0) return Bytes.EMPTY;

    // Build a contiguous buffer of whole words, zero-padding missing words.
    final MutableBytes acc = MutableBytes.create(wordCount * 32);
    for (int w = 0; w < wordCount; w++) {
      final int i = startWord + w;
      final Bytes word = (memory != null && i >= 0 && i < memory.length) ? memory[i] : Bytes32.ZERO;
      word.copyTo(acc, w * 32);
    }

    // Slice to exact [offset % 32, length]
    final int startByteInWord = offset & 31; // offset % 32
    if (startByteInWord + length <= acc.size()) {
      return acc.slice(startByteInWord, length);
    }

    // If somehow short, pad with zeros to the requested length.
    final Bytes slice =
        acc.slice(startByteInWord, Math.max(0, Math.min(length, acc.size() - startByteInWord)));
    final int missing = length - slice.size();
    if (missing <= 0) return slice;
    return Bytes.concatenate(slice, MutableBytes.create(missing)); // zero padding
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

    // If we actually enter the callee (depth+1), that frame's starting gas is authoritative.
    if (nextTrace != null && nextTrace.getDepth() == frame.getDepth() + 1) {
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
              // CALL/CALLCODE: 7 args; STATICCALL/DELEGATECALL: 6 args
              final boolean callOrCallCode = "CALL".equals(opcode) || "CALLCODE".equals(opcode);
              final int required = callOrCallCode ? 7 : 6;
              if (stack.length < required) return null;

              // gas is the leftmost of the N args, i.e. at index length - required
              final int gasIdx = stack.length - required;

              try {
                final java.math.BigInteger bi = stack[gasIdx].toUnsignedBigInteger();
                if (bi.signum() <= 0) return 0L;
                final java.math.BigInteger cap = java.math.BigInteger.valueOf(Long.MAX_VALUE);
                return (bi.compareTo(cap) > 0) ? Long.MAX_VALUE : bi.longValue();
              } catch (Exception ignore) {
                return null;
              }
            })
        .orElse(null);
  }

  /** Detect precompile addresses: 0x000...0001 through 0x000...000a (inclusive). */
  private static boolean isPrecompileAddress(final String to) {
    if (to == null) return false;
    final String s = (to.startsWith("0x") || to.startsWith("0X")) ? to.substring(2) : to;
    if (s.length() != 40) return false;

    // First 19 bytes must be zero
    for (int i = 0; i < 38; i++) {
      if (s.charAt(i) != '0') return false;
    }

    // Last byte: 0x01..0x0a (0x0a = KZG point evaluation precompile)
    final int lastByte;
    try {
      lastByte = Integer.parseInt(s.substring(38, 40), 16);
    } catch (NumberFormatException e) {
      return false;
    }
    return lastByte >= 0x01 && lastByte <= 0x0a;
  }

  /**
   * Finalize a precompile "child" call: set gas/gasUsed, compute input/output from memory, and
   * attach immediately to the parent (no depth+1 callee frame will arrive).
   */
  private static void finalizePrecompileChild(
      final TraceFrame entryFrame,
      final TraceFrame nextTrace,
      final CallTracerResult.Builder childBuilder,
      final CallInfo parentCallInfo) {

    // Ensure 'gas' (provided) present for the child; for precompiles read from stack if needed.
    if (childBuilder.getGas() == null || childBuilder.getGas().longValue() == 0L) {
      final Long g = gasFromStack(entryFrame, childBuilder.getType());
      if (g != null && g > 0L) childBuilder.gas(g);
    }

    // gasUsed: use explicit precompile cost; fallback to opcode cost
    if (entryFrame.getPrecompiledGasCost().isPresent()) {
      childBuilder.gasUsed(entryFrame.getPrecompiledGasCost().getAsLong());
    } else if (entryFrame.getGasCost().isPresent()) {
      childBuilder.gasUsed(entryFrame.getGasCost().getAsLong());
    }

    // Parse precompile id from the child "to" address (last byte).
    final int precompileId = parsePrecompileIdFromTo(childBuilder.getTo());

    // Stack at callsite (TOS on right):
    //   inOffset = -4, inSize = -3, outOffset = -2, outSize = -1
    entryFrame
        .getStack()
        .ifPresent(
            stack -> {
              if (stack.length < 4) return;

              final int inOffset = bytesToInt(stack[stack.length - 4]);
              final int inSize = bytesToInt(stack[stack.length - 3]);
              final int outOffset = bytesToInt(stack[stack.length - 2]);
              final int outSize = bytesToInt(stack[stack.length - 1]);

              // INPUT: authoritative pre-call slice (precompiles execute inline, so entry frame
              // memory is before the call)
              entryFrame
                  .getMemory()
                  .ifPresent(
                      preMem -> {
                        final Bytes in = extractCallDataFromMemory(preMem, inOffset, inSize);
                        childBuilder.input(in.toHexString());
                      });

              // OUTPUT: authoritative post-call slice.
              // For identity (0x04): len = min(inSize, outSize)
              // For fixed-size precompiles (e.g., ecrecover/sha256/ripemd160/pairing/blake2f/kzg):
              // len = min(expected, outSize)
              // For modexp (0x05): expected = modulusLen from header; len = min(expected, outSize)
              // Fallback: conservative min(outSize, inSize)
              if (nextTrace != null && nextTrace.getMemory().isPresent()) {
                final Bytes[] postMem = nextTrace.getMemory().get();
                final Bytes[] preMem = entryFrame.getMemory().orElse(null);

                final int expected =
                    expectedReturndataLenForPrecompile(precompileId, preMem, inOffset, inSize);
                final int len =
                    (expected >= 0)
                        ? Math.max(0, Math.min(expected, outSize))
                        : Math.max(0, Math.min(inSize, outSize)); // conservative fallback

                final Bytes out = extractCallDataFromMemory(postMem, outOffset, len);
                childBuilder.output(out.toHexString());
              }
            });

    // Attach immediately — there will be no callee frame at depth+1 for precompiles.
    if (parentCallInfo != null) {
      parentCallInfo.builder.addCall(childBuilder.build());
    }
  }

  /** Parse the precompile ID (last byte) from a hex 'to' address; returns -1 if unknown. */
  private static int parsePrecompileIdFromTo(final String to) {
    if (to == null) return -1;
    final String s = (to.startsWith("0x") || to.startsWith("0X")) ? to.substring(2) : to;
    if (s.length() != 40) return -1;
    try {
      return Integer.parseInt(s.substring(38, 40), 16);
    } catch (Exception ignored) {
      return -1;
    }
  }

  /**
   * Expected returndata size for a given precompile. - Identity (0x04): inSize - ModExp (0x05):
   * modulusLen from header (third 32-byte length field at inOffset+64) - Fixed-size returns: 32 or
   * 64 depending on precompile Returns -1 if unknown (use a conservative fallback).
   */
  private static int expectedReturndataLenForPrecompile(
      final int precompileId, final Bytes[] preCallMemory, final int inOffset, final int inSize) {

    switch (precompileId) {
      case 0x01: // ecrecover
      case 0x02: // sha256
      case 0x03: // ripemd160 (20 bytes but padded to 32 in returndata)
      case 0x08: // bn128 pairing
      case 0x0a: // kzg point evaluation
        return 32;

      case 0x06: // bn128 add
      case 0x07: // bn128 mul
      case 0x09: // blake2f compression
        return 64;

      case 0x04: // identity
        return Math.max(0, inSize);

      case 0x05:
        { // modexp: output length = modulusLen (third 32-byte length field)
          if (preCallMemory == null) return -1;
          // Need at least 96 bytes header; also guard against negative offset.
          if (inSize < 96 || inOffset < 0) return -1;
          try {
            final Bytes aLenWord = extractCallDataFromMemory(preCallMemory, inOffset + 64, 32);
            final java.math.BigInteger bi = aLenWord.toUnsignedBigInteger();
            if (bi.signum() < 0) return 0;
            final java.math.BigInteger cap = java.math.BigInteger.valueOf(Integer.MAX_VALUE);
            return (bi.compareTo(cap) > 0) ? Integer.MAX_VALUE : bi.intValue();
          } catch (Exception ignored) {
            return -1;
          }
        }

      default:
        return -1;
    }
  }

  /** Converts Bytes to integer safely (unsigned) and clamps to Integer.MAX_VALUE. */
  private static int bytesToInt(final Bytes bytes) {
    try {
      final java.math.BigInteger bi = bytes.toUnsignedBigInteger();
      final java.math.BigInteger max = java.math.BigInteger.valueOf(Integer.MAX_VALUE);
      return bi.compareTo(max) > 0 ? Integer.MAX_VALUE : bi.intValue();
    } catch (final Exception e) {
      return 0;
    }
  }
}
