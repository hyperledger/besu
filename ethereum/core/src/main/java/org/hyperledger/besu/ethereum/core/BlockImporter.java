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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;

import java.util.List;

/**
 * An interface for a block importer.
 *
 * <p>The block importer is responsible for assessing whether a candidate block can be added to a
 * given blockchain given the block history and its corresponding state. If the block is able to be
 * successfully added, the corresponding blockchain and world state will be updated as well.
 */
public interface BlockImporter {

  /**
   * Attempts to import the given block to the specified blockchain and world state.
   *
   * @param context The context to attempt to update
   * @param block The block
   * @param headerValidationMode Determines the validation to perform on this header.
   * @return {@code true} if the block was added somewhere in the blockchain; otherwise {@code
   *     false}
   */
  default boolean importBlock(
      final ProtocolContext context,
      final Block block,
      final HeaderValidationMode headerValidationMode) {
    return importBlock(context, block, headerValidationMode, HeaderValidationMode.FULL);
  }

  /**
   * Attempts to import the given block to the specified blockchain and world state.
   *
   * @param context The context to attempt to update
   * @param block The block
   * @param headerValidationMode Determines the validation to perform on this header.
   * @param ommerValidationMode Determines the validation to perform on ommer headers.
   * @return {@code true} if the block was added somewhere in the blockchain; otherwise {@code
   *     false}
   */
  boolean importBlock(
      ProtocolContext context,
      Block block,
      HeaderValidationMode headerValidationMode,
      HeaderValidationMode ommerValidationMode);

  /**
   * Attempts to import the given block. Uses "fast" validation. Performs light validation using the
   * block's receipts rather than processing all transactions and fully validating world state.
   *
   * @param context The context to attempt to update
   * @param block The block
   * @param receipts The receipts associated with this block.
   * @param headerValidationMode Determines the validation to perform on this header.
   * @param ommerValidationMode Determines the validation to perform on ommer headers.
   * @return {@code true} if the block was added somewhere in the blockchain; otherwise {@code
   *     false}
   */
  boolean fastImportBlock(
      ProtocolContext context,
      Block block,
      List<TransactionReceipt> receipts,
      HeaderValidationMode headerValidationMode,
      HeaderValidationMode ommerValidationMode);
}
