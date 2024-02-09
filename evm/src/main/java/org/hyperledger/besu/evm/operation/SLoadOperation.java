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
import org.hyperledger.besu.evm.internal.OverflowException;
import org.hyperledger.besu.evm.internal.UnderflowException;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/** The SLoad operation. */
public class SLoadOperation extends AbstractOperation {

  /**
   * Instantiates a new SLoad operation.
   *
   * @param gasCalculator the gas calculator
   */
  public SLoadOperation(final GasCalculator gasCalculator) {
    super(0x54, "SLOAD", 1, 1, gasCalculator);

  }

  protected long cost(final MessageFrame frame, final Bytes32 key, final boolean slotIsWarm) {
    return gasCalculator().getSloadOperationGasCost(frame, UInt256.fromBytes(key))
            + (slotIsWarm
            ? gasCalculator().getWarmStorageReadCost()
            : gasCalculator().getColdSloadCost());
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    try {
      final Account account = frame.getWorldUpdater().get(frame.getRecipientAddress());
      final Address address = account.getAddress();
      final Bytes32 key = UInt256.fromBytes(frame.popStackItem());
      final boolean slotIsWarm = frame.warmUpStorage(address, key);

      final long cost = cost(frame, key, slotIsWarm);
      if (frame.getRemainingGas() < cost) {
        return new OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_GAS);
      } else {
        frame.pushStackItem(account.getStorageValue(UInt256.fromBytes(key)));

        return new OperationResult(cost, null);
      }
    } catch (final UnderflowException ufe) {
      //TODO VERKLE FIX THE SLOTISWARM
      return new OperationResult(cost(frame, Bytes32.ZERO,true), ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
    } catch (final OverflowException ofe) {
      //TODO VERKLE FIX THE SLOTISWARM
      return new OperationResult(cost(frame, Bytes32.ZERO, true), ExceptionalHaltReason.TOO_MANY_STACK_ITEMS);
    }
  }
}
