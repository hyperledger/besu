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

import static org.apache.logging.log4j.LogManager.getLogger;

import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.vm.MessageFrame.State;
import org.hyperledger.besu.ethereum.vm.ehalt.ExceptionalHaltException;
import org.hyperledger.besu.ethereum.vm.ehalt.ExceptionalHaltManager;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.EnumSet;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.apache.logging.log4j.Logger;

public class EVM {
  private static final Logger LOG = getLogger();

  private static final int STOP_OPCODE = 0x00;
  private final OperationRegistry operations;
  private final Operation invalidOperation;

  public EVM(final OperationRegistry operations, final Operation invalidOperation) {
    this.operations = operations;
    this.invalidOperation = invalidOperation;
  }

  public void runToHalt(final MessageFrame frame, final OperationTracer operationTracer)
      throws ExceptionalHaltException {
    while (frame.getState() == MessageFrame.State.CODE_EXECUTING) {
      executeNextOperation(frame, operationTracer);
    }
  }

  public void forEachOperation(
      final Code code,
      final int contractAccountVersion,
      final BiConsumer<Operation, Integer> operationDelegate) {
    int pc = 0;
    final int length = code.getSize();

    while (pc < length) {
      final Operation curOp = operationAtOffset(code, contractAccountVersion, pc);
      operationDelegate.accept(curOp, pc);
      pc += curOp.getOpSize();
    }
  }

  private void executeNextOperation(final MessageFrame frame, final OperationTracer operationTracer)
      throws ExceptionalHaltException {
    frame.setCurrentOperation(
        operationAtOffset(frame.getCode(), frame.getContractAccountVersion(), frame.getPC()));
    evaluateExceptionalHaltReasons(frame);
    final Optional<Gas> currentGasCost = calculateGasCost(frame);
    operationTracer.traceExecution(
        frame,
        currentGasCost,
        () -> {
          logState(frame, currentGasCost);
          checkForExceptionalHalt(frame);
          decrementRemainingGas(frame, currentGasCost);
          frame.getCurrentOperation().execute(frame);
          incrementProgramCounter(frame);
        });
  }

  private void evaluateExceptionalHaltReasons(final MessageFrame frame) {
    final EnumSet<ExceptionalHaltReason> haltReasons =
        ExceptionalHaltManager.evaluateAll(frame, this);
    frame.getExceptionalHaltReasons().addAll(haltReasons);
  }

  private Optional<Gas> calculateGasCost(final MessageFrame frame) {
    // Calculate the cost if, and only if, we are not halting as a result of a stack underflow, as
    // the operation may need all its stack items to calculate gas.
    // This is how existing EVM implementations behave.
    if (!frame
        .getExceptionalHaltReasons()
        .contains(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS)) {
      try {
        return Optional.ofNullable(frame.getCurrentOperation().cost(frame));
      } catch (final IllegalArgumentException e) {
        // TODO: Figure out a better way to handle gas overflows.
      }
    }
    return Optional.empty();
  }

  private void decrementRemainingGas(final MessageFrame frame, final Optional<Gas> currentGasCost) {
    frame.decrementRemainingGas(
        currentGasCost.orElseThrow(() -> new IllegalStateException("Gas overflow detected")));
  }

  private void checkForExceptionalHalt(final MessageFrame frame) throws ExceptionalHaltException {
    final EnumSet<ExceptionalHaltReason> exceptionalHaltReasons = frame.getExceptionalHaltReasons();
    if (!exceptionalHaltReasons.isEmpty()) {
      LOG.trace("MessageFrame evaluation halted because of {}", exceptionalHaltReasons);
      frame.setState(State.EXCEPTIONAL_HALT);
      frame.setOutputData(BytesValue.EMPTY);
      throw new ExceptionalHaltException(exceptionalHaltReasons);
    }
  }

  private void incrementProgramCounter(final MessageFrame frame) {
    final Operation operation = frame.getCurrentOperation();
    if (frame.getState() == State.CODE_EXECUTING && !operation.getUpdatesProgramCounter()) {
      final int currentPC = frame.getPC();
      final int opSize = operation.getOpSize();
      frame.setPC(currentPC + opSize);
    }
  }

  private static void logState(final MessageFrame frame, final Optional<Gas> currentGasCost) {
    if (LOG.isTraceEnabled()) {
      final StringBuilder builder = new StringBuilder();
      builder.append("Depth: ").append(frame.getMessageStackDepth()).append("\n");
      builder.append("Operation: ").append(frame.getCurrentOperation().getName()).append("\n");
      builder.append("PC: ").append(frame.getPC()).append("\n");
      currentGasCost.ifPresent(gas -> builder.append("Gas cost: ").append(gas).append("\n"));
      builder.append("Gas Remaining: ").append(frame.getRemainingGas()).append("\n");
      builder.append("Depth: ").append(frame.getMessageStackDepth()).append("\n");
      builder.append("Stack:");
      for (int i = 0; i < frame.stackSize(); ++i) {
        builder.append("\n\t").append(i).append(" ").append(frame.getStackItem(i));
      }
      LOG.trace(builder.toString());
    }
  }

  private Operation operationAtOffset(
      final Code code, final int contractAccountVersion, final int offset) {
    final BytesValue bytecode = code.getBytes();
    // If the length of the program code is shorter than the required offset, halt execution.
    if (offset >= bytecode.size()) {
      return operations.get(STOP_OPCODE, contractAccountVersion);
    }

    return operations.getOrDefault(bytecode.get(offset), contractAccountVersion, invalidOperation);
  }
}
