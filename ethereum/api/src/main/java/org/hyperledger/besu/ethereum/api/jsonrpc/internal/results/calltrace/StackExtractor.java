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

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.calltrace.CallTracerHelper.bytesToInt;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.calltrace.CallTracerHelper.extractCallDataFromMemory;
import static org.hyperledger.besu.evm.internal.Words.toAddress;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.tracing.TraceFrame;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts data from EVM stack and memory for call tracing.
 *
 * <p>This class encapsulates the logic for extracting various pieces of information from the EVM
 * stack during call operations. It handles the different stack layouts for CALL, CREATE, CREATE2,
 * and SELFDESTRUCT operations.
 *
 * <p>Stack layouts handled:
 *
 * <ul>
 *   <li>CALL/CALLCODE: gas, to, value, inOffset, inSize, outOffset, outSize
 *   <li>DELEGATECALL/STATICCALL: gas, to, inOffset, inSize, outOffset, outSize
 *   <li>CREATE: value, offset, size
 *   <li>CREATE2: value, offset, size, salt
 *   <li>SELFDESTRUCT: beneficiary
 * </ul>
 */
public final class StackExtractor {
  private static final Logger LOG = LoggerFactory.getLogger(StackExtractor.class);

  // Stack position constants for CALL operations
  // Stack layout (from top): gas, to, value, inOffset, inSize, outOffset, outSize
  private static final int CALL_STACK_VALUE_OFFSET = 3; // 3rd from top
  private static final int CALL_STACK_TO_OFFSET = 2; // 2nd from top
  private static final int CALL_STACK_IN_OFFSET_POS = 4; // 4th from top
  private static final int CALL_STACK_IN_SIZE_POS = 3; // 3rd from top (for size, after value)

  // Stack position constants for CREATE operations
  // CREATE stack layout: value, offset, size
  private static final int CREATE_STACK_OFFSET_POS = 2; // 2nd from top
  private static final int CREATE_STACK_SIZE_POS = 1; // 1st from top (top of stack)

  // CREATE2 stack layout: value, offset, size, salt
  private static final int CREATE2_STACK_OFFSET_POS = 3; // 3rd from top
  private static final int CREATE2_STACK_SIZE_POS = 2; // 2nd from top

  private static final String ZERO_VALUE = "0x0";

  private StackExtractor() {
    // Utility class - prevent instantiation
  }

  /**
   * Extracts the value (wei) being transferred in a CALL or CALLCODE operation.
   *
   * @param frame the trace frame containing the stack
   * @return the value as a hex string, or "0x0" if not available
   */
  public static String extractCallValue(final TraceFrame frame) {
    return frame
        .getStack()
        .filter(stack -> stack.length >= CALL_STACK_VALUE_OFFSET)
        .map(stack -> Wei.wrap(stack[stack.length - CALL_STACK_VALUE_OFFSET]).toShortHexString())
        .orElse(ZERO_VALUE);
  }

  /**
   * Extracts the target address from a CALL operation's stack.
   *
   * @param frame the trace frame containing the stack
   * @return the target address as a hex string, or null if not available
   */
  public static String extractCallToAddress(final TraceFrame frame) {
    return frame
        .getStack()
        .filter(
            stack ->
                stack.length >= CALL_STACK_TO_OFFSET
                    && stack[stack.length - CALL_STACK_TO_OFFSET] != null)
        .map(stack -> toAddress(stack[stack.length - CALL_STACK_TO_OFFSET]).toHexString())
        .orElse(null);
  }

  /**
   * Extracts the input data for a CALL operation from memory.
   *
   * <p>This method reads the inOffset and inSize from the stack, then extracts the corresponding
   * bytes from memory.
   *
   * @param frame the trace frame containing stack and memory
   * @return the input data bytes, or the frame's input data as fallback
   */
  public static Bytes extractCallInputFromMemory(final TraceFrame frame) {
    return frame
        .getStack()
        .filter(stack -> stack.length >= CALL_STACK_IN_OFFSET_POS)
        .map(
            stack -> {
              int inOffset = bytesToInt(stack[stack.length - CALL_STACK_IN_OFFSET_POS]);
              int inSize = bytesToInt(stack[stack.length - CALL_STACK_IN_SIZE_POS]);
              return extractFromMemory(frame, inOffset, inSize);
            })
        .orElse(frame.getInputData());
  }

  /**
   * Extracts the initialization code for a CREATE or CREATE2 operation.
   *
   * @param frame the trace frame containing stack and memory
   * @param opcode the opcode ("CREATE" or "CREATE2")
   * @return the initialization code bytes, or empty if extraction fails
   */
  public static Bytes extractCreateInitCode(final TraceFrame frame, final String opcode) {
    // Try getMaybeCode() first - this is the preferred source
    if (frame.getMaybeCode().isPresent()) {
      return frame.getMaybeCode().get().getBytes();
    }

    // Fallback to memory extraction
    if (LOG.isTraceEnabled()) {
      LOG.trace(
          "Falling back to memory extraction for CREATE input data at depth {}", frame.getDepth());
    }

    return frame
        .getStack()
        .map(stack -> extractCreateInitCodeFromStack(frame, stack, opcode))
        .orElse(Bytes.EMPTY);
  }

  /**
   * Extracts the beneficiary address from a SELFDESTRUCT operation's stack.
   *
   * @param frame the trace frame containing the stack
   * @return the beneficiary address, or empty if not available
   */
  public static Optional<Address> extractSelfDestructBeneficiary(final TraceFrame frame) {
    return frame
        .getStack()
        .filter(stack -> stack.length > 0)
        .map(stack -> toAddress(stack[stack.length - 1]));
  }

  /**
   * Extracts the value being transferred in a SELFDESTRUCT operation.
   *
   * <p>The value is determined by looking at the refunds map for the beneficiary address.
   *
   * @param frame the trace frame containing refund information
   * @param beneficiary the address receiving the funds
   * @return the value as a hex string, or "0x0" if not available
   */
  public static String extractSelfDestructValue(final TraceFrame frame, final Address beneficiary) {
    return frame
        .getMaybeRefunds()
        .map(refunds -> refunds.get(beneficiary))
        .map(Wei::toShortHexString)
        .orElse(ZERO_VALUE);
  }

  /**
   * Resolves the input data for a call operation, preferring the callee's input data.
   *
   * @param frame the current trace frame
   * @param nextTrace the next trace frame (callee's first frame), may be null
   * @param opcode the opcode of the operation
   * @return the resolved input data
   */
  public static Bytes resolveCallInputData(
      final TraceFrame frame, final TraceFrame nextTrace, final String opcode) {

    if (OpcodeCategory.isCreateOp(opcode)) {
      return extractCreateInitCode(frame, opcode);
    }

    // Prefer callee frame's input data for calls
    if (nextTrace != null && nextTrace.getDepth() == frame.getDepth() + 1) {
      return nextTrace.getInputData() != null ? nextTrace.getInputData() : Bytes.EMPTY;
    }

    // Fallback to memory extraction for calls
    if (OpcodeCategory.isCallOp(opcode)) {
      if (LOG.isTraceEnabled()) {
        LOG.trace(
            "Falling back to memory extraction for CALL input data at depth {}", frame.getDepth());
      }
      return extractCallInputFromMemory(frame);
    }

    return frame.getInputData();
  }

  private static Bytes extractCreateInitCodeFromStack(
      final TraceFrame frame, final Bytes[] stack, final String opcode) {

    if ("CREATE".equals(opcode)) {
      if (stack.length < CREATE_STACK_OFFSET_POS) {
        return Bytes.EMPTY;
      }

      int offset = bytesToInt(stack[stack.length - CREATE_STACK_OFFSET_POS]);
      int length = bytesToInt(stack[stack.length - CREATE_STACK_SIZE_POS]);

      return extractFromMemory(frame, offset, length);
    } else { // CREATE2
      if (stack.length < CREATE2_STACK_OFFSET_POS) {
        return Bytes.EMPTY;
      }

      int offset = bytesToInt(stack[stack.length - CREATE2_STACK_OFFSET_POS]);
      int length = bytesToInt(stack[stack.length - CREATE2_STACK_SIZE_POS]);

      return extractFromMemory(frame, offset, length);
    }
  }

  private static Bytes extractFromMemory(
      final TraceFrame frame, final int offset, final int length) {
    if (length == 0) {
      return Bytes.EMPTY;
    }

    return frame
        .getMemory()
        .map(memory -> extractCallDataFromMemory(memory, offset, length))
        .orElse(Bytes.EMPTY);
  }
}
