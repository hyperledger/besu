/*
 * Copyright contributors to Hyperledger Besu
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

import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import org.apache.tuweni.bytes.Bytes;

/** The Memory copy operation. */
public class MCopyOperation extends AbstractOperation {

  /**
   * Instantiates a new Memory Copy operation.
   *
   * @param gasCalculator the gas calculator
   */
  public MCopyOperation(final GasCalculator gasCalculator) {
    super(0x5e, "MCOPY", 3, 0, gasCalculator);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    final long dst = clampedToLong(frame.popStackItem());
    final long src = clampedToLong(frame.popStackItem());
    final long length = clampedToLong(frame.popStackItem());

    final long cost = gasCalculator().dataCopyOperationGasCost(frame, Math.max(src, dst), length);
    if (frame.getRemainingGas() < cost) {
      return new OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_GAS);
    }

    // read memory copies into a new buffer, no need to copy here.
    final Bytes callData = frame.readMemory(src, length);

    frame.writeMemory(dst, length, callData, true);

    return new OperationResult(cost, null);
  }
}
