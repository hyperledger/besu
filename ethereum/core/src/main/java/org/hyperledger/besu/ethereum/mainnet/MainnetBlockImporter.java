/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;

import java.util.List;
import java.util.Optional;

public class MainnetBlockImporter<C> implements BlockImporter<C> {

  final BlockValidator<C> blockValidator;

  public MainnetBlockImporter(final BlockValidator<C> blockValidator) {
    this.blockValidator = blockValidator;
  }

  @Override
  public synchronized boolean importBlock(
      final ProtocolContext<C> context,
      final Block block,
      final HeaderValidationMode headerValidationMode,
      final HeaderValidationMode ommerValidationMode) {
    if (context.getBlockchain().contains(block.getHash())) {
      return true;
    }

    final Optional<BlockValidator.BlockProcessingOutputs> outputs =
        blockValidator.validateAndProcessBlock(
            context, block, headerValidationMode, ommerValidationMode);

    outputs.ifPresent(processingOutputs -> persistState(processingOutputs, block, context));

    return outputs.isPresent();
  }

  private void persistState(
      final BlockValidator.BlockProcessingOutputs processingOutputs,
      final Block block,
      final ProtocolContext<C> context) {
    processingOutputs.worldState.persist();
    final MutableBlockchain blockchain = context.getBlockchain();
    blockchain.appendBlock(block, processingOutputs.receipts);
  }

  @Override
  public boolean fastImportBlock(
      final ProtocolContext<C> context,
      final Block block,
      final List<TransactionReceipt> receipts,
      final HeaderValidationMode headerValidationMode,
      final HeaderValidationMode ommerValidationMode) {

    if (blockValidator.fastBlockValidation(
        context, block, receipts, headerValidationMode, ommerValidationMode)) {
      context.getBlockchain().appendBlock(block, receipts);
      return true;
    }

    return false;
  }
}
