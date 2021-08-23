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
package org.hyperledger.besu.evm.operations;

import org.hyperledger.besu.evm.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.EVMWorldState;
import org.hyperledger.besu.evm.ExceptionalHaltReason;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.GasCalculator;
import org.hyperledger.besu.evm.MessageFrame;
import org.hyperledger.besu.evm.Wei;
import org.hyperledger.besu.evm.Words;
import org.hyperledger.besu.plugin.data.Account;

import java.util.Optional;

public class SelfDestructOperation extends AbstractOperation {

  public SelfDestructOperation(final GasCalculator gasCalculator) {
    super(0xFF, "SELFDESTRUCT", 1, 0, false, 1, gasCalculator);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    final Address recipientAddress = Words.toAddress(frame.popStackItem());

    // because of weird EIP150/158 reasons we care about a null account so we can't merge this.
    EVMWorldState evmWorldState = frame.getWorldState();
    final Account recipientNullable = evmWorldState.getAccount(recipientAddress);
    final Wei inheritance =
        Wei.wrap(evmWorldState.getAccount(frame.getRecipientAddress()).getBalance().getAsBytes32());

    final boolean accountIsWarm =
        frame.warmUpAddress(recipientAddress) || gasCalculator().isPrecompile(recipientAddress);

    final Gas cost =
        gasCalculator()
            .selfDestructOperationGasCost(recipientNullable, inheritance)
            .plus(accountIsWarm ? Gas.ZERO : gasCalculator().getColdAccountAccessCost());
    final Optional<Gas> optionalCost = Optional.of(cost);

    if (frame.isStatic()) {
      return new OperationResult(
          optionalCost, Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE));
    } else if (frame.getRemainingGas().compareTo(cost) < 0) {
      return new OperationResult(optionalCost, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
    }

    final Address address = frame.getRecipientAddress();
    final Account account = evmWorldState.getAccount(address);

    frame.addSelfDestruct(address);

    final Optional<Account> maybeRecipient = evmWorldState.getOrCreateAccount(recipientAddress);

    if (maybeRecipient.isEmpty()) {
      // FIXME a better halt reason
      return new OperationResult(
          optionalCost, Optional.of(ExceptionalHaltReason.INVALID_OPERATION));
    }
    final Account recipient = maybeRecipient.get();

    if (!account.getAddress().equals(recipient.getAddress())) {
      evmWorldState.updateAccountBalance(
          Address.fromPlugin(recipient.getAddress()),
          Wei.fromQuantity(recipient.getBalance()).add(Wei.fromQuantity(account.getBalance())));
    }

    // add refund in message frame
    frame.addRefund(
        Address.fromPlugin(recipient.getAddress()), Wei.fromQuantity(account.getBalance()));

    evmWorldState.updateAccountBalance(Address.fromPlugin(account.getAddress()), Wei.ZERO);

    frame.setState(MessageFrame.State.CODE_SUCCESS);
    return new OperationResult(optionalCost, Optional.empty());
  }
}
