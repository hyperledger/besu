/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.vm.operations;

import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.core.MutableAccount;
import tech.pegasys.pantheon.ethereum.vm.AbstractOperation;
import tech.pegasys.pantheon.ethereum.vm.EVM;
import tech.pegasys.pantheon.ethereum.vm.ExceptionalHaltReason;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.EnumSet;
import java.util.Optional;

public class SStoreOperation extends AbstractOperation {

  public static final Gas FRONTIER_MINIMUM = Gas.ZERO;
  public static final Gas EIP_1706_MINIMUM = Gas.of(2300);

  private final Gas minumumGasRemaining;

  public SStoreOperation(final GasCalculator gasCalculator, final Gas minumumGasRemaining) {
    super(0x55, "SSTORE", 2, 0, false, 1, gasCalculator);
    this.minumumGasRemaining = minumumGasRemaining;
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    final UInt256 key = frame.getStackItem(0).asUInt256();
    final UInt256 newValue = frame.getStackItem(1).asUInt256();

    final Account account = frame.getWorldState().get(frame.getRecipientAddress());
    return gasCalculator().calculateStorageCost(account, key, newValue);
  }

  @Override
  public void execute(final MessageFrame frame) {
    final UInt256 key = frame.popStackItem().asUInt256();
    final UInt256 value = frame.popStackItem().asUInt256();

    final MutableAccount account = frame.getWorldState().getMutable(frame.getRecipientAddress());
    assert account != null : "VM account should exists";

    // Increment the refund counter.
    frame.incrementGasRefund(gasCalculator().calculateStorageRefundAmount(account, key, value));

    account.setStorageValue(key.copy(), value.copy());
  }

  @Override
  public Optional<ExceptionalHaltReason> exceptionalHaltCondition(
      final MessageFrame frame,
      final EnumSet<ExceptionalHaltReason> previousReasons,
      final EVM evm) {
    if (frame.isStatic()) {
      return Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE);
    } else if (frame.getRemainingGas().compareTo(minumumGasRemaining) <= 0) {
      return Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS);
    } else {
      return Optional.empty();
    }
  }
}
