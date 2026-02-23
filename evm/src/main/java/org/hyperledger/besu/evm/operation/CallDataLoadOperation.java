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
    final org.hyperledger.besu.evm.UInt256 startWord = frame.peekStackItemUnsafe(0);

    // If the start index doesn't fit in a positive int, it comes after anything in data
    if (startWord.u3() != 0 || startWord.u2() != 0 || startWord.u1() != 0
        || startWord.u0() < 0 || startWord.u0() > Integer.MAX_VALUE) {
      frame.overwriteStackItemUnsafe(0, org.hyperledger.besu.evm.UInt256.ZERO);
      return successResponse;
    }

    final int offset = (int) startWord.u0();
    final Bytes data = frame.getInputData();
    if (offset < data.size()) {
      final byte[] result = new byte[32];
      final int toCopy = Math.min(32, data.size() - offset);
      System.arraycopy(data.slice(offset, toCopy).toArrayUnsafe(), 0, result, 0, toCopy);
      frame.overwriteStackItemUnsafe(0, org.hyperledger.besu.evm.UInt256.fromBytesBE(result));
    } else {
      frame.overwriteStackItemUnsafe(0, org.hyperledger.besu.evm.UInt256.ZERO);
    }

    return successResponse;
  }
}
