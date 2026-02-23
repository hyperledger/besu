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
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;


/** The Block hash operation. */
public class BlockHashOperation extends AbstractOperation {

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
    if (!frame.stackHasItems(1)) {
      return new OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
    }
    if (frame.getRemainingGas() < cost) {
      return new OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_GAS);
    }

    final org.hyperledger.besu.evm.UInt256 blockArg = frame.popStackItemUnsafe();
    // If blockArg doesn't fit in a long, it's out of range
    if (blockArg.u3() != 0 || blockArg.u2() != 0 || blockArg.u1() != 0 || blockArg.u0() < 0) {
      frame.pushStackItemUnsafe(org.hyperledger.besu.evm.UInt256.ZERO);
      return new OperationResult(cost, null);
    }

    final long soughtBlock = blockArg.u0();
    final BlockValues blockValues = frame.getBlockValues();
    final long currentBlockNumber = blockValues.getNumber();
    final BlockHashLookup blockHashLookup = frame.getBlockHashLookup();

    // If the sought block is negative, a future block, the current block, or not in the
    // lookback window, zero is returned.
    if (soughtBlock < 0
        || soughtBlock >= currentBlockNumber
        || soughtBlock < (currentBlockNumber - blockHashLookup.getLookback())) {
      frame.pushStackItemUnsafe(org.hyperledger.besu.evm.UInt256.ZERO);
    } else {
      final Hash blockHash = blockHashLookup.apply(frame, soughtBlock);
      frame.pushStackItemUnsafe(org.hyperledger.besu.evm.UInt256.fromBytesBE(blockHash.getBytes().toArrayUnsafe()));
    }

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
