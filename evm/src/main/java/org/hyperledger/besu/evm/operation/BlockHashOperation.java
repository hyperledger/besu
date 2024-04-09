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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/** The Block hash operation. */
public class BlockHashOperation extends AbstractFixedCostOperation {

  private static final int MAX_RELATIVE_BLOCK = 256;

  public static final Address HISTORICAL_BLOCKHASH_ADDRESS =
      Address.fromHexString("0xfffffffffffffffffffffffffffffffffffffffe");

  private final boolean readFromState;
  /**
   * Instantiates a new Block hash operation.
   *
   * @param gasCalculator the gas calculator
   */
  public BlockHashOperation(final GasCalculator gasCalculator) {
    this(
        0x40,
        "BLOCKHASH",
        1,
        1,
        gasCalculator,
        gasCalculator.getBlockHashOperationGasCost(),
        false);
  }

  public BlockHashOperation(final GasCalculator gasCalculator, final boolean readFromState) {
    this(
        0x40,
        "BLOCKHASH",
        1,
        1,
        gasCalculator,
        gasCalculator.getBlockHashOperationGasCost(),
        readFromState);
  }

  public BlockHashOperation(
      final int opcode,
      final String name,
      final int stackItemsConsumed,
      final int stackItemsProduced,
      final GasCalculator gasCalculator,
      final long fixedCost,
      final boolean readFromState) {
    super(opcode, name, stackItemsConsumed, stackItemsProduced, gasCalculator, fixedCost);
    this.readFromState = readFromState;
  }

  @Override
  public Operation.OperationResult executeFixedCostOperation(
      final MessageFrame frame, final EVM evm) {
    final Bytes blockArg = frame.popStackItem().trimLeadingZeros();

    // Short-circuit if value is unreasonably large
    if (blockArg.size() > 8) {
      frame.pushStackItem(UInt256.ZERO);
      return successResponse;
    }

    final long soughtBlock = blockArg.toLong();
    final BlockValues blockValues = frame.getBlockValues();
    final long currentBlockNumber = blockValues.getNumber();

    // If the current block is the genesis block or the sought block is
    // not within the last 256 completed blocks, zero is returned.
    if (soughtBlock < Math.max(currentBlockNumber - MAX_RELATIVE_BLOCK, 0)
        || soughtBlock >= currentBlockNumber) {
      frame.pushStackItem(Bytes32.ZERO);
    } else {
      final Hash blockHash;
      if (readFromState) {
        blockHash =
            Hash.wrap(
                frame
                    .getWorldUpdater()
                    .get(HISTORICAL_BLOCKHASH_ADDRESS)
                    .getStorageValue(UInt256.valueOf(soughtBlock % MAX_RELATIVE_BLOCK)));
      } else {
        final Function<Long, Hash> blockHashLookup = frame.getBlockHashLookup();
        blockHash = blockHashLookup.apply(soughtBlock);
      }
      frame.pushStackItem(blockHash);
    }

    return successResponse;
  }
}
