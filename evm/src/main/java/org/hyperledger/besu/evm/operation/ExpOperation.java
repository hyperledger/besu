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

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;

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
    final Bytes number = frame.popStackItem();
    final Bytes power = frame.popStackItem();

    final int numBytes = (power.bitLength() + 7) / 8;

    final long cost = gasCalculator.expOperationGasCost(numBytes);
    if (frame.getRemainingGas() < cost) {
      return new OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_GAS);
    }

    byte[] numberBytes = number.toArrayUnsafe();
    BigInteger numBI = numberBytes.length > 0 ? new BigInteger(1, numberBytes) : BigInteger.ZERO;
    byte[] powBytes = power.toArrayUnsafe();
    BigInteger powBI = powBytes.length > 0 ? new BigInteger(1, powBytes) : BigInteger.ZERO;

    final BigInteger result = numBI.modPow(powBI, MOD_BASE);

    byte[] resultArray = result.toByteArray();
    int length = resultArray.length;
    if (length > 32) {
      frame.pushStackItem(Bytes.wrap(resultArray, length - 32, 32));
    } else {
      frame.pushStackItem(Bytes.wrap(resultArray));
    }
    return new OperationResult(cost, null);
  }
}
