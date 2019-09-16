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
package org.hyperledger.besu.chainimport;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.logging.log4j.LogManager.getLogger;

import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.util.RawBlockIterator;
import org.hyperledger.besu.util.uint.UInt256;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import com.google.common.base.MoreObjects;
import org.apache.logging.log4j.Logger;

/** Tool for importing rlp-encoded block data from files. */
public class RlpBlockImporter {
  private static final Logger LOG = getLogger();

  private final Semaphore blockBacklog = new Semaphore(2);

  private final ExecutorService validationExecutor = Executors.newCachedThreadPool();
  private final ExecutorService importExecutor = Executors.newSingleThreadExecutor();

  /**
   * Imports blocks that are stored as concatenated RLP sections in the given file into Besu's block
   * storage.
   *
   * @param blocks Path to the file containing the blocks
   * @param besuController the BesuController that defines blockchain behavior
   * @param <C> the consensus context type
   * @return the import result
   * @throws IOException On Failure
   */
  public <C> RlpBlockImporter.ImportResult importBlockchain(
      final Path blocks, final BesuController<C> besuController) throws IOException {
    final ProtocolSchedule<C> protocolSchedule = besuController.getProtocolSchedule();
    final ProtocolContext<C> context = besuController.getProtocolContext();
    final MutableBlockchain blockchain = context.getBlockchain();
    int count = 0;

    try (final RawBlockIterator iterator =
        new RawBlockIterator(
            blocks,
            rlp ->
                BlockHeader.readFrom(
                    rlp, ScheduleBasedBlockHeaderFunctions.create(protocolSchedule)))) {
      BlockHeader previousHeader = null;
      CompletableFuture<Void> previousBlockFuture = null;
      while (iterator.hasNext()) {
        final Block block = iterator.next();
        final BlockHeader header = block.getHeader();
        if (header.getNumber() == BlockHeader.GENESIS_BLOCK_NUMBER) {
          continue;
        }
        if (header.getNumber() % 100 == 0) {
          LOG.info("Import at block {}", header.getNumber());
        }
        if (blockchain.contains(header.getHash())) {
          continue;
        }
        if (previousHeader == null) {
          previousHeader = lookupPreviousHeader(blockchain, header);
        }
        final ProtocolSpec<C> protocolSpec = protocolSchedule.getByBlockNumber(header.getNumber());
        final BlockHeader lastHeader = previousHeader;

        final CompletableFuture<Void> validationFuture =
            CompletableFuture.runAsync(
                () -> validateBlock(protocolSpec, context, lastHeader, header), validationExecutor);

        final CompletableFuture<Void> extractingFuture =
            CompletableFuture.runAsync(() -> extractSignatures(block));

        final CompletableFuture<Void> calculationFutures;
        if (previousBlockFuture == null) {
          calculationFutures = extractingFuture;
        } else {
          calculationFutures = CompletableFuture.allOf(extractingFuture, previousBlockFuture);
        }

        try {
          blockBacklog.acquire();
        } catch (final InterruptedException e) {
          LOG.error("Interrupted adding to backlog.", e);
          break;
        }
        previousBlockFuture =
            validationFuture.runAfterBothAsync(
                calculationFutures,
                () -> evaluateBlock(context, block, header, protocolSpec),
                importExecutor);

        ++count;
        previousHeader = header;
      }
      if (previousBlockFuture != null) {
        previousBlockFuture.join();
      }
      return new RlpBlockImporter.ImportResult(
          blockchain.getChainHead().getTotalDifficulty(), count);
    } finally {
      validationExecutor.shutdownNow();
      try {
        validationExecutor.awaitTermination(5, SECONDS);
      } catch (final Exception e) {
        LOG.error("Error shutting down validatorExecutor.", e);
      }
      importExecutor.shutdownNow();
      try {
        importExecutor.awaitTermination(5, SECONDS);
      } catch (final Exception e) {
        LOG.error("Error shutting down importExecutor", e);
      }
      besuController.close();
    }
  }

  private void extractSignatures(final Block block) {
    final List<CompletableFuture<Void>> futures =
        new ArrayList<>(block.getBody().getTransactions().size());
    for (final Transaction tx : block.getBody().getTransactions()) {
      futures.add(CompletableFuture.runAsync(tx::getSender, validationExecutor));
    }
    for (final CompletableFuture<Void> future : futures) {
      future.join();
    }
  }

  private <C> void validateBlock(
      final ProtocolSpec<C> protocolSpec,
      final ProtocolContext<C> context,
      final BlockHeader previousHeader,
      final BlockHeader header) {
    final BlockHeaderValidator<C> blockHeaderValidator = protocolSpec.getBlockHeaderValidator();
    final boolean validHeader =
        blockHeaderValidator.validateHeader(
            header, previousHeader, context, HeaderValidationMode.DETACHED_ONLY);
    if (!validHeader) {
      throw new IllegalStateException("Invalid header at block number " + header.getNumber() + ".");
    }
  }

  private <C> void evaluateBlock(
      final ProtocolContext<C> context,
      final Block block,
      final BlockHeader header,
      final ProtocolSpec<C> protocolSpec) {
    try {
      final BlockImporter<C> blockImporter = protocolSpec.getBlockImporter();
      final boolean blockImported =
          blockImporter.importBlock(context, block, HeaderValidationMode.SKIP_DETACHED);
      if (!blockImported) {
        throw new IllegalStateException(
            "Invalid block at block number " + header.getNumber() + ".");
      }
    } finally {
      blockBacklog.release();
    }
  }

  private BlockHeader lookupPreviousHeader(
      final MutableBlockchain blockchain, final BlockHeader header) {
    return blockchain
        .getBlockHeader(header.getParentHash())
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format(
                        "Block %s does not connect to the existing chain. Current chain head %s",
                        header.getNumber(), blockchain.getChainHeadBlockNumber())));
  }

  public static final class ImportResult {

    public final UInt256 td;

    final int count;

    ImportResult(final UInt256 td, final int count) {
      this.td = td;
      this.count = count;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("td", td).add("count", count).toString();
    }
  }
}
