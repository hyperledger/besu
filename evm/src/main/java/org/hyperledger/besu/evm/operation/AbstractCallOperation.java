/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.evm.operation;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import java.util.Optional;
import java.util.OptionalLong;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * A skeleton class for implementing call operations.
 *
 * <p>A call operation creates a child message call from the current message context, allows it to
 * execute, and then updates the current message context based on its execution.
 */
public abstract class AbstractCallOperation extends AbstractOperation {

  protected static final OperationResult UNDERFLOW_RESPONSE =
      new OperationResult(
          OptionalLong.of(0L), Optional.of(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS));

  protected AbstractCallOperation(
      final int opcode,
      final String name,
      final int stackItemsConsumed,
      final int stackItemsProduced,
      final int opSize,
      final GasCalculator gasCalculator) {
    super(opcode, name, stackItemsConsumed, stackItemsProduced, opSize, gasCalculator);
  }

  /**
   * Returns the additional gas to provide the call operation.
   *
   * @param frame The current message frame
   * @return the additional gas to provide the call operation
   */
  protected abstract long gas(MessageFrame frame);

  /**
   * Returns the account the call is being made to.
   *
   * @param frame The current message frame
   * @return the account the call is being made to
   */
  protected abstract Address to(MessageFrame frame);

  /**
   * Returns the value being transferred in the call
   *
   * @param frame The current message frame
   * @return the value being transferred in the call
   */
  protected abstract Wei value(MessageFrame frame);

  /**
   * Returns the apparent value being transferred in the call
   *
   * @param frame The current message frame
   * @return the apparent value being transferred in the call
   */
  protected abstract Wei apparentValue(MessageFrame frame);

  /**
   * Returns the memory offset the input data starts at.
   *
   * @param frame The current message frame
   * @return the memory offset the input data starts at
   */
  protected abstract long inputDataOffset(MessageFrame frame);

  /**
   * Returns the length of the input data to read from memory.
   *
   * @param frame The current message frame
   * @return the length of the input data to read from memory.
   */
  protected abstract long inputDataLength(MessageFrame frame);

  /**
   * Returns the memory offset the offset data starts at.
   *
   * @param frame The current message frame
   * @return the memory offset the offset data starts at
   */
  protected abstract long outputDataOffset(MessageFrame frame);

  /**
   * Returns the length of the output data to read from memory.
   *
   * @param frame The current message frame
   * @return the length of the output data to read from memory.
   */
  protected abstract long outputDataLength(MessageFrame frame);

  /**
   * Returns the account address the call operation is being performed on
   *
   * @param frame The current message frame
   * @return the account address the call operation is being performed on
   */
  protected abstract Address address(MessageFrame frame);

  /**
   * Returns the account address the call operation is being sent from
   *
   * @param frame The current message frame
   * @return the account address the call operation is being sent from
   */
  protected abstract Address sender(MessageFrame frame);

  /**
   * Returns the gas available to execute the child message call.
   *
   * @param frame The current message frame
   * @return the gas available to execute the child message call
   */
  protected abstract long gasAvailableForChildCall(MessageFrame frame);

  /**
   * Returns whether the child message call should be static.
   *
   * @param frame The current message frame
   * @return {@code true} if the child message call should be static; otherwise {@code false}
   */
  protected abstract boolean isStatic(MessageFrame frame);

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    // manual check because some reads won't come until the "complete" step.
    if (frame.stackSize() < getStackItemsConsumed()) {
      return UNDERFLOW_RESPONSE;
    }

    final long cost = cost(frame);
    if (frame.getRemainingGas() < cost) {
      return new OperationResult(
          OptionalLong.of(cost), Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
    }
    frame.decrementRemainingGas(cost);

    frame.clearReturnData();

    final Address to = to(frame);
    final Account contract = frame.getWorldUpdater().get(to);

    final Account account = frame.getWorldUpdater().get(frame.getRecipientAddress());
    final Wei balance = account == null ? Wei.ZERO : account.getBalance();
    // If the call is sending more value than the account has or the message frame is to deep
    // return a failed call
    if (value(frame).compareTo(balance) > 0 || frame.getMessageStackDepth() >= 1024) {
      frame.expandMemory(inputDataOffset(frame), inputDataLength(frame));
      frame.expandMemory(outputDataOffset(frame), outputDataLength(frame));
      frame.incrementRemainingGas(gasAvailableForChildCall(frame) + cost);
      frame.popStackItems(getStackItemsConsumed());
      frame.pushStackItem(UInt256.ZERO);
      return new OperationResult(OptionalLong.of(cost), Optional.empty());
    }

    final Bytes inputData = frame.readMutableMemory(inputDataOffset(frame), inputDataLength(frame));

    final Code code =
        contract == null
            ? Code.EMPTY_CODE
            : evm.getCode(contract.getCodeHash(), contract.getCode());

    final MessageFrame childFrame =
        MessageFrame.builder()
            .type(MessageFrame.Type.MESSAGE_CALL)
            .messageFrameStack(frame.getMessageFrameStack())
            .worldUpdater(frame.getWorldUpdater().updater())
            .initialGas(gasAvailableForChildCall(frame))
            .address(address(frame))
            .originator(frame.getOriginatorAddress())
            .contract(to)
            .gasPrice(frame.getGasPrice())
            .inputData(inputData)
            .sender(sender(frame))
            .value(value(frame))
            .apparentValue(apparentValue(frame))
            .code(code)
            .blockValues(frame.getBlockValues())
            .depth(frame.getMessageStackDepth() + 1)
            .isStatic(isStatic(frame))
            .completer(child -> complete(frame, child))
            .miningBeneficiary(frame.getMiningBeneficiary())
            .blockHashLookup(frame.getBlockHashLookup())
            .maxStackSize(frame.getMaxStackSize())
            .build();
    frame.incrementRemainingGas(cost);

    frame.getMessageFrameStack().addFirst(childFrame);
    frame.setState(MessageFrame.State.CODE_SUSPENDED);
    return new OperationResult(OptionalLong.of(cost), Optional.empty(), 0);
  }

  protected abstract long cost(final MessageFrame frame);

  public void complete(final MessageFrame frame, final MessageFrame childFrame) {
    frame.setState(MessageFrame.State.CODE_EXECUTING);

    final long outputOffset = outputDataOffset(frame);
    final long outputSize = outputDataLength(frame);
    final Bytes outputData = childFrame.getOutputData();

    if (outputSize > outputData.size()) {
      frame.expandMemory(outputOffset, outputSize);
      frame.writeMemory(outputOffset, outputData.size(), outputData, true);
    } else {
      frame.writeMemory(outputOffset, outputSize, outputData, true);
    }

    frame.setReturnData(outputData);
    frame.addLogs(childFrame.getLogs());
    frame.addSelfDestructs(childFrame.getSelfDestructs());
    frame.incrementGasRefund(childFrame.getGasRefund());

    final long gasRemaining = childFrame.getRemainingGas();
    frame.incrementRemainingGas(gasRemaining);

    frame.popStackItems(getStackItemsConsumed());
    if (childFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
      frame.mergeWarmedUpFields(childFrame);
      frame.pushStackItem(UInt256.ONE);
    } else {
      frame.pushStackItem(UInt256.ZERO);
    }

    final int currentPC = frame.getPC();
    frame.setPC(currentPC + 1);
  }
}
