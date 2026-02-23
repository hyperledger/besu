/*
 * Copyright contributors to Hyperledger Besu.
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
import org.hyperledger.besu.evm.internal.StackMath;

import org.apache.tuweni.bytes.Bytes;

/** The Call data load operation. */
public class CallDataLoadOperation extends AbstractFixedCostOperation {

  /**
   * Instantiates a new Call data load operation.
   *
   * @param gasCalculator the gas calculator
   */
  public CallDataLoadOperation(final GasCalculator gasCalculator) {
    super(0x35, "CALLDATALOAD", 1, 1, gasCalculator, gasCalculator.getVeryLowTierGasCost());
  }

  @Override
  public Operation.OperationResult executeFixedCostOperation(
      final MessageFrame frame, final EVM evm) {
    if (!frame.stackHasItems(1)) return UNDERFLOW_RESPONSE;
    final long[] s = frame.stackData();
    final int top = frame.stackTop();
    final int off = (top - 1) << 2;

    // If the start index doesn't fit in a positive int, result is zero
    if (s[off] != 0
        || s[off + 1] != 0
        || s[off + 2] != 0
        || s[off + 3] < 0
        || s[off + 3] > Integer.MAX_VALUE) {
      s[off] = 0;
      s[off + 1] = 0;
      s[off + 2] = 0;
      s[off + 3] = 0;
      return successResponse;
    }

    final int offset = (int) s[off + 3];
    final Bytes data = frame.getInputData();
    if (offset < data.size()) {
      final byte[] result = new byte[32];
      final int toCopy = Math.min(32, data.size() - offset);
      System.arraycopy(data.slice(offset, toCopy).toArrayUnsafe(), 0, result, 0, toCopy);
      StackMath.fromBytesAt(s, top, 0, result, 0, 32);
    } else {
      s[off] = 0;
      s[off + 1] = 0;
      s[off + 2] = 0;
      s[off + 3] = 0;
    }

    return successResponse;
  }
}
