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
import org.hyperledger.besu.evm.word256.Word256;

import org.apache.tuweni.bytes.Bytes32;

/**
 * The Add operation performs a simple addition of two 256-bit words, returning the result in theç
 * stack. If the result exceeds 32 bytes, it truncates the result to the last 32 bytes.
 */
public class AddOperation extends AbstractFixedCostOperation {

  /** The Add operation success result. */
  static final OperationResult addSuccess = new OperationResult(3, null);

  /**
   * Instantiates a new Add operation.
   *
   * @param gasCalculator the gas calculator
   */
  public AddOperation(final GasCalculator gasCalculator) {
    super(0x01, "ADD", 2, 1, gasCalculator, gasCalculator.getVeryLowTierGasCost());
  }

  @Override
  public Operation.OperationResult executeFixedCostOperation(
      final MessageFrame frame, final EVM evm) {
    return staticOperation(frame);
  }

  /**
   * Static operation.
   *
   * @param frame the frame
   * @return the operation result
   */
  public static OperationResult staticOperation(final MessageFrame frame) {
    final Word256 a = Word256.fromBytes(frame.popStackItem().toArrayUnsafe());
    final Word256 b = Word256.fromBytes(frame.popStackItem().toArrayUnsafe());

    frame.pushStackItem(Bytes32.wrap(a.add(b).toBytes()));
    return addSuccess;
  }
}
