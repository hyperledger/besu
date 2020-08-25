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
package org.hyperledger.besu.ethereum.vm.operations;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.EVM;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class BlockHashOperation extends AbstractFixedCostOperation {

  private static final int MAX_RELATIVE_BLOCK = 255;

  public BlockHashOperation(final GasCalculator gasCalculator) {
    super(
        0x40,
        "BLOCKHASH",
        1,
        1,
        false,
        1,
        gasCalculator,
        gasCalculator.getBlockHashOperationGasCost());
  }

  @Override
  public OperationResult executeFixedCostOperation(final MessageFrame frame, final EVM evm) {
    final UInt256 blockArg = UInt256.fromBytes(frame.popStackItem());

    // Short-circuit if value is unreasonably large
    if (!blockArg.fitsLong()) {
      frame.pushStackItem(Bytes32.ZERO);
      return successResponse;
    }

    final long soughtBlock = blockArg.toLong();
    final ProcessableBlockHeader blockHeader = frame.getBlockHeader();
    final long currentBlockNumber = blockHeader.getNumber();
    final long mostRecentBlockNumber = currentBlockNumber - 1;

    // If the current block is the genesis block or the sought block is
    // not within the last 256 completed blocks, zero is returned.
    if (currentBlockNumber == BlockHeader.GENESIS_BLOCK_NUMBER
        || soughtBlock < (mostRecentBlockNumber - MAX_RELATIVE_BLOCK)
        || soughtBlock > mostRecentBlockNumber) {
      frame.pushStackItem(Bytes32.ZERO);
    } else {
      final BlockHashLookup blockHashLookup = frame.getBlockHashLookup();
      final Hash blockHash = blockHashLookup.getBlockHash(soughtBlock);
      frame.pushStackItem(blockHash);
    }

    return successResponse;
  }
}
