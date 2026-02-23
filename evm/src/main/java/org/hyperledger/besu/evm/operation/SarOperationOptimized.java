/*
 * Copyright contributors to Besu.
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

import static org.hyperledger.besu.evm.operation.Shift256Operations.ALL_ONES;
import static org.hyperledger.besu.evm.operation.Shift256Operations.ALL_ONES_BYTES;
import static org.hyperledger.besu.evm.operation.Shift256Operations.isShiftOverflow;

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import java.util.Arrays;

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

  /**
   * Performs sar operation.
   *
   * @param frame the frame
   * @return the operation result
   */
  public static OperationResult staticOperation(final MessageFrame frame) {
    final Bytes shiftAmount = frame.popStackItem();
    final Bytes value = frame.popStackItem();
    byte[] valueBytes = value.toArrayUnsafe();
    if (Arrays.equals(valueBytes, ALL_ONES_BYTES)) {
      frame.pushStackItem(ALL_ONES);
      return sarSuccess;
    }
    valueBytes = Bytes32.leftPad(value).toArrayUnsafe();
    final byte[] shiftBytes = shiftAmount.toArrayUnsafe();
    final boolean negative = (valueBytes[0] & 0x80) != 0;

    // shift >= 256, push All 1s if negative, All 0s otherwise
    if (isShiftOverflow(shiftBytes)) {
      frame.pushStackItem(negative ? ALL_ONES : Bytes.EMPTY);
      return sarSuccess;
    }
    final int shift = shiftBytes.length == 0 ? 0 : (shiftBytes[shiftBytes.length - 1] & 0xFF);

    frame.pushStackItem(sar256(valueBytes, shift, negative));
    return sarSuccess;
  }

  /**
   * Performs a 256-bit arithmetic right shift (EVM SAR).
   *
   * <p>The input value is treated as a signed 256-bit integer in two's complement representation.
   * The shift amount is in the range {@code [0..255]} and is assumed to have been validated by the
   * caller.
   *
   * <p>For shift values greater than or equal to 256, the result is fully sign-extended and handled
   * by the caller.
   *
   * @param in the raw 32-byte array of the input value
   * @param shift the right shift amount in bits (0–255)
   * @param negative whether the input value is negative (sign bit set)
   * @return the shifted 256-bit value
   */
  private static Bytes sar256(final byte[] in, final int shift, final boolean negative) {
    if (shift == 0) return Bytes.wrap(in);

    final int shiftBytes = shift >>> 3; // /8
    final int shiftBits = shift & 7; // %8
    final int fill = negative ? 0xFF : 0x00;

    final byte[] out = new byte[32];

    // Pre-fill sign-extended bytes (indices below shiftBytes are fully sign-extended)
    if (negative && shiftBytes > 0) {
      Arrays.fill(out, 0, shiftBytes, (byte) 0xFF);
    }

    // Only iterate bytes that receive shifted data from the input
    for (int i = 31; i >= shiftBytes; i--) {
      final int srcIndex = i - shiftBytes;
      final int curr = in[srcIndex] & 0xFF;
      if (shiftBits == 0) {
        out[i] = (byte) curr;
      } else {
        final int prev = (srcIndex - 1 >= 0) ? (in[srcIndex - 1] & 0xFF) : fill;
        out[i] = (byte) ((curr >>> shiftBits) | (prev << (8 - shiftBits)));
      }
    }

    return Bytes.wrap(out);
  }
}
