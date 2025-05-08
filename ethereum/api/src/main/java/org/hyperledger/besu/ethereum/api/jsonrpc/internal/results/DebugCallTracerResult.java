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
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.debug.TraceFrame;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.tuweni.bytes.Bytes;

/**
 * Implementation of the callTracer result format as specified in Geth documentation:
 * https://geth.ethereum.org/docs/developers/evm-tracing/built-in-tracers#call-tracer
 */
@JsonPropertyOrder({
  "from",
  "gas",
  "gasUsed",
  "to",
  "input",
  "output",
  "error",
  "revertReason",
  "calls",
  "value",
  "type",
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DebugCallTracerResult implements DebugTracerResult {
  private final String type;
  private final String from;
  private String to;
  private String value;
  private final BigInteger gas;
  private BigInteger gasUsed;
  private final String input;
  private String output;
  private String error;
  private String revertReason;
  private final List<DebugCallTracerResult> calls;

  // Constants for gas calculations
  private static final int CODE_DEPOSIT_GAS_PER_BYTE = 200;
  private static final int CALL_STIPEND = 2300;
  private static final int EIP_150_DIVISOR = 64;

  public DebugCallTracerResult(final TransactionTrace transactionTrace) {
    final Transaction tx = transactionTrace.getTransaction();
    final TransactionProcessingResult result = transactionTrace.getResult();

    // Initialize calls list
    this.calls = new ArrayList<>();

    // Determine call type based on transaction
    if (tx.getTo().isPresent()) {
      this.type = "CALL";
      this.to = tx.getTo().map(Address::toHexString).orElse(null);
      final Bytes payload = tx.getPayload();
      this.input = payload == null ? "0x" : payload.toHexString();
    } else {
      this.type = "CREATE";
      this.to = Address.contractAddress(tx.getSender(), tx.getNonce()).toHexString();
      this.input = tx.getInit().map(Bytes::toHexString).orElse("0x");
    }

    // Set transaction details
    this.from = tx.getSender().toHexString();
    this.value = tx.getValue().toShortHexString();
    this.gas = BigInteger.valueOf(tx.getGasLimit());

    // Set result details based on success/failure
    if (result.isSuccessful()) {
      var outputBytes = result.getOutput();
      if (!outputBytes.isEmpty()) {
        this.output = outputBytes.toHexString();
      }
      // Calculate gas used from gas limit and gas remaining
      final long gasUsed = tx.getGasLimit() - result.getGasRemaining();
      this.gasUsed = BigInteger.valueOf(gasUsed);
    } else {
      this.error =
          result
              .getExceptionalHaltReason()
              .map(ExceptionalHaltReason::getDescription)
              .orElse("execution reverted");
      // Calculate gas used for failed transaction
      final long gasUsed = result.getEstimateGasUsedByTransaction();
      this.gasUsed = BigInteger.valueOf(gasUsed);
      this.revertReason =
          result.getRevertReason().filter(r -> !r.isEmpty()).map(Bytes::toHexString).orElse(null);
    }

    // Process trace frames to build call hierarchy
    processTraceFrames(transactionTrace);
  }

  // Private constructor for nested calls
  private DebugCallTracerResult(
      final String type,
      final String from,
      final String to,
      final String value,
      final BigInteger gas,
      final String input) {
    this.type = type;
    this.from = from;
    this.to = to;
    this.value = value;
    this.gas = gas;
    this.input = input;
    this.calls = new ArrayList<>();
    this.gasUsed = null; // Explicitly set to null for nested calls
  }

  /**
   * Process trace frames to build a hierarchical call tree structure.
   *
   * @param transactionTrace the transaction trace containing frames
   */
  /**
   * Process trace frames to build a hierarchical call tree structure.
   *
   * @param transactionTrace the transaction trace containing frames
   */
  private void processTraceFrames(final TransactionTrace transactionTrace) {
    final List<TraceFrame> frames = transactionTrace.getTraceFrames();
    if (frames.isEmpty()) {
      return;
    }

    // Track active calls by depth to build the call hierarchy
    Map<Integer, DebugCallTracerResult> callsByDepth = new HashMap<>();
    callsByDepth.put(0, this);

    // Track call stack for resolving returns
    Deque<CallStackEntry> callStack = new ArrayDeque<>();

    // Track cumulative gas cost (similar to FlatTraceGenerator)
    long cumulativeGasCost = 0;

    for (int i = 0; i < frames.size(); i++) {
      final TraceFrame frame = frames.get(i);
      final String opcodeString = frame.getOpcode();
      final int depth = frame.getDepth();

      // Update cumulative gas cost
      cumulativeGasCost += frame.getGasCost().orElse(0L) + frame.getPrecompiledGasCost().orElse(0L);

      // Get parent call at previous depth (or root if at depth 1)
      final DebugCallTracerResult parentCall = callsByDepth.getOrDefault(depth - 1, this);

      // Handle different operation types
      if (isCallOp(opcodeString)) {
        // Handle CALL, CALLCODE, DELEGATECALL, STATICCALL
        Optional<TraceFrame> nextFrame = getNextFrameAtDepth(frames, i, depth + 1);
        if (nextFrame.isEmpty() || frame.getDepth() >= nextFrame.get().getDepth()) {
          // Skip calls that don't execute
          continue;
        }

        handleCall(frames, i, frame, nextFrame.get(), opcodeString, depth, parentCall, callsByDepth, callStack, cumulativeGasCost);
      } else if (isCreateOp(opcodeString)) {
        // Handle CREATE, CREATE2
        Optional<TraceFrame> nextFrame = getNextFrameAtDepth(frames, i, depth + 1);
        if (nextFrame.isEmpty() || frame.getDepth() >= nextFrame.get().getDepth()) {
          // Skip creates that don't execute
          continue;
        }

        handleCreate(frames, i, frame, nextFrame.get(), opcodeString, depth, parentCall, callsByDepth, callStack, cumulativeGasCost);
      } else if ("SELFDESTRUCT".equals(opcodeString)) {
        if (frame.getExceptionalHaltReason().isPresent()) {
          // If there's an exceptional halt reason, handle it as a call
          Optional<TraceFrame> nextFrame = getNextFrameAtDepth(frames, i, depth + 1);
          if (nextFrame.isPresent() && frame.getDepth() < nextFrame.get().getDepth()) {
            handleCall(frames, i, frame, nextFrame.get(), opcodeString, depth, parentCall, callsByDepth, callStack, cumulativeGasCost);
          }
        } else {
          // Otherwise, handle it as a self-destruct
          handleSelfDestruct(frame, depth, callsByDepth);
        }
      } else if ("CALLDATALOAD".equals(opcodeString)) {
        handleCallDataLoad(frame, depth, callsByDepth);
      } else if ("RETURN".equals(opcodeString) || "REVERT".equals(opcodeString) || "STOP".equals(opcodeString)) {
        handleReturn(frame, opcodeString, depth, callStack, callsByDepth);
      } else if (frame.getExceptionalHaltReason().isPresent()) {
        handleExceptionalHalt(frame, depth, callStack, callsByDepth);
      }
    }

    // Handle any remaining calls in the stack (could happen if trace is incomplete)
    while (!callStack.isEmpty()) {
      final CallStackEntry entry = callStack.pop();
      final DebugCallTracerResult call = entry.call();

      // Mark as failed with unknown error
      call.error = "execution incomplete";

      // Use all available gas as gasUsed
      call.gasUsed = call.gas;
    }
  }

  /**
   * Handle call operations (CALL, STATICCALL, DELEGATECALL, CALLCODE).
   *
   * @param frames the list of trace frames
   * @param frameIndex the current frame index
   * @param frame the current trace frame
   * @param nextFrame the next frame at the call's depth
   * @param opcodeString the opcode string
   * @param depth the current depth
   * @param parentCall the parent call result
   * @param callsByDepth map of calls by depth
   * @param callStack the call stack
   * @param cumulativeGasCost the cumulative gas cost
   */
  private void handleCall(
          final List<TraceFrame> frames,
          final int frameIndex,
          final TraceFrame frame,
          final TraceFrame nextFrame,
          final String opcodeString,
          final int depth,
          final DebugCallTracerResult parentCall,
          final Map<Integer, DebugCallTracerResult> callsByDepth,
          final Deque<CallStackEntry> callStack,
          final long cumulativeGasCost) {

    // Determine from address (caller)
    final String from = parentCall.to;

    // Determine to address (callee)
    final String to;
    // For regular calls, get the recipient from the stack if available
    if (frame.getStack().isPresent() && frame.getStack().get().length > 1) {
      Bytes[] stack = frame.getStack().get();
      to = Address.wrap(stack[stack.length - 2]).toHexString();
    } else {
      // Fallback to next frame's recipient
      to = nextFrame.getRecipient() != null ? nextFrame.getRecipient().toHexString() : null;
    }

    // Determine value
    final String value;
    if ("DELEGATECALL".equals(opcodeString) || "STATICCALL".equals(opcodeString)) {
      // These call types don't transfer value
      value = "0x0";
    } else {
      // Use the value from the frame if available, otherwise default to 0
      final Wei frameValue = frame.getValue();
      value = frameValue != null ? frameValue.toShortHexString() : "0x0";
    }

    // Determine gas - use the same approach as FlatTraceGenerator
    // In FlatTraceGenerator, gas is set to the gas remaining in the next frame
    BigInteger callGas = BigInteger.valueOf(nextFrame.getGasRemaining());

    // Determine input data - same as FlatTraceGenerator
    final Bytes inputData = nextFrame.getInputData();
    final String input = inputData != null ? inputData.toHexString() : "0x";

    // Create the call result
    DebugCallTracerResult childCall = new DebugCallTracerResult(
            opcodeString, from, to, value, callGas, input);

    // Add to parent's calls list
    parentCall.calls.add(childCall);

    // Register in depth map
    callsByDepth.put(depth + 1, childCall);

    // Push to call stack with index for gas calculation
    CallStackEntry entry = new CallStackEntry(frameIndex, depth + 1, childCall, frame.getGasRemaining(), cumulativeGasCost);

    // Set gas stipend for value-transferring CALL operations
    if ("CALL".equals(opcodeString) && !Wei.ZERO.equals(frame.getValue())) {
      entry = entry.withGasStipend(CALL_STIPEND);
    }

    callStack.push(entry);

    // Debug output
    System.out.println("Handled " + opcodeString + " operation:");
    System.out.println("  From: " + from);
    System.out.println("  To: " + to);
    System.out.println("  Gas: " + callGas + " (0x" + callGas.toString(16) + ")");
    System.out.println("  Value: " + value);
    System.out.println("  Frame gas remaining: " + frame.getGasRemaining());
    System.out.println("  Next frame gas remaining: " + nextFrame.getGasRemaining());
    System.out.println("  Cumulative gas cost: " + cumulativeGasCost);
  }

  /**
   * Handle create operations (CREATE, CREATE2).
   *
   * @param frames the list of trace frames
   * @param frameIndex the current frame index
   * @param frame the current trace frame
   * @param nextFrame the next frame at the call's depth
   * @param opcodeString the opcode string
   * @param depth the current depth
   * @param parentCall the parent call result
   * @param callsByDepth map of calls by depth
   * @param callStack the call stack
   * @param cumulativeGasCost the cumulative gas cost
   */
  private void handleCreate(
          final List<TraceFrame> frames,
          final int frameIndex,
          final TraceFrame frame,
          final TraceFrame nextFrame,
          final String opcodeString,
          final int depth,
          final DebugCallTracerResult parentCall,
          final Map<Integer, DebugCallTracerResult> callsByDepth,
          final Deque<CallStackEntry> callStack,
          final long cumulativeGasCost) {

    // Determine from address (creator) - similar to calculateCallingAddress in FlatTraceGenerator
    final String from = parentCall.to;

    // Determine gas - use computeGas from FlatTraceGenerator
    long callGas = computeGas(frame, Optional.of(nextFrame));

    // Determine value - from the next frame's value
    final String value = nextFrame.getValue() != null ?
            nextFrame.getValue().toShortHexString() : "0x0";

    // Determine to address - will be set later, but initialize with recipient if available
    final String to = nextFrame.getRecipient() != null ?
            nextFrame.getRecipient().toHexString() : null;

    // Determine input data (initialization code)
    final String input;
    if (frame.getMaybeCode().isPresent()) {
      input = frame.getMaybeCode().get().getBytes().toHexString();
    } else {
      final Bytes inputData = nextFrame.getInputData();
      input = inputData != null ? inputData.toHexString() : "0x";
    }

    // Create the call result
    DebugCallTracerResult childCall = new DebugCallTracerResult(
            opcodeString, from, to, value, BigInteger.valueOf(callGas), input);

    // Add to parent's calls list
    parentCall.calls.add(childCall);

    // Register in depth map
    callsByDepth.put(depth + 1, childCall);

    // Push to call stack with index for gas calculation
    CallStackEntry entry = new CallStackEntry(frameIndex, depth + 1, childCall, frame.getGasRemaining(), cumulativeGasCost);

    // Mark this as a create operation (similar to setCreateOp in FlatTraceGenerator)
    // We'll use this information in calculateGasUsed to handle code deposit costs
    entry = entry.withCreateOp(true);

    callStack.push(entry);

    // Debug output
    System.out.println("Handled " + opcodeString + " operation:");
    System.out.println("  From: " + from);
    System.out.println("  To: " + to);
    System.out.println("  Gas: " + callGas + " (0x" + Long.toHexString(callGas) + ")");
    System.out.println("  Value: " + value);
    System.out.println("  Input: " + (input.length() > 100 ? input.substring(0, 100) + "..." : input));
    System.out.println("  Frame gas remaining: " + frame.getGasRemaining());
    System.out.println("  Next frame gas remaining: " + nextFrame.getGasRemaining());
    System.out.println("  Computed gas: " + callGas);
    System.out.println("  Cumulative gas cost: " + cumulativeGasCost);
  }

  /**
   * Compute gas for a call based on FlatTraceGenerator's computeGas method.
   */
  private long computeGas(final TraceFrame frame, final Optional<TraceFrame> nextFrame) {
    if (frame.getGasCost().isPresent()) {
      final long gasNeeded = frame.getGasCost().getAsLong();
      final long currentGas = frame.getGasRemaining();
      if (currentGas >= gasNeeded) {
        final long gasRemaining = currentGas - gasNeeded;
        return gasRemaining - Math.floorDiv(gasRemaining, EIP_150_DIVISOR);
      }
    }
    return nextFrame.map(TraceFrame::getGasRemaining).orElse(0L);
  }

  /**
   * Handle SELFDESTRUCT operations.
   *
   * @param frame the current trace frame
   * @param depth the current depth
   * @param callsByDepth map of calls by depth
   */
  private void handleSelfDestruct(
          final TraceFrame frame,
          final int depth,
          final Map<Integer, DebugCallTracerResult> callsByDepth) {

    // Get the current call context
    final DebugCallTracerResult currentCall = callsByDepth.get(depth);
    if (currentCall == null) {
      return;
    }

    // Calculate gas used for the current call
    long gasUsed = 0;
    if (currentCall.gas != null) {
      gasUsed = currentCall.gas.longValue() - frame.getGasRemaining() + frame.getGasCost().orElse(0L);
    }

    // Set gas used on the current call
    currentCall.gasUsed = BigInteger.valueOf(Math.max(0, gasUsed));

    // Get the refund address from the stack
    final Bytes[] stack = frame.getStack().orElseThrow();
    final Address refundAddress = Address.wrap(stack[stack.length - 1]);

    // Determine the from address (the self-destructing contract)
    final String from;
    if (frame.getRecipient() != null) {
      from = frame.getRecipient().toHexString();
    } else {
      from = currentCall.to;
    }

    // Determine the balance being transferred
    Wei balance = Wei.ZERO;
    if (frame.getMaybeRefunds().isPresent()) {
      balance = frame.getMaybeRefunds().get().getOrDefault(refundAddress, Wei.ZERO);
    }

    // Create a SELFDESTRUCT call result
    final DebugCallTracerResult selfDestructCall = new DebugCallTracerResult(
            "SELFDESTRUCT",  // Keep as SELFDESTRUCT to match Geth's callTracer
            from,  // from address is the self-destructing contract
            refundAddress.toHexString(),  // to address is the refund address
            balance.toShortHexString(),  // value is the balance being transferred
            BigInteger.ZERO,  // no gas allocation needed
            "0x"  // no input data
    );

    // Set the output to null since SELFDESTRUCT doesn't return data
    selfDestructCall.output = null;

    // Calculate gas used for the SELFDESTRUCT operation
    selfDestructCall.gasUsed = BigInteger.valueOf(frame.getGasCost().orElse(0L));

    // Add to the current call's calls list
    currentCall.calls.add(selfDestructCall);

    // Debug output
    System.out.println("Handled SELFDESTRUCT operation:");
    System.out.println("  From: " + from);
    System.out.println("  To (refund address): " + refundAddress.toHexString());
    System.out.println("  Balance transferred: " + balance.toShortHexString());
    System.out.println("  Gas used: " + selfDestructCall.gasUsed);
  }



  /**
   * Calculate the gas used for a call and set it on the call result.
   *
   * @param entry the call stack entry
   * @param currentFrame the current frame (RETURN/REVERT)
   * @param opcodeString the opcode string
   */
  private void calculateGasUsed(
          final CallStackEntry entry, final TraceFrame currentFrame, final String opcodeString) {

    final DebugCallTracerResult call = entry.call();

    // Check for precompiled contract first
    if (currentFrame.getPrecompiledGasCost().isPresent()) {
      // Use precompiled contract gas cost if available
      call.gasUsed = BigInteger.valueOf(currentFrame.getPrecompiledGasCost().getAsLong());
      System.out.println("  Using precompiled gas cost: " + call.gasUsed);
      return;
    }

    // Follow FlatTraceGenerator's computeGasUsed logic
    long gasRemainingBeforeProcessed;
    long gasRemainingAfterProcessed;
    long gasRefund = 0;

    // For sub-calls (not the top-level transaction)
    gasRemainingBeforeProcessed = entry.initialGas();
    gasRemainingAfterProcessed = currentFrame.getGasRemaining();

    // Calculate gas refund
    gasRefund = currentFrame.getGasRefund();

    // Calculate gas used
    long gasUsedValue = gasRemainingBeforeProcessed - gasRemainingAfterProcessed + gasRefund;
    BigInteger gasUsed = BigInteger.valueOf(gasUsedValue);

    // Debug output
    System.out.println("Calculating gasUsed using FlatTraceGenerator logic:");
    System.out.println("  Gas remaining before: " + gasRemainingBeforeProcessed);
    System.out.println("  Gas remaining after: " + gasRemainingAfterProcessed);
    System.out.println("  Gas refund: " + gasRefund);
    System.out.println("  Calculated gasUsed: " + gasUsed + " (0x" + gasUsed.toString(16) + ")");

    call.gasUsed = gasUsed;
  }

  /**
   * Find the next trace frame at a specific depth, starting from a given index.
   *
   * @param frames the list of trace frames
   * @param startIndex the starting index to search from
   * @param targetDepth the depth to search for
   * @return the next trace frame at the target depth, or empty if not found
   */
  private Optional<TraceFrame> getNextFrameAtDepth(
      final List<TraceFrame> frames, final int startIndex, final int targetDepth) {
    for (int i = startIndex + 1; i < frames.size(); i++) {
      TraceFrame frame = frames.get(i);
      if (frame.getDepth() == targetDepth) {
        return Optional.of(frame);
      } else if (frame.getDepth() < targetDepth) {
        // If we encounter a frame with lower depth, the call didn't execute
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  /**
   * Create a call result object based on the trace frame and opcode.
   *
   * @param frame the current trace frame
   * @param opcodeString the opcode string
   * @param parentCall the parent call result
   * @param nextFrame the next frame at the call's depth
   * @param cumulativeGasCost the cumulative gas cost so far
   * @return a new call result object
   */
  private DebugCallTracerResult createCallResult(
          final TraceFrame frame,
          final String opcodeString,
          final DebugCallTracerResult parentCall,
          final TraceFrame nextFrame,
          final long cumulativeGasCost) {

    // Determine from address (caller)
    final String from = parentCall.to;

    // Determine to address (callee)
    final String to;
    if (isCallOp(opcodeString) && !isCreateOp(opcodeString)) {
      // For regular calls, get the recipient from the stack if available
      if (frame.getStack().isPresent() && frame.getStack().get().length > 1) {
        Bytes[] stack = frame.getStack().get();
        to = Address.wrap(stack[stack.length - 2]).toHexString();
      } else {
        // Fallback to next frame's recipient
        to = nextFrame.getRecipient() != null ? nextFrame.getRecipient().toHexString() : null;
      }
    } else if (isCreateOp(opcodeString)) {
      // For CREATE/CREATE2, we'll set the address later when we have the actual address
      if (frame.getRecipient() != null) {
        to = frame.getRecipient().toHexString();
      } else {
        to = null;
      }
    } else {
      // For other operations, use next frame's recipient
      to = nextFrame.getRecipient() != null ? nextFrame.getRecipient().toHexString() : null;
    }

    // Determine value
    final String value;
    if ("DELEGATECALL".equals(opcodeString) || "STATICCALL".equals(opcodeString)) {
      // These call types don't transfer value
      value = "0x0";
    } else {
      // Use the value from the frame if available, otherwise default to 0
      final Wei frameValue = frame.getValue();
      value = frameValue != null ? frameValue.toShortHexString() : "0x0";
    }

    // Determine gas - use the same approach as FlatTraceGenerator
    // In FlatTraceGenerator, gas is set to the gas remaining in the next frame
    BigInteger callGas = BigInteger.valueOf(nextFrame.getGasRemaining());

    // Determine input data - same as FlatTraceGenerator
    final Bytes inputData = nextFrame.getInputData();
    final String input = inputData != null ? inputData.toHexString() : "0x";

    // Create the call result
    DebugCallTracerResult childCall = new DebugCallTracerResult(
            opcodeString, from, to, value, callGas, input);

    // Debug output
    System.out.println("Creating call using FlatTraceGenerator logic:");
    System.out.println("  Type: " + opcodeString);
    System.out.println("  From: " + from);
    System.out.println("  To: " + to);
    System.out.println("  Gas: " + callGas + " (0x" + callGas.toString(16) + ")");
    System.out.println("  Next frame gas remaining: " + nextFrame.getGasRemaining());

    return childCall;
  }

  private boolean isCallOp(final String opcodeString) {
    return "CALL".equals(opcodeString)
            || "DELEGATECALL".equals(opcodeString)
            || "CALLCODE".equals(opcodeString)
            || "STATICCALL".equals(opcodeString);
  }

  private boolean isCreateOp(final String opcodeString) {
    return "CREATE".equals(opcodeString) || "CREATE2".equals(opcodeString);
  }

  @JsonGetter("type")
  public String getType() {
    return type;
  }

  @JsonGetter("from")
  public String getFrom() {
    return from;
  }

  @JsonGetter("to")
  public String getTo() {
    return to;
  }

  @JsonGetter("value")
  public String getValue() {
    return value;
  }

  @JsonGetter("gas")
  public String getGas() {
    return "0x" + gas.toString(16);
  }

  @JsonGetter("gasUsed")
  public String getGasUsed() {
    if (gasUsed == null) {
      return null;
    }
    String hexString = gasUsed.toString(16);
    return hexString.isEmpty() ? "0x0" : "0x" + hexString;
  }

  @JsonGetter("input")
  public String getInput() {
    return input;
  }

  @JsonGetter("output")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public String getOutput() {
    return output;
  }

  @JsonGetter("error")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public String getError() {
    return error;
  }

  @JsonGetter("revertReason")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public String getRevertReason() {
    return revertReason;
  }

  @JsonGetter("calls")
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public List<DebugCallTracerResult> getCalls() {
    return calls;
  }

  /** Helper record to track call stack entries for resolving returns. */
  @JsonIgnoreType
  private record CallStackEntry(
          int startFrameIndex,
          int depth,
          DebugCallTracerResult call,
          long initialGas,
          long cumulativeGasCost,
          long gasStipend,
          boolean isCreateOp) {

    // Compact constructor for default values
    public CallStackEntry(
            final int startFrameIndex,
            final int depth,
            final DebugCallTracerResult call,
            final long initialGas,
            final long cumulativeGasCost) {
      this(startFrameIndex, depth, call, initialGas, cumulativeGasCost, 0, false);
    }

    // Method to create a new instance with updated gasStipend
    public CallStackEntry withGasStipend(final long newGasStipend) {
      return new CallStackEntry(startFrameIndex, depth, call, initialGas, cumulativeGasCost, newGasStipend, isCreateOp);
    }

    // Method to create a new instance with updated isCreateOp
    public CallStackEntry withCreateOp(final boolean newIsCreateOp) {
      return new CallStackEntry(startFrameIndex, depth, call, initialGas, cumulativeGasCost, gasStipend, newIsCreateOp);
    }
  }
}
