/*
 * Copyright ConsenSys AG.
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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.debug.TraceFrame;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.AbstractCallOperation;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class DebugOperationTracer implements OperationTracer {

  private final TraceOptions options;

  /**
   * A flag to indicate if call operations should trace just the operation cost (false, Geth style,
   * debug_ series RPCs) or the operation cost and all gas granted to the child call (true, Parity
   * style, trace_ series RPCs)
   */
  private final boolean recordChildCallGas;

  private List<TraceFrame> traceFrames = new ArrayList<>();
  private TraceFrame lastFrame;

  private Optional<Bytes[]> preExecutionStack;
  private long gasRemaining;
  private Bytes inputData;
  private int pc;
  private int depth;

  /**
   * Creates the operation tracer.
   *
   * @param options The options, as passed in through the RPC
   * @param recordChildCallGas A flag on whether to produce geth style (true) or parity style
   *     (false) gas amounts for call operations
   */
  public DebugOperationTracer(final TraceOptions options, final boolean recordChildCallGas) {
    this.options = options;
    this.recordChildCallGas = recordChildCallGas;
  }

  @Override
  public void tracePreExecution(final MessageFrame frame) {
    preExecutionStack = captureStack(frame);
    gasRemaining = frame.getRemainingGas();
    if (lastFrame != null && frame.getDepth() > lastFrame.getDepth())
      inputData = frame.getInputData().copy();
    else inputData = frame.getInputData();
    pc = frame.getPC();
    depth = frame.getDepth();
  }

  @Override
  public void tracePostExecution(final MessageFrame frame, final OperationResult operationResult) {
    final Operation currentOperation = frame.getCurrentOperation();
    final String opcode = currentOperation.getName();
    final int opcodeNumber = (opcode != null) ? currentOperation.getOpcode() : Integer.MAX_VALUE;
    final WorldUpdater worldUpdater = frame.getWorldUpdater();
    final Bytes outputData = frame.getOutputData();
    final Optional<Bytes[]> memory = captureMemory(frame);
    final Optional<Bytes[]> stackPostExecution = captureStack(frame);

    if (lastFrame != null) {
      lastFrame.setGasRemainingPostExecution(gasRemaining);
    }
    final Optional<Map<UInt256, UInt256>> storage = captureStorage(frame);
    final Optional<Map<Address, Wei>> maybeRefunds =
        frame.getRefunds().isEmpty() ? Optional.empty() : Optional.of(frame.getRefunds());
    long thisGasCost = operationResult.getGasCost();
    if (recordChildCallGas && currentOperation instanceof AbstractCallOperation) {
      thisGasCost += frame.getMessageFrameStack().getFirst().getRemainingGas();
    }
    lastFrame =
        new TraceFrame(
            pc,
            Optional.of(opcode),
            opcodeNumber,
            gasRemaining,
            thisGasCost == 0 ? OptionalLong.empty() : OptionalLong.of(thisGasCost),
            frame.getGasRefund(),
            depth,
            Optional.ofNullable(operationResult.getHaltReason())
                .or(frame::getExceptionalHaltReason),
            frame.getRecipientAddress(),
            frame.getApparentValue(),
            inputData,
            outputData,
            preExecutionStack,
            memory,
            storage,
            worldUpdater,
            frame.getRevertReason(),
            maybeRefunds,
            Optional.ofNullable(frame.getMessageFrameStack().peek()).map(MessageFrame::getCode),
            frame.getCurrentOperation().getStackItemsProduced(),
            stackPostExecution,
            currentOperation.isVirtualOperation(),
            frame.getMaybeUpdatedMemory(),
            frame.getMaybeUpdatedStorage());
    traceFrames.add(lastFrame);
    frame.reset();
  }

  @Override
  public void tracePrecompileCall(
      final MessageFrame frame, final long gasRequirement, final Bytes output) {
    if (traceFrames.isEmpty()) {
      final TraceFrame traceFrame =
          new TraceFrame(
              frame.getPC(),
              Optional.empty(),
              Integer.MAX_VALUE,
              frame.getRemainingGas(),
              OptionalLong.empty(),
              frame.getGasRefund(),
              frame.getDepth(),
              Optional.empty(),
              frame.getRecipientAddress(),
              frame.getValue(),
              frame.getInputData().copy(),
              frame.getOutputData(),
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              frame.getWorldUpdater(),
              Optional.empty(),
              Optional.ofNullable(frame.getRefunds()),
              Optional.ofNullable(frame.getCode()),
              frame.getMaxStackSize(),
              Optional.empty(),
              true,
              Optional.empty(),
              Optional.empty());
      traceFrames.add(traceFrame);
    }
    traceFrames.get(traceFrames.size() - 1).setPrecompiledGasCost(OptionalLong.of(gasRequirement));
  }

  @Override
  public void traceAccountCreationResult(
      final MessageFrame frame, final Optional<ExceptionalHaltReason> haltReason) {
    haltReason.ifPresent(
        exceptionalHaltReason -> {
          if (!traceFrames.isEmpty()) {
            TraceFrame foundTraceFrame = null;
            int frameIndex = traceFrames.size() - 1;
            do {
              if (!traceFrames.get(frameIndex).getOpcode().equals("RETURN")) {
                foundTraceFrame = traceFrames.get(frameIndex);
              }
              frameIndex--;
            } while (foundTraceFrame == null);
            foundTraceFrame.setExceptionalHaltReason(exceptionalHaltReason);
          } else {
            final TraceFrame traceFrame =
                new TraceFrame(
                    frame.getPC(),
                    Optional.empty(),
                    Integer.MAX_VALUE,
                    frame.getRemainingGas(),
                    OptionalLong.empty(),
                    frame.getGasRefund(),
                    frame.getDepth(),
                    Optional.of(exceptionalHaltReason),
                    frame.getRecipientAddress(),
                    frame.getValue(),
                    frame.getInputData().copy(),
                    frame.getOutputData(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    frame.getWorldUpdater(),
                    Optional.empty(),
                    Optional.ofNullable(frame.getRefunds()),
                    Optional.ofNullable(frame.getCode()),
                    frame.getMaxStackSize(),
                    Optional.empty(),
                    true,
                    Optional.empty(),
                    Optional.empty());
            traceFrames.add(traceFrame);
          }
        });
  }

  private Optional<Map<UInt256, UInt256>> captureStorage(final MessageFrame frame) {
    if (!options.isStorageEnabled()) {
      return Optional.empty();
    }
    try {
      Map<UInt256, UInt256> updatedStorage =
          frame.getWorldUpdater().getAccount(frame.getRecipientAddress()).getUpdatedStorage();
      if (updatedStorage.isEmpty()) return Optional.empty();
      final Map<UInt256, UInt256> storageContents = new TreeMap<>(updatedStorage);

      return Optional.of(storageContents);
    } catch (final ModificationNotAllowedException e) {
      return Optional.of(new TreeMap<>());
    }
  }

  private Optional<Bytes[]> captureMemory(final MessageFrame frame) {
    if (!options.isMemoryEnabled() || frame.memoryWordSize() == 0) {
      return Optional.empty();
    }
    final Bytes[] memoryContents = new Bytes[frame.memoryWordSize()];
    for (int i = 0; i < memoryContents.length; i++) {
      memoryContents[i] = frame.readMemory(i * 32L, 32);
    }
    return Optional.of(memoryContents);
  }

  private Optional<Bytes[]> captureStack(final MessageFrame frame) {
    if (!options.isStackEnabled()) {
      return Optional.empty();
    }

    final Bytes[] stackContents = new Bytes[frame.stackSize()];
    for (int i = 0; i < stackContents.length; i++) {
      // Record stack contents in reverse
      stackContents[i] = frame.getStackItem(stackContents.length - i - 1);
    }
    return Optional.of(stackContents);
  }

  public List<TraceFrame> getTraceFrames() {
    return traceFrames;
  }

  public void reset() {
    traceFrames = new ArrayList<>();
    lastFrame = null;
  }

  public List<TraceFrame> copyTraceFrames() {
    return new ArrayList<>(traceFrames);
  }
}
