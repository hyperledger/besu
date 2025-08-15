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
import org.hyperledger.besu.evm.word256.Word256;

import org.apache.tuweni.bytes.Bytes32;

/**
 * The Exp operation implements the exponentiation operation in the EVM. The overflow behavior is
 * defined by the EVM specification, where the result is truncated to fit within 256 bits.
 */
public class ExpOperation extends AbstractOperation {
  /**
   * Instantiates a new Exp operation.
   *
   * @param gasCalculator the gas calculator
   */
  public ExpOperation(final GasCalculator gasCalculator) {
    super(0x0A, "EXP", 2, 1, gasCalculator);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    return staticOperation(frame, gasCalculator());
  }

  /**
   * Performs exp operation.
   *
   * @param frame the frame
   * @param gasCalculator the gas calculator
   * @return the operation result
   */
  public static OperationResult staticOperation(
      final MessageFrame frame, final GasCalculator gasCalculator) {
    final Word256 number = Word256.fromBytes(frame.popStackItem().toArrayUnsafe());
    final Word256 power = Word256.fromBytes(frame.popStackItem().toArrayUnsafe());

    // Compute the number of non-zero bytes needed to represent the exponent.
    // This is used to scale the gas cost of the EXP opcode linearly with exponent size,
    // as defined in EIP-160: cost = 10 + (50 * numBytes)
    final int numBytes = (power.bitLength() + 7) / 8;

    final long cost = gasCalculator.expOperationGasCost(numBytes);
    if (frame.getRemainingGas() < cost) {
      return new OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_GAS);
    }

    frame.pushStackItem(Bytes32.wrap(number.exp(power).toBytesArray()));
    return new OperationResult(cost, null);
  }
}
