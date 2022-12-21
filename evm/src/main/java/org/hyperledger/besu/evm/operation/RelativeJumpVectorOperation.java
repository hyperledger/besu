/*
 * Copyright contributors to Hyperledger Besu
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
 *
 */
package org.hyperledger.besu.evm.operation;

import static org.hyperledger.besu.evm.internal.Words.readBigEndianI16;

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import org.apache.tuweni.bytes.Bytes;

public class RelativeJumpVectorOperation extends AbstractFixedCostOperation {

  public static final int OPCODE = 0x5e;

  public RelativeJumpVectorOperation(final GasCalculator gasCalculator) {
    super(OPCODE, "RJUMPV", 0, 0, gasCalculator, 4L);
  }

  @Override
  protected OperationResult executeFixedCostOperation(final MessageFrame frame, final EVM evm) {
    final Bytes code = frame.getCode().getCodeBytes(frame.getSection());
    final int offsetCase = frame.popStackItem().toInt();
    final int vectorSize = getVectorSize(code, frame.getPC() + 1);
    return new OperationResult(
        gasCost,
        null,
        1
            + 2 * vectorSize
            + ((offsetCase >= vectorSize)
                ? 0
                : readBigEndianI16(frame.getPC() + 1 + offsetCase * 2, code.toArrayUnsafe()))
            + 1);
  }

  public static int getVectorSize(final Bytes code, final int offsetCountByteIndex) {
    return code.get(offsetCountByteIndex) & 0xff;
  }
}
