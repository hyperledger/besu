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
package org.hyperledger.besu.ethereum.vm.operations;

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.MutableAccount;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.vm.AbstractOperation;
import org.hyperledger.besu.ethereum.vm.Code;
import org.hyperledger.besu.ethereum.vm.EVM;
import org.hyperledger.besu.ethereum.vm.ExceptionalHaltReason;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.vm.Words;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.uint.UInt256;

import java.util.EnumSet;
import java.util.Optional;

public abstract class AbstractCreateOperation extends AbstractOperation {

  public AbstractCreateOperation(
      final int opcode,
      final String name,
      final int stackItemsConsumed,
      final int stackItemsProduced,
      final boolean updatesProgramCounter,
      final int opSize,
      final GasCalculator gasCalculator) {
    super(
        opcode,
        name,
        stackItemsConsumed,
        stackItemsProduced,
        updatesProgramCounter,
        opSize,
        gasCalculator);
  }

  @Override
  public void execute(final MessageFrame frame) {
    final Wei value = Wei.wrap(frame.getStackItem(0));

    final Address address = frame.getRecipientAddress();
    final MutableAccount account = frame.getWorldState().getAccount(address).getMutable();

    frame.clearReturnData();

    if (value.compareTo(account.getBalance()) > 0 || frame.getMessageStackDepth() >= 1024) {
      fail(frame);
    } else {
      spawnChildMessage(frame);
    }
  }

  protected abstract Address targetContractAddress(MessageFrame frame);

  @Override
  public Optional<ExceptionalHaltReason> exceptionalHaltCondition(
      final MessageFrame frame,
      final EnumSet<ExceptionalHaltReason> previousReasons,
      final EVM evm) {
    return frame.isStatic()
        ? Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE)
        : Optional.empty();
  }

  private void fail(final MessageFrame frame) {
    final UInt256 inputOffset = frame.getStackItem(1).asUInt256();
    final UInt256 inputSize = frame.getStackItem(2).asUInt256();
    frame.readMemory(inputOffset, inputSize);
    frame.popStackItems(getStackItemsConsumed());
    frame.pushStackItem(Bytes32.ZERO);
  }

  private void spawnChildMessage(final MessageFrame frame) {
    final Address address = frame.getRecipientAddress();
    final MutableAccount account = frame.getWorldState().getAccount(address).getMutable();

    account.incrementNonce();

    final Wei value = Wei.wrap(frame.getStackItem(0));
    final UInt256 inputOffset = frame.getStackItem(1).asUInt256();
    final UInt256 inputSize = frame.getStackItem(2).asUInt256();
    final BytesValue inputData = frame.readMemory(inputOffset, inputSize);

    final Address contractAddress = targetContractAddress(frame);

    final Gas childGasStipend = gasCalculator().gasAvailableForChildCreate(frame.getRemainingGas());
    frame.decrementRemainingGas(childGasStipend);

    final MessageFrame childFrame =
        MessageFrame.builder()
            .type(MessageFrame.Type.CONTRACT_CREATION)
            .messageFrameStack(frame.getMessageFrameStack())
            .blockchain(frame.getBlockchain())
            .worldState(frame.getWorldState().updater())
            .initialGas(childGasStipend)
            .address(contractAddress)
            .originator(frame.getOriginatorAddress())
            .contract(contractAddress)
            .contractAccountVersion(frame.getContractAccountVersion())
            .gasPrice(frame.getGasPrice())
            .inputData(BytesValue.EMPTY)
            .sender(frame.getRecipientAddress())
            .value(value)
            .apparentValue(value)
            .code(new Code(inputData))
            .blockHeader(frame.getBlockHeader())
            .depth(frame.getMessageStackDepth() + 1)
            .completer(child -> complete(frame, child))
            .miningBeneficiary(frame.getMiningBeneficiary())
            .blockHashLookup(frame.getBlockHashLookup())
            .maxStackSize(frame.getMaxStackSize())
            .build();

    frame.getMessageFrameStack().addFirst(childFrame);
    frame.setState(MessageFrame.State.CODE_SUSPENDED);
  }

  private void complete(final MessageFrame frame, final MessageFrame childFrame) {
    frame.setState(MessageFrame.State.CODE_EXECUTING);

    frame.incrementRemainingGas(childFrame.getRemainingGas());
    frame.addLogs(childFrame.getLogs());
    frame.addSelfDestructs(childFrame.getSelfDestructs());
    frame.incrementGasRefund(childFrame.getGasRefund());

    frame.popStackItems(getStackItemsConsumed());

    if (childFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
      frame.pushStackItem(Words.fromAddress(childFrame.getContractAddress()));
    } else {
      frame.setReturnData(childFrame.getOutputData());
      frame.pushStackItem(Bytes32.ZERO);
    }

    final int currentPC = frame.getPC();
    frame.setPC(currentPC + 1);
  }
}
