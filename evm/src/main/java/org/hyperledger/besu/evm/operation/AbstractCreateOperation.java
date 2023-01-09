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

import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.code.CodeFactory;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.Words;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public abstract class AbstractCreateOperation extends AbstractOperation {

  protected static final OperationResult UNDERFLOW_RESPONSE =
      new OperationResult(0L, ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);

  protected AbstractCreateOperation(
      final int opcode,
      final String name,
      final int stackItemsConsumed,
      final int stackItemsProduced,
      final GasCalculator gasCalculator) {
    super(opcode, name, stackItemsConsumed, stackItemsProduced, gasCalculator);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    // manual check because some reads won't come until the "complete" step.
    if (frame.stackSize() < getStackItemsConsumed()) {
      return UNDERFLOW_RESPONSE;
    }

    final long cost = cost(frame);
    if (frame.isStatic()) {
      return new OperationResult(cost, ExceptionalHaltReason.ILLEGAL_STATE_CHANGE);
    } else if (frame.getRemainingGas() < cost) {
      return new OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_GAS);
    }
    final Wei value = Wei.wrap(frame.getStackItem(0));

    final Address address = frame.getRecipientAddress();
    final MutableAccount account = frame.getWorldUpdater().getAccount(address).getMutable();

    frame.clearReturnData();

    if (value.compareTo(account.getBalance()) > 0
        || frame.getMessageStackDepth() >= 1024
        || account.getNonce() == -1) {
      fail(frame);
    } else {
      account.incrementNonce();

      final long inputOffset = clampedToLong(frame.getStackItem(1));
      final long inputSize = clampedToLong(frame.getStackItem(2));
      final Bytes inputData = frame.readMemory(inputOffset, inputSize);
      Code code = evm.getCode(Hash.hash(inputData), inputData);

      if (code.isValid()) {
        frame.decrementRemainingGas(cost);
        spawnChildMessage(frame, code, evm);
        frame.incrementRemainingGas(cost);
      } else {
        fail(frame);
      }
    }

    return new OperationResult(cost, null);
  }

  protected abstract long cost(final MessageFrame frame);

  protected abstract Address targetContractAddress(MessageFrame frame);

  private void fail(final MessageFrame frame) {
    final long inputOffset = clampedToLong(frame.getStackItem(1));
    final long inputSize = clampedToLong(frame.getStackItem(2));
    frame.readMutableMemory(inputOffset, inputSize);
    frame.popStackItems(getStackItemsConsumed());
    frame.pushStackItem(UInt256.ZERO);
  }

  private void spawnChildMessage(final MessageFrame frame, final Code code, final EVM evm) {
    final Wei value = Wei.wrap(frame.getStackItem(0));

    final Address contractAddress = targetContractAddress(frame);

    final long childGasStipend =
        gasCalculator().gasAvailableForChildCreate(frame.getRemainingGas());
    frame.decrementRemainingGas(childGasStipend);

    final MessageFrame childFrame =
        MessageFrame.builder()
            .type(MessageFrame.Type.CONTRACT_CREATION)
            .messageFrameStack(frame.getMessageFrameStack())
            .worldUpdater(frame.getWorldUpdater().updater())
            .initialGas(childGasStipend)
            .address(contractAddress)
            .originator(frame.getOriginatorAddress())
            .contract(contractAddress)
            .gasPrice(frame.getGasPrice())
            .inputData(Bytes.EMPTY)
            .sender(frame.getRecipientAddress())
            .value(value)
            .apparentValue(value)
            .code(code)
            .blockValues(frame.getBlockValues())
            .depth(frame.getMessageStackDepth() + 1)
            .completer(child -> complete(frame, child, evm))
            .miningBeneficiary(frame.getMiningBeneficiary())
            .blockHashLookup(frame.getBlockHashLookup())
            .maxStackSize(frame.getMaxStackSize())
            .build();

    frame.getMessageFrameStack().addFirst(childFrame);
    frame.setState(MessageFrame.State.CODE_SUSPENDED);
  }

  private void complete(final MessageFrame frame, final MessageFrame childFrame, final EVM evm) {
    frame.setState(MessageFrame.State.CODE_EXECUTING);

    Code outputCode =
        CodeFactory.createCode(
            childFrame.getOutputData(),
            Hash.hash(childFrame.getOutputData()),
            evm.getMaxEOFVersion(),
            true);
    frame.popStackItems(getStackItemsConsumed());

    if (outputCode.isValid()) {
      frame.incrementRemainingGas(childFrame.getRemainingGas());
      frame.addLogs(childFrame.getLogs());
      frame.addSelfDestructs(childFrame.getSelfDestructs());
      frame.incrementGasRefund(childFrame.getGasRefund());

      if (childFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
        frame.mergeWarmedUpFields(childFrame);
        frame.pushStackItem(Words.fromAddress(childFrame.getContractAddress()));
      } else {
        frame.setReturnData(childFrame.getOutputData());
        frame.pushStackItem(UInt256.ZERO);
      }
    } else {
      frame.getWorldUpdater().deleteAccount(childFrame.getRecipientAddress());
      frame.setReturnData(childFrame.getOutputData());
      frame.pushStackItem(UInt256.ZERO);
    }

    final int currentPC = frame.getPC();
    frame.setPC(currentPC + 1);
  }
}
