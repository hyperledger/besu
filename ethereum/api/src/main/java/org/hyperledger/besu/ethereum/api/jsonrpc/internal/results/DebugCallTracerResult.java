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

    // Track cumulative gas cost
    long cumulativeGasCost = 0;

    for (int i = 0; i < frames.size(); i++) {
      final TraceFrame frame = frames.get(i);
      final String opcodeString = frame.getOpcode();
      final int depth = frame.getDepth();

      // Update cumulative gas cost
      cumulativeGasCost += frame.getGasCost().orElse(0L) + frame.getPrecompiledGasCost().orElse(0L);

      // Get parent call at previous depth (or root if at depth 1)
      final DebugCallTracerResult parentCall = callsByDepth.getOrDefault(depth - 1, this);

      // Handle call operations
      if (isCallOp(opcodeString)) {
        // Get next frame at this depth to determine if call was executed
        Optional<TraceFrame> nextFrame = getNextFrameAtDepth(frames, i, depth + 1);
        if (nextFrame.isEmpty()) {
          // Skip calls that don't execute (like insufficient gas or precompiles that return
          // immediately)
          continue;
        }

        // Create new call based on opcode
        final DebugCallTracerResult childCall =
            createCallResult(frame, opcodeString, parentCall, nextFrame.get(), cumulativeGasCost);

        // Add to parent's calls list
        parentCall.calls.add(childCall);

        // Register in depth map
        callsByDepth.put(depth + 1, childCall);

        // Push to call stack with index for gas calculation
        CallStackEntry entry =
            new CallStackEntry(depth + 1, childCall, frame.getGasRemaining(), cumulativeGasCost);

        // Set gas stipend for value-transferring CALL operations
        if ("CALL".equals(opcodeString) && !Wei.ZERO.equals(frame.getValue())) {
          entry = entry.withGasStipend(CALL_STIPEND);
        }

        callStack.push(entry);
      }
      // Handle SELFDESTRUCT operations
      else if ("SELFDESTRUCT".equals(opcodeString)) {
        // Get the current call context
        final DebugCallTracerResult currentCall = callsByDepth.get(depth);
        if (currentCall != null) {
          // Get the refund address from the stack
          Address refundAddress = null;
          if (frame.getStack().isPresent() && frame.getStack().get().length > 0) {
            Bytes[] stack = frame.getStack().get();
            refundAddress = Address.wrap(stack[stack.length - 1]);
          }

          // Create a SELFDESTRUCT call result
          final DebugCallTracerResult selfDestructCall =
              new DebugCallTracerResult(
                  "SELFDESTRUCT",
                  currentCall.to, // from address is the current contract
                  refundAddress != null
                      ? refundAddress.toHexString()
                      : null, // to address is the refund address
                  "0x0", // Initialize with zero value
                  BigInteger.ZERO, // no gas allocation needed
                  "0x" // no input data
                  );

          // Set the output to null since SELFDESTRUCT doesn't return data
          selfDestructCall.output = null;

          // Calculate gas used for the SELFDESTRUCT operation
          long gasUsed = frame.getGasCost().orElse(0L);
          selfDestructCall.gasUsed = BigInteger.valueOf(gasUsed);

          // Get the balance being transferred if available
          if (frame.getMaybeRefunds().isPresent() && refundAddress != null) {
            Wei balance = frame.getMaybeRefunds().get().getOrDefault(refundAddress, Wei.ZERO);
            // Update the value field with the balance
            selfDestructCall.value = balance.toShortHexString();
          }

          // Add to the current call's calls list
          currentCall.calls.add(selfDestructCall);
        }
      }
      // Handle return/revert operations
      else if ("RETURN".equals(opcodeString)
          || "REVERT".equals(opcodeString)
          || "STOP".equals(opcodeString)) {
        if (!callStack.isEmpty() && callStack.peek().depth() == depth) {
          final CallStackEntry entry = callStack.pop();
          final DebugCallTracerResult call = entry.call();

          if ("RETURN".equals(opcodeString) || "STOP".equals(opcodeString)) {
            // Set output data on successful return
            final Bytes outputData = frame.getOutputData();
            call.output =
                outputData != null && !outputData.isEmpty() ? outputData.toShortHexString() : null;

            // For CREATE operations, update the contract address
            if ("CREATE".equals(call.type) || "CREATE2".equals(call.type)) {
              // Contract address should be set if available
              final Address recipient = frame.getRecipient();
              if (recipient != null) {
                call.to = recipient.toHexString();
              }
            }
          } else {
            // Handle revert
            call.error = "execution reverted";
            call.output = null;

            // Get revert reason if available
            final Optional<Bytes> revertReason = frame.getRevertReason();
            if (revertReason.isPresent() && !revertReason.get().isEmpty()) {
              call.revertReason = revertReason.get().toHexString();
            }
          }

          // Calculate gas used for this call
          calculateGasUsed(entry, frame, opcodeString);

          // Remove from tracking
          callsByDepth.remove(depth);
        }
      }
      // Handle exceptional halts
      else if (frame.getExceptionalHaltReason().isPresent()) {
        if (!callStack.isEmpty() && callStack.peek().depth() == depth) {
          final CallStackEntry entry = callStack.pop();
          final DebugCallTracerResult call = entry.call();

          // Set error message
          call.error =
              frame
                  .getExceptionalHaltReason()
                  .map(ExceptionalHaltReason::getDescription)
                  .orElse("execution failed");

          // Calculate gas used for this call
          calculateGasUsed(entry, frame, opcodeString);

          // Remove from tracking
          callsByDepth.remove(depth);
        }
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
      return;
    }

    // Convert initial values to BigInteger to avoid overflow
    BigInteger startGas = BigInteger.valueOf(entry.initialGas());
    BigInteger endGas = BigInteger.valueOf(currentFrame.getGasRemaining());
    BigInteger cumulativeGasCost = BigInteger.valueOf(entry.cumulativeGasCost());

    // Basic gas calculation
    BigInteger gasUsed = startGas.subtract(endGas);

    // Adjust for cumulative gas cost
    gasUsed = gasUsed.subtract(cumulativeGasCost).max(BigInteger.ZERO);

    // Add cost of the final operation if present
    if (currentFrame.getGasCost().isPresent()) {
      gasUsed = gasUsed.add(BigInteger.valueOf(currentFrame.getGasCost().getAsLong()));
    }

    // Account for gas refunds
    long gasRefund = currentFrame.getGasRefund();
    if (gasRefund > 0) {
      // Only apply refund up to a maximum of gasUsed / 5
      BigInteger maxRefund =
          gasUsed.divide(BigInteger.valueOf(5)); // TODO: Use ProtocolSpec GasCalculator
      BigInteger actualRefund = BigInteger.valueOf(gasRefund).min(maxRefund);
      gasUsed = gasUsed.subtract(actualRefund);
    }

    // Handle special cases for different call types

    // For CREATE operations, add code deposit cost on successful return
    if (("CREATE".equals(call.type) || "CREATE2".equals(call.type))
        && "RETURN".equals(opcodeString)) {
      final Bytes outputData = currentFrame.getOutputData();
      if (outputData != null && !outputData.isEmpty()) {
        // Code deposit costs 200 gas per byte
        gasUsed =
            gasUsed.add(
                BigInteger.valueOf(outputData.size())
                    .multiply(BigInteger.valueOf(CODE_DEPOSIT_GAS_PER_BYTE)));
      }
    }

    // Adjust for call stipends if applicable
    if (entry.gasStipend() > 0 && ("RETURN".equals(opcodeString) || "STOP".equals(opcodeString))) {
      // Only subtract stipend if the call was successful
      gasUsed = gasUsed.subtract(BigInteger.valueOf(entry.gasStipend())).max(BigInteger.ZERO);
    }

    // Ensure gas used doesn't exceed the allocated gas
    gasUsed = gasUsed.min(call.gas);

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

  private boolean isCallOp(final String opcodeString) {
    return "CALL".equals(opcodeString)
        || "DELEGATECALL".equals(opcodeString)
        || "CALLCODE".equals(opcodeString)
        || "STATICCALL".equals(opcodeString)
        || "CREATE".equals(opcodeString)
        || "CREATE2".equals(opcodeString);
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
    if (isCreateOp(opcodeString)) {
      // For CREATE/CREATE2, we'll set the address later when we have the actual address
      // For now, use the expected contract address if we can calculate it
      if (frame.getRecipient() != null) {
        to = frame.getRecipient().toHexString();
      } else {
        to = null;
      }
    } else {
      // For other calls, get the recipient from the next frame
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

    // Determine gas
    // For calls, calculate the gas that will be available to the call
    long gasRemaining = frame.getGasRemaining();
    long gasCost = frame.getGasCost().orElse(0L);

    // Calculate gas available to the call
    long callGas;

    // If we have stack information, extract the gas parameter
    if (frame.getStack().isPresent() && frame.getStack().get().length > 0) {
      // Get the gas value from the stack
      BigInteger gasValue = frame.getStack().get()[0].toUnsignedBigInteger();

      // Convert to long, capping at Long.MAX_VALUE if necessary
      if (gasValue.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
        callGas = Long.MAX_VALUE;
      } else {
        callGas = gasValue.longValue();
      }

      // Apply EIP-150 gas forwarding rules
      // Adjust the maximum available gas by subtracting the cumulative gas cost
      long maxCallGas = gasRemaining - gasCost - cumulativeGasCost;

      // Cap the call gas at the maximum available
      if (callGas > maxCallGas) {
        callGas = maxCallGas;
      }

      // Apply the EIP-150 divisor for non-CREATE calls
      if (!isCreateOp(opcodeString)) {
        callGas = callGas - (callGas / EIP_150_DIVISOR);
      }
    } else {
      // Fallback to next frame's gas remaining
      callGas = nextFrame.getGasRemaining();
    }

    // Determine input data
    final Bytes inputData = nextFrame.getInputData();
    final String input = inputData != null ? inputData.toHexString() : "0x";

    // Create and return the call result
    return new DebugCallTracerResult(
        opcodeString, from, to, value, BigInteger.valueOf(callGas), input);
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

  /** Helper class to track call stack entries for resolving returns. */
  @JsonIgnoreType
  private record CallStackEntry(
      int depth,
      DebugCallTracerResult call,
      long initialGas,
      long cumulativeGasCost,
      long gasStipend) {

    // Compact constructor for default gasStipend
    public CallStackEntry(
        final int depth,
        final DebugCallTracerResult call,
        final long initialGas,
        final long cumulativeGasCost) {
      this(depth, call, initialGas, cumulativeGasCost, 0); // Default gasStipend to 0
    }

    // Method to create a new instance with updated gasStipend
    public CallStackEntry withGasStipend(final long newGasStipend) {
      return new CallStackEntry(depth, call, initialGas, cumulativeGasCost, newGasStipend);
    }
  }
}
