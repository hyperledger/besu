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

import static org.hyperledger.besu.evm.internal.Words.clampedAdd;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.worldstate.CodeDelegationGasCostHelper;

import javax.annotation.Nonnull;

import org.apache.tuweni.bytes.Bytes;

/**
 * A skeleton class for implementing call operations.
 *
 * <p>A call operation creates a child message call from the current message context, allows it to
 * execute, and then updates the current message context based on its execution.
 */
public abstract class AbstractExtCallOperation extends AbstractCallOperation {

  static final int STACK_TO = 0;
  static final int STACK_INPUT_OFFSET = 1;
  static final int STACK_INPUT_LENGTH = 2;

  /** EXT*CALL response indicating success */
  public static final Bytes EOF1_SUCCESS_STACK_ITEM = Bytes.EMPTY;

  /** EXT*CALL response indicating a "soft failure" */
  public static final Bytes EOF1_EXCEPTION_STACK_ITEM = BYTES_ONE;

  /** EXT*CALL response indicating a hard failure, such as a REVERT was called */
  public static final Bytes EOF1_FAILURE_STACK_ITEM = Bytes.of(2);

  /**
   * Instantiates a new Abstract call operation.
   *
   * @param opcode the opcode
   * @param name the name
   * @param stackItemsConsumed the stack items consumed
   * @param stackItemsProduced the stack items produced
   * @param gasCalculator the gas calculator
   */
  AbstractExtCallOperation(
      final int opcode,
      final String name,
      final int stackItemsConsumed,
      final int stackItemsProduced,
      final GasCalculator gasCalculator) {
    super(opcode, name, stackItemsConsumed, stackItemsProduced, gasCalculator);
  }

  @Override
  protected Address to(final MessageFrame frame) {
    return Words.toAddress(frame.getStackItem(STACK_TO));
  }

  @Override
  protected long gas(final MessageFrame frame) {
    return Long.MAX_VALUE;
  }

  @Override
  protected long outputDataOffset(final MessageFrame frame) {
    return 0;
  }

  @Override
  protected long outputDataLength(final MessageFrame frame) {
    return 0;
  }

  @Override
  public long gasAvailableForChildCall(final MessageFrame frame) {
    throw new UnsupportedOperationException("EXTCALL does not use gasAvailableForChildCall");
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    Code callingCode = frame.getCode();
    if (callingCode.getEofVersion() == 0) {
      return InvalidOperation.INVALID_RESULT;
    }

    final Bytes toBytes = frame.getStackItem(STACK_TO).trimLeadingZeros();
    final Wei value = value(frame);
    final boolean zeroValue = value.isZero();
    long inputOffset = inputDataOffset(frame);
    long inputLength = inputDataLength(frame);

    GasCalculator gasCalculator = gasCalculator();
    if (!zeroValue && isStatic(frame)) {
      return new OperationResult(
          gasCalculator.callValueTransferGasCost(), ExceptionalHaltReason.ILLEGAL_STATE_CHANGE);
    }
    if (toBytes.size() > Address.SIZE) {
      return new OperationResult(
          clampedAdd(
              clampedAdd(
                  gasCalculator.memoryExpansionGasCost(frame, inputOffset, inputLength),
                  (zeroValue ? 0 : gasCalculator.callValueTransferGasCost())),
              gasCalculator.getColdAccountAccessCost()),
          ExceptionalHaltReason.ADDRESS_OUT_OF_RANGE);
    }
    Address to = Words.toAddress(toBytes);
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

    boolean accountCreation = (contract == null || contract.isEmpty()) && !zeroValue;
    long cost =
        clampedAdd(
            clampedAdd(
                clampedAdd(
                    gasCalculator.memoryExpansionGasCost(frame, inputOffset, inputLength),
                    (zeroValue ? 0 : gasCalculator.callValueTransferGasCost())),
                (frame.warmUpAddress(to) || gasCalculator.isPrecompile(to)
                    ? gasCalculator.getWarmStorageReadCost()
                    : gasCalculator.getColdAccountAccessCost())),
            (accountCreation ? gasCalculator.newAccountGasCost() : 0));
    long currentGas = frame.getRemainingGas();
    if (currentGas < cost) {
      return new OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_GAS);
    }
    currentGas -= cost;
    frame.expandMemory(inputOffset, inputLength);

    final Code code = getCode(evm, contract);

    // invalid code results in a quick exit
    if (!code.isValid()) {
      return new OperationResult(cost, ExceptionalHaltReason.INVALID_CODE, 0);
    }

    // last exceptional failure, prepare for call or soft failures
    frame.clearReturnData();

    // delegate calls to prior EOF versions are prohibited
    if (isDelegate() && callingCode.getEofVersion() != code.getEofVersion()) {
      return softFailure(frame, cost);
    }

    long retainedGas = Math.max(currentGas / 64, gasCalculator.getMinRetainedGas());
    long childGas = currentGas - retainedGas;

    final Account account = frame.getWorldUpdater().get(frame.getRecipientAddress());
    final Wei balance = (zeroValue || account == null) ? Wei.ZERO : account.getBalance();

    // There myst be a minimum gas for a call to have access to.
    if (childGas < gasCalculator.getMinCalleeGas()) {
      return softFailure(frame, cost);
    }
    // transferring value you don't have is not a halting exception, just a failure
    if (!zeroValue && (value.compareTo(balance) > 0)) {
      return softFailure(frame, cost);
    }
    // stack too deep, for large gas systems.
    if (frame.getDepth() >= 1024) {
      return softFailure(frame, cost);
    }

    // all checks passed, do the call
    final Bytes inputData = frame.readMutableMemory(inputOffset, inputLength);

    MessageFrame.builder()
        .parentMessageFrame(frame)
        .type(MessageFrame.Type.MESSAGE_CALL)
        .initialGas(childGas)
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

    frame.setState(MessageFrame.State.CODE_SUSPENDED);
    return new OperationResult(clampedAdd(cost, childGas), null, 0);
  }

  private @Nonnull OperationResult softFailure(final MessageFrame frame, final long cost) {
    frame.popStackItems(getStackItemsConsumed());
    frame.pushStackItem(EOF1_EXCEPTION_STACK_ITEM);
    return new OperationResult(cost, null);
  }

  @Override
  Bytes getCallResultStackItem(final MessageFrame childFrame) {
    return switch (childFrame.getState()) {
      case COMPLETED_SUCCESS -> EOF1_SUCCESS_STACK_ITEM;
      case EXCEPTIONAL_HALT -> EOF1_EXCEPTION_STACK_ITEM;
      case COMPLETED_FAILED ->
          childFrame.getExceptionalHaltReason().isPresent()
              ? EOF1_FAILURE_STACK_ITEM
              : EOF1_EXCEPTION_STACK_ITEM;
      default -> EOF1_FAILURE_STACK_ITEM;
    };
  }
}
