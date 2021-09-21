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

import static org.apache.logging.log4j.LogManager.getLogger;

import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.evm.worldstate.MutableWorldState;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.BlockBodyValidator;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.BlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.BlockProcessor.Result;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.Logger;

public class MainnetBlockValidator implements BlockValidator {

  private static final Logger LOG = getLogger();
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
  public Optional<BlockProcessingOutputs> validateAndProcessBlock(
      final ProtocolContext context,
      final Block block,
      final HeaderValidationMode headerValidationMode,
      final HeaderValidationMode ommerValidationMode) {
    final BlockHeader header = block.getHeader();

    final MutableBlockchain blockchain = context.getBlockchain();
    final Optional<BlockHeader> maybeParentHeader =
        blockchain.getBlockHeader(header.getParentHash());
    if (maybeParentHeader.isEmpty()) {
      LOG.error(
          "Attempted to import block {} with hash {} but parent block {} was not present",
          header.getNumber(),
          header.getHash(),
          header.getParentHash());
      badBlockManager.addBadBlock(block);
      return Optional.empty();
    }
    final BlockHeader parentHeader = maybeParentHeader.get();

    if (!blockHeaderValidator.validateHeader(header, parentHeader, context, headerValidationMode)) {
      badBlockManager.addBadBlock(block);
      return Optional.empty();
    }

    final Optional<MutableWorldState> maybeWorldState =
        context
            .getWorldStateArchive()
            .getMutable(parentHeader.getStateRoot(), parentHeader.getHash());
    if (!maybeWorldState.isPresent()) {
      LOG.debug(
          "Unable to process block {} because parent world state {} is not available",
          block.getHeader().getNumber(),
          parentHeader.getStateRoot());
      badBlockManager.addBadBlock(block);
      return Optional.empty();
    }
    final MutableWorldState worldState = maybeWorldState.get();

    final Result result = processBlock(context, worldState, block);
    if (result.isFailed()) {
      badBlockManager.addBadBlock(block);
      return Optional.empty();
    }

    List<TransactionReceipt> receipts = result.getReceipts();
    if (!blockBodyValidator.validateBody(
        context, block, receipts, worldState.rootHash(), ommerValidationMode)) {
      badBlockManager.addBadBlock(block);
      return Optional.empty();
    }

    if (!result.getPrivateReceipts().isEmpty()) {
      // replace the public receipts for marker transactions with the private receipts if we are in
      // goQuorumCompatibilityMode. That can be done now because we have validated the block.
      final List<TransactionReceipt> privateTransactionReceipts = result.getPrivateReceipts();
      final ArrayList<TransactionReceipt> resultingList = new ArrayList<>();
      for (int i = 0; i < receipts.size(); i++) {
        if (privateTransactionReceipts.get(i) != null) {
          resultingList.add(privateTransactionReceipts.get(i));
        } else {
          resultingList.add(receipts.get(i));
        }
      }
      receipts = Collections.unmodifiableList(resultingList);
    }

    return Optional.of(new BlockProcessingOutputs(worldState, receipts));
  }

  /**
   * Processes a block, returning the result of the processing
   *
   * @param context the ProtocolContext
   * @param worldState the world state for the parent block state root hash
   * @param block the block to be processed
   * @return the result of processing the block
   */
  protected Result processBlock(
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
