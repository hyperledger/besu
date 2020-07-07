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

import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.MutableAccount;
import org.hyperledger.besu.ethereum.vm.AbstractOperation;
import org.hyperledger.besu.ethereum.vm.EVM;
import org.hyperledger.besu.ethereum.vm.ExceptionalHaltReason;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.vm.OperandStack.UnderflowException;

import java.util.Optional;

import org.apache.tuweni.units.bigints.UInt256;

public class SStoreOperation extends AbstractOperation {

  public static final Gas FRONTIER_MINIMUM = Gas.ZERO;
  public static final Gas EIP_1706_MINIMUM = Gas.of(2300);

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
    try {
      if (frame.isStatic()) {
        return ILLEGAL_STATE_CHANGE;
      }

      final UInt256 key = UInt256.fromBytes(frame.popStackItem());
      final UInt256 value = UInt256.fromBytes(frame.popStackItem());

      final MutableAccount account =
          frame.getWorldState().getAccount(frame.getRecipientAddress()).getMutable();
      if (account == null) {
        return ILLEGAL_STATE_CHANGE;
      }
      final Gas cost = gasCalculator().calculateStorageCost(account, key, value);
      final Optional<Gas> optionalCost = Optional.of(cost);
      final Gas remainingGas = frame.getRemainingGas();
      if (remainingGas.compareTo(cost) < 0) {
        return new OperationResult(
            optionalCost, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
      }
      if (remainingGas.compareTo(minumumGasRemaining) <= 0) {
        return new OperationResult(
            Optional.of(minumumGasRemaining), Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
      }

      // Increment the refund counter.
      frame.incrementGasRefund(gasCalculator().calculateStorageRefundAmount(account, key, value));

      account.setStorageValue(key, value);
      frame.storageWasUpdated(key, value.toBytes());
      return new OperationResult(optionalCost, Optional.empty());
    } catch (final UnderflowException ue) {
      return UNDERFLOW_RESPONSE;
    }
  }
}
