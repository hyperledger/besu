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

/** The Byte operation. */
public class ByteOperation extends AbstractFixedCostOperation {

  /** The Byte operation success result. */
  static final OperationResult byteSuccess = new OperationResult(3, null);

  /**
   * Instantiates a new Byte operation.
   *
   * @param gasCalculator the gas calculator
   */
  public ByteOperation(final GasCalculator gasCalculator) {
    super(0x1A, "BYTE", 2, 1, gasCalculator, gasCalculator.getVeryLowTierGasCost());
  }

  @Override
  public Operation.OperationResult executeFixedCostOperation(
      final MessageFrame frame, final EVM evm) {
    return staticOperation(frame);
  }

  /**
   * Static Byte operation.
   *
   * @param frame the frame
   * @return the operation result
   */
  public static OperationResult staticOperation(final MessageFrame frame) {
    if (!frame.stackHasItems(2)) return UNDERFLOW_RESPONSE;
    final org.hyperledger.besu.evm.UInt256 offset = frame.peekStackItemUnsafe(0);
    final org.hyperledger.besu.evm.UInt256 value = frame.peekStackItemUnsafe(1);
    frame.shrinkStackUnsafe(1);

    // offset must be 0..31 to select a byte from the 32-byte value
    if (offset.u3() != 0 || offset.u2() != 0 || offset.u1() != 0 || offset.u0() >= 32) {
      frame.overwriteStackItemUnsafe(0, org.hyperledger.besu.evm.UInt256.ZERO);
      return byteSuccess;
    }

    final int index = (int) offset.u0();
    final byte[] bytes = value.toBytesBE();
    frame.overwriteStackItemUnsafe(0, org.hyperledger.besu.evm.UInt256.fromInt(bytes[index] & 0xFF));

    return byteSuccess;
  }
}
