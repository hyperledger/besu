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
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.sync.ValidationPolicy;
import org.hyperledger.besu.ethereum.eth.sync.tasks.exceptions.InvalidBlockException;
import org.hyperledger.besu.ethereum.mainnet.BlockImportResult;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImportBlocksStep implements Consumer<List<BlockWithReceipts>> {
  private static final Logger LOG = LoggerFactory.getLogger(ImportBlocksStep.class);
  private static final long PRINT_DELAY = TimeUnit.SECONDS.toMillis(30L);

  private final ProtocolSchedule protocolSchedule;
  protected final ProtocolContext protocolContext;
  private final ValidationPolicy headerValidationPolicy;
  private final ValidationPolicy ommerValidationPolicy;
  private final EthContext ethContext;
  private long accumulatedTime = 0L;
  private OptionalLong logStartBlock = OptionalLong.empty();
  private final BlockHeader pivotHeader;

  public ImportBlocksStep(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final ValidationPolicy headerValidationPolicy,
      final ValidationPolicy ommerValidationPolicy,
      final EthContext ethContext,
      final BlockHeader pivotHeader) {
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.headerValidationPolicy = headerValidationPolicy;
    this.ommerValidationPolicy = ommerValidationPolicy;
    this.ethContext = ethContext;
    this.pivotHeader = pivotHeader;
  }

  @Override
  public void accept(final List<BlockWithReceipts> blocksWithReceipts) {
    final long startTime = System.nanoTime();
    for (final BlockWithReceipts blockWithReceipts : blocksWithReceipts) {
      if (!importBlock(blockWithReceipts)) {
        throw new InvalidBlockException(
            "Failed to import block",
            blockWithReceipts.getHeader().getNumber(),
            blockWithReceipts.getHash());
      }
      LOG.atTrace()
          .setMessage("Imported block {}")
          .addArgument(blockWithReceipts.getBlock()::toLogString)
          .log();
    }
    if (logStartBlock.isEmpty()) {
      logStartBlock = OptionalLong.of(blocksWithReceipts.get(0).getNumber());
    }
    final long lastBlock = blocksWithReceipts.get(blocksWithReceipts.size() - 1).getNumber();
    int peerCount = -1; // ethContext is not available in tests
    if (ethContext != null && ethContext.getEthPeers().peerCount() >= 0) {
      peerCount = ethContext.getEthPeers().peerCount();
    }
    final long endTime = System.nanoTime();

    accumulatedTime += TimeUnit.MILLISECONDS.convert(endTime - startTime, TimeUnit.NANOSECONDS);
    if (accumulatedTime > PRINT_DELAY) {
      final long blocksPercent = getBlocksPercent(lastBlock, pivotHeader.getNumber());
      LOG.info(
          "Block import progress: {} of {} ({}%)",
          lastBlock, pivotHeader.getNumber(), blocksPercent);
      LOG.debug(
          "Completed importing chain segment {} to {} ({} blocks in {}ms), Peer count: {}",
          logStartBlock.getAsLong(),
          lastBlock,
          lastBlock - logStartBlock.getAsLong() + 1,
          accumulatedTime,
          peerCount);
      accumulatedTime = 0L;
      logStartBlock = OptionalLong.empty();
    }
  }

  @VisibleForTesting
  protected static long getBlocksPercent(final long lastBlock, final long totalBlocks) {
    if (totalBlocks == 0) {
      return 0;
    }
    final long blocksPercent = (100 * lastBlock / totalBlocks);
    return blocksPercent;
  }

  protected boolean importBlock(final BlockWithReceipts blockWithReceipts) {
    final BlockImporter importer =
        protocolSchedule.getByBlockHeader(blockWithReceipts.getHeader()).getBlockImporter();
    final BlockImportResult blockImportResult =
        importer.fastImportBlock(
            protocolContext,
            blockWithReceipts.getBlock(),
            blockWithReceipts.getReceipts(),
            headerValidationPolicy.getValidationModeForNextBlock(),
            ommerValidationPolicy.getValidationModeForNextBlock());
    return blockImportResult.isImported();
  }
}
