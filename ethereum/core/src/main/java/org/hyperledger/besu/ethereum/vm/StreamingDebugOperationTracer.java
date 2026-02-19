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
package org.hyperledger.besu.ethereum.vm;

import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.AbstractCallOperation;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.tracing.OpCodeTracerConfigBuilder.OpCodeTracerConfig;
import org.hyperledger.besu.evm.tracing.OperationTracer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import com.fasterxml.jackson.core.JsonGenerator;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * An operation tracer that streams StructLog entries directly to a JsonGenerator during EVM
 * execution. Unlike DebugOperationTracer which accumulates all TraceFrames in an ArrayList, this
 * tracer writes each struct log inline and immediately discards it, achieving O(1) frame memory.
 *
 * <p>Additionally implements lazy memory capture: EVM memory is only captured for opcodes that
 * actually read or write memory (24 out of ~140 opcodes). For all other opcodes, memory is omitted,
 * reducing per-frame size from ~256KB to ~1-2KB for the majority of opcode steps.
 */
public class StreamingDebugOperationTracer implements OperationTracer {

  private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

  /**
   * Set of opcode numbers that read from or write to EVM memory. Only these opcodes trigger a full
   * memory capture in tracePostExecution. All other opcodes emit null/omitted memory field.
   */
  private static final Set<Integer> MEMORY_OPCODES =
      Set.of(
          0x20, // KECCAK256 (reads memory to hash)
          0x37, // CALLDATACOPY (writes calldata into memory)
          0x39, // CODECOPY (writes code into memory)
          0x3C, // EXTCODECOPY (writes external code into memory)
          0x3E, // RETURNDATACOPY (writes return data into memory)
          0x51, // MLOAD (reads 32 bytes from memory)
          0x52, // MSTORE (writes 32 bytes to memory)
          0x53, // MSTORE8 (writes 1 byte to memory)
          0x5E, // MCOPY (copies memory region)
          0xA0, // LOG0 (reads memory for log data)
          0xA1, // LOG1
          0xA2, // LOG2
          0xA3, // LOG3
          0xA4, // LOG4
          0xF0, // CREATE (reads memory for init code)
          0xF1, // CALL (reads input from memory, writes output to memory)
          0xF2, // CALLCODE (reads/writes memory)
          0xF3, // RETURN (reads memory for return data)
          0xF4, // DELEGATECALL (reads/writes memory)
          0xF5, // CREATE2 (reads memory for init code)
          0xFA, // STATICCALL (reads/writes memory)
          0xFD // REVERT (reads memory for revert reason)
          );

  private final OpCodeTracerConfig options;
  private final boolean recordChildCallGas;
  private final JsonGenerator generator;

  // Pre-execution state captured in tracePreExecution, consumed in tracePostExecution
  private Optional<Bytes[]> preExecutionStack;
  private long gasRemaining;
  private int pc;
  private int depth;

  // traceOpcodes filtering
  private boolean traceOpcode;
  private Operation previousOpcode = null;

  public StreamingDebugOperationTracer(
      final OpCodeTracerConfig options,
      final boolean recordChildCallGas,
      final JsonGenerator generator) {
    this.options = options;
    this.recordChildCallGas = recordChildCallGas;
    this.generator = generator;
  }

  @Override
  public void tracePreExecution(final MessageFrame frame) {
    final Operation currentOperation = frame.getCurrentOperation();
    if (!(traceOpcode = traceOpcode(currentOperation))) {
      return;
    }
    preExecutionStack = captureStack(frame);
    gasRemaining = frame.getRemainingGas();
    pc = frame.getPC();
    depth = frame.getDepth();
  }

  @Override
  public void tracePostExecution(final MessageFrame frame, final OperationResult operationResult) {
    final Operation currentOperation = frame.getCurrentOperation();
    final String opcode = currentOperation.getName();
    if (!traceOpcode) {
      return;
    }
    final int opcodeNumber = (opcode != null) ? currentOperation.getOpcode() : Integer.MAX_VALUE;

    long thisGasCost = operationResult.getGasCost();
    if (recordChildCallGas && currentOperation instanceof AbstractCallOperation) {
      thisGasCost += frame.getMessageFrameStack().getFirst().getRemainingGas();
    }

    final Optional<ExceptionalHaltReason> haltReason =
        Optional.ofNullable(operationResult.getHaltReason()).or(frame::getExceptionalHaltReason);

    try {
      generator.writeStartObject();

      // pc
      generator.writeNumberField("pc", pc);

      // op
      generator.writeStringField("op", opcode != null ? opcode : "INVALID");

      // gas
      generator.writeNumberField("gas", gasRemaining);

      // gasCost
      generator.writeNumberField("gasCost", thisGasCost);

      // depth (besu uses 0-based internally, geth uses 1-based)
      generator.writeNumberField("depth", depth + 1);

      // stack (pre-execution)
      if (preExecutionStack.isPresent()) {
        generator.writeFieldName("stack");
        generator.writeStartArray();
        for (final Bytes item : preExecutionStack.get()) {
          generator.writeString(toCompactHex(item));
        }
        generator.writeEndArray();
      }

      // memory — only capture for memory-touching opcodes
      if (options.traceMemory() && frame.memoryWordSize() > 0) {
        if (MEMORY_OPCODES.contains(opcodeNumber)) {
          generator.writeFieldName("memory");
          generator.writeStartArray();
          for (int i = 0; i < frame.memoryWordSize(); i++) {
            generator.writeString(toCompactHex(frame.readMemory(i * 32L, 32)));
          }
          generator.writeEndArray();
        }
        // else: omit memory field entirely for non-memory opcodes
      }

      // storage
      if (options.traceStorage()) {
        writeStorage(frame);
      }

      // error
      if (haltReason.isPresent()) {
        generator.writeFieldName("error");
        generator.writeStartArray();
        generator.writeString(haltReason.get().name());
        generator.writeEndArray();
      }

      // reason (revert)
      if (frame.getRevertReason().isPresent()) {
        generator.writeStringField("reason", toCompactHex(frame.getRevertReason().get()));
      }

      generator.writeEndObject();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    frame.reset();
  }

  @Override
  public void tracePrecompileCall(
      final MessageFrame frame, final long gasRequirement, final Bytes output) {
    // Precompile calls are not part of standard debug_traceBlock structLogs
  }

  @Override
  public void traceAccountCreationResult(
      final MessageFrame frame, final Optional<ExceptionalHaltReason> haltReason) {
    // Account creation results are not part of standard debug_traceBlock structLogs
  }

  @Override
  public List<org.hyperledger.besu.evm.tracing.TraceFrame> getTraceFrames() {
    // This tracer streams inline — no frames are accumulated
    return List.of();
  }

  public void reset() {
    previousOpcode = null;
  }

  private boolean traceOpcode(final Operation currentOpcode) {
    if (options.traceOpcodes().isEmpty()) {
      return true;
    }
    final boolean traceCurrentOpcode =
        options.traceOpcodes().contains(currentOpcode.getName().toLowerCase(Locale.ROOT));
    final boolean tracePreviousOpcode =
        previousOpcode != null
            && options.traceOpcodes().contains(previousOpcode.getName().toLowerCase(Locale.ROOT));
    if (!traceCurrentOpcode && !tracePreviousOpcode) {
      return false;
    }
    previousOpcode = currentOpcode;
    return true;
  }

  private Optional<Bytes[]> captureStack(final MessageFrame frame) {
    if (!options.traceStack()) {
      return Optional.empty();
    }
    final Bytes[] stackContents = new Bytes[frame.stackSize()];
    for (int i = 0; i < stackContents.length; i++) {
      stackContents[i] = frame.getStackItem(stackContents.length - i - 1);
    }
    return Optional.of(stackContents);
  }

  private void writeStorage(final MessageFrame frame) throws IOException {
    try {
      final Map<UInt256, UInt256> updatedStorage =
          frame.getWorldUpdater().getAccount(frame.getRecipientAddress()).getUpdatedStorage();
      if (updatedStorage.isEmpty()) {
        return;
      }
      final Map<UInt256, UInt256> sorted = new TreeMap<>(updatedStorage);
      generator.writeFieldName("storage");
      generator.writeStartObject();
      for (final Map.Entry<UInt256, UInt256> entry : sorted.entrySet()) {
        generator.writeStringField(
            toCompactHex(entry.getKey()), toCompactHex(entry.getValue()));
      }
      generator.writeEndObject();
    } catch (final ModificationNotAllowedException e) {
      // Write empty storage
      generator.writeFieldName("storage");
      generator.writeStartObject();
      generator.writeEndObject();
    }
  }

  /**
   * Converts bytes to compact hex string with 0x prefix. Strips leading zeros. Matches the format
   * used by StructLog.toCompactHex().
   */
  private static String toCompactHex(final Bytes bytes) {
    if (bytes.isEmpty()) {
      return "0x0";
    }
    final byte[] raw = bytes.toArrayUnsafe();
    final StringBuilder sb = new StringBuilder(raw.length * 2 + 2);
    sb.append("0x");
    boolean leadingZero = true;
    for (int i = 0; i < raw.length; i++) {
      final byte b = raw[i];
      final int hi = (b >> 4) & 0xF;
      if (!leadingZero || hi != 0) {
        sb.append(HEX_CHARS[hi]);
        leadingZero = false;
      }
      final int lo = b & 0xF;
      if (!leadingZero || lo != 0 || i == raw.length - 1) {
        sb.append(HEX_CHARS[lo]);
        leadingZero = false;
      }
    }
    return sb.toString();
  }
}
