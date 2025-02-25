/*
 * Copyright contributors to Hyperledger Besu.
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

import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.code.CodeV0;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.frame.MessageFrame.State;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.CodeDelegationGasCostHelper;

import org.apache.tuweni.bytes.Bytes;

/**
 * A skeleton class for implementing call operations.
 *
 * <p>A call operation creates a child message call from the current message context, allows it to
 * execute, and then updates the current message context based on its execution.
 */
public abstract class AbstractCallOperation extends AbstractOperation {

  /** The constant UNDERFLOW_RESPONSE. */
  protected static final OperationResult UNDERFLOW_RESPONSE =
      new OperationResult(0L, ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);

  static final Bytes LEGACY_SUCCESS_STACK_ITEM = BYTES_ONE;
  static final Bytes LEGACY_FAILURE_STACK_ITEM = Bytes.EMPTY;

  /**
   * Instantiates a new Abstract call operation.
   *
   * @param opcode the opcode
   * @param name the name
   * @param stackItemsConsumed the stack items consumed
   * @param stackItemsProduced the stack items produced
   * @param gasCalculator the gas calculator
   */
  AbstractCallOperation(
      final int opcode,
      final String name,
      final int stackItemsConsumed,
      final int stackItemsProduced,
      final GasCalculator gasCalculator) {
    super(opcode, name, stackItemsConsumed, stackItemsProduced, gasCalculator);
  }

  /**
   * Returns the additional gas to provide the call operation.
   *
   * @param frame The current message frame
   * @return the additional gas to provide the call operation
   */
  protected long gas(final MessageFrame frame) {
    return clampedToLong(frame.getStackItem(0));
  }

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
  public abstract long gasAvailableForChildCall(MessageFrame frame);

  /**
   * Returns whether the child message call should be static.
   *
   * @param frame The current message frame
   * @return {@code true} if the child message call should be static; otherwise {@code false}
   */
  protected boolean isStatic(final MessageFrame frame) {
    return frame.isStatic();
  }

  /**
   * Returns whether the child message call is a delegate call.
   *
   * @return {@code true} if the child message call is a delegate call; otherwise {@code false}
   */
  protected boolean isDelegate() {
    return false;
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    // manual check because some reads won't come until the "complete" step.
    if (frame.stackSize() < getStackItemsConsumed()) {
      return UNDERFLOW_RESPONSE;
    }

    final Address to = to(frame);
    final boolean accountIsWarm = frame.warmUpAddress(to) || gasCalculator().isPrecompile(to);
    final long cost = cost(frame, accountIsWarm);
    if (frame.getRemainingGas() < cost) {
      return new OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_GAS);
    }
    frame.decrementRemainingGas(cost);

    frame.clearReturnData();

    final Account contract = frame.getWorldUpdater().get(to);

    if (contract != null && contract.hasDelegatedCode()) {
      if (contract.getCodeDelegationTargetCode().isEmpty()) {
        throw new RuntimeException("A delegated code account must have delegated code");
      }

      if (contract.getCodeDelegationTargetHash().isEmpty()) {
        throw new RuntimeException("A delegated code account must have a delegated code hash");
      }

      final long codeDelegationResolutionGas =
          CodeDelegationGasCostHelper.codeDelegationGasCost(frame, gasCalculator(), contract);

      if (frame.getRemainingGas() < codeDelegationResolutionGas) {
        return new Operation.OperationResult(
            codeDelegationResolutionGas, ExceptionalHaltReason.INSUFFICIENT_GAS);
      }

      frame.decrementRemainingGas(codeDelegationResolutionGas);
    }

    final Account account = frame.getWorldUpdater().get(frame.getRecipientAddress());
    final Wei balance = account == null ? Wei.ZERO : account.getBalance();
    // If the call is sending more value than the account has or the message frame is to deep
    // return a failed call
    if (value(frame).compareTo(balance) > 0 || frame.getDepth() >= 1024) {
      frame.expandMemory(inputDataOffset(frame), inputDataLength(frame));
      frame.expandMemory(outputDataOffset(frame), outputDataLength(frame));
      // For the following, we either increment the gas or return zero, so we don't get double
      // charged. If we return zero then the traces don't have the right per-opcode cost.
      frame.incrementRemainingGas(gasAvailableForChildCall(frame) + cost);
      frame.popStackItems(getStackItemsConsumed());
      frame.pushStackItem(LEGACY_FAILURE_STACK_ITEM);
      return new OperationResult(cost, null);
    }

    final Bytes inputData = frame.readMutableMemory(inputDataOffset(frame), inputDataLength(frame));

    final Code code = getCode(evm, contract);

    // invalid code results in a quick exit
    if (!code.isValid()) {
      return new OperationResult(cost, ExceptionalHaltReason.INVALID_CODE, 0);
    }

    MessageFrame.builder()
        .parentMessageFrame(frame)
        .type(MessageFrame.Type.MESSAGE_CALL)
        .initialGas(gasAvailableForChildCall(frame))
        .address(address(frame))
        .contract(to)
        .inputData(inputData)
        .sender(sender(frame))
        .value(value(frame))
        .apparentValue(apparentValue(frame))
        .code(code)
        .isStatic(isStatic(frame))
        .completer(child -> complete(frame, child))
        .build();
    // see note in stack depth check about incrementing cost
    frame.incrementRemainingGas(cost);

    frame.setState(MessageFrame.State.CODE_SUSPENDED);
    return new OperationResult(cost, null, 0);
  }

  /**
   * Calculates Cost.
   *
   * @param frame the frame
   * @return the long
   * @deprecated use the form with the `accountIsWarm` boolean
   */
  @Deprecated(since = "24.2.0", forRemoval = true)
  @SuppressWarnings("InlineMeSuggester") // downstream users override, so @InlineMe is inappropriate
  public long cost(final MessageFrame frame) {
    return cost(frame, true);
  }

  /**
   * Calculates Cost.
   *
   * @param frame the frame
   * @param accountIsWarm whether the contract being called is "warm" as per EIP-2929.
   * @return the long
   */
  public long cost(final MessageFrame frame, final boolean accountIsWarm) {
    final long stipend = gas(frame);
    final long inputDataOffset = inputDataOffset(frame);
    final long inputDataLength = inputDataLength(frame);
    final long outputDataOffset = outputDataOffset(frame);
    final long outputDataLength = outputDataLength(frame);
    final Account recipient = frame.getWorldUpdater().get(address(frame));
    final Address to = to(frame);
    GasCalculator gasCalculator = gasCalculator();

    return gasCalculator.callOperationGasCost(
        frame,
        stipend,
        inputDataOffset,
        inputDataLength,
        outputDataOffset,
        outputDataLength,
        value(frame),
        recipient,
        to,
        accountIsWarm);
  }

  /**
   * Complete.
   *
   * @param frame the frame
   * @param childFrame the child frame
   */
  public void complete(final MessageFrame frame, final MessageFrame childFrame) {
    frame.setState(MessageFrame.State.CODE_EXECUTING);

    final long outputOffset = outputDataOffset(frame);
    final long outputSize = outputDataLength(frame);
    final Bytes outputData = childFrame.getOutputData();

    if (outputSize > outputData.size()) {
      frame.expandMemory(outputOffset, outputSize);
      frame.writeMemory(outputOffset, outputData.size(), outputData, true);
    } else if (outputSize > 0) {
      frame.writeMemory(outputOffset, outputSize, outputData, true);
    }

    frame.setReturnData(outputData);
    frame.addLogs(childFrame.getLogs());
    frame.addSelfDestructs(childFrame.getSelfDestructs());
    frame.addCreates(childFrame.getCreates());

    final long gasRemaining = childFrame.getRemainingGas();
    frame.incrementRemainingGas(gasRemaining);

    frame.popStackItems(getStackItemsConsumed());
    Bytes resultItem;

    resultItem = getCallResultStackItem(childFrame);
    frame.pushStackItem(resultItem);

    final int currentPC = frame.getPC();
    frame.setPC(currentPC + 1);
  }

  Bytes getCallResultStackItem(final MessageFrame childFrame) {
    if (childFrame.getState() == State.COMPLETED_SUCCESS) {
      return LEGACY_SUCCESS_STACK_ITEM;
    } else {
      return LEGACY_FAILURE_STACK_ITEM;
    }
  }

  /**
   * Gets the code from the contract or EOA with delegated code.
   *
   * @param evm the evm
   * @param account the account which needs to be retrieved
   * @return the code
   */
  protected static Code getCode(final EVM evm, final Account account) {
    if (account == null) {
      return CodeV0.EMPTY_CODE;
    }

    if (account.hasDelegatedCode()) {
      return evm.getCode(
          account.getCodeDelegationTargetHash().get(), account.getCodeDelegationTargetCode().get());
    }

    return evm.getCode(account.getCodeHash(), account.getCode());
  }
}
