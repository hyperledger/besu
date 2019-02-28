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
package tech.pegasys.pantheon.ethereum.blockcreation;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.MinedBlockObserver;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockImporter;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.util.Subscribers;

import java.util.concurrent.CancellationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
public class BlockMiner<C, M extends AbstractBlockCreator<C>> implements Runnable {

  private static final Logger LOG = LogManager.getLogger();

  protected final M blockCreator;
  protected final ProtocolContext<C> protocolContext;
  protected final BlockHeader parentHeader;

  private final ProtocolSchedule<C> protocolSchedule;
  private final Subscribers<MinedBlockObserver> observers;
  private final AbstractBlockScheduler scheduler;

  public BlockMiner(
      final M blockCreator,
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final Subscribers<MinedBlockObserver> observers,
      final AbstractBlockScheduler scheduler,
      final BlockHeader parentHeader) {
    this.blockCreator = blockCreator;
    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
    this.observers = observers;
    this.scheduler = scheduler;
    this.parentHeader = parentHeader;
  }

  @Override
  public void run() {

    boolean blockMined = false;
    while (!blockMined && !blockCreator.isCancelled()) {
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

  protected boolean mineBlock() throws InterruptedException {
    // Ensure the block is allowed to be mined - i.e. the timestamp on the new block is sufficiently
    // ahead of the parent, and still within allowable clock tolerance.
    LOG.trace("Started a mining operation.");

    final long newBlockTimestamp = scheduler.waitUntilNextBlockCanBeMined(parentHeader);

    LOG.trace("Mining a new block with timestamp {}", newBlockTimestamp);
    final Block block = blockCreator.createBlock(newBlockTimestamp);
    LOG.info(
        "Block created, importing to local chain, block includes {} transactions",
        block.getBody().getTransactions().size());

    final BlockImporter<C> importer =
        protocolSchedule.getByBlockNumber(block.getHeader().getNumber()).getBlockImporter();
    final boolean blockImported =
        importer.importBlock(protocolContext, block, HeaderValidationMode.FULL);
    if (blockImported) {
      notifyNewBlockListeners(block);
      LOG.trace("Block {} imported to block chain.", block.getHeader().getNumber());
    } else {
      LOG.error("Illegal block mined, could not be imported to local chain.");
    }
    return blockImported;
  }

  public void cancel() {
    blockCreator.cancel();
  }

  private void notifyNewBlockListeners(final Block block) {
    observers.forEach(obs -> obs.blockMined(block));
  }

  public BlockHeader getParentHeader() {
    return parentHeader;
  }
}
