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
import org.hyperledger.besu.evm.UInt256;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.StackMath;

import java.math.BigInteger;

/** The Exp operation. */
public class ExpOperation extends AbstractOperation {

  static final BigInteger MOD_BASE = BigInteger.TWO.pow(256);

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
    return staticOperation(frame, frame.stackData(), gasCalculator());
  }

  /**
   * Performs exp operation.
   *
   * @param frame the frame
   * @param s the stack data array
   * @param gasCalculator the gas calculator
   * @return the operation result
   */
  public static OperationResult staticOperation(
      final MessageFrame frame, final long[] s, final GasCalculator gasCalculator) {
    if (!frame.stackHasItems(2)) {
      return new OperationResult(0, ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
    }
    final int top = frame.stackTop();
    final UInt256 number = StackMath.getAt(s, top, 0);
    final UInt256 power = StackMath.getAt(s, top, 1);

    final int numBytes = StackMath.byteLengthAt(s, top, 1);

    final long cost = gasCalculator.expOperationGasCost(numBytes);
    if (frame.getRemainingGas() < cost) {
      return new OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_GAS);
    }

    // Use BigInteger for modPow - complex to implement natively
    final BigInteger numBI = number.toBigInteger();
    final BigInteger powBI = power.toBigInteger();
    final BigInteger result = numBI.modPow(powBI, MOD_BASE);
    // Pop 2, push 1: net effect is top - 1
    final int newTop = top - 1;
    frame.setTop(newTop);
    StackMath.putAt(s, newTop, 0, UInt256.fromBigInteger(result));

    return new OperationResult(cost, null);
  }
}
