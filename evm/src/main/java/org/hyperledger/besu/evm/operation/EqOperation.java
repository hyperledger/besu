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

import java.util.Arrays;

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/** The Eq operation. */
public class EqOperation extends AbstractFixedCostOperation {

  /** The Eq operation success result. */
  static final OperationResult eqSuccess = new OperationResult(3, null);

  /**
   * Instantiates a new Eq operation.
   *
   * @param gasCalculator the gas calculator
   */
  public EqOperation(final GasCalculator gasCalculator) {
    super(0x14, "EQ", 2, 1, gasCalculator, gasCalculator.getVeryLowTierGasCost());
  }

  @Override
  public Operation.OperationResult executeFixedCostOperation(
      final MessageFrame frame, final EVM evm) {
    return staticOperation(frame);
  }

  /**
   * Performs Eq operation.
   *
   * @param frame the frame
   * @return the operation result
   */
  public static OperationResult staticOperation(final MessageFrame frame) {
    final byte[] a = frame.popStackItem().toArrayUnsafe();
    final byte[] b = frame.popStackItem().toArrayUnsafe();
    final int nonZeroA = firstNonZeroIndex(a);
    final int nonZeroB = firstNonZeroIndex(b);
    Bytes result = UInt256.ZERO;
    if (Arrays.equals(a, nonZeroA, a.length, b, nonZeroB, b.length)) {
      result = UInt256.ONE;
    }

    frame.pushStackItem(result);
    return eqSuccess;
  }

  private static int firstNonZeroIndex(final byte[] value) {
    int i = 0;
    while (i < value.length && value[i] == 0) {
      i++;
    }
    return i;
  }
}
