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
import org.hyperledger.besu.ethereum.eth.manager.task.GetBlockFromPeersTask;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class BlockPropagationManager {
  private static final Logger LOG = LogManager.getLogger();

  private final SynchronizerConfiguration config;
  private final ProtocolSchedule protocolSchedule;
  private final ProtocolContext protocolContext;
  private final EthContext ethContext;
  private final SyncState syncState;
  private final MetricsSystem metricsSystem;
  private final BlockBroadcaster blockBroadcaster;

  private final AtomicBoolean started = new AtomicBoolean(false);

  private final Set<Hash> requestedBlocks = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final Set<Hash> importingBlocks = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final PendingBlocksManager pendingBlocksManager;

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

    LOG.trace(
        "Ready for import blocks found {} for {}",
        readyForImport,
        newBlock.getHeader().getNumber());

    if (!readyForImport.isEmpty()) {
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
              });
    } else {

      LOG.trace("Not ready for import blocks found for {}", newBlock.getHeader().getNumber());

      pendingBlocksManager
          .lowestAnnouncedBlock()
          .map(ProcessableBlockHeader::getNumber)
          .ifPresent(
              minAnnouncedBlockNumber -> {
                long distance =
                    minAnnouncedBlockNumber
                        - protocolContext.getBlockchain().getChainHeadBlockNumber();
                LOG.trace(
                    "Found lowest announced block {} with distance {}",
                    minAnnouncedBlockNumber,
                    distance);
                if (distance < config.getBlockPropagationRange().upperEndpoint()
                    && minAnnouncedBlockNumber > newBlock.getHeader().getNumber()) {
                  retrieveMissingAnnouncedBlock(newBlock.getHeader().getNumber() + 1);
                }
              });
    }

    if (blockAddedEvent.getEventType().equals(EventType.HEAD_ADVANCED)) {
      final long head = protocolContext.getBlockchain().getChainHeadBlockNumber();
      final long cutoff = head + config.getBlockPropagationRange().lowerEndpoint();
      pendingBlocksManager.purgeBlocksOlderThan(cutoff);
    }
  }

  private void handleNewBlockFromNetwork(final EthMessage message) {
    final Blockchain blockchain = protocolContext.getBlockchain();
    final NewBlockMessage newBlockMessage = NewBlockMessage.readFrom(message.getData());
    try {
      final Block block = newBlockMessage.block(protocolSchedule);
      final Difficulty totalDifficulty = newBlockMessage.totalDifficulty(protocolSchedule);

      message.getPeer().chainState().updateForAnnouncedBlock(block.getHeader(), totalDifficulty);

      // Return early if we don't care about this block
      final long localChainHeight = protocolContext.getBlockchain().getChainHeadBlockNumber();
      final long bestChainHeight = syncState.bestChainHeight(localChainHeight);
      if (!shouldImportBlockAtHeight(
          block.getHeader().getNumber(), localChainHeight, bestChainHeight)) {
        return;
      }
      if (pendingBlocksManager.contains(block.getHash())) {
        return;
      }
      if (blockchain.contains(block.getHash())) {
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
        if (requestedBlocks.contains(announcedBlock.hash())) {
          continue;
        }
        if (pendingBlocksManager.contains(announcedBlock.hash())) {
          continue;
        }
        if (importingBlocks.contains(announcedBlock.hash())) {
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
        final List<EthPeer> peers =
            ethContext.getEthPeers().streamBestPeers().collect(Collectors.toList());
        if (!peers.contains(message.getPeer())) {
          peers.add(message.getPeer());
        }
        processAnnouncedBlock(newBlock)
            .whenComplete((r, t) -> requestedBlocks.remove(newBlock.hash()));
      }
    } catch (final RLPException e) {
      LOG.debug(
          "Malformed NEW_BLOCK_HASHES message received from peer, disconnecting: {}",
          message.getPeer(),
          e);
      message.getPeer().disconnect(DisconnectReason.BREACH_OF_PROTOCOL);
    }
  }

  private CompletableFuture<Block> retrieveMissingAnnouncedBlock(final long blockNumber) {
    LOG.trace("Retrieve missing announced block {} from peer", blockNumber);
    final List<EthPeer> peers =
        ethContext.getEthPeers().streamBestPeers().collect(Collectors.toList());
    final GetBlockFromPeersTask getBlockTask =
        GetBlockFromPeersTask.create(
            peers, protocolSchedule, ethContext, Optional.empty(), blockNumber, metricsSystem);
    return getBlockTask
        .run()
        .thenCompose((r) -> importOrSavePendingBlock(r.getResult(), r.getPeer().nodeId()));
  }

  private CompletableFuture<Block> processAnnouncedBlock(final NewBlockHash newBlock) {
    final RetryingGetBlockFromPeersTask getBlockTask =
        RetryingGetBlockFromPeersTask.create(
            ethContext,
            protocolSchedule,
            Optional.of(newBlock.hash()),
            newBlock.number(),
            metricsSystem);
    return getBlockTask
        .run()
        .thenCompose((r) -> importOrSavePendingBlock(r.getResult(), r.getPeer().nodeId()));
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
    LOG.trace("Import or save pending block {}", block.getHeader().getNumber());

    synchronized (pendingBlocksManager) {
      if (!protocolContext.getBlockchain().contains(block.getHeader().getParentHash())) {
        // Block isn't connected to local chain, save it to pending blocks collection
        if (pendingBlocksManager.registerPendingBlock(block, nodeId)) {
          LOG.info(
              "Saving announced block {} ({}) for future import",
              block.getHeader().getNumber(),
              block.getHash());
        }
        return CompletableFuture.completedFuture(block);
      }
    }

    if (!importingBlocks.add(block.getHash())) {
      // We're already importing this block.
      return CompletableFuture.completedFuture(block);
    }

    if (protocolContext.getBlockchain().contains(block.getHash())) {
      // We've already imported this block.
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
                            + block.getHeader().getNumber()
                            + "."));
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
      LOG.warn(
          "Failed to import announced block {} ({}).",
          block.getHeader().getNumber(),
          block.getHash());
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
                LOG.warn(
                    "Failed to import announced block {} ({}).",
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
    final Range<Long> importRange = config.getBlockPropagationRange();
    return importRange.contains(distanceFromLocalHead)
        && importRange.contains(distanceFromBestPeer);
  }
}
