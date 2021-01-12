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
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.BlockBodyValidator;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.BlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;

import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.Logger;

public class MainnetBlockValidator implements BlockValidator {

  private static final Logger LOG = getLogger();

  private final BlockHeaderValidator blockHeaderValidator;

  private final BlockBodyValidator blockBodyValidator;

  private final BlockProcessor blockProcessor;

  private final BadBlockManager badBlockManager;

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

  @Override
  public Optional<BlockProcessingOutputs> validateAndProcessBlock(
      final ProtocolContext context,
      final Block block,
      final HeaderValidationMode headerValidationMode,
      final HeaderValidationMode ommerValidationMode) {
    final BlockHeader header = block.getHeader();

    final Optional<BlockHeader> maybeParentHeader =
        context.getBlockchain().getBlockHeader(header.getParentHash());
    if (!maybeParentHeader.isPresent()) {
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

    final MutableBlockchain blockchain = context.getBlockchain();
    final Optional<MutableWorldState> maybeWorldState =
        context
            .getWorldStateArchive()
            .getMutable(parentHeader.getStateRoot(), parentHeader.getHash());
    if (!maybeWorldState.isPresent()) {
      LOG.debug(
          "Unable to process block {} because parent world state {} is not available",
          header.getNumber(),
          parentHeader.getStateRoot());
      badBlockManager.addBadBlock(block);
      return Optional.empty();
    }
    final MutableWorldState worldState = maybeWorldState.get();
    final BlockProcessor.Result result = blockProcessor.processBlock(blockchain, worldState, block);
    if (!result.isSuccessful()) {
      badBlockManager.addBadBlock(block);
      return Optional.empty();
    }

    final List<TransactionReceipt> receipts = result.getReceipts();
    if (!blockBodyValidator.validateBody(
        context, block, receipts, worldState.rootHash(), ommerValidationMode)) {
      badBlockManager.addBadBlock(block);
      return Optional.empty();
    }

    return Optional.of(new BlockProcessingOutputs(worldState, receipts));
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
