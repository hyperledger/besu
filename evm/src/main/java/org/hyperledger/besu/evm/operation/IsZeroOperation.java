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

/** The Is zero operation. */
public class IsZeroOperation extends AbstractFixedCostOperation {

  /** The Is zero operation success result. */
  static final OperationResult isZeroSuccess = new OperationResult(3, null);

  /**
   * Instantiates a new Is zero operation.
   *
   * @param gasCalculator the gas calculator
   */
  public IsZeroOperation(final GasCalculator gasCalculator) {
    super(0x15, "ISZERO", 1, 1, gasCalculator, gasCalculator.getVeryLowTierGasCost());
  }

  @Override
  public Operation.OperationResult executeFixedCostOperation(
      final MessageFrame frame, final EVM evm) {
    return staticOperation(frame);
  }

  /**
   * Performs Is Zero operation.
   *
   * @param frame the frame
   * @return the operation result
   */
  public static OperationResult staticOperation(final MessageFrame frame) {
    final Bytes value = frame.popStackItem().trimLeadingZeros();

    frame.pushStackItem((value.size() == 0) ? BYTES_ONE : Bytes.EMPTY);

    return isZeroSuccess;
  }
}
