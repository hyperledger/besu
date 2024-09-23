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

import static org.hyperledger.besu.evm.internal.Words.clampedToInt;

import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/** The Return data copy operation. */
public class ReturnDataLoadOperation extends AbstractOperation {

  /**
   * Instantiates a new Return data copy operation.
   *
   * @param gasCalculator the gas calculator
   */
  public ReturnDataLoadOperation(final GasCalculator gasCalculator) {
    super(0xf7, "RETURNDATALOAD", 3, 0, gasCalculator);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    Code code = frame.getCode();
    if (code.getEofVersion() == 0) {
      return InvalidOperation.INVALID_RESULT;
    }

    final int offset = clampedToInt(frame.popStackItem());
    Bytes returnData = frame.getReturnData();
    int retunDataSize = returnData.size();

    Bytes value;
    if (offset > retunDataSize) {
      value = Bytes.EMPTY;
    } else if (offset + 32 >= returnData.size()) {
      value = Bytes32.rightPad(returnData.slice(offset));
    } else {
      value = returnData.slice(offset, 32);
    }

    frame.pushStackItem(value);
    return new OperationResult(3L, null);
  }
}
