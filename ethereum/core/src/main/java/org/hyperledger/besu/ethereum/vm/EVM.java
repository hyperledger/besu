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

import static org.apache.logging.log4j.LogManager.getLogger;

import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.vm.FixedStack.OverflowException;
import org.hyperledger.besu.ethereum.vm.FixedStack.UnderflowException;
import org.hyperledger.besu.ethereum.vm.MessageFrame.State;
import org.hyperledger.besu.ethereum.vm.Operation.OperationResult;
import org.hyperledger.besu.ethereum.vm.operations.InvalidOperation;
import org.hyperledger.besu.ethereum.vm.operations.ReturnStack;
import org.hyperledger.besu.ethereum.vm.operations.StopOperation;
import org.hyperledger.besu.ethereum.vm.operations.VirtualOperation;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.BiConsumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class EVM {
  private static final Logger LOG = getLogger();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  protected static final OperationResult OVERFLOW_RESPONSE =
      new OperationResult(
          Optional.empty(), Optional.of(ExceptionalHaltReason.TOO_MANY_STACK_ITEMS));
  protected static final OperationResult UNDERFLOW_RESPONSE =
      new OperationResult(
          Optional.empty(), Optional.of(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS));

  private final OperationRegistry operations;
  private final Operation endOfScriptStop;

  public EVM(final OperationRegistry operations, final GasCalculator gasCalculator) {
    this.operations = operations;
    this.endOfScriptStop = new VirtualOperation(new StopOperation(gasCalculator));
  }

  public void runToHalt(final MessageFrame frame, final OperationTracer operationTracer) {
    while (frame.getState() == MessageFrame.State.CODE_EXECUTING) {
      executeNextOperation(frame, operationTracer);
    }
  }

  void forEachOperation(
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

  private void executeNextOperation(
      final MessageFrame frame, final OperationTracer operationTracer) {
    frame.setCurrentOperation(
        operationAtOffset(frame.getCode(), frame.getContractAccountVersion(), frame.getPC()));
    operationTracer.traceExecution(
        frame,
        () -> {
          OperationResult result;
          var loggerPrep = logStatePre(frame);
          try {
            result = frame.getCurrentOperation().execute(frame, this);
          } catch (final OverflowException oe) {
            result = OVERFLOW_RESPONSE;
          } catch (final UnderflowException ue) {
            result = UNDERFLOW_RESPONSE;
          }
          frame.setGasCost(result.getGasCost());
          logStatePost(frame, result, loggerPrep);
          final Optional<ExceptionalHaltReason> haltReason = result.getHaltReason();
          if (haltReason.isPresent()) {
            LOG.trace("MessageFrame evaluation halted because of {}", haltReason.get());
            frame.setExceptionalHaltReason(haltReason);
            frame.setState(State.EXCEPTIONAL_HALT);
          } else if (result.getGasCost().isPresent()) {
            frame.decrementRemainingGas(result.getGasCost().get());
          }
          incrementProgramCounter(frame);

          return result;
        });
  }

  private void incrementProgramCounter(final MessageFrame frame) {
    final Operation operation = frame.getCurrentOperation();
    if (frame.getState() == State.CODE_EXECUTING && !operation.getUpdatesProgramCounter()) {
      final int currentPC = frame.getPC();
      final int opSize = operation.getOpSize();
      frame.setPC(currentPC + opSize);
    }
  }

  private static ObjectNode logStatePre(final MessageFrame frame) {
//    if (LOG.isTraceEnabled()) {
      final ObjectNode traceLine = OBJECT_MAPPER.createObjectNode();

      final Operation currentOp = frame.getCurrentOperation();
      traceLine.put("pc", frame.getPC());
      traceLine.put("op", Bytes.of(currentOp.getOpcode()).toInt());
      traceLine.put("gas", StandardJsonTracer.shortNumber(frame.getRemainingGas().asUInt256()));
      traceLine.putNull("gasCost");
      traceLine.putNull("memory");
      traceLine.putNull("memSize");
      final ArrayNode stack = traceLine.putArray("stack");
      for (int i = frame.stackSize() - 1; i >= 0; i--) {
        stack.add(StandardJsonTracer.shortBytes(frame.getStackItem(i)));
      }
      final ArrayNode returnStack = traceLine.putArray("returnStack");
      final ReturnStack rs = frame.getReturnStack();
      for (int i = rs.size() - 1; i >= 0; i--) {
        returnStack.add("0x" + Integer.toHexString(rs.get(i) - 1));
      }
      Bytes returnData = frame.getReturnData();
      traceLine.put("returnData", returnData.size() > 0 ? returnData.toHexString() : null);
      traceLine.put("depth", frame.getMessageStackDepth() + 1);

      return traceLine;
//    } else {
//      return null;
//    }
  }

  private static void logStatePost(
      final MessageFrame frame, final OperationResult executeResult, final ObjectNode traceLine) {
//    if (LOG.isTraceEnabled()) {
//      final StringBuilder builder = new StringBuilder();
//      builder.append("Depth: ").append(frame.getMessageStackDepth()).append("\n");
//      builder.append("Operation: ").append(frame.getCurrentOperation().getName()).append("\n");
//      builder.append("PC: ").append(frame.getPC()).append("\n");
//      builder.append("Gas cost: ").append(currentGasCost).append("\n");
//      builder.append("Gas Remaining: ").append(frame.getRemainingGas()).append("\n");
//      builder.append("Depth: ").append(frame.getMessageStackDepth()).append("\n");
//      builder.append("Stack:");
//      for (int i = 0; i < frame.stackSize(); ++i) {
//        builder.append("\n\t").append(i).append(" ").append(frame.getStackItem(i));
//      }
//      LOG.trace(builder.toString());
      traceLine.put("refund", frame.getGasRefund().toLong());
      traceLine.put(
          "gasCost", executeResult.getGasCost().map(gas -> StandardJsonTracer.shortNumber(gas.asUInt256())).orElse(""));
//      if (showMemory) {
//        traceLine.put(
//            "memory",
//            frame
//                .readMemory(UInt256.ZERO, frame.memoryWordSize().multiply(32))
//                .toHexString());
//      } else {
        traceLine.put("memory", "0x");
//      }
      traceLine.put("memSize", frame.memoryByteSize());

      final String error =
          executeResult
              .getHaltReason()
              .map(ExceptionalHaltReason::getDescription)
              .orElse(
                  frame
                      .getRevertReason()
                      .map(bytes -> new String(bytes.toArrayUnsafe(), StandardCharsets.UTF_8))
                      .orElse(""));

      traceLine.put("opName", frame.getCurrentOperation().getName());
      traceLine.put("error", error);
      LOG.info(traceLine.toString());
//    }
  }

  @VisibleForTesting
  Operation operationAtOffset(final Code code, final int contractAccountVersion, final int offset) {
    final Bytes bytecode = code.getBytes();
    // If the length of the program code is shorter than the required offset, halt execution.
    if (offset >= bytecode.size()) {
      return endOfScriptStop;
    }

    final byte opcode = bytecode.get(offset);
    final Operation operation = operations.get(opcode, contractAccountVersion);
    if (operation == null) {
      return new InvalidOperation(opcode, null);
    } else {
      return operation;
    }
  }
}
