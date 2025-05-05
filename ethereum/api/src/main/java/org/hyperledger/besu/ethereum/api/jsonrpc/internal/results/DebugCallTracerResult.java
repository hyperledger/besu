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

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
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
  "type",
  "from",
  "to",
  "value",
  "gas",
  "gasUsed",
  "input",
  "output",
  "error",
  "revertReason",
  "calls"
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DebugCallTracerResult implements DebugTracerResult {
  private final String type;
  private final String from;
  private final String to;
  private final String value;
  private final String gas;
  private String gasUsed;
  private final String input;
  private String output;
  private String error;
  private String revertReason;
  private final List<DebugCallTracerResult> calls;

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
    this.value = tx.getValue().toHexString();
    this.gas = "0x" + Long.toHexString(tx.getGasLimit());

    // Set result details based on success/failure
    if (result.isSuccessful()) {
      this.output = result.getOutput().toHexString();
      // Calculate gas used from gas limit and gas remaining
      final long gasUsed = tx.getGasLimit() - result.getGasRemaining();
      this.gasUsed = "0x" + Long.toHexString(gasUsed);
    } else {
      this.error = "execution reverted";
      // Calculate gas used for failed transaction
      final long gasUsed = result.getEstimateGasUsedByTransaction();
      this.gasUsed = "0x" + Long.toHexString(gasUsed);
      result.getRevertReason().ifPresent(r -> this.revertReason = r.toHexString());
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
      final String gas,
      final String input) {
    this.type = type;
    this.from = from;
    this.to = to;
    this.value = value;
    this.gas = gas;
    this.input = input;
    this.calls = new ArrayList<>();
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
    Deque<CallStackEntry> callStack = new LinkedList<>();

    for (int i = 0; i < frames.size(); i++) {
      final TraceFrame frame = frames.get(i);
      final String opcodeString = frame.getOpcode();
      final int depth = frame.getDepth();

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
        final DebugCallTracerResult childCall = createCallResult(frame, opcodeString, parentCall);

        // Add to parent's calls list
        parentCall.calls.add(childCall);

        // Register in depth map
        callsByDepth.put(depth + 1, childCall);

        // Push to call stack with index for gas calculation
        callStack.push(new CallStackEntry(i, depth + 1, childCall));
      }
      // Handle return/revert operations
      else if ("RETURN".equals(opcodeString) || "REVERT".equals(opcodeString)) {
        if (!callStack.isEmpty() && callStack.peek().getDepth() == depth) {
          final CallStackEntry entry = callStack.pop();
          final DebugCallTracerResult call = entry.getCall();

          if ("RETURN".equals(opcodeString)) {
            // Set output data on successful return
            final Bytes outputData = frame.getOutputData();
            call.output = outputData != null ? outputData.toHexString() : "0x";

            // For CREATE operations, update the contract address
            if ("CREATE".equals(call.type) || "CREATE2".equals(call.type)) {
              // Contract address should be set if available
              final Address recipient = frame.getRecipient();
              if (recipient != null) {
                call.updateContractAddress(recipient.toHexString());
              }
            }
          } else {
            // Handle revert
            call.error = "execution reverted";
            call.output = null;

            // Get revert reason if available
            final Optional<Bytes> revertReason = frame.getRevertReason();
            if (revertReason.isPresent()) {
              call.revertReason = revertReason.get().toHexString();
            }
          }

          // Calculate gas used - check for precompiled contract first
          if (frame.getPrecompiledGasCost().isPresent()) {
            // Use precompiled contract gas cost if available
            call.gasUsed = "0x" + Long.toHexString(frame.getPrecompiledGasCost().getAsLong());
          } else {
            // Calculate gas used from start frame to current frame
            final TraceFrame startFrame = frames.get(entry.getStartFrameIndex());
            long startGas = startFrame.getGasRemaining();
            long endGas = frame.getGasRemaining();
            long gasUsed = startGas - endGas;

            // Add any gas cost from the final operation (RETURN/REVERT)
            if (frame.getGasCost().isPresent()) {
              gasUsed += frame.getGasCost().getAsLong();
            }

            call.gasUsed = "0x" + Long.toHexString(gasUsed);
          }

          // Remove from tracking
          callsByDepth.remove(depth);
        }
      }
    }
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

  private DebugCallTracerResult createCallResult(
      final TraceFrame frame, final String opcodeString, final DebugCallTracerResult parentCall) {

    // Determine call type
    final String callType;
    if (isCallOp(opcodeString)) {
      // For valid opcodes, use the opcode string directly as the call type
      callType = opcodeString;
    } else {
      callType = "UNKNOWN";
    }

    // Determine from address (caller)
    final String from = parentCall.to;

    // Determine to address (callee)
    final String to;
    if (isCreateOp(opcodeString)) {
      // For CREATE/CREATE2, generate the contract address that will be created
      // Or we'll update it later when we have the actual address from the return frame
      to = null;
    } else {
      // For other calls, use the recipient address from the frame if available
      final Address recipient = frame.getRecipient();
      to = recipient != null ? recipient.toHexString() : null;
    }

    // Determine value
    final String value;
    if ("DELEGATECALL".equals(opcodeString) || "STATICCALL".equals(opcodeString)) {
      // These call types don't transfer value
      value = "0x0";
    } else {
      // Use the value from the frame if available, otherwise default to 0
      final Wei frameValue = frame.getValue();
      value = frameValue != null ? frameValue.toHexString() : "0x0";
    }

    // Determine gas
    final String gas = "0x" + Long.toHexString(frame.getGasRemaining());

    // Determine input data
    final Bytes inputData = frame.getInputData();
    final String input = inputData != null ? inputData.toHexString() : "0x";

    // Create and return the call result
    return new DebugCallTracerResult(callType, from, to, value, gas, input);
  }

  private boolean isCreateOp(final String opcodeString) {
    return "CREATE".equals(opcodeString) || "CREATE2".equals(opcodeString);
  }

  /**
   * Update the contract address for CREATE/CREATE2 operations.
   *
   * @param address the contract address in hex string format
   */
  private void updateContractAddress(final String address) {
    // This would be called after contract creation to update the 'to' field with the actual address
    ((DebugCallTracerResultAccessor) this).updateToAddress(address);
  }

  /**
   * Interface to allow updating of the 'to' field which is final. This is a workaround since we
   * need to update the contract address after creation.
   */
  @JsonIgnoreType
  private interface DebugCallTracerResultAccessor {
    void updateToAddress(String address);
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
    return gas;
  }

  @JsonGetter("gasUsed")
  public String getGasUsed() {
    return gasUsed;
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
  private static class CallStackEntry {
    private final int startFrameIndex;
    private final int depth;
    private final DebugCallTracerResult call;

    public CallStackEntry(
        final int startFrameIndex, final int depth, final DebugCallTracerResult call) {
      this.startFrameIndex = startFrameIndex;
      this.depth = depth;
      this.call = call;
    }

    public int getStartFrameIndex() {
      return startFrameIndex;
    }

    public int getDepth() {
      return depth;
    }

    public DebugCallTracerResult getCall() {
      return call;
    }
  }
}
