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
package tech.pegasys.pantheon.ethereum.mainnet;

import tech.pegasys.pantheon.ethereum.BlockValidator;
import tech.pegasys.pantheon.ethereum.BlockValidator.BlockProcessingOutputs;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockImporter;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;

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

    final Optional<BlockProcessingOutputs> outputs =
        blockValidator.validateAndProcessBlock(
            context, block, headerValidationMode, ommerValidationMode);

    outputs.ifPresent(processingOutputs -> persistState(processingOutputs, block, context));

    return outputs.isPresent();
  }

  private void persistState(
      final BlockProcessingOutputs processingOutputs,
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
      final HeaderValidationMode headerValidationMode) {

    if (blockValidator.fastBlockValidation(context, block, receipts, headerValidationMode)) {
      context.getBlockchain().appendBlock(block, receipts);
      return true;
    }

    return false;
  }
}
