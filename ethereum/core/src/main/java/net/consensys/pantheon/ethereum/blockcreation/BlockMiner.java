package net.consensys.pantheon.ethereum.blockcreation;

import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.blockcreation.MiningCoordinator.MinedBlockObserver;
import net.consensys.pantheon.ethereum.core.Block;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.BlockImporter;
import net.consensys.pantheon.ethereum.mainnet.HeaderValidationMode;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.util.Subscribers;

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
public class BlockMiner<C> implements Runnable {

  private static final Logger LOG = LogManager.getLogger();

  private final AbstractBlockCreator<C> blockCreator;
  private final ProtocolContext<C> protocolContext;
  private final ProtocolSchedule<C> protocolSchedule;
  private final Subscribers<MinedBlockObserver> observers;
  private final AbstractBlockScheduler scheduler;
  private final BlockHeader parentHeader;

  public BlockMiner(
      final AbstractBlockCreator<C> blockCreator,
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
    while (!blockMined) {
      try {
        blockMined = mineBlock();
      } catch (final CancellationException ex) {
        LOG.info("Block creation process cancelled.");
        break;
      } catch (final InterruptedException ex) {
        LOG.error("Block mining was interrupted {}", ex);
      } catch (final Exception ex) {
        LOG.error("Blocking mining threw an exception {}", ex);
      }
    }
  }

  private boolean mineBlock() throws InterruptedException {
    // Ensure the block is allowed to be mined - i.e. the timestamp on the new block is sufficiently
    // ahead of the parent, and still within allowable clock tolerance.
    LOG.trace("Waiting for next block timestamp to be valid.");
    long newBlockTimestamp = scheduler.waitUntilNextBlockCanBeMined(parentHeader);

    LOG.trace("Started a mining operation.");
    Block block = blockCreator.createBlock(newBlockTimestamp);
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
}
