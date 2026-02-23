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
import org.apache.tuweni.bytes.MutableBytes32;

/** The Sign extend operation. */
public class SignExtendOperation extends AbstractFixedCostOperation {

  private static final OperationResult signExtendSuccess = new OperationResult(5, null);

  /**
   * Instantiates a new Sign extend operation.
   *
   * @param gasCalculator the gas calculator
   */
  public SignExtendOperation(final GasCalculator gasCalculator) {
    super(0x0B, "SIGNEXTEND", 2, 1, gasCalculator, gasCalculator.getLowTierGasCost());
  }

  @Override
  public Operation.OperationResult executeFixedCostOperation(
      final MessageFrame frame, final EVM evm) {
    return staticOperation(frame);
  }

  /**
   * Performs Sign Extend operation.
   *
   * @param frame the frame
   * @return the operation result
   */
  public static OperationResult staticOperation(final MessageFrame frame) {
    if (!frame.stackHasItems(2)) return UNDERFLOW_RESPONSE;
    final org.hyperledger.besu.evm.UInt256 value0 = frame.peekStackItemUnsafe(0);
    final org.hyperledger.besu.evm.UInt256 value1 = frame.peekStackItemUnsafe(1);
    frame.shrinkStackUnsafe(1);

    // Any value >= 31 means no sign extension needed
    if (value0.u3() != 0 || value0.u2() != 0 || value0.u1() != 0 || value0.u0() >= 31) {
      frame.overwriteStackItemUnsafe(0, value1);
      return signExtendSuccess;
    }

    final int b = (int) value0.u0(); // byte index (0..30)
    final int byteIndex = 31 - b;
    final byte[] bytes = value1.toBytesBE();
    final byte signByte = (bytes[byteIndex] & 0x80) != 0 ? (byte) 0xFF : 0x00;
    for (int i = 0; i < byteIndex; i++) {
      bytes[i] = signByte;
    }
    frame.overwriteStackItemUnsafe(0, org.hyperledger.besu.evm.UInt256.fromBytesBE(bytes));

    return signExtendSuccess;
  }
}
