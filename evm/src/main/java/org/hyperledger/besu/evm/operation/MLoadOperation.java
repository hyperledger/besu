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

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.StackMath;

/** The M load operation. */
public class MLoadOperation extends AbstractOperation {

  /**
   * Instantiates a new M load operation.
   *
   * @param gasCalculator the gas calculator
   */
  public MLoadOperation(final GasCalculator gasCalculator) {
    super(0x51, "MLOAD", 1, 1, gasCalculator);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    if (!frame.stackHasItems(1)) {
      return new OperationResult(0, ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
    }
    final long[] s = frame.stackData();
    final int top = frame.stackTop();
    final long location = StackMath.clampedToLong(s, top, 0);

    final long cost = gasCalculator().mLoadOperationGasCost(frame, location);
    if (frame.getRemainingGas() < cost) {
      return new OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_GAS);
    }

    final byte[] value = frame.readMutableMemory(location, 32, true).toArrayUnsafe();
    // Overwrite the top slot in-place (pop offset, push value = same slot)
    StackMath.fromBytesAt(s, top, 0, value, 0, 32);
    return new OperationResult(cost, null);
  }
}
