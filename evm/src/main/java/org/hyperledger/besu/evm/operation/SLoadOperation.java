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
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.FixedStack.OverflowException;
import org.hyperledger.besu.evm.internal.FixedStack.UnderflowException;

import java.util.Optional;
import java.util.OptionalLong;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class SLoadOperation extends AbstractOperation {

  private final OptionalLong warmCost;
  private final OptionalLong coldCost;

  private final OperationResult warmSuccess;
  private final OperationResult coldSuccess;

  public SLoadOperation(final GasCalculator gasCalculator) {
    super(0x54, "SLOAD", 1, 1, 1, gasCalculator);
    final long baseCost = gasCalculator.getSloadOperationGasCost();
    warmCost = OptionalLong.of(baseCost + gasCalculator.getWarmStorageReadCost());
    coldCost = OptionalLong.of(baseCost + gasCalculator.getColdSloadCost());

    warmSuccess = new OperationResult(warmCost, Optional.empty());
    coldSuccess = new OperationResult(coldCost, Optional.empty());
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    try {
      final Account account = frame.getWorldUpdater().get(frame.getRecipientAddress());
      final Address address = account.getAddress();
      final Bytes32 key = UInt256.fromBytes(frame.popStackItem());
      final boolean slotIsWarm = frame.warmUpStorage(address, key);
      final OptionalLong cost = slotIsWarm ? warmCost : coldCost;
      if (frame.getRemainingGas() < cost.orElse(0L)) {
        return new OperationResult(cost, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
      } else {
        frame.pushStackItem(account.getStorageValue(UInt256.fromBytes(key)));

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
