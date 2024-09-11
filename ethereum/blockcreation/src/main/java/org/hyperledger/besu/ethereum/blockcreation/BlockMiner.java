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
package org.hyperledger.besu.ethereum.blockcreation;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.BlockCreator.BlockCreationResult;
import org.hyperledger.besu.ethereum.chain.MinedBlockObserver;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.BlockImportResult;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.util.Subscribers;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for creating a block, and importing it to the blockchain. This is specifically a
 * mainnet capability (as IBFT would then use the block as part of a proposal round).
 *
 * <p>While the capability is largely functional, it has been wrapped in an object to allow it to be
 * cancelled safely.
 *
 * <p>This class is responsible for mining a single block only - the AbstractBlockCreator maintains
 * state so must be destroyed between block mining activities.
 */
public class BlockMiner<M extends AbstractBlockCreator> implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(BlockMiner.class);

  protected final Function<BlockHeader, M> blockCreatorFactory;
  protected final M minerBlockCreator;

  protected final ProtocolContext protocolContext;
  protected final BlockHeader parentHeader;

  private final ProtocolSchedule protocolSchedule;
  private final Subscribers<MinedBlockObserver> observers;
  private final AbstractBlockScheduler scheduler;

  public BlockMiner(
      final Function<BlockHeader, M> blockCreatorFactory,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final Subscribers<MinedBlockObserver> observers,
      final AbstractBlockScheduler scheduler,
      final BlockHeader parentHeader) {
    this.blockCreatorFactory = blockCreatorFactory;
    this.minerBlockCreator = blockCreatorFactory.apply(parentHeader);
    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
    this.observers = observers;
    this.scheduler = scheduler;
    this.parentHeader = parentHeader;
  }

  @Override
  public void run() {

    boolean blockMined = false;
    while (!blockMined && !minerBlockCreator.isCancelled()) {
      try {
        blockMined = mineBlock();
      } catch (final CancellationException ex) {
        LOG.debug("Block creation process cancelled.");
        break;
      } catch (final InterruptedException ex) {
        LOG.debug("Block mining was interrupted.", ex);
        Thread.currentThread().interrupt();
      } catch (final Exception ex) {
        LOG.error("Block mining threw an unhandled exception.", ex);
      }
    }
  }

  /**
   * Create a block with the given transactions and ommers. The list of transactions are validated
   * as they are processed, and are not guaranteed to be included in the final block. If
   * transactions must match exactly, the caller must verify they were all able to be included.
   *
   * @param parentHeader The header of the parent of the block to be produced
   * @param transactions The list of transactions which may be included.
   * @param ommers The list of ommers to include.
   * @return the newly created block.
   */
  public BlockCreationResult createBlock(
      final BlockHeader parentHeader,
      final List<Transaction> transactions,
      final List<BlockHeader> ommers) {
    final BlockCreator blockCreator = this.blockCreatorFactory.apply(parentHeader);
    final long timestamp = scheduler.getNextTimestamp(parentHeader).timestampForHeader();
    return blockCreator.createBlock(transactions, ommers, timestamp, parentHeader);
  }

  /**
   * Create a block with the given timestamp.
   *
   * @param parentHeader The header of the parent of the block to be produced
   * @param timestamp unix timestamp of the new block.
   * @return the newly created block.
   */
  public BlockCreationResult createBlock(final BlockHeader parentHeader, final long timestamp) {
    final BlockCreator blockCreator = this.blockCreatorFactory.apply(parentHeader);
    return blockCreator.createBlock(Optional.empty(), Optional.empty(), timestamp, parentHeader);
  }

  protected boolean shouldImportBlock(final Block block) throws InterruptedException {
    return true;
  }

  protected boolean mineBlock() throws InterruptedException {
    // Ensure the block is allowed to be mined - i.e. the timestamp on the new block is sufficiently
    // ahead of the parent, and still within allowable clock tolerance.
    final var timing = new BlockCreationTiming();

    LOG.trace("Started a mining operation.");

    final long newBlockTimestamp = scheduler.waitUntilNextBlockCanBeMined(parentHeader);
    timing.register("protocolWait");

    LOG.trace("Mining a new block with timestamp {}", newBlockTimestamp);

    final var blockCreationResult = minerBlockCreator.createBlock(newBlockTimestamp, parentHeader);
    timing.registerAll(blockCreationResult.getBlockCreationTimings());

    final Block block = blockCreationResult.getBlock();
    LOG.trace(
        "Block created, importing to local chain, block includes {} transactions",
        block.getBody().getTransactions().size());

    if (!shouldImportBlock(block)) {
      return false;
    }

    final BlockImporter importer =
        protocolSchedule.getByBlockHeader(block.getHeader()).getBlockImporter();
    final BlockImportResult blockImportResult =
        importer.importBlock(protocolContext, block, HeaderValidationMode.FULL);
    timing.register("importingBlock");
    if (blockImportResult.isImported()) {
      notifyNewBlockListeners(block);
      timing.register("notifyListeners");
      logProducedBlock(block, timing);
    } else {
      LOG.error("Illegal block mined, could not be imported to local chain.");
    }
    return blockImportResult.isImported();
  }

  private void logProducedBlock(final Block block, final BlockCreationTiming blockCreationTiming) {
    LOG.info(
        String.format(
            "Produced #%,d / %d tx / %d om / %,d (%01.1f%%) gas / (%s) in %01.3fs / Timing(%s)",
            block.getHeader().getNumber(),
            block.getBody().getTransactions().size(),
            block.getBody().getOmmers().size(),
            block.getHeader().getGasUsed(),
            (block.getHeader().getGasUsed() * 100.0) / block.getHeader().getGasLimit(),
            block.getHash(),
            blockCreationTiming.end("log").toMillis() / 1000.0,
            blockCreationTiming));
  }

  public void cancel() {
    minerBlockCreator.cancel();
  }

  private void notifyNewBlockListeners(final Block block) {
    observers.forEach(obs -> obs.blockMined(block));
  }

  public BlockHeader getParentHeader() {
    return parentHeader;
  }
}
