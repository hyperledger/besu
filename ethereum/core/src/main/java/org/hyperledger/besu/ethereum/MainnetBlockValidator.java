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
import org.hyperledger.besu.ethereum.goquorum.GoQuorumBlockProcessingResult;
import org.hyperledger.besu.ethereum.mainnet.BlockBodyValidator;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.BlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.plugin.services.exception.StorageException;

import java.util.ArrayList;
import java.util.Collections;
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
    return validateAndProcessBlock(context, block, headerValidationMode, ommerValidationMode, true);
  }

  @Override
  public BlockProcessingResult validateAndProcessBlock(
      final ProtocolContext context,
      final Block block,
      final HeaderValidationMode headerValidationMode,
      final HeaderValidationMode ommerValidationMode,
      final boolean shouldPersist) {

    try {
      final BlockHeader header = block.getHeader();

      final MutableBlockchain blockchain = context.getBlockchain();
      Optional<BlockHeader> maybeParentHeader = blockchain.getBlockHeader(header.getParentHash());
      if (maybeParentHeader.isEmpty()) {
        var retval =
            new BlockProcessingResult(
                "Parent block with hash " + header.getParentHash() + " not present");
        handleAndLogImportFailure(block, retval);
        return retval;
      }
      final BlockHeader parentHeader = maybeParentHeader.get();

      if (!blockHeaderValidator.validateHeader(
          header, parentHeader, context, headerValidationMode)) {
        var retval = new BlockProcessingResult("header validation rule violated, see logs");
        handleAndLogImportFailure(block, retval);
        return retval;
      }

      final Optional<MutableWorldState> maybeWorldState =
          context
              .getWorldStateArchive()
              .getMutable(parentHeader.getStateRoot(), parentHeader.getHash());

      if (maybeWorldState.isEmpty()) {
        var retval =
            new BlockProcessingResult(
                "Unable to process block because parent world state "
                    + parentHeader.getStateRoot()
                    + " is not available");
        handleAndLogImportFailure(block, retval);
        return retval;
      }
      final MutableWorldState worldState =
          shouldPersist ? maybeWorldState.get() : maybeWorldState.get().copy();

      var retval = processBlock(context, worldState, block);
      if (retval.isFailed()) {
        handleAndLogImportFailure(block, retval);
        return retval;
      } else {
        // didn't fail, should have some output so it should be safe to narrow it.

        var output = retval;

        List<TransactionReceipt> receipts =
            output.getYield().map(BlockProcessingOutputs::getReceipts).orElse(new ArrayList<>());
        if (!blockBodyValidator.validateBody(
            context, block, receipts, worldState.rootHash(), ommerValidationMode)) {
          handleAndLogImportFailure(block, output);
          return output;
        }
        if (output instanceof GoQuorumBlockProcessingResult) {
          var privateOutput = (GoQuorumBlockProcessingResult) output;
          if (!privateOutput.getPrivateReceipts().isEmpty()) {
            // replace the public receipts for marker transactions with the private receipts if we
            // are
            // in
            // goQuorumCompatibilityMode. That can be done now because we have validated the block.
            final List<TransactionReceipt> privateTransactionReceipts =
                privateOutput.getPrivateReceipts();
            final ArrayList<TransactionReceipt> resultingList = new ArrayList<>(receipts.size());
            for (int i = 0; i < receipts.size(); i++) {
              if (privateTransactionReceipts.get(i) != null) {
                resultingList.add(privateTransactionReceipts.get(i));
              } else {
                resultingList.add(receipts.get(i));
              }
            }
            receipts = Collections.unmodifiableList(resultingList);
          }
        }

        // it's almost like we have an intermediate BlockProcessingResult that has both public and
        // private receipts, but we flatten it.
        return new BlockProcessingResult(new BlockProcessingOutputs(worldState, receipts));
      }
    } catch (StorageException dbProblem) {
      var retval = new BlockProcessingResult(BlockProcessingOutputs.empty(), dbProblem);
      handleAndLogImportFailure(block, retval);
      return retval;
    }
  }

  private void handleAndLogImportFailure(
      final Block invalidBlock, final BlockValidationResult result) {
    if (result.causedBy().isPresent()) {
      LOG.info(
          "{}. Block {}, caused by {}",
          result.errorMessage,
          invalidBlock.toLogString(),
          result.causedBy().get());
      LOG.debug("with stack", result.causedBy().get());
      if (!result.isInternalError()) {
        badBlockManager.addBadBlock(invalidBlock);
      }

    } else {
      LOG.info("{}. Block {}", result.errorMessage, invalidBlock.toLogString());
      badBlockManager.addBadBlock(invalidBlock);
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
      badBlockManager.addBadBlock(block);
      return false;
    }

    if (!blockBodyValidator.validateBodyLight(context, block, receipts, ommerValidationMode)) {
      badBlockManager.addBadBlock(block);
      return false;
    }
    return true;
  }
}
