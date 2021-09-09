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
 */
package org.hyperledger.besu.evm.operation;

import static java.lang.Math.min;

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.frame.MessageFrame;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class PushOperation extends AbstractFixedCostOperation {

  private final int length;

  public PushOperation(final int length, final GasCalculator gasCalculator) {
    super(
        0x60 + length - 1,
        "PUSH" + length,
        0,
        1,
        false,
        length + 1,
        gasCalculator,
        gasCalculator.getVeryLowTierGasCost());
    this.length = length;
  }

  @Override
  public Operation.OperationResult executeFixedCostOperation(
      final MessageFrame frame, final EVM evm) {
    final int pc = frame.getPC();
    final Bytes code = frame.getCode().getBytes();

    final int copyLength = min(length, code.size() - pc - 1);
    final MutableBytes32 bytes = MutableBytes32.create();
    code.slice(pc + 1, copyLength).copyTo(bytes, bytes.size() - length);
    frame.pushStackItem(UInt256.fromBytes(bytes));

    return successResponse;
  }
}
