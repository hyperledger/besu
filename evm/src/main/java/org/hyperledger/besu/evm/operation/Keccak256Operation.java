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

import static org.hyperledger.besu.crypto.Hash.keccak256;

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.StackMath;

import org.apache.tuweni.bytes.Bytes;

/** The Keccak256 operation. */
public class Keccak256Operation extends AbstractOperation {

  /**
   * Instantiates a new Keccak256 operation.
   *
   * @param gasCalculator the gas calculator
   */
  public Keccak256Operation(final GasCalculator gasCalculator) {
    super(0x20, "KECCAK256", 2, 1, gasCalculator);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    if (!frame.stackHasItems(2)) {
      return new OperationResult(0, ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
    }
    final long[] s = frame.stackData();
    final int top = frame.stackTop();
    final long from = StackMath.clampedToLong(s, top, 0);
    final long length = StackMath.clampedToLong(s, top, 1);

    final long cost = gasCalculator().keccak256OperationGasCost(frame, from, length);
    if (frame.getRemainingGas() < cost) {
      return new OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_GAS);
    }

    final Bytes bytes = frame.readMutableMemory(from, length);
    final byte[] hashBytes = keccak256(bytes).toArrayUnsafe();
    // Pop 2, push 1: net effect is top - 1
    final int newTop = top - 1;
    frame.setTop(newTop);
    StackMath.fromBytesAt(s, newTop, 0, hashBytes, 0, hashBytes.length);
    return new OperationResult(cost, null);
  }
}
