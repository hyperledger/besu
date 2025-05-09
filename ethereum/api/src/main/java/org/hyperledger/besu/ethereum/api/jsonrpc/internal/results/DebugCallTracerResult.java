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
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.Words;

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
  private final GasCalculator gasCalculator;

  // Constants for gas calculations
  private static final int CODE_DEPOSIT_GAS_PER_BYTE = 200;
  private static final int CALL_STIPEND = 2300;
  private static final int EIP_150_DIVISOR = 64;

  /**
   * Constructor for the DebugCallTracerResult.
   *
   * @param transactionTrace the transaction trace containing the call information
   * @param gasCalculator the gas calculator to use for gas calculations
   */
  public DebugCallTracerResult(
      final TransactionTrace transactionTrace, final GasCalculator gasCalculator) {
    this.gasCalculator = gasCalculator;
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
      final String input,
      final GasCalculator gasCalculator) {
    this.type = type;
    this.from = from;
    this.to = to;
    this.value = value;
    this.gas = gas;
    this.input = input;
    this.calls = new ArrayList<>();
    this.gasUsed = null; // Explicitly set to null for nested calls
    this.gasCalculator = gasCalculator;
  }

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
    Map<Integer, DebugCallTracerResult> activeCallsByDepth = new HashMap<>();
    activeCallsByDepth.put(0, this); // Root call at depth 0

    // Track call stack for resolving returns
    Deque<CallStackEntry> callStack = new ArrayDeque<>();
    callStack.push(new CallStackEntry(0, 0, this, 0, 0)); // Push root call

    // Track cumulative gas cost
    long cumulativeGasCost = 0;

    for (int i = 0; i < frames.size(); i++) {
      final TraceFrame frame = frames.get(i);
      final String opcodeString = frame.getOpcode();
      final int depth = frame.getDepth();

      // Update cumulative gas cost
      cumulativeGasCost += frame.getGasCost().orElse(0L) + frame.getPrecompiledGasCost().orElse(0L);

      // Get the current active call at this depth
      final DebugCallTracerResult currentCall = activeCallsByDepth.get(depth);
      if (currentCall == null
          && !isReturnOp(opcodeString)
          && !frame.getExceptionalHaltReason().isPresent()) {
        // Skip frames where we don't have a current call context and it's not a return operation
        continue;
      }

      // Handle different operation types
      if (isCallOp(opcodeString)) {
        // Handle CALL, CALLCODE, DELEGATECALL, STATICCALL
        Optional<TraceFrame> nextFrame = getNextFrameAtDepth(frames, i, depth + 1);
        if (nextFrame.isEmpty() || frame.getDepth() <= nextFrame.get().getDepth()) {
          // Skip calls that don't execute
          continue;
        }

        // Determine from address (caller)
        final String from = currentCall.to;

        // Determine to address (callee)
        final String to;
        if (frame.getStack().isPresent() && frame.getStack().get().length > 1) {
          Bytes[] stack = frame.getStack().get();
          to = Words.toAddress(stack[stack.length - 2]).toString();
        } else {
          to =
              nextFrame.get().getRecipient() != null
                  ? nextFrame.get().getRecipient().toHexString()
                  : null;
        }

        // Determine value
        final String value;
        if ("DELEGATECALL".equals(opcodeString) || "STATICCALL".equals(opcodeString)) {
          value = "0x0";
        } else {
          final Wei frameValue = frame.getValue();
          value = frameValue != null ? frameValue.toShortHexString() : "0x0";
        }

        // Determine gas
        BigInteger callGas = BigInteger.valueOf(nextFrame.get().getGasRemaining());

        // Determine input data
        final Bytes inputData = nextFrame.get().getInputData();
        final String input = inputData != null ? inputData.toHexString() : "0x";

        // Create the call result
        DebugCallTracerResult childCall =
            new DebugCallTracerResult(opcodeString, from, to, value, callGas, input, gasCalculator);

        // Add to parent's calls list - this is the key change for nesting
        currentCall.calls.add(childCall);

        // Register in active calls map
        activeCallsByDepth.put(depth + 1, childCall);

        // Push to call stack with index for gas calculation
        CallStackEntry entry =
            new CallStackEntry(i, depth + 1, childCall, frame.getGasRemaining(), cumulativeGasCost);

        // Set gas stipend for value-transferring CALL operations
        if ("CALL".equals(opcodeString) && !Wei.ZERO.equals(frame.getValue())) {
          entry = entry.withGasStipend(CALL_STIPEND);
        }

        callStack.push(entry);

        // Debug output
        System.out.println("Handled " + opcodeString + " operation at depth " + depth);
        System.out.println("  From: " + from);
        System.out.println("  To: " + to);
        System.out.println("  Added to parent: " + currentCall.from + " -> " + currentCall.to);

      } else if (isCreateOp(opcodeString)) {
        // Handle CREATE, CREATE2
        Optional<TraceFrame> nextFrame = getNextFrameAtDepth(frames, i, depth + 1);
        if (nextFrame.isEmpty() || nextFrame.get().getDepth() <= frame.getDepth()) {
          // Skip calls that don't execute
          continue;
        }

        // Determine from address (creator)
        final String from = currentCall.to;

        // Determine gas
        long callGas = computeGas(frame, Optional.of(nextFrame.get()));

        // Determine value
        final String value =
            nextFrame.get().getValue() != null
                ? nextFrame.get().getValue().toShortHexString()
                : "0x0";

        // Determine to address - will be set later
        final String to =
            nextFrame.get().getRecipient() != null
                ? nextFrame.get().getRecipient().toHexString()
                : null;

        // Determine input data (initialization code)
        final String input;
        if (frame.getMaybeCode().isPresent()) {
          input = frame.getMaybeCode().get().getBytes().toHexString();
        } else {
          final Bytes inputData = nextFrame.get().getInputData();
          input = inputData != null ? inputData.toHexString() : "0x";
        }

        // Create the call result
        DebugCallTracerResult childCall =
            new DebugCallTracerResult(
                opcodeString, from, to, value, BigInteger.valueOf(callGas), input, gasCalculator);

        // Add to parent's calls list - this is the key change for nesting
        currentCall.calls.add(childCall);

        // Register in active calls map
        activeCallsByDepth.put(depth + 1, childCall);

        // Push to call stack
        CallStackEntry entry =
            new CallStackEntry(i, depth + 1, childCall, frame.getGasRemaining(), cumulativeGasCost);
        entry = entry.withCreateOp(true);
        callStack.push(entry);

        // Debug output
        System.out.println("Handled " + opcodeString + " operation at depth " + depth);
        System.out.println("  From: " + from);
        System.out.println("  Added to parent: " + currentCall.from + " -> " + currentCall.to);

      } else if ("RETURN".equals(opcodeString) || "STOP".equals(opcodeString)) {
        // Handle return operations
        if (callStack.isEmpty() || callStack.peek().depth() != depth) {
          continue;
        }

        final CallStackEntry entry = callStack.pop();
        final DebugCallTracerResult call = entry.call();

        // Set value from the frame if available
        if (frame.getValue() != null) {
          call.value = frame.getValue().toShortHexString();
        }

        // Set output data
        final Bytes outputData = frame.getOutputData();
        call.output = outputData != null ? outputData.toHexString() : null;

        // For CREATE operations, update the contract address
        if (("CREATE".equals(call.type) || "CREATE2".equals(call.type))
            && frame.getRecipient() != null) {
          call.to = frame.getRecipient().toHexString();
        }

        // Calculate gas used
        calculateGasUsed(entry, frame, opcodeString);

        // Remove from active calls tracking
        activeCallsByDepth.remove(depth);

        // Debug output
        System.out.println("Handled " + opcodeString + " operation at depth " + depth);
        System.out.println("  Call completed: " + call.from + " -> " + call.to);
        System.out.println("  Call stack size after pop: " + callStack.size());

      } else if ("REVERT".equals(opcodeString)) {
        // Handle revert operations
        if (callStack.isEmpty() || callStack.peek().depth() != depth) {
          continue;
        }

        final CallStackEntry entry = callStack.pop();
        final DebugCallTracerResult call = entry.call();

        // Set error message
        call.error = "execution reverted";
        call.output = null;

        // Get revert reason if available
        final Optional<Bytes> revertReason = frame.getRevertReason();
        if (revertReason.isPresent() && !revertReason.get().isEmpty()) {
          call.revertReason = revertReason.get().toHexString();
        }

        // Calculate gas used
        calculateGasUsed(entry, frame, "REVERT");

        // Remove from active calls tracking
        activeCallsByDepth.remove(depth);

        // Debug output
        System.out.println("Handled REVERT operation at depth " + depth);
        System.out.println("  Call reverted: " + call.from + " -> " + call.to);
        System.out.println("  Call stack size after pop: " + callStack.size());

      } else if ("SELFDESTRUCT".equals(opcodeString)) {
        // Handle SELFDESTRUCT operations
        if (currentCall == null) {
          continue;
        }

        try {
          // Get the refund address from the stack
          if (!frame.getStack().isPresent() || frame.getStack().get().length == 0) {
            continue;
          }

          final Bytes[] stack = frame.getStack().get();
          final Address refundAddress = Address.wrap(stack[stack.length - 1]);

          // Determine the from address (the self-destructing contract)
          final String from =
              frame.getRecipient() != null ? frame.getRecipient().toHexString() : currentCall.to;

          // Determine the balance being transferred
          Wei balance = Wei.ZERO;
          if (frame.getMaybeRefunds().isPresent()) {
            balance = frame.getMaybeRefunds().get().getOrDefault(refundAddress, Wei.ZERO);
          }

          // Create a SELFDESTRUCT call result
          final DebugCallTracerResult selfDestructCall =
              new DebugCallTracerResult(
                  "SELFDESTRUCT",
                  from,
                  refundAddress.toHexString(),
                  balance.toShortHexString(),
                  BigInteger.ZERO,
                  "0x",
                  gasCalculator);

          // Set the output to null since SELFDESTRUCT doesn't return data
          selfDestructCall.output = null;

          // Calculate gas used for the SELFDESTRUCT operation
          selfDestructCall.gasUsed = BigInteger.valueOf(frame.getGasCost().orElse(0L));

          // Add to the current call's calls list - this maintains the nested structure
          currentCall.calls.add(selfDestructCall);

          // Debug output
          System.out.println("Handled SELFDESTRUCT operation at depth " + depth);
          System.out.println("  From: " + from);
          System.out.println("  To (refund address): " + refundAddress.toHexString());
          System.out.println("  Balance transferred: " + balance.toShortHexString());
          System.out.println("  Gas used: " + selfDestructCall.gasUsed);
          System.out.println("  Added to parent: " + currentCall.from + " -> " + currentCall.to);
        } catch (Exception e) {
          System.out.println("Error handling SELFDESTRUCT: " + e.getMessage());
        }
      } else if ("CALLDATALOAD".equals(opcodeString)) {
        // Handle CALLDATALOAD operations
        if (currentCall != null && frame.getValue() != null) {
          // Update the value based on the frame's value
          if (!frame.getValue().isZero()) {
            currentCall.value = frame.getValue().toShortHexString();
          } else {
            currentCall.value = "0x0";
          }

          // Debug output
          System.out.println("Handled CALLDATALOAD operation at depth " + depth);
          System.out.println("  Updated value: " + currentCall.value);
        }
      } else if (frame.getExceptionalHaltReason().isPresent()) {
        // Handle exceptional halts
        if (callStack.isEmpty() || callStack.peek().depth() != depth) {
          continue;
        }

        final CallStackEntry entry = callStack.pop();
        final DebugCallTracerResult call = entry.call();

        // Set error message based on exceptional halt reason
        call.error =
            frame
                .getExceptionalHaltReason()
                .map(
                    exceptionalHaltReason -> {
                      if (exceptionalHaltReason
                          .name()
                          .equals(ExceptionalHaltReason.INVALID_OPERATION.name())) {
                        return ExceptionalHaltReason.INVALID_OPERATION.getDescription();
                      } else {
                        return exceptionalHaltReason.getDescription();
                      }
                    })
                .orElse("execution failed");

        // Set value from the frame if available
        if (frame.getValue() != null) {
          call.value = frame.getValue().toShortHexString();
        }

        // Set output to null for exceptional halts
        call.output = null;

        // Calculate gas used
        calculateGasUsed(entry, frame, frame.getOpcode());

        // Remove from active calls tracking
        activeCallsByDepth.remove(depth);

        // Debug output
        System.out.println("Handled exceptional halt at depth " + depth);
        System.out.println("  Call failed: " + call.from + " -> " + call.to);
        System.out.println("  Error: " + call.error);
        System.out.println("  Call stack size after pop: " + callStack.size());
      }
    }

    // Handle any remaining calls in the stack (could happen if trace is incomplete)
    while (!callStack.isEmpty()) {
      final CallStackEntry entry = callStack.pop();
      final DebugCallTracerResult call = entry.call();

      // Skip the root call
      if (entry.depth() == 0) {
        continue;
      }

      // Mark as failed with unknown error
      call.error = "execution incomplete";

      // Use all available gas as gasUsed
      call.gasUsed = call.gas;

      // Debug output
      System.out.println("Handled incomplete call: " + call.from + " -> " + call.to);
    }
  }

  // Helper method to check if an opcode is a return operation
  private boolean isReturnOp(final String opcodeString) {
    return "RETURN".equals(opcodeString)
        || "STOP".equals(opcodeString)
        || "REVERT".equals(opcodeString);
  }

  /**
   * Compute gas for a call based on Geth's callTracer approach.
   *
   * @param frame the current trace frame
   * @param nextFrame the next frame at the call's depth
   * @return the computed gas amount
   */
  private long computeGas(final TraceFrame frame, final Optional<TraceFrame> nextFrame) {
    // For CREATE operations in Geth's callTracer, the gas value represents
    // the initial gas allocated to the call

    if (frame.getGasCost().isPresent()) {
      // Get the gas cost of the operation itself
      final long gasCost = frame.getGasCost().getAsLong();

      // In Geth's callTracer, for CREATE operations, the gas shown is the gas
      // allocated to the child call, which is the gas remaining after the CREATE
      // operation minus the EIP-150 adjustment
      final long currentGas = frame.getGasRemaining();

      if (currentGas >= gasCost) {
        // Calculate gas allocated to the child call
        // This follows EIP-150 rules where child calls get at most all but 1/64 of remaining gas
        final long gasForCall = currentGas - gasCost;
        final long eip150Adjustment = Math.floorDiv(gasForCall, EIP_150_DIVISOR);
        return gasForCall - eip150Adjustment;
      }
    }

    // If we can't calculate it, use the next frame's gas remaining as a fallback
    // This is the gas available at the start of the child call
    return nextFrame.map(TraceFrame::getGasRemaining).orElse(0L);
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

    // For nested calls, we need to calculate gas used differently than for the root call
    BigInteger gasUsed;

    // Get gas remaining at the start of the call
    BigInteger gasRemainingBeforeProcessed = BigInteger.valueOf(entry.initialGas());

    // Get gas remaining at the end of the call
    BigInteger gasRemainingAfterProcessed = BigInteger.valueOf(currentFrame.getGasRemaining());

    // Get gas refund if any
    BigInteger gasRefund = BigInteger.valueOf(currentFrame.getGasRefund());

    // Calculate basic gas used
    gasUsed = gasRemainingBeforeProcessed.subtract(gasRemainingAfterProcessed);

    // Add gas refund if any
    if (gasRefund.compareTo(BigInteger.ZERO) > 0) {
      gasUsed = gasUsed.add(gasRefund);
    }

    // Ensure gas used is not negative
    gasUsed = gasUsed.max(BigInteger.ZERO);

    // For CREATE operations, add code deposit cost on successful return
    if (entry.isCreateOp()
        && ("CREATE".equals(call.type) || "CREATE2".equals(call.type))
        && "RETURN".equals(opcodeString)) {
      final Bytes outputData = currentFrame.getOutputData();
      if (outputData != null && !outputData.isEmpty()) {
        // Use the CODE_DEPOSIT_GAS_PER_BYTE constant
        long codeDepositCost = (long) outputData.size() * CODE_DEPOSIT_GAS_PER_BYTE;
        gasUsed = gasUsed.add(BigInteger.valueOf(codeDepositCost));
        System.out.println("  Added code deposit cost: " + codeDepositCost);
      }
    }

    // Adjust for call stipends if applicable
    if (entry.gasStipend() > 0 && ("RETURN".equals(opcodeString) || "STOP".equals(opcodeString))) {
      // Only subtract stipend if the call was successful
      gasUsed = gasUsed.subtract(BigInteger.valueOf(entry.gasStipend())).max(BigInteger.ZERO);
      System.out.println("  Subtracted gas stipend: " + entry.gasStipend());
    }

    // For failed calls, we need to ensure all gas is consumed
    if (call.error != null && !("REVERT".equals(opcodeString))) {
      // For errors other than REVERT, all gas should be consumed
      gasUsed = call.gas;
      System.out.println("  Using all gas for failed call: " + gasUsed);
    }

    // For REVERT, we need to ensure we're not counting the gas that was refunded
    if ("REVERT".equals(opcodeString)) {
      // For REVERT, we need to calculate the actual gas used before the revert
      gasUsed = gasRemainingBeforeProcessed.subtract(gasRemainingAfterProcessed);
      System.out.println("  Calculated gas used for REVERT: " + gasUsed);
    }

    // Ensure gas used doesn't exceed the allocated gas
    if (call.gas != null) {
      gasUsed = gasUsed.min(call.gas);
    }

    // Debug output
    System.out.println("Calculating gasUsed for " + opcodeString + ":");
    System.out.println("  Gas remaining before: " + gasRemainingBeforeProcessed);
    System.out.println("  Gas remaining after: " + gasRemainingAfterProcessed);
    System.out.println("  Gas refund: " + gasRefund);
    System.out.println("  Is create op: " + entry.isCreateOp());
    System.out.println("  Gas stipend: " + entry.gasStipend());
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
    // Skip the current frame and search for the next frame at the target depth
    for (int i = startIndex + 1; i < frames.size(); i++) {
      TraceFrame frame = frames.get(i);

      // If we find a frame at the target depth, return it
      if (frame.getDepth() == targetDepth) {
        return Optional.of(frame);
      }

      // If we find a frame with depth less than the target depth,
      // then we've exited the context where a frame at the target depth would be found
      if (frame.getDepth() < targetDepth) {
        return Optional.empty();
      }
    }

    // If we reach the end of the frames without finding a match
    return Optional.empty();
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
      return new CallStackEntry(
          startFrameIndex, depth, call, initialGas, cumulativeGasCost, newGasStipend, isCreateOp);
    }

    // Method to create a new instance with updated isCreateOp
    public CallStackEntry withCreateOp(final boolean newIsCreateOp) {
      return new CallStackEntry(
          startFrameIndex, depth, call, initialGas, cumulativeGasCost, gasStipend, newIsCreateOp);
    }
  }
}
