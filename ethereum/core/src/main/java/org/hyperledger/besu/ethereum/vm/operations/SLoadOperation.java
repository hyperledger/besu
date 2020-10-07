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
import org.hyperledger.besu.ethereum.vm.AbstractOperation;
import org.hyperledger.besu.ethereum.vm.EVM;
import org.hyperledger.besu.ethereum.vm.ExceptionalHaltReason;
import org.hyperledger.besu.ethereum.vm.FixedStack.OverflowException;
import org.hyperledger.besu.ethereum.vm.FixedStack.UnderflowException;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class SLoadOperation extends AbstractOperation {

  private final Optional<Gas> warmCost;
  private final Optional<Gas> coldCost;

  private final OperationResult warmSuccess;
  private final OperationResult coldSuccess;

  public SLoadOperation(final GasCalculator gasCalculator) {
    super(0x54, "SLOAD", 1, 1, false, 1, gasCalculator);
    final Gas baseCost = gasCalculator.getSloadOperationGasCost();
    warmCost = Optional.of(baseCost.plus(gasCalculator.getWarmStorageReadCost()));
    coldCost = Optional.of(baseCost.plus(gasCalculator.getColdSloadCost()));

    warmSuccess = new OperationResult(warmCost, Optional.empty());
    coldSuccess = new OperationResult(coldCost, Optional.empty());
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    try {
      final Account account = frame.getWorldState().get(frame.getRecipientAddress());
      final Address address = account.getAddress();
      final Bytes32 key = frame.popStackItem();
      final boolean slotIsWarm = frame.warmUpStorage(address, key);
      final Optional<Gas> optionalCost = slotIsWarm ? warmCost : coldCost;
      if (frame.getRemainingGas().compareTo(optionalCost.get()) < 0) {
        return new OperationResult(
            optionalCost, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
      } else {
        frame.pushStackItem(account.getStorageValue(UInt256.fromBytes(key)).toBytes());

        return slotIsWarm ? warmSuccess : coldSuccess;
      }
    } catch (final UnderflowException ufe) {
      return new OperationResult(
          warmCost, Optional.of(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS));
    } catch (final OverflowException ofe) {
      return new OperationResult(warmCost, Optional.of(ExceptionalHaltReason.TOO_MANY_STACK_ITEMS));
    }
  }
}
