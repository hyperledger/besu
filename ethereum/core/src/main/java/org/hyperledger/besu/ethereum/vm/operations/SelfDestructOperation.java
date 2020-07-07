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
import org.hyperledger.besu.ethereum.vm.EVM;
import org.hyperledger.besu.ethereum.vm.ExceptionalHaltReason;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.vm.OperandStack.UnderflowException;
import org.hyperledger.besu.ethereum.vm.Words;

import java.util.Optional;

public class SelfDestructOperation extends AbstractOperation {

  public SelfDestructOperation(final GasCalculator gasCalculator) {
    super(0xFF, "SELFDESTRUCT", 1, 0, false, 1, gasCalculator);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    try {
      if (frame.isStatic()) {
        return ILLEGAL_STATE_CHANGE;
      }

      final Address recipientAddress = Words.toAddress(frame.popStackItem());

      final MutableAccount recipient =
          frame.getWorldState().getOrCreate(recipientAddress).getMutable();
      final Wei inheritance = frame.getWorldState().get(frame.getRecipientAddress()).getBalance();

      final Gas cost = gasCalculator().selfDestructOperationGasCost(recipient, inheritance);
      final Optional<Gas> optionalCost = Optional.of(cost);
      if (frame.getRemainingGas().compareTo(cost) < 0) {
        return new OperationResult(
            optionalCost, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
      }

      final Address address = frame.getRecipientAddress();
      final MutableAccount account = frame.getWorldState().getAccount(address).getMutable();

      frame.addSelfDestruct(address);

      if (!account.getAddress().equals(recipient.getAddress())) {
        recipient.incrementBalance(account.getBalance());
      }

      // add refund in message frame
      frame.addRefund(recipient.getAddress(), account.getBalance());

      account.setBalance(Wei.ZERO);

      frame.setState(MessageFrame.State.CODE_SUCCESS);
      return new OperationResult(optionalCost, Optional.empty());
    } catch (final UnderflowException ue) {
      return UNDERFLOW_RESPONSE;
    }
  }
}
