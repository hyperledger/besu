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

import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.MutableAccount;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.vm.AbstractOperation;
import org.hyperledger.besu.ethereum.vm.EVM;
import org.hyperledger.besu.ethereum.vm.ExceptionalHaltReason;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.vm.Words;

import java.util.EnumSet;
import java.util.Optional;

public class SelfDestructOperation extends AbstractOperation {

  public SelfDestructOperation(final GasCalculator gasCalculator) {
    super(0xFF, "SELFDESTRUCT", 1, 0, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    final Address recipientAddress = Words.toAddress(frame.getStackItem(0));

    final Account recipient = frame.getWorldState().get(recipientAddress);
    final Wei inheritance = frame.getWorldState().get(frame.getRecipientAddress()).getBalance();

    return gasCalculator().selfDestructOperationGasCost(recipient, inheritance);
  }

  @Override
  public void execute(final MessageFrame frame) {
    final Address address = frame.getRecipientAddress();
    final MutableAccount account = frame.getWorldState().getAccount(address).getMutable();

    frame.addSelfDestruct(address);

    final MutableAccount recipient =
        frame.getWorldState().getOrCreate(Words.toAddress(frame.popStackItem())).getMutable();

    recipient.incrementBalance(account.getBalance());

    // add refund in message frame
    frame.addRefund(recipient.getAddress(), account.getBalance());

    account.setBalance(Wei.ZERO);

    frame.setState(MessageFrame.State.CODE_SUCCESS);
  }

  @Override
  public Optional<ExceptionalHaltReason> exceptionalHaltCondition(
      final MessageFrame frame,
      final EnumSet<ExceptionalHaltReason> previousReasons,
      final EVM evm) {
    return frame.isStatic()
        ? Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE)
        : Optional.empty();
  }
}
