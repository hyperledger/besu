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
package org.hyperledger.besu.ethereum.mainnet;

import static org.hyperledger.besu.evm.gascalculator.OsakaGasCalculator.MAX_RLP_BLOCK_SIZE;

import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.MainnetBlockValidator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Block;

/** Validates blocks ensuring they do not exceed the maximum allowed size. */
public class BlockSizeBlockValidator extends MainnetBlockValidator {

  /**
   * Constructs a new MainnetBlockValidator with the given BlockHeaderValidator, BlockBodyValidator,
   * BlockProcessor, and BadBlockManager.
   *
   * @param blockHeaderValidator the BlockHeaderValidator used to validate block headers
   * @param blockBodyValidator the BlockBodyValidator used to validate block bodies
   * @param blockProcessor the BlockProcessor used to process blocks
   */
  public BlockSizeBlockValidator(
      final BlockHeaderValidator blockHeaderValidator,
      final BlockBodyValidator blockBodyValidator,
      final BlockProcessor blockProcessor) {
    super(blockHeaderValidator, blockBodyValidator, blockProcessor);
  }

  @Override
  public BlockProcessingResult validateAndProcessBlock(
      final ProtocolContext context,
      final Block block,
      final HeaderValidationMode headerValidationMode,
      final HeaderValidationMode ommerValidationMode,
      final boolean shouldUpdateHead,
      final boolean shouldRecordBadBlock) {
    final int blockSize = block.calculateSize();
    if (blockSize > MAX_RLP_BLOCK_SIZE) {
      final String errorMessage =
          "Block size of " + blockSize + " bytes exceeds limit of " + MAX_RLP_BLOCK_SIZE + " bytes";
      var retval = new BlockProcessingResult(errorMessage);
      handleFailedBlockProcessing(block, retval, true, context);
      return retval;
    }

    return super.validateAndProcessBlock(
        context,
        block,
        headerValidationMode,
        ommerValidationMode,
        shouldUpdateHead,
        shouldRecordBadBlock);
  }
}
