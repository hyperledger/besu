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
package org.hyperledger.besu.evm.operation;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.Words;

/** The Self destruct operation. */
public class SelfDestructOperation extends AbstractOperation {

  final boolean eip6780Semantics;

  /**
   * Instantiates a new Self destruct operation.
   *
   * @param gasCalculator the gas calculator
   */
  public SelfDestructOperation(final GasCalculator gasCalculator) {
    this(gasCalculator, false);
  }

  /**
   * Instantiates a new Self destruct operation, with an optional EIP-6780 semantics flag. EIP-6780
   * will only remove an account if the account was created within the current transaction. All
   * other semantics remain.
   *
   * @param gasCalculator the gas calculator
   * @param eip6780Semantics Enforce EIP6780 semantics.
   */
  public SelfDestructOperation(final GasCalculator gasCalculator, final boolean eip6780Semantics) {
    super(0xFF, "SELFDESTRUCT", 1, 0, gasCalculator);
    this.eip6780Semantics = eip6780Semantics;
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    // First calculate cost.  There's a bit of yak shaving getting values to calculate the cost.
    final Address beneficiaryAddress = Words.toAddress(frame.popStackItem());
    // Because of weird EIP150/158 reasons we care about a null account, so we can't merge this.
    final Account beneficiaryNullable = frame.getWorldUpdater().get(beneficiaryAddress);
    final boolean beneficiaryIsWarm =
        frame.warmUpAddress(beneficiaryAddress) || gasCalculator().isPrecompile(beneficiaryAddress);

    final Address originatorAddress = frame.getRecipientAddress();
    final MutableAccount originatorAccount = frame.getWorldUpdater().getAccount(originatorAddress);
    final Wei originatorBalance = originatorAccount.getBalance();

    final long cost =
        gasCalculator().selfDestructOperationGasCost(beneficiaryNullable, originatorBalance)
            + (beneficiaryIsWarm ? 0L : gasCalculator().getColdAccountAccessCost());

    // With the cost we can test for two early WithdrawalRequests: static or not enough gas.
    if (frame.isStatic()) {
      return new OperationResult(cost, ExceptionalHaltReason.ILLEGAL_STATE_CHANGE);
    } else if (frame.getRemainingGas() < cost) {
      return new OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_GAS);
    }

    // We passed preliminary checks, get mutable accounts.
    final MutableAccount beneficiaryAccount =
        frame.getWorldUpdater().getOrCreate(beneficiaryAddress);

    // Do the "sweep," all modes send all originator balance to the beneficiary account.
    originatorAccount.decrementBalance(originatorBalance);
    beneficiaryAccount.incrementBalance(originatorBalance);

    // If we are actually destroying the originator (pre-Cancun or same-tx-create) we need to
    // explicitly zero out the account balance (destroying ether/value if the originator is the
    // beneficiary) as well as tag it for later self-destruct cleanup.
    if (!eip6780Semantics || frame.wasCreatedInTransaction(originatorAccount.getAddress())) {
      frame.addSelfDestruct(originatorAccount.getAddress());
      originatorAccount.setBalance(Wei.ZERO);
    }

    // Add refund in message frame.
    frame.addRefund(beneficiaryAddress, originatorBalance);

    // Set frame to CODE_SUCCESS so that the frame performs a normal halt.
    frame.setState(MessageFrame.State.CODE_SUCCESS);

    return new OperationResult(cost, null);
  }
}
