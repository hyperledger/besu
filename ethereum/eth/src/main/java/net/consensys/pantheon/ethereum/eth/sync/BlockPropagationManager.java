package net.consensys.pantheon.ethereum.eth.sync;

import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.chain.BlockAddedEvent;
import net.consensys.pantheon.ethereum.chain.BlockAddedEvent.EventType;
import net.consensys.pantheon.ethereum.chain.Blockchain;
import net.consensys.pantheon.ethereum.core.Block;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.eth.manager.AbstractPeerTask;
import net.consensys.pantheon.ethereum.eth.manager.EthContext;
import net.consensys.pantheon.ethereum.eth.manager.EthMessage;
import net.consensys.pantheon.ethereum.eth.manager.EthPeer;
import net.consensys.pantheon.ethereum.eth.messages.EthPV62;
import net.consensys.pantheon.ethereum.eth.messages.NewBlockHashesMessage;
import net.consensys.pantheon.ethereum.eth.messages.NewBlockHashesMessage.NewBlockHash;
import net.consensys.pantheon.ethereum.eth.messages.NewBlockMessage;
import net.consensys.pantheon.ethereum.eth.sync.state.PendingBlocks;
import net.consensys.pantheon.ethereum.eth.sync.state.SyncState;
import net.consensys.pantheon.ethereum.eth.sync.tasks.GetBlockFromPeerTask;
import net.consensys.pantheon.ethereum.eth.sync.tasks.PersistBlockTask;
import net.consensys.pantheon.ethereum.mainnet.HeaderValidationMode;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import net.consensys.pantheon.ethereum.rlp.RLPException;
import net.consensys.pantheon.util.uint.UInt256;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import io.netty.util.internal.ConcurrentSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BlockPropagationManager<C> {
  private static final Logger LOG = LogManager.getLogger();

  private final SynchronizerConfiguration config;
  private final ProtocolSchedule<C> protocolSchedule;
  private final ProtocolContext<C> protocolContext;
  private final EthContext ethContext;
  private final SyncState syncState;

  private final AtomicBoolean started = new AtomicBoolean(false);

  private final Set<Hash> requestedBlocks = new ConcurrentSet<>();
  private final PendingBlocks pendingBlocks;

  BlockPropagationManager(
      final SynchronizerConfiguration config,
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final SyncState syncState) {
    this.config = config;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;

    this.syncState = syncState;
    pendingBlocks = syncState.pendingBlocks();
  }

  public void start() {
    if (started.compareAndSet(false, true)) {
      setupListeners();
    } else {
      throw new IllegalStateException(
          "Attempt to start an already started " + this.getClass().getSimpleName() + ".");
    }
  }

  private void setupListeners() {
    protocolContext.getBlockchain().observeBlockAdded(this::onBlockAdded);
    ethContext.getEthMessages().subscribe(EthPV62.NEW_BLOCK, this::handleNewBlockFromNetwork);
    ethContext
        .getEthMessages()
        .subscribe(EthPV62.NEW_BLOCK_HASHES, this::handleNewBlockHashesFromNetwork);
  }

  private void onBlockAdded(final BlockAddedEvent blockAddedEvent, final Blockchain blockchain) {
    // Check to see if any of our pending blocks are now ready for import
    final Block newBlock = blockAddedEvent.getBlock();

    List<Block> readyForImport;
    synchronized (pendingBlocks) {
      // Remove block from pendingBlocks list
      pendingBlocks.deregisterPendingBlock(newBlock);

      // Import any pending blocks that are children of the newly added block
      readyForImport = pendingBlocks.childrenOf(newBlock.getHash());
    }

    if (!readyForImport.isEmpty()) {
      final Supplier<CompletableFuture<List<Block>>> importBlocksTask =
          PersistBlockTask.forUnorderedBlocks(
              protocolSchedule, protocolContext, readyForImport, HeaderValidationMode.FULL);
      ethContext
          .getScheduler()
          .scheduleWorkerTask(importBlocksTask)
          .whenComplete(
              (r, t) -> {
                if (r != null) {
                  LOG.info("Imported {} pending blocks", r.size());
                }
              });
    }

    if (blockAddedEvent.getEventType().equals(EventType.HEAD_ADVANCED)) {
      final long head = blockchain.getChainHeadBlockNumber();
      final long cutoff = head + config.blockPropagationRange().lowerEndpoint();
      pendingBlocks.purgeBlocksOlderThan(cutoff);
    }
  }

  private void handleNewBlockFromNetwork(final EthMessage message) {
    final Blockchain blockchain = protocolContext.getBlockchain();
    final NewBlockMessage newBlockMessage = NewBlockMessage.readFrom(message.getData());
    try {
      final Block block = newBlockMessage.block(protocolSchedule);
      final UInt256 totalDifficulty = newBlockMessage.totalDifficulty(protocolSchedule);

      message.getPeer().chainState().update(block.getHeader(), totalDifficulty);

      // Return early if we don't care about this block
      final long localChainHeight = protocolContext.getBlockchain().getChainHeadBlockNumber();
      final long bestChainHeight = syncState.bestChainHeight(localChainHeight);
      if (!shouldImportBlockAtHeight(
          block.getHeader().getNumber(), localChainHeight, bestChainHeight)) {
        return;
      }
      if (pendingBlocks.contains(block.getHash())) {
        return;
      }
      if (blockchain.contains(block.getHash())) {
        return;
      }

      importOrSavePendingBlock(block);
    } catch (final RLPException e) {
      message.getPeer().disconnect(DisconnectReason.BREACH_OF_PROTOCOL);
    } finally {
      newBlockMessage.release();
    }
  }

  private void handleNewBlockHashesFromNetwork(final EthMessage message) {
    final Blockchain blockchain = protocolContext.getBlockchain();
    final NewBlockHashesMessage newBlockHashesMessage =
        NewBlockHashesMessage.readFrom(message.getData());
    try {
      // Register announced blocks
      final List<NewBlockHash> announcedBlocks =
          Lists.newArrayList(newBlockHashesMessage.getNewHashes());
      for (final NewBlockHash announcedBlock : announcedBlocks) {
        message.getPeer().registerKnownBlock(announcedBlock.hash());
        message.getPeer().registerHeight(announcedBlock.hash(), announcedBlock.number());
      }

      // Filter announced blocks for blocks we care to import
      final long localChainHeight = protocolContext.getBlockchain().getChainHeadBlockNumber();
      final long bestChainHeight = syncState.bestChainHeight(localChainHeight);
      final List<NewBlockHash> relevantAnnouncements =
          announcedBlocks
              .stream()
              .filter(a -> shouldImportBlockAtHeight(a.number(), localChainHeight, bestChainHeight))
              .collect(Collectors.toList());

      // Filter for blocks we don't yet know about
      final List<NewBlockHash> newBlocks = new ArrayList<>();
      for (final NewBlockHash announcedBlock : relevantAnnouncements) {
        if (requestedBlocks.contains(announcedBlock.hash())) {
          continue;
        }
        if (pendingBlocks.contains(announcedBlock.hash())) {
          continue;
        }
        if (blockchain.contains(announcedBlock.hash())) {
          continue;
        }
        if (requestedBlocks.add(announcedBlock.hash())) {
          newBlocks.add(announcedBlock);
        }
      }

      // Process known blocks we care about
      for (final NewBlockHash newBlock : newBlocks) {
        processAnnouncedBlock(message.getPeer(), newBlock)
            .whenComplete((r, t) -> requestedBlocks.remove(newBlock.hash()));
      }
    } catch (final RLPException e) {
      message.getPeer().disconnect(DisconnectReason.BREACH_OF_PROTOCOL);
    } finally {
      newBlockHashesMessage.release();
    }
  }

  private CompletableFuture<Block> processAnnouncedBlock(
      final EthPeer peer, final NewBlockHash newBlock) {
    final AbstractPeerTask<Block> getBlockTask =
        GetBlockFromPeerTask.create(protocolSchedule, ethContext, newBlock.hash()).assignPeer(peer);

    return getBlockTask.run().thenCompose((r) -> importOrSavePendingBlock(r.getResult()));
  }

  @VisibleForTesting
  CompletableFuture<Block> importOrSavePendingBlock(final Block block) {
    // Synchronize to avoid race condition where block import event fires after the
    // blockchain.contains() check and before the block is registered, causing onBlockAdded() to be
    // invoked for the parent of this block before we are able to register it.
    synchronized (pendingBlocks) {
      if (!protocolContext.getBlockchain().contains(block.getHeader().getParentHash())) {
        // Block isn't connected to local chain, save it to pending blocks collection
        if (pendingBlocks.registerPendingBlock(block)) {
          LOG.info(
              "Saving announced block {} ({}) for future import",
              block.getHeader().getNumber(),
              block.getHash());
        }
        return CompletableFuture.completedFuture(block);
      }
    }

    // Import block
    final PersistBlockTask<C> importTask =
        PersistBlockTask.create(
            protocolSchedule, protocolContext, block, HeaderValidationMode.FULL);
    return ethContext
        .getScheduler()
        .scheduleWorkerTask(importTask::run)
        .whenComplete(
            (r, t) -> {
              if (t != null) {
                LOG.warn(
                    "Failed to import announced block {} ({}).",
                    block.getHeader().getNumber(),
                    block.getHash());
              } else {
                LOG.info(
                    "Successfully imported announced block {} ({}).",
                    block.getHeader().getNumber(),
                    block.getHash());
              }
            });
  }

  // Only import blocks within a certain range of our head and sync target
  private boolean shouldImportBlockAtHeight(
      final long blockNumber, final long localHeight, final long bestChainHeight) {
    final long distanceFromLocalHead = blockNumber - localHeight;
    final long distanceFromBestPeer = blockNumber - bestChainHeight;
    final Range<Long> importRange = config.blockPropagationRange();
    return importRange.contains(distanceFromLocalHead)
        && importRange.contains(distanceFromBestPeer);
  }
}
