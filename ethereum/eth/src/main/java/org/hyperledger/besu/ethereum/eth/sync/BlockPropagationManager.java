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
package org.hyperledger.besu.ethereum.eth.sync;

import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent.EventType;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthMessage;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.task.RetryingGetBlockFromPeersTask;
import org.hyperledger.besu.ethereum.eth.messages.EthPV62;
import org.hyperledger.besu.ethereum.eth.messages.NewBlockHashesMessage;
import org.hyperledger.besu.ethereum.eth.messages.NewBlockHashesMessage.NewBlockHash;
import org.hyperledger.besu.ethereum.eth.messages.NewBlockMessage;
import org.hyperledger.besu.ethereum.eth.sync.state.PendingBlocksManager;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.sync.tasks.PersistBlockTask;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockPropagationManager {
  private static final Logger LOG = LoggerFactory.getLogger(BlockPropagationManager.class);
  private final SynchronizerConfiguration config;
  private final ProtocolSchedule protocolSchedule;
  private final ProtocolContext protocolContext;
  private final EthContext ethContext;
  private final SyncState syncState;
  private final MetricsSystem metricsSystem;
  private final BlockBroadcaster blockBroadcaster;

  private final AtomicBoolean started = new AtomicBoolean(false);

  private final Set<Hash> importingBlocks = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final Set<Hash> requestedBlocks = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final Set<Long> requestedNonAnnouncedBlocks =
      Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final PendingBlocksManager pendingBlocksManager;
  private Optional<Long> onBlockAddedSId = Optional.empty();
  private Optional<Long> newBlockSId;
  private Optional<Long> newBlockHashesSId;

  BlockPropagationManager(
      final SynchronizerConfiguration config,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final SyncState syncState,
      final PendingBlocksManager pendingBlocksManager,
      final MetricsSystem metricsSystem,
      final BlockBroadcaster blockBroadcaster) {
    this.config = config;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.metricsSystem = metricsSystem;
    this.blockBroadcaster = blockBroadcaster;
    this.syncState = syncState;
    this.pendingBlocksManager = pendingBlocksManager;
    this.syncState.subscribeTTDReached(this::reactToTTDReachedEvent);
  }

  public void start() {
    if (started.compareAndSet(false, true)) {
      setupListeners();
    } else {
      throw new IllegalStateException(
          "Attempt to start an already started " + this.getClass().getSimpleName() + ".");
    }
  }

  public void stop() {
    if (started.get()) {
      clearListeners();
      started.set(false);
    } else {
      LOG.warn("Attempted to stop when we are not even running...");
    }
  }

  public boolean isRunning() {
    return started.get();
  }

  private void setupListeners() {
    onBlockAddedSId =
        Optional.of(protocolContext.getBlockchain().observeBlockAdded(this::onBlockAdded));
    newBlockSId =
        Optional.of(
            ethContext
                .getEthMessages()
                .subscribe(EthPV62.NEW_BLOCK, this::handleNewBlockFromNetwork));
    newBlockHashesSId =
        Optional.of(
            ethContext
                .getEthMessages()
                .subscribe(EthPV62.NEW_BLOCK_HASHES, this::handleNewBlockHashesFromNetwork));
  }

  private void clearListeners() {
    onBlockAddedSId.ifPresent(id -> protocolContext.getBlockchain().removeObserver(id));
    newBlockSId.ifPresent(id -> ethContext.getEthMessages().unsubscribe(id, EthPV62.NEW_BLOCK));
    newBlockHashesSId.ifPresent(
        id -> ethContext.getEthMessages().unsubscribe(id, EthPV62.NEW_BLOCK_HASHES));
    onBlockAddedSId = Optional.empty();
    newBlockSId = Optional.empty();
    newBlockHashesSId = Optional.empty();
  }

  private void onBlockAdded(final BlockAddedEvent blockAddedEvent) {
    // Check to see if any of our pending blocks are now ready for import
    final Block newBlock = blockAddedEvent.getBlock();

    final List<Block> readyForImport;
    synchronized (pendingBlocksManager) {
      // Remove block from pendingBlocks list
      pendingBlocksManager.deregisterPendingBlock(newBlock);

      // Import any pending blocks that are children of the newly added block
      readyForImport = pendingBlocksManager.childrenOf(newBlock.getHash());
    }

    traceLambda(
        LOG,
        "Block added event type {} for block {}. Current status {}",
        blockAddedEvent::getEventType,
        newBlock::toLogString,
        () -> this);

    if (!readyForImport.isEmpty()) {
      traceLambda(
          LOG,
          "Ready to import pending blocks found [{}] for block {}",
          () -> readyForImport.stream().map(Block::toLogString).collect(Collectors.joining(", ")),
          newBlock::toLogString);

      final Supplier<CompletableFuture<List<Block>>> importBlocksTask =
          PersistBlockTask.forUnorderedBlocks(
              protocolSchedule,
              protocolContext,
              ethContext,
              readyForImport,
              HeaderValidationMode.FULL,
              metricsSystem);
      ethContext
          .getScheduler()
          .scheduleSyncWorkerTask(importBlocksTask)
          .whenComplete(
              (r, t) -> {
                if (r != null) {
                  LOG.info("Imported {} pending blocks", r.size());
                }
                if (t != null) {
                  LOG.error("Error importing pending blocks", t);
                }
              });
    } else {

      traceLambda(
          LOG, "There are no pending blocks ready to import for block {}", newBlock::toLogString);

      maybeProcessNonAnnouncedBlocks(newBlock);
    }

    if (blockAddedEvent.getEventType().equals(EventType.HEAD_ADVANCED)) {
      final long head = protocolContext.getBlockchain().getChainHeadBlockNumber();
      final long cutoff = head + config.getBlockPropagationRange().lowerEndpoint();
      pendingBlocksManager.purgeBlocksOlderThan(cutoff);
    }
  }

  private void maybeProcessNonAnnouncedBlocks(final Block newBlock) {
    final long localHeadBlockNumber = protocolContext.getBlockchain().getChainHeadBlockNumber();

    if (newBlock.getHeader().getNumber() > localHeadBlockNumber) {
      pendingBlocksManager
          .lowestAnnouncedBlock()
          .map(ProcessableBlockHeader::getNumber)
          .ifPresent(
              minAnnouncedBlockNumber -> {
                long distance = minAnnouncedBlockNumber - localHeadBlockNumber;
                LOG.trace(
                    "Found lowest announced block {} with distance {}",
                    minAnnouncedBlockNumber,
                    distance);

                long firstNonAnnouncedBlockNumber = newBlock.getHeader().getNumber() + 1;

                if (distance < config.getBlockPropagationRange().upperEndpoint()
                    && minAnnouncedBlockNumber > firstNonAnnouncedBlockNumber) {

                  if (requestedNonAnnouncedBlocks.add(firstNonAnnouncedBlockNumber)) {
                    retrieveNonAnnouncedBlock(firstNonAnnouncedBlockNumber);
                  }
                }
              });
    }
  }

  private void handleNewBlockFromNetwork(final EthMessage message) {
    final Blockchain blockchain = protocolContext.getBlockchain();
    final NewBlockMessage newBlockMessage = NewBlockMessage.readFrom(message.getData());
    try {
      final Block block = newBlockMessage.block(protocolSchedule);
      traceLambda(
          LOG,
          "New block from network {} from peer {}. Current status {}",
          block::toLogString,
          message::getPeer,
          () -> this);

      final Difficulty totalDifficulty = newBlockMessage.totalDifficulty(protocolSchedule);

      message.getPeer().chainState().updateForAnnouncedBlock(block.getHeader(), totalDifficulty);

      // Return early if we don't care about this block
      final long localChainHeight = protocolContext.getBlockchain().getChainHeadBlockNumber();
      final long bestChainHeight = syncState.bestChainHeight(localChainHeight);
      if (!shouldImportBlockAtHeight(
          block.getHeader().getNumber(), localChainHeight, bestChainHeight)) {
        traceLambda(
            LOG,
            "Do not import new block from network {}, current chain heights are: local {}, best {}",
            block::toLogString,
            () -> localChainHeight,
            () -> bestChainHeight);
        return;
      }
      if (pendingBlocksManager.contains(block.getHash())) {
        traceLambda(LOG, "New block from network {} is already pending", block::toLogString);
        return;
      }
      if (blockchain.contains(block.getHash())) {
        traceLambda(LOG, "New block from network {} is already present", block::toLogString);
        return;
      }

      importOrSavePendingBlock(block, message.getPeer().nodeId());
    } catch (final RLPException e) {
      LOG.debug(
          "Malformed NEW_BLOCK message received from peer, disconnecting: {}",
          message.getPeer(),
          e);
      message.getPeer().disconnect(DisconnectReason.BREACH_OF_PROTOCOL);
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
      traceLambda(
          LOG,
          "New block hashes from network {} from peer {}. Current status {}",
          () -> toLogString(announcedBlocks),
          message::getPeer,
          () -> this);

      for (final NewBlockHash announcedBlock : announcedBlocks) {
        message.getPeer().registerKnownBlock(announcedBlock.hash());
        message.getPeer().registerHeight(announcedBlock.hash(), announcedBlock.number());
      }

      // Filter announced blocks for blocks we care to import
      final long localChainHeight = protocolContext.getBlockchain().getChainHeadBlockNumber();
      final long bestChainHeight = syncState.bestChainHeight(localChainHeight);
      final List<NewBlockHash> relevantAnnouncements =
          announcedBlocks.stream()
              .filter(a -> shouldImportBlockAtHeight(a.number(), localChainHeight, bestChainHeight))
              .collect(Collectors.toList());

      // Filter for blocks we don't yet know about
      final List<NewBlockHash> newBlocks = new ArrayList<>();
      for (final NewBlockHash announcedBlock : relevantAnnouncements) {
        if (pendingBlocksManager.contains(announcedBlock.hash())) {
          LOG.trace("New block hash from network {} is already pending", announcedBlock);
          continue;
        }
        if (importingBlocks.contains(announcedBlock.hash())) {
          LOG.trace("New block hash from network {} is already importing", announcedBlock);
          continue;
        }
        if (blockchain.contains(announcedBlock.hash())) {
          LOG.trace("New block hash from network {} was already imported", announcedBlock);
          continue;
        }
        if (requestedBlocks.add(announcedBlock.hash())) {
          newBlocks.add(announcedBlock);
        } else {
          LOG.trace("New block hash from network {} was already requested", announcedBlock);
        }
      }

      // Process known blocks we care about
      for (final NewBlockHash newBlock : newBlocks) {
        processAnnouncedBlock(message.getPeer(), newBlock);
      }
    } catch (final RLPException e) {
      LOG.debug(
          "Malformed NEW_BLOCK_HASHES message received from peer, disconnecting: {}",
          message.getPeer(),
          e);
      message.getPeer().disconnect(DisconnectReason.BREACH_OF_PROTOCOL);
    }
  }

  private CompletableFuture<Block> retrieveNonAnnouncedBlock(final long blockNumber) {
    LOG.trace("Retrieve non announced block {} from peers", blockNumber);
    return getBlockFromPeers(Optional.empty(), blockNumber, Optional.empty());
  }

  private CompletableFuture<Block> processAnnouncedBlock(
      final EthPeer peer, final NewBlockHash blockHash) {
    LOG.trace("Retrieve announced block by header {} from peers", blockHash);
    return getBlockFromPeers(Optional.of(peer), blockHash.number(), Optional.of(blockHash.hash()));
  }

  private void requestParentBlock(final BlockHeader blockHeader) {
    if (requestedBlocks.add(blockHeader.getParentHash())) {
      retrieveParentBlock(blockHeader);
    } else {
      LOG.trace("Parent block with hash {} was already requested", blockHeader.getParentHash());
    }
  }

  private CompletableFuture<Block> retrieveParentBlock(final BlockHeader blockHeader) {
    final long targetParentBlockNumber = blockHeader.getNumber() - 1L;
    final Hash targetParentBlockHash = blockHeader.getParentHash();
    LOG.info(
        "Retrieving parent {} of block #{} from peers",
        targetParentBlockHash,
        blockHeader.getNumber());
    return getBlockFromPeers(
        Optional.empty(), targetParentBlockNumber, Optional.of(targetParentBlockHash));
  }

  private CompletableFuture<Block> getBlockFromPeers(
      final Optional<EthPeer> preferredPeer,
      final long blockNumber,
      final Optional<Hash> blockHash) {
    final RetryingGetBlockFromPeersTask getBlockTask =
        RetryingGetBlockFromPeersTask.create(
            protocolContext,
            protocolSchedule,
            ethContext,
            metricsSystem,
            ethContext.getEthPeers().getMaxPeers(),
            blockHash,
            blockNumber);
    preferredPeer.ifPresent(getBlockTask::assignPeer);

    return ethContext
        .getScheduler()
        .scheduleSyncWorkerTask(getBlockTask::run)
        .thenCompose(r -> importOrSavePendingBlock(r.getResult(), r.getPeer().nodeId()))
        .whenComplete(
            (r, t) -> {
              requestedNonAnnouncedBlocks.remove(blockNumber);
              blockHash.ifPresentOrElse(
                  requestedBlocks::remove,
                  () -> {
                    if (r != null) {
                      // in case we successfully retrieved only by block number, when can remove
                      // the request by hash too
                      requestedBlocks.remove(r.getHash());
                    }
                  });
            });
  }

  private void broadcastBlock(final Block block, final BlockHeader parent) {
    final Difficulty totalDifficulty =
        protocolContext
            .getBlockchain()
            .getTotalDifficultyByHash(parent.getHash())
            .get()
            .add(block.getHeader().getDifficulty());
    blockBroadcaster.propagate(block, totalDifficulty);
  }

  @VisibleForTesting
  CompletableFuture<Block> importOrSavePendingBlock(final Block block, final Bytes nodeId) {
    // Synchronize to avoid race condition where block import event fires after the
    // blockchain.contains() check and before the block is registered, causing onBlockAdded() to be
    // invoked for the parent of this block before we are able to register it.
    traceLambda(LOG, "Import or save pending block {}", block::toLogString);

    synchronized (pendingBlocksManager) {
      if (!protocolContext.getBlockchain().contains(block.getHeader().getParentHash())) {
        // Block isn't connected to local chain, save it to pending blocks collection
        if (pendingBlocksManager.registerPendingBlock(block, nodeId)) {
          LOG.info("Saving announced block {} for future import", block.toLogString());
        }

        // Request parent of the lowest announced block
        pendingBlocksManager.lowestAnnouncedBlock().ifPresent(this::requestParentBlock);

        return CompletableFuture.completedFuture(block);
      }
    }

    if (!importingBlocks.add(block.getHash())) {
      traceLambda(LOG, "We're already importing this block {}", block::toLogString);
      return CompletableFuture.completedFuture(block);
    }

    if (protocolContext.getBlockchain().contains(block.getHash())) {
      traceLambda(LOG, "We've already imported this block {}", block::toLogString);
      importingBlocks.remove(block.getHash());
      return CompletableFuture.completedFuture(block);
    }

    final BlockHeader parent =
        protocolContext
            .getBlockchain()
            .getBlockHeader(block.getHeader().getParentHash())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Incapable of retrieving header from non-existent parent of "
                            + block.toLogString()));
    final ProtocolSpec protocolSpec =
        protocolSchedule.getByBlockNumber(block.getHeader().getNumber());
    final BlockHeaderValidator blockHeaderValidator = protocolSpec.getBlockHeaderValidator();
    final BadBlockManager badBlockManager = protocolSpec.getBadBlocksManager();
    return ethContext
        .getScheduler()
        .scheduleSyncWorkerTask(
            () ->
                validateAndProcessPendingBlock(
                    blockHeaderValidator, block, parent, badBlockManager));
  }

  private CompletableFuture<Block> validateAndProcessPendingBlock(
      final BlockHeaderValidator blockHeaderValidator,
      final Block block,
      final BlockHeader parent,
      final BadBlockManager badBlockManager) {
    if (blockHeaderValidator.validateHeader(
        block.getHeader(), parent, protocolContext, HeaderValidationMode.FULL)) {
      ethContext.getScheduler().scheduleSyncWorkerTask(() -> broadcastBlock(block, parent));
      return runImportTask(block);
    } else {
      importingBlocks.remove(block.getHash());
      badBlockManager.addBadBlock(block);
      LOG.warn("Failed to import announced block {}", block.toLogString());
      return CompletableFuture.completedFuture(block);
    }
  }

  private CompletableFuture<Block> runImportTask(final Block block) {
    final PersistBlockTask importTask =
        PersistBlockTask.create(
            protocolSchedule,
            protocolContext,
            ethContext,
            block,
            HeaderValidationMode.NONE,
            metricsSystem);
    return importTask
        .run()
        .whenComplete(
            (result, throwable) -> {
              importingBlocks.remove(block.getHash());
              if (throwable != null) {
                LOG.warn("Failed to import announced block {}", block.toLogString());
              }
            });
  }

  // Only import blocks within a certain range of our head and sync target
  private boolean shouldImportBlockAtHeight(
      final long blockNumber, final long localHeight, final long bestChainHeight) {
    final long distanceFromLocalHead = blockNumber - localHeight;
    final long distanceFromBestPeer = blockNumber - bestChainHeight;
    final Range<Long> importRange = config.getBlockPropagationRange();
    return importRange.contains(distanceFromLocalHead)
        && importRange.contains(distanceFromBestPeer);
  }

  private String toLogString(final Collection<NewBlockHash> newBlockHashs) {
    return newBlockHashs.stream()
        .map(NewBlockHash::toString)
        .collect(Collectors.joining(", ", "[", "]"));
  }

  private void reactToTTDReachedEvent(final boolean ttdReached) {
    if (started.get() && ttdReached) {
      LOG.info("Block propagation was running, then ttd reached, stopping");
      stop();
    } else if (!started.get()) {
      start();
    }
  }

  @Override
  public String toString() {
    return "BlockPropagationManager{"
        + "requestedBlocks="
        + requestedBlocks
        + ", requestedNonAnnounceBlocks="
        + requestedNonAnnouncedBlocks
        + ", importingBlocks="
        + importingBlocks
        + ", pendingBlocksManager="
        + pendingBlocksManager
        + '}';
  }
}
