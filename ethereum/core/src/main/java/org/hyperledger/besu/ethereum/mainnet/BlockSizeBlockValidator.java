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

import org.hyperledger.besu.ethereum.MainnetBlockValidator;

/** Validates blocks ensuring they do not exceed the maximum allowed size. */
public class BlockSizeBlockValidator extends MainnetBlockValidator {
  /** The maximum size of a block in bytes */
  public static final int MAX_BLOCK_SIZE = 10_485_760;

  /** The safety margin to take the CL block overhead into account */
  public static final int SAFETY_MARGIN = 2_097_152;

  /** The maximum size of an RLP encoded block size in bytes */
  public static final int MAX_RLP_BLOCK_SIZE = MAX_BLOCK_SIZE - SAFETY_MARGIN;

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
    super(blockHeaderValidator, blockBodyValidator, blockProcessor, MAX_RLP_BLOCK_SIZE);
  }
}
