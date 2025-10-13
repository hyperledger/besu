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
package org.hyperledger.besu.ethereum;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.BodyValidationMode;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;

import java.util.List;

/**
 * The BlockValidator interface defines the methods for validating and processing blocks in the
 * Ethereum protocol.
 */
public interface BlockValidator {

  /**
   * Validates and processes a block with the given context, block, header validation mode, and
   * ommer validation mode.
   *
   * @param context the protocol context
   * @param block the block to validate and process
   * @param headerValidationMode the header validation mode
   * @param ommerValidationMode the ommer validation mode
   * @return the result of the block processing
   */
  BlockProcessingResult validateAndProcessBlock(
      final ProtocolContext context,
      final Block block,
      final HeaderValidationMode headerValidationMode,
      final HeaderValidationMode ommerValidationMode);

  /**
   * Validates and processes a block with the given context, block, header validation mode, ommer
   * validation mode, and persistence flag.
   *
   * @param context the protocol context
   * @param block the block to validate and process
   * @param headerValidationMode the header validation mode
   * @param ommerValidationMode the ommer validation mode
   * @param shouldPersist flag indicating whether the block should be persisted
   * @return the result of the block processing
   */
  BlockProcessingResult validateAndProcessBlock(
      final ProtocolContext context,
      final Block block,
      final HeaderValidationMode headerValidationMode,
      final HeaderValidationMode ommerValidationMode,
      final boolean shouldPersist);

  /**
   * Validates and processes a block with the given context, block, header validation mode, ommer
   * validation mode, persistence flag, and bad block recording flag.
   *
   * @param context the protocol context
   * @param block the block to validate and process
   * @param headerValidationMode the header validation mode
   * @param ommerValidationMode the ommer validation mode
   * @param shouldPersist flag indicating whether the block should be persisted
   * @param shouldRecordBadBlock flag indicating whether bad blocks should be recorded
   * @return the result of the block processing
   */
  BlockProcessingResult validateAndProcessBlock(
      final ProtocolContext context,
      final Block block,
      final HeaderValidationMode headerValidationMode,
      final HeaderValidationMode ommerValidationMode,
      final boolean shouldPersist,
      final boolean shouldRecordBadBlock);

  /**
   * Performs fast block validation appropriate for use during syncing skipping transaction receipt
   * roots and receipts roots as these are done during the download of the blocks.
   *
   * @param context the protocol context
   * @param block the block to validate
   * @param receipts the transaction receipts
   * @param headerValidationMode the header validation mode
   * @param ommerValidationMode the ommer validation mode
   * @param bodyValidationMode the body validation mode
   * @return true if the block is valid, false otherwise
   */
  boolean validateBlockForSyncing(
      final ProtocolContext context,
      final Block block,
      final List<TransactionReceipt> receipts,
      final HeaderValidationMode headerValidationMode,
      final HeaderValidationMode ommerValidationMode,
      final BodyValidationMode bodyValidationMode);
}
