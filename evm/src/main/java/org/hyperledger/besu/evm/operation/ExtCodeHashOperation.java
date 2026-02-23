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
import org.hyperledger.besu.evm.internal.StackMath;

/** The Ext code hash operation. */
public class ExtCodeHashOperation extends AbstractOperation {
  /**
   * Instantiates a new Ext code hash operation.
   *
   * @param gasCalculator the gas calculator
   */
  public ExtCodeHashOperation(final GasCalculator gasCalculator) {
    super(0x3F, "EXTCODEHASH", 1, 1, gasCalculator);
  }

  /**
   * Cost of Ext code hash operation.
   *
   * @param accountIsWarm the account is warm
   * @return the long
   */
  protected long cost(final boolean accountIsWarm) {
    return gasCalculator().extCodeHashOperationGasCost()
        + (accountIsWarm
            ? gasCalculator().getWarmStorageReadCost()
            : gasCalculator().getColdAccountAccessCost());
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    if (!frame.stackHasItems(1)) {
      return new OperationResult(cost(true), ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
    }
    final long[] s = frame.stackData();
    final int top = frame.stackTop();
    final Address address = StackMath.toAddressAt(s, top, 0);
    final boolean accountIsWarm =
        frame.warmUpAddress(address) || gasCalculator().isPrecompile(address);
    final long cost = cost(accountIsWarm);
    if (frame.getRemainingGas() < cost) {
      return new OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_GAS);
    }

    final Account account = getAccount(address, frame);

    // Overwrite in place (pop 1, push 1)
    if (account == null || account.isEmpty()) {
      StackMath.putAt(s, top, 0, 0L, 0L, 0L, 0L);
    } else {
      final byte[] hashBytes = account.getCodeHash().getBytes().toArrayUnsafe();
      StackMath.fromBytesAt(s, top, 0, hashBytes, 0, hashBytes.length);
    }
    return new OperationResult(cost, null);
  }
}
