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

import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.BlockBodyValidator;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.BlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.plugin.services.exception.StorageException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainnetBlockValidator implements BlockValidator {

  private static final Logger LOG = LoggerFactory.getLogger(MainnetBlockValidator.class);
  protected final BlockHeaderValidator blockHeaderValidator;
  protected final BlockBodyValidator blockBodyValidator;
  protected final BlockProcessor blockProcessor;
  protected final BadBlockManager badBlockManager;

  public MainnetBlockValidator(
      final BlockHeaderValidator blockHeaderValidator,
      final BlockBodyValidator blockBodyValidator,
      final BlockProcessor blockProcessor,
      final BadBlockManager badBlockManager) {
    this.blockHeaderValidator = blockHeaderValidator;
    this.blockBodyValidator = blockBodyValidator;
    this.blockProcessor = blockProcessor;
    this.badBlockManager = badBlockManager;
  }

  /**
   * Performs a full validation and processing of a block
   *
   * @param context the {@link ProtocolContext}
   * @param block the block being validated and processed
   * @param headerValidationMode the {@link HeaderValidationMode} used for validating the header
   * @param ommerValidationMode the {@link HeaderValidationMode} used for validating the ommers
   * @return an optional containing the {@link BlockProcessingOutputs} with the output of processing
   *     the block, empty if the block was deemed invalid or couldn't be processed
   */
  @Override
  public BlockProcessingResult validateAndProcessBlock(
      final ProtocolContext context,
      final Block block,
      final HeaderValidationMode headerValidationMode,
      final HeaderValidationMode ommerValidationMode) {
    return validateAndProcessBlock(
        context, block, headerValidationMode, ommerValidationMode, true, true);
  }

  @Override
  public BlockProcessingResult validateAndProcessBlock(
      final ProtocolContext context,
      final Block block,
      final HeaderValidationMode headerValidationMode,
      final HeaderValidationMode ommerValidationMode,
      final boolean shouldPersist) {
    return validateAndProcessBlock(
        context, block, headerValidationMode, ommerValidationMode, shouldPersist, true);
  }

  @Override
  public BlockProcessingResult validateAndProcessBlock(
      final ProtocolContext context,
      final Block block,
      final HeaderValidationMode headerValidationMode,
      final HeaderValidationMode ommerValidationMode,
      final boolean shouldPersist,
      final boolean shouldRecordBadBlock) {

    final BlockHeader header = block.getHeader();
    final BlockHeader parentHeader;

    try {
      final MutableBlockchain blockchain = context.getBlockchain();
      final Optional<BlockHeader> maybeParentHeader =
          blockchain.getBlockHeader(header.getParentHash());
      if (maybeParentHeader.isEmpty()) {
        var retval =
            new BlockProcessingResult(
                "Parent block with hash " + header.getParentHash() + " not present");
        handleAndLogImportFailure(block, retval, shouldRecordBadBlock);
        return retval;
      }
      parentHeader = maybeParentHeader.get();

      if (!blockHeaderValidator.validateHeader(
          header, parentHeader, context, headerValidationMode)) {
        var retval = new BlockProcessingResult("header validation rule violated, see logs");
        handleAndLogImportFailure(block, retval, shouldRecordBadBlock);
        return retval;
      }
    } catch (StorageException ex) {
      var retval = new BlockProcessingResult(Optional.empty(), ex);
      handleAndLogImportFailure(block, retval, shouldRecordBadBlock);
      return retval;
    }
    try (final var worldState =
        context.getWorldStateArchive().getMutable(parentHeader, shouldPersist).orElse(null)) {

      if (worldState == null) {
        var retval =
            new BlockProcessingResult(
                "Unable to process block because parent world state "
                    + parentHeader.getStateRoot()
                    + " is not available");
        handleAndLogImportFailure(block, retval, shouldRecordBadBlock);
        return retval;
      }
      var result = processBlock(context, worldState, block);
      if (result.isFailed()) {
        handleAndLogImportFailure(block, result, shouldRecordBadBlock);
        return result;
      } else {
        List<TransactionReceipt> receipts =
            result.getYield().map(BlockProcessingOutputs::getReceipts).orElse(new ArrayList<>());
        if (!blockBodyValidator.validateBody(
            context, block, receipts, worldState.rootHash(), ommerValidationMode)) {
          handleAndLogImportFailure(block, result, shouldRecordBadBlock);
          return new BlockProcessingResult("failed to validate output of imported block");
        }

        return new BlockProcessingResult(
            Optional.of(new BlockProcessingOutputs(worldState, receipts)));
      }
    } catch (MerkleTrieException ex) {
      context
          .getSynchronizer()
          .ifPresentOrElse(
              synchronizer ->
                  synchronizer.healWorldState(ex.getMaybeAddress(), ex.getLocation(), true),
              () ->
                  handleAndLogImportFailure(
                      block,
                      new BlockProcessingResult(Optional.empty(), ex),
                      shouldRecordBadBlock));
      return new BlockProcessingResult(Optional.empty(), ex);
    } catch (StorageException ex) {
      var retval = new BlockProcessingResult(Optional.empty(), ex);
      handleAndLogImportFailure(block, retval, shouldRecordBadBlock);
      return retval;
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private void handleAndLogImportFailure(
      final Block invalidBlock,
      final BlockValidationResult result,
      final boolean shouldRecordBadBlock) {
    if (result.causedBy().isPresent()) {
      LOG.info(
          "Invalid block {}: {}, caused by {}",
          invalidBlock.toLogString(),
          result.errorMessage,
          result.causedBy().get());
      LOG.debug("with stack", result.causedBy().get());
    } else {
      if (result.errorMessage.isPresent()) {
        LOG.info("Invalid block {}: {}", invalidBlock.toLogString(), result.errorMessage);
      } else {
        LOG.info("Invalid block {}", invalidBlock.toLogString());
      }
    }
    if (shouldRecordBadBlock) {
      badBlockManager.addBadBlock(invalidBlock, result.causedBy());
    } else {
      LOG.debug("Invalid block {} not added to badBlockManager ", invalidBlock.toLogString());
    }
  }

  /**
   * Processes a block, returning the result of the processing
   *
   * @param context the ProtocolContext
   * @param worldState the world state for the parent block state root hash
   * @param block the block to be processed
   * @return the result of processing the block
   */
  protected BlockProcessingResult processBlock(
      final ProtocolContext context, final MutableWorldState worldState, final Block block) {

    return blockProcessor.processBlock(context.getBlockchain(), worldState, block);
  }

  @Override
  public boolean fastBlockValidation(
      final ProtocolContext context,
      final Block block,
      final List<TransactionReceipt> receipts,
      final HeaderValidationMode headerValidationMode,
      final HeaderValidationMode ommerValidationMode) {
    final BlockHeader header = block.getHeader();
    if (!blockHeaderValidator.validateHeader(header, context, headerValidationMode)) {
      badBlockManager.addBadBlock(block, Optional.empty());
      return false;
    }

    if (!blockBodyValidator.validateBodyLight(context, block, receipts, ommerValidationMode)) {
      badBlockManager.addBadBlock(block, Optional.empty());
      return false;
    }
    return true;
  }
}
