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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.MutableAccount;
import org.hyperledger.besu.ethereum.vm.AbstractOperation;
import org.hyperledger.besu.ethereum.vm.EVM;
import org.hyperledger.besu.ethereum.vm.ExceptionalHaltReason;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;

import java.util.Optional;

import org.apache.tuweni.units.bigints.UInt256;

public class SStoreOperation extends AbstractOperation {

  public static final Gas FRONTIER_MINIMUM = Gas.ZERO;
  public static final Gas EIP_1706_MINIMUM = Gas.of(2300);

  protected static final OperationResult ILLEGAL_STATE_CHANGE =
      new OperationResult(
          Optional.empty(), Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE));

  private final Gas minumumGasRemaining;

  public SStoreOperation(final GasCalculator gasCalculator, final Gas minumumGasRemaining) {
    super(0x55, "SSTORE", 2, 0, false, 1, gasCalculator);
    this.minumumGasRemaining = minumumGasRemaining;
  }

  public Gas getMinumumGasRemaining() {
    return minumumGasRemaining;
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {

    final UInt256 key = frame.popStackItem();
    final UInt256 value = frame.popStackItem();

    final MutableAccount account =
        frame.getWorldState().getAccount(frame.getRecipientAddress()).getMutable();
    if (account == null) {
      return ILLEGAL_STATE_CHANGE;
    }

    final Address address = account.getAddress();
    final boolean slotIsWarm = frame.warmUpStorage(address, key);
    final Gas cost =
        gasCalculator()
            .calculateStorageCost(account, key, value)
            .plus(slotIsWarm ? Gas.ZERO : gasCalculator().getColdSloadCost());

    final Optional<Gas> optionalCost = Optional.of(cost);
    final Gas remainingGas = frame.getRemainingGas();
    if (frame.isStatic()) {
      return new OperationResult(
          optionalCost, Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE));
    } else if (remainingGas.compareTo(cost) < 0) {
      return new OperationResult(optionalCost, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
    } else if (remainingGas.compareTo(minumumGasRemaining) <= 0) {
      return new OperationResult(
          Optional.of(minumumGasRemaining), Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
    }

    // Increment the refund counter.
    frame.incrementGasRefund(gasCalculator().calculateStorageRefundAmount(account, key, value));

    account.setStorageValue(key, value);
    frame.storageWasUpdated(key, value);
    return new OperationResult(optionalCost, Optional.empty());
  }
}
