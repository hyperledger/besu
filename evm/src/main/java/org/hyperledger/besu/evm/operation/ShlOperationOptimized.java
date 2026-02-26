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

import static org.hyperledger.besu.evm.operation.Shift256Operations.isShiftOverflow;

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * The optimized SHL (Shift Left) operation.
 *
 * <p>This implementation uses direct byte[] manipulation instead of Tuweni's Bytes.shiftLeft() to
 * avoid intermediate object allocation and improve performance.
 */
public class ShlOperationOptimized extends AbstractFixedCostOperation {

  /** The Shl operation success result. */
  static final OperationResult shlSuccess = new OperationResult(3, null);

  /**
   * Instantiates a new optimized Shl operation.
   *
   * @param gasCalculator the gas calculator
   */
  public ShlOperationOptimized(final GasCalculator gasCalculator) {
    super(0x1b, "SHL", 2, 1, gasCalculator, gasCalculator.getVeryLowTierGasCost());
  }

  @Override
  public Operation.OperationResult executeFixedCostOperation(
      final MessageFrame frame, final EVM evm) {
    return staticOperation(frame);
  }

  /**
   * Performs optimized Shift Left operation.
   *
   * @param frame the frame
   * @return the operation result
   */
  public static OperationResult staticOperation(final MessageFrame frame) {
    final Bytes shiftAmount = frame.popStackItem();
    final Bytes value = frame.popStackItem();
    if (value.isZero()) {
      frame.pushStackItem(Bytes.EMPTY);
      return shlSuccess;
    }

    final byte[] valueBytes = Bytes32.leftPad(value).toArrayUnsafe();
    final byte[] shiftBytes = shiftAmount.toArrayUnsafe();

    // shift >= 256, push All 0s
    if (isShiftOverflow(shiftBytes)) {
      frame.pushStackItem(Bytes.EMPTY);
      return shlSuccess;
    }

    final int shift = shiftBytes.length == 0 ? 0 : (shiftBytes[shiftBytes.length - 1] & 0xFF);

    frame.pushStackItem(shl256(valueBytes, shift));
    return shlSuccess;
  }

  /**
   * Performs a 256-bit logical left shift (EVM SHL).
   *
   * <p>The shift amount is in the range {@code [0..255]} and is assumed to have been validated by
   * the caller. For shift values >= 256, zero is returned by the caller.
   *
   * @param in the raw 32-byte array of the input value
   * @param shift the left shift amount in bits (0–255)
   * @return the shifted 256-bit value
   */
  private static Bytes shl256(final byte[] in, final int shift) {
    if (shift == 0) {
      return Bytes.wrap(in);
    }

    final int shiftBytes = shift >>> 3; // /8
    final int shiftBits = shift & 7; // %8

    final byte[] out = new byte[32];

    // Shift left: bytes move to lower indices (towards index 0)
    // Bytes at index >= (32 - shiftBytes) are guaranteed zero (already from new byte[32])
    final int limit = 32 - shiftBytes;
    for (int i = 0; i < limit; i++) {
      final int srcIndex = i + shiftBytes;
      final int curr = in[srcIndex] & 0xFF;
      if (shiftBits == 0) {
        out[i] = (byte) curr;
      } else {
        final int next = (srcIndex + 1 < 32) ? (in[srcIndex + 1] & 0xFF) : 0;
        out[i] = (byte) ((curr << shiftBits) | (next >>> (8 - shiftBits)));
      }
    }

    return Bytes.wrap(out);
  }
}
