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
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/** The Sar operation. */
public class SarOperationOptimized extends AbstractFixedCostOperation {

  /** The Sar operation success result. */
  static final OperationResult sarSuccess = new OperationResult(3, null);

  /**
   * Instantiates a new Sar operation.
   *
   * @param gasCalculator the gas calculator
   */
  public SarOperationOptimized(final GasCalculator gasCalculator) {
    super(0x1d, "SAR", 2, 1, gasCalculator, gasCalculator.getVeryLowTierGasCost());
  }

  @Override
  public Operation.OperationResult executeFixedCostOperation(
      final MessageFrame frame, final EVM evm) {
    return staticOperation(frame);
  }

  /** All ones (0xFF repeated 32 times). */
  public static final Bytes ALL_ONES = Bytes.repeat((byte) 0xFF, 32);

  /** Zero value (32 zero bytes). */
  public static final Bytes ZERO_32 = Bytes.wrap(new byte[32]);

  /**
   * Performs sar operation.
   *
   * @param frame the frame
   * @return the operation result
   */
  public static OperationResult staticOperation(final MessageFrame frame) {
    final Bytes shiftAmount = frame.popStackItem();
    final Bytes value = Bytes32.leftPad(frame.popStackItem());
    final boolean negative = (value.get(0) & 0x80) != 0;

    // detect shift >= 256 cheaply (check high bytes)
    if (isShiftOverflow(shiftAmount)) {
      frame.pushStackItem(negative ? ALL_ONES : ZERO_32);
      return sarSuccess;
    }
    final int shift = shiftAmount.isEmpty() ? 0 : (shiftAmount.get(shiftAmount.size() - 1) & 0xFF);

    frame.pushStackItem(sar256(value, shift, negative));
    return sarSuccess;
  }

  /**
   * Performs a 256-bit arithmetic right shift (EVM SAR).
   *
   * <p>The input value is treated as a signed 256-bit integer in two’s complement representation.
   * The shift amount is in the range {@code [0..255]} and is assumed to have been validated by the
   * caller.
   *
   * <p>For shift values greater than or equal to 256, the result is fully sign-extended and handled
   * by the caller.
   *
   * @param value32 a 32-byte value representing a signed 256-bit integer
   * @param shift the right shift amount in bits (0–255)
   * @param negative whether the input value is negative (sign bit set)
   * @return the shifted 256-bit value
   */
  private static Bytes sar256(final Bytes value32, final int shift, final boolean negative) {
    if (shift == 0) return value32;

    final int shiftBytes = shift >>> 3; // /8
    final int shiftBits = shift & 7; // %8
    final int fill = negative ? 0xFF : 0x00;

    final byte[] out = new byte[32];
    final byte[] in = value32.toArrayUnsafe();

    for (int i = 31; i >= 0; i--) {
      final int src = i - shiftBytes;
      final int hi = (src >= 0) ? (in[src] & 0xFF) : fill;
      if (shiftBits == 0) {
        out[i] = (byte) hi;
      } else {
        final int lo = (src - 1 >= 0) ? (in[src - 1] & 0xFF) : fill;
        out[i] = (byte) ((hi >>> shiftBits) | ((lo << (8 - shiftBits)) & 0xFF));
      }
    }

    return Bytes.wrap(out);
  }

  /**
   * Checks whether the EVM SAR shift amount overflows (shift ≥ 256).
   *
   * @param shiftAmount the shift amount as a stack value
   * @return {@code true} if the shift amount is ≥ 256, {@code false} otherwise
   */
  private static boolean isShiftOverflow(final Bytes shiftAmount) {
    final byte[] a = shiftAmount.toArrayUnsafe();
    final int n = a.length;
    for (int i = 0; i < n - 1; i++) {
      if (a[i] != 0) return true;
    }
    return false;
  }
}
