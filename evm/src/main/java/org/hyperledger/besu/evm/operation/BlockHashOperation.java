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

import static org.hyperledger.besu.evm.operation.BlockHashOperation.BlockHashRetrievalStrategy.BLOCK_HASH_LOOKUP;
import static org.hyperledger.besu.evm.operation.BlockHashOperation.BlockHashRetrievalStrategy.STATE_READ;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/** The Block hash operation. */
public class BlockHashOperation extends AbstractFixedCostOperation {

  /** The HISTORICAL_BLOCKHASH_ADDRESS */
  public static final Address HISTORICAL_BLOCKHASH_ADDRESS =
      Address.fromHexString("0xfffffffffffffffffffffffffffffffffffffffe");

  private static final int MAX_RELATIVE_BLOCK = 256;
  private static final int MAX_BLOCK_ARG_SIZE = 8;

  private final BlockHashRetrievalStrategy blockHashRetrievalStrategy;

  /**
   * Instantiates a new Block hash operation.
   *
   * @param gasCalculator the gas calculator
   */
  public BlockHashOperation(final GasCalculator gasCalculator) {
    this(gasCalculator, BLOCK_HASH_LOOKUP);
  }

  /**
   * Instantiates a new Block hash operation.
   *
   * @param gasCalculator the gas calculator
   * @param blockHashRetrievalStrategy whether read from state (EIP-2935)
   */
  public BlockHashOperation(
      final GasCalculator gasCalculator,
      final BlockHashRetrievalStrategy blockHashRetrievalStrategy) {
    super(0x40, "BLOCKHASH", 1, 1, gasCalculator, gasCalculator.getBlockHashOperationGasCost());
    this.blockHashRetrievalStrategy = blockHashRetrievalStrategy;
  }

  @Override
  public OperationResult executeFixedCostOperation(final MessageFrame frame, final EVM evm) {
    final Bytes blockArg = frame.popStackItem().trimLeadingZeros();

    if (blockArg.size() > MAX_BLOCK_ARG_SIZE) {
      frame.pushStackItem(UInt256.ZERO);
      return successResponse;
    }

    final long soughtBlock = blockArg.toLong();
    final long currentBlockNumber = frame.getBlockValues().getNumber();

    if (!isBlockWithinLast256Blocks(soughtBlock, currentBlockNumber)) {
      frame.pushStackItem(UInt256.ZERO);
    } else {
      frame.pushStackItem(getBlockHash(frame, soughtBlock));
    }

    return successResponse;
  }

  private boolean isBlockWithinLast256Blocks(
      final long soughtBlock, final long currentBlockNumber) {
    return soughtBlock >= Math.max(currentBlockNumber - MAX_RELATIVE_BLOCK, 0)
        && soughtBlock < currentBlockNumber;
  }

  private Bytes32 getBlockHash(final MessageFrame frame, final long soughtBlock) {
    if (blockHashRetrievalStrategy == STATE_READ) {
      return readBlockHashFromState(frame, soughtBlock);
    } else {
      return lookupBlockHash(frame, soughtBlock);
    }
  }

  private Bytes32 readBlockHashFromState(final MessageFrame frame, final long soughtBlock) {
    Hash blockHash =
        Hash.wrap(
            frame
                .getWorldUpdater()
                .get(HISTORICAL_BLOCKHASH_ADDRESS)
                .getStorageValue(UInt256.valueOf(soughtBlock % MAX_RELATIVE_BLOCK)));
    return Bytes32.wrap(blockHash);
  }

  private Bytes32 lookupBlockHash(final MessageFrame frame, final long soughtBlock) {
    final Function<Long, Hash> blockHashLookup = frame.getBlockHashLookup();
    Hash blockHash = blockHashLookup.apply(soughtBlock);
    return Bytes32.wrap(blockHash);
  }

  /** Defines the strategies for retrieving a block hash. */
  public enum BlockHashRetrievalStrategy {
    /** Direct block hash lookup */
    BLOCK_HASH_LOOKUP,
    /** Block hash retrieval from state. (EIP-2935) */
    STATE_READ,
  }
}
