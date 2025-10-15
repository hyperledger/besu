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

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.UInt256;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/** The SMod operation. */
public class SModOperationOptimized extends AbstractFixedCostOperation {

  private static final OperationResult smodSuccess = new OperationResult(5, null);

  /**
   * Instantiates a new SMod operation.
   *
   * @param gasCalculator the gas calculator
   */
  public SModOperationOptimized(final GasCalculator gasCalculator) {
    super(0x07, "SMOD", 2, 1, gasCalculator, gasCalculator.getLowTierGasCost());
  }

  @Override
  public Operation.OperationResult executeFixedCostOperation(
      final MessageFrame frame, final EVM evm) {
    return staticOperation(frame);
  }

  /**
   * Performs SMod operation.
   *
   * @param frame the frame
   * @return the operation result
   */
  public static OperationResult staticOperation(final MessageFrame frame) {
    final Bytes value0 = frame.popStackItem();
    final Bytes value1 = frame.popStackItem();

    Bytes resultBytes;
    if (value1.isZero()) {
      resultBytes = (Bytes) Bytes32.ZERO;
    } else {
      UInt256 b0 = UInt256.fromBytesBE(value0.toArrayUnsafe());
      UInt256 b1 = UInt256.fromBytesBE(value1.toArrayUnsafe());
      resultBytes = Bytes.wrap(b0.signedMod(b1).toBytesBE());
    }
    frame.pushStackItem(resultBytes);

    return smodSuccess;
  }
}
