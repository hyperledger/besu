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
import org.hyperledger.besu.evm.UInt256;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.StackMath;

/** The SLoad operation. */
public class SLoadOperation extends AbstractOperation {

  private final long warmCost;
  private final long coldCost;

  private final OperationResult warmSuccess;
  private final OperationResult coldSuccess;

  /**
   * Instantiates a new SLoad operation.
   *
   * @param gasCalculator the gas calculator
   */
  public SLoadOperation(final GasCalculator gasCalculator) {
    super(0x54, "SLOAD", 1, 1, gasCalculator);
    final long baseCost = gasCalculator.getSloadOperationGasCost();
    warmCost = baseCost + gasCalculator.getWarmStorageReadCost();
    coldCost = baseCost + gasCalculator.getColdSloadCost();

    warmSuccess = new OperationResult(warmCost, null);
    coldSuccess = new OperationResult(coldCost, null);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    if (!frame.stackHasItems(1)) {
      return new OperationResult(warmCost, ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
    }
    final long[] s = frame.stackData();
    final int top = frame.stackTop();
    final Account account = getAccount(frame.getRecipientAddress(), frame);
    final Address address = account.getAddress();
    final UInt256 key = StackMath.getAt(s, top, 0);
    final boolean slotIsWarm = frame.warmUpStorage(address, key.toBytes32());
    final long cost = slotIsWarm ? warmCost : coldCost;
    if (frame.getRemainingGas() < cost) {
      return new OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_GAS);
    }
    // Overwrite in place (pop 1, push 1)
    final UInt256 result = UInt256.fromTuweni(getStorageValue(account, key.toTuweni(), frame));
    StackMath.putAt(s, top, 0, result);
    return slotIsWarm ? warmSuccess : coldSuccess;
  }
}
