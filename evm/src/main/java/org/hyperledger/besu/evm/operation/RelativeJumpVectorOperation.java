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

import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import org.apache.tuweni.bytes.Bytes;

/** The type Relative jump vector operation. */
public class RelativeJumpVectorOperation extends AbstractFixedCostOperation {

  /** The constant OPCODE. */
  public static final int OPCODE = 0xe2;

  /**
   * Instantiates a new Relative jump vector operation.
   *
   * @param gasCalculator the gas calculator
   */
  public RelativeJumpVectorOperation(final GasCalculator gasCalculator) {
    super(OPCODE, "RJUMPV", 0, 0, gasCalculator, 4L);
  }

  @Override
  protected OperationResult executeFixedCostOperation(final MessageFrame frame, final EVM evm) {
    Code code = frame.getCode();
    if (code.getEofVersion() == 0) {
      return InvalidOperation.INVALID_RESULT;
    }
    int offsetCase;
    try {
      offsetCase = frame.popStackItem().trimLeadingZeros().toInt();
      if (offsetCase < 0) {
        offsetCase = Integer.MAX_VALUE;
      }
    } catch (ArithmeticException | IllegalArgumentException ae) {
      offsetCase = Integer.MAX_VALUE;
    }
    final int vectorSize = getVectorSize(code.getBytes(), frame.getPC() + 1);
    int jumpDelta =
        (offsetCase < vectorSize)
            ? code.readBigEndianI16(
                frame.getPC() + 2 + offsetCase * 2) // lookup delta if offset is in vector
            : 0; // if offsetCase is outside the vector the jump delta is zero / next opcode.
    return new OperationResult(
        gasCost,
        null,
        2 // Opcode + length immediate
            + 2 * vectorSize // vector size
            + jumpDelta);
  }

  /**
   * Gets vector size. Vector size is one greater than length immediate, because (a) zero length
   * tables are useless and (b) it allows for 256 byte tables
   *
   * @param code the code
   * @param offsetCountByteIndex the offset count byte index
   * @return the vector size
   */
  public static int getVectorSize(final Bytes code, final int offsetCountByteIndex) {
    return (code.toArrayUnsafe()[offsetCountByteIndex] & 0xff) + 1;
  }
}
