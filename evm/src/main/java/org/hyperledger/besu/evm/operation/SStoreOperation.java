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
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import java.util.Optional;
import java.util.OptionalLong;

import org.apache.tuweni.units.bigints.UInt256;

public class SStoreOperation extends AbstractOperation {

  public static final long FRONTIER_MINIMUM = 0L;
  public static final long EIP_1706_MINIMUM = 2300L;

  protected static final OperationResult ILLEGAL_STATE_CHANGE =
      new OperationResult(
          OptionalLong.of(0L), Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE));

  private final long minimumGasRemaining;

  public SStoreOperation(final GasCalculator gasCalculator, final long minimumGasRemaining) {
    super(0x55, "SSTORE", 2, 0, 1, gasCalculator);
    this.minimumGasRemaining = minimumGasRemaining;
  }

  public long getMinimumGasRemaining() {
    return minimumGasRemaining;
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {

    final UInt256 key = UInt256.fromBytes(frame.popStackItem());
    final UInt256 value = UInt256.fromBytes(frame.popStackItem());

    final MutableAccount account =
        frame.getWorldUpdater().getAccount(frame.getRecipientAddress()).getMutable();
    if (account == null) {
      return ILLEGAL_STATE_CHANGE;
    }

    final Address address = account.getAddress();
    final boolean slotIsWarm = frame.warmUpStorage(address, key);
    final long cost =
        gasCalculator().calculateStorageCost(account, key, value)
            + (slotIsWarm ? 0L : gasCalculator().getColdSloadCost());

    final long remainingGas = frame.getRemainingGas();
    if (frame.isStatic()) {
      return new OperationResult(
          OptionalLong.of(remainingGas), Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE));
    } else if (remainingGas < cost) {
      return new OperationResult(
          OptionalLong.of(cost), Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
    } else if (remainingGas <= minimumGasRemaining) {
      return new OperationResult(
          OptionalLong.of(minimumGasRemaining),
          Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
    }

    // Increment the refund counter.
    frame.incrementGasRefund(gasCalculator().calculateStorageRefundAmount(account, key, value));

    account.setStorageValue(key, value);
    frame.storageWasUpdated(key, value);
    return new OperationResult(OptionalLong.of(cost), Optional.empty());
  }
}
