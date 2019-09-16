/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.vm;

import static org.hyperledger.besu.util.uint.UInt256.U_32;

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.debug.TraceFrame;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.vm.ehalt.ExceptionalHaltException;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.uint.UInt256;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public class DebugOperationTracer implements OperationTracer {

  private final TraceOptions options;
  private final List<TraceFrame> traceFrames = new ArrayList<>();

  public DebugOperationTracer(final TraceOptions options) {
    this.options = options;
  }

  @Override
  public void traceExecution(
      final MessageFrame frame,
      final Optional<Gas> currentGasCost,
      final ExecuteOperation executeOperation)
      throws ExceptionalHaltException {
    final int depth = frame.getMessageStackDepth();
    final String opcode = frame.getCurrentOperation().getName();
    final int pc = frame.getPC();
    final Gas gasRemaining = frame.getRemainingGas();
    final EnumSet<ExceptionalHaltReason> exceptionalHaltReasons =
        EnumSet.copyOf(frame.getExceptionalHaltReasons());
    final Optional<Bytes32[]> stack = captureStack(frame);
    final Optional<Bytes32[]> memory = captureMemory(frame);

    try {
      executeOperation.execute();
    } finally {
      final Optional<Map<UInt256, UInt256>> storage = captureStorage(frame);
      final Optional<Map<Address, Wei>> maybeRefunds =
          frame.getRefunds().isEmpty() ? Optional.empty() : Optional.of(frame.getRefunds());
      traceFrames.add(
          new TraceFrame(
              pc,
              opcode,
              gasRemaining,
              currentGasCost,
              depth,
              exceptionalHaltReasons,
              stack,
              memory,
              storage,
              frame.getRevertReason(),
              maybeRefunds));
    }
  }

  private Optional<Map<UInt256, UInt256>> captureStorage(final MessageFrame frame) {
    if (!options.isStorageEnabled()) {
      return Optional.empty();
    }
    final Map<UInt256, UInt256> storageContents =
        new TreeMap<>(
            frame.getWorldState().getMutable(frame.getRecipientAddress()).getUpdatedStorage());
    return Optional.of(storageContents);
  }

  private Optional<Bytes32[]> captureMemory(final MessageFrame frame) {
    if (!options.isMemoryEnabled()) {
      return Optional.empty();
    }
    final Bytes32[] memoryContents = new Bytes32[frame.memoryWordSize().toInt()];
    for (int i = 0; i < memoryContents.length; i++) {
      memoryContents[i] = Bytes32.wrap(frame.readMemory(UInt256.of(i).times(U_32), U_32), 0);
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
      stackContents[i] = frame.getStackItem(stackContents.length - i - 1);
    }
    return Optional.of(stackContents);
  }

  public List<TraceFrame> getTraceFrames() {
    return traceFrames;
  }
}
