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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import org.apache.tuweni.bytes.Bytes;

/** The Block hash operation. */
public class BlockHashOperation extends AbstractOperation {
  private static final int MAX_BLOCK_ARG_SIZE = 8;

  /**
   * Instantiates a new Block hash operation.
   *
   * @param gasCalculator the gas calculator
   */
  public BlockHashOperation(final GasCalculator gasCalculator) {
    super(0x40, "BLOCKHASH", 1, 1, gasCalculator);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    final long cost = gasCalculator().getBlockHashOperationGasCost();
    if (frame.getRemainingGas() < cost) {
      return new OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_GAS);
    }

    // Make sure we can convert to long
    final Bytes blockArg = frame.popStackItem().trimLeadingZeros();
    if (blockArg.size() > MAX_BLOCK_ARG_SIZE) {
      frame.pushStackItem(Hash.ZERO);
      return new OperationResult(cost, null);
    }

    final BlockHashLookup blockHashLookup = frame.getBlockHashLookup();
    final Hash blockHash = blockHashLookup.apply(frame, blockArg.toLong());
    frame.pushStackItem(blockHash);

    return new OperationResult(cost, null);
  }

  /**
   * Cost of the opcode execution.
   *
   * @return the cost
   */
  protected long cost() {
    return gasCalculator().getBlockHashOperationGasCost();
  }
}
