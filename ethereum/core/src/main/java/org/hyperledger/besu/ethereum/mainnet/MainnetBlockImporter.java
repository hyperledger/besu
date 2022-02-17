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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;

import java.util.List;

public class MainnetBlockImporter implements BlockImporter {

  final BlockValidator blockValidator;

  public MainnetBlockImporter(final BlockValidator blockValidator) {
    this.blockValidator = blockValidator;
  }

  @Override
  public synchronized boolean importBlock(
      final ProtocolContext context,
      final Block block,
      final HeaderValidationMode headerValidationMode,
      final HeaderValidationMode ommerValidationMode) {
    if (context.getBlockchain().contains(block.getHash())) {
      return true;
    }

    final var result =
        blockValidator.validateAndProcessBlock(
            context, block, headerValidationMode, ommerValidationMode);

    result.blockProcessingOutputs.ifPresent(
        processingOutputs ->
            context.getBlockchain().appendBlock(block, processingOutputs.receipts));

    return result.blockProcessingOutputs.isPresent();
  }

  @Override
  public boolean fastImportBlock(
      final ProtocolContext context,
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
