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
package org.hyperledger.besu.evm.operation.linea;

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.AbstractFixedCostOperation;

import org.apache.tuweni.bytes.Bytes;

/** Retrieves the current block number and pushes it onto the stack. */
public class BlockHashNumberOperation extends AbstractFixedCostOperation {

  /**
   * Instantiates a new Block hash operation.
   *
   * @param gasCalculator the gas calculator
   */
  public BlockHashNumberOperation(final GasCalculator gasCalculator) {
    super(0x40, "BLOCKHASH", 1, 1, gasCalculator, gasCalculator.getBlockHashOperationGasCost());
  }

  /**
   * Retrieves the current block number and pushes it onto the stack
   *
   * @param frame the message frame in which to execute the operation
   * @param evm the Ethereum Virtual Machine instance
   * @return the result of the operation
   */
  @Override
  public OperationResult executeFixedCostOperation(final MessageFrame frame, final EVM evm) {
    frame.popStackItem();
    final long number = frame.getBlockValues().getNumber();
    frame.pushStackItem(Bytes.ofUnsignedLong(number));
    return successResponse;
  }
}
