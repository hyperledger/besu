/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.eth.sync.ValidationPolicy;
import org.hyperledger.besu.ethereum.eth.sync.tasks.exceptions.InvalidBlockException;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.List;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FastImportBlocksStep<C> implements Consumer<List<BlockWithReceipts>> {
  private static final Logger LOG = LogManager.getLogger();
  private final ProtocolSchedule<C> protocolSchedule;
  private final ProtocolContext<C> protocolContext;
  private final ValidationPolicy headerValidationPolicy;
  private final ValidationPolicy ommerValidationPolicy;

  public FastImportBlocksStep(
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final ValidationPolicy headerValidationPolicy,
      final ValidationPolicy ommerValidationPolicy) {
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.headerValidationPolicy = headerValidationPolicy;
    this.ommerValidationPolicy = ommerValidationPolicy;
  }

  @Override
  public void accept(final List<BlockWithReceipts> blocksWithReceipts) {
    for (final BlockWithReceipts blockWithReceipts : blocksWithReceipts) {
      if (!importBlock(blockWithReceipts)) {
        throw new InvalidBlockException(
            "Failed to import block",
            blockWithReceipts.getHeader().getNumber(),
            blockWithReceipts.getHash());
      }
    }
    final long firstBlock = blocksWithReceipts.get(0).getNumber();
    final long lastBlock = blocksWithReceipts.get(blocksWithReceipts.size() - 1).getNumber();
    LOG.info("Completed importing chain segment {} to {}", firstBlock, lastBlock);
  }

  private boolean importBlock(final BlockWithReceipts blockWithReceipts) {
    final BlockImporter<C> importer =
        protocolSchedule.getByBlockNumber(blockWithReceipts.getNumber()).getBlockImporter();
    return importer.fastImportBlock(
        protocolContext,
        blockWithReceipts.getBlock(),
        blockWithReceipts.getReceipts(),
        headerValidationPolicy.getValidationModeForNextBlock(),
        ommerValidationPolicy.getValidationModeForNextBlock());
  }
}
