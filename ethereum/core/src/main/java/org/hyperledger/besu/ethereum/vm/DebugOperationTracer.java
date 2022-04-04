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

import static org.apache.tuweni.bytes.Bytes32.leftPad;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.debug.TraceFrame;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class DebugOperationTracer implements OperationTracer {

  private final TraceOptions options;
  private List<TraceFrame> traceFrames = new ArrayList<>();
  private TraceFrame lastFrame;

  public DebugOperationTracer(final TraceOptions options) {
    this.options = options;
  }

  @Override
  public void traceExecution(final MessageFrame frame, final ExecuteOperation executeOperation) {
    final Operation currentOperation = frame.getCurrentOperation();
    final int depth = frame.getMessageStackDepth();
    final String opcode = currentOperation.getName();
    final int pc = frame.getPC();
    final long gasRemaining = frame.getRemainingGas();
    final Bytes inputData = frame.getInputData();
    final Optional<Bytes32[]> stack = captureStack(frame);
    final WorldUpdater worldUpdater = frame.getWorldUpdater();
    final Optional<Bytes32[]> stackPostExecution;
    final Operation.OperationResult operationResult = executeOperation.execute();
    final Bytes outputData = frame.getOutputData();
    final Optional<Bytes[]> memory = captureMemory(frame);
    stackPostExecution = captureStack(frame);
    if (lastFrame != null) {
      lastFrame.setGasRemainingPostExecution(gasRemaining);
    }
    final Optional<Map<UInt256, UInt256>> storage = captureStorage(frame);
    final Optional<Map<Address, Wei>> maybeRefunds =
        frame.getRefunds().isEmpty() ? Optional.empty() : Optional.of(frame.getRefunds());
    lastFrame =
        new TraceFrame(
            pc,
            Optional.of(opcode),
            gasRemaining,
            operationResult.getGasCost(),
            frame.getGasRefund(),
            depth,
            operationResult.getHaltReason(),
            frame.getRecipientAddress(),
            frame.getApparentValue(),
            pc == 0 ? inputData.copy() : inputData,
            outputData,
            stack,
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
              frame.getRemainingGas(),
              OptionalLong.empty(),
              frame.getGasRefund(),
              frame.getMessageStackDepth(),
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
                    frame.getRemainingGas(),
                    OptionalLong.empty(),
                    frame.getGasRefund(),
                    frame.getMessageStackDepth(),
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
      final Map<UInt256, UInt256> storageContents =
          new TreeMap<>(
              frame
                  .getWorldUpdater()
                  .getAccount(frame.getRecipientAddress())
                  .getMutable()
                  .getUpdatedStorage());
      return Optional.of(storageContents);
    } catch (final ModificationNotAllowedException e) {
      return Optional.of(new TreeMap<>());
    }
  }

  private Optional<Bytes[]> captureMemory(final MessageFrame frame) {
    if (!options.isMemoryEnabled()) {
      return Optional.empty();
    }
    final Bytes[] memoryContents = new Bytes[frame.memoryWordSize()];
    for (int i = 0; i < memoryContents.length; i++) {
      memoryContents[i] = frame.readMemory(i * 32L, 32);
    }
    return Optional.of(memoryContents);
  }

  private Optional<Bytes32[]> captureStack(final MessageFrame frame) {
    if (!options.isStackEnabled()) {
      return Optional.empty();
    }

    final Bytes32[] stackContents = new Bytes32[frame.stackSize()];
    for (int i = 0; i < stackContents.length; i++) {
      // Record stack contents in reverse
      stackContents[i] = leftPad(frame.getStackItem(stackContents.length - i - 1));
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
