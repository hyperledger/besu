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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.debug.TraceFrame;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  private static final Logger LOG = LoggerFactory.getLogger(DebugCallTracerResult.class);

  private final String type;
  private final String from;
  private final String to;
  private final String value;
  private final BigInteger gas;
  private BigInteger gasUsed;
  private final String input;
  private String output;
  private String error;
  private String revertReason;
  private final List<DebugCallTracerResult> calls;

  private static final Set<String> CALL_OPCODES =
      Set.of("CALL", "CALLCODE", "DELEGATECALL", "STATICCALL", "CREATE", "CREATE2");
  private static final Set<String> RETURN_OPCODES =
      Set.of("STOP", "RETURN", "REVERT", "INVALID", "SELFDESTRUCT");
  private static final int EIP_150_DIVISOR = 64;

  public DebugCallTracerResult(final TransactionTrace transactionTrace) {
    final Transaction tx = transactionTrace.getTransaction();
    final TransactionProcessingResult result = transactionTrace.getResult();
    this.calls = new ArrayList<>();

    // Root call init
    if (tx.getTo().isPresent()) {
      this.type = "CALL";
      this.to = tx.getTo().get().toHexString();
      this.input = Optional.ofNullable(tx.getPayload()).map(Bytes::toHexString).orElse("0x");
    } else {
      this.type = "CREATE";
      this.to = Address.contractAddress(tx.getSender(), tx.getNonce()).toHexString();
      this.input = tx.getInit().map(Bytes::toHexString).orElse("0x");
    }
    this.from = tx.getSender().toHexString();
    this.value = tx.getValue().toShortHexString();
    this.gas = BigInteger.valueOf(tx.getGasLimit());

    // Root gasUsed/output/error/revertReason
    if (result.isSuccessful()) {
      Bytes out = result.getOutput();
      if (!out.isEmpty()) {
        this.output = out.toHexString();
      }
      this.gasUsed = BigInteger.valueOf(tx.getGasLimit() - result.getGasRemaining());
    } else {
      this.error =
          result
              .getExceptionalHaltReason()
              .map(ExceptionalHaltReason::getDescription)
              .orElse("execution reverted");
      this.gasUsed = BigInteger.valueOf(result.getEstimateGasUsedByTransaction());
      this.revertReason =
          result.getRevertReason().filter(r -> !r.isEmpty()).map(Bytes::toHexString).orElse(null);
    }

    // Build nested calls
    processTraceFrames(transactionTrace);
  }

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
  }

  private void processTraceFrames(final TransactionTrace transactionTrace) {
    List<TraceFrame> frames = transactionTrace.getTraceFrames();
    if (frames == null || frames.isEmpty()) {
      // nothing to do; root already initialized
      return;
    }

    LOG.info("Processing {} trace frames", frames.size());

    Deque<DebugCallTracerResult> stack = new ArrayDeque<>();
    stack.push(this);

    for (TraceFrame frame : frames) {
      String opcode = frame.getOpcode();
      LOG.info("Processing opcode: {} at depth: {}", opcode, frame.getDepth());
      // Entering a nested call/create
      if (CALL_OPCODES.contains(opcode)) {
        DebugCallTracerResult parent = stack.peek();
        DebugCallTracerResult child =
            new DebugCallTracerResult(
                opcode,
                parent.to, // set from address
                extractToAddress(frame, opcode),
                frame.getValue().toShortHexString(),
                extractGas(frame, opcode),
                extractInput(frame));
        parent.calls.add(child);
        stack.push(child);
      }

      // Exiting a call
      if (RETURN_OPCODES.contains(opcode) && stack.size() > 1) {
        DebugCallTracerResult done = stack.pop();
        // gasUsed (precompiled or normal)
        if (frame.getPrecompiledGasCost().isPresent()) {
          done.gasUsed = BigInteger.valueOf(frame.getPrecompiledGasCost().getAsLong());
        } else {
          done.gasUsed =
              done.gas.subtract(BigInteger.valueOf(frame.getGasRemainingPostExecution()));
        }
        done.output = frame.getOutputData().toHexString();
        frame.getExceptionalHaltReason().ifPresent(r -> done.error = r.getDescription());
        frame.getRevertReason().ifPresent(r -> done.revertReason = r.toHexString());
      }
    }

    // Ensure root has gasUsed/output if missing
    if (this.gasUsed == null) {
      long used = transactionTrace.getGasLimit() - transactionTrace.getResult().getGasRemaining();
      this.gasUsed = BigInteger.valueOf(used);
      this.output = transactionTrace.getResult().getOutput().toHexString();
    }
  }

  private String extractToAddress(final TraceFrame frame, final String opcode) {
    if ("CREATE".equals(opcode) || "CREATE2".equals(opcode)) {
      // TODO: Test and Fix CREATE2 address calculation
      return Address.contractAddress(frame.getRecipient(), frame.getValue().toLong()).toHexString();
    } else {
      Bytes[] stack = frame.getStack().orElseThrow();
      return Address.wrap(stack[stack.length - 2]).toHexString();
    }
  }

  private BigInteger extractGas(final TraceFrame frame, final String opcode) {
    Bytes[] stack = frame.getStack().orElseThrow();
    return switch (opcode) {
      case "CALL", "CALLCODE", "DELEGATECALL", "STATICCALL" ->
          // gas arg is on top of stack
          UInt256.fromBytes(stack[stack.length - 1]).toBigInteger();
      case "CREATE", "CREATE2" -> {
        // EIP-150: childGas = parentGasBefore - floor(parentGasBefore/64)
        long parentGas = frame.getGasRemaining();
        long childGas = parentGas - (parentGas / EIP_150_DIVISOR);
        yield BigInteger.valueOf(childGas);
      }
      default -> throw new IllegalArgumentException("Unexpected opcode: " + opcode);
    };
  }

  private String extractInput(final TraceFrame frame) {
    Bytes in = frame.getInputData();
    return (in == null || in.isEmpty()) ? "0x" : in.toHexString();
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
}
