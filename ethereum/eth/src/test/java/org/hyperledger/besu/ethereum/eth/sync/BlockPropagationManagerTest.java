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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator.BlockOptions;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer.Responder;
import org.hyperledger.besu.ethereum.eth.messages.NewBlockHashesMessage;
import org.hyperledger.besu.ethereum.eth.messages.NewBlockMessage;
import org.hyperledger.besu.ethereum.eth.sync.state.PendingBlocksManager;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.testutil.TestClock;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class BlockPropagationManagerTest {

  private static final Bytes NODE_ID_1 = Bytes.fromHexString("0x00");

  private static Blockchain fullBlockchain;

  private BlockchainSetupUtil blockchainUtil;
  private ProtocolSchedule protocolSchedule;
  private ProtocolContext protocolContext;
  private MutableBlockchain blockchain;
  private BlockBroadcaster blockBroadcaster;
  private EthProtocolManager ethProtocolManager;
  private BlockPropagationManager blockPropagationManager;
  private SynchronizerConfiguration syncConfig;
  private final PendingBlocksManager pendingBlocksManager =
      new PendingBlocksManager(
          SynchronizerConfiguration.builder().blockPropagationRange(-10, 30).build());
  private SyncState syncState;
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  @BeforeClass
  public static void setupSuite() {
    fullBlockchain = BlockchainSetupUtil.forTesting().importAllBlocks();
  }

  @Before
  public void setup() {
    blockchainUtil = BlockchainSetupUtil.forTesting();
    blockchain = blockchainUtil.getBlockchain();
    protocolSchedule = blockchainUtil.getProtocolSchedule();
    final ProtocolContext tempProtocolContext = blockchainUtil.getProtocolContext();
    protocolContext =
        new ProtocolContext(
            blockchain,
            tempProtocolContext.getWorldStateArchive(),
            tempProtocolContext.getConsensusState(Object.class));
    ethProtocolManager =
        EthProtocolManagerTestUtil.create(
            blockchain,
            blockchainUtil.getWorldArchive(),
            blockchainUtil.getTransactionPool(),
            EthProtocolConfiguration.defaultConfig());
    syncConfig = SynchronizerConfiguration.builder().blockPropagationRange(-3, 5).build();
    syncState = new SyncState(blockchain, ethProtocolManager.ethContext().getEthPeers());
    blockBroadcaster = mock(BlockBroadcaster.class);
    blockPropagationManager =
        new BlockPropagationManager(
            syncConfig,
            protocolSchedule,
            protocolContext,
            ethProtocolManager.ethContext(),
            syncState,
            pendingBlocksManager,
            metricsSystem,
            blockBroadcaster);
  }

  @Test
  public void importsAnnouncedBlocks_aheadOfChainInOrder() {
    blockchainUtil.importFirstBlocks(2);
    final Block nextBlock = blockchainUtil.getBlock(2);
    final Block nextNextBlock = blockchainUtil.getBlock(3);

    // Sanity check
    assertThat(blockchain.contains(nextBlock.getHash())).isFalse();
    assertThat(blockchain.contains(nextNextBlock.getHash())).isFalse();

    blockPropagationManager.start();

    // Setup additonal peer for best peers list
    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 0);
    // Setup peer and messages
    final RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 0);
    final NewBlockHashesMessage nextAnnouncement =
        NewBlockHashesMessage.create(
            Collections.singletonList(
                new NewBlockHashesMessage.NewBlockHash(
                    nextBlock.getHash(), nextBlock.getHeader().getNumber())));
    final NewBlockHashesMessage nextNextAnnouncement =
        NewBlockHashesMessage.create(
            Collections.singletonList(
                new NewBlockHashesMessage.NewBlockHash(
                    nextNextBlock.getHash(), nextNextBlock.getHeader().getNumber())));
    final Responder responder = RespondingEthPeer.blockchainResponder(fullBlockchain);

    // Broadcast first message
    EthProtocolManagerTestUtil.broadcastMessage(ethProtocolManager, peer, nextAnnouncement);
    peer.respondWhile(responder, peer::hasOutstandingRequests);
    // Broadcast second message
    EthProtocolManagerTestUtil.broadcastMessage(ethProtocolManager, peer, nextNextAnnouncement);
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    assertThat(blockchain.contains(nextBlock.getHash())).isTrue();
    assertThat(blockchain.contains(nextNextBlock.getHash())).isTrue();
  }

  @Test
  public void importsAnnouncedBlocks_aheadOfChainOutOfOrder() {
    blockchainUtil.importFirstBlocks(2);
    final Block nextBlock = blockchainUtil.getBlock(2);
    final Block nextNextBlock = blockchainUtil.getBlock(3);

    // Sanity check
    assertThat(blockchain.contains(nextBlock.getHash())).isFalse();
    assertThat(blockchain.contains(nextNextBlock.getHash())).isFalse();

    blockPropagationManager.start();

    // Setup peer and messages
    final RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 0);
    final NewBlockHashesMessage nextAnnouncement =
        NewBlockHashesMessage.create(
            Collections.singletonList(
                new NewBlockHashesMessage.NewBlockHash(
                    nextBlock.getHash(), nextBlock.getHeader().getNumber())));
    final NewBlockHashesMessage nextNextAnnouncement =
        NewBlockHashesMessage.create(
            Collections.singletonList(
                new NewBlockHashesMessage.NewBlockHash(
                    nextNextBlock.getHash(), nextNextBlock.getHeader().getNumber())));
    final Responder responder = RespondingEthPeer.blockchainResponder(fullBlockchain);

    // Broadcast second message first
    EthProtocolManagerTestUtil.broadcastMessage(ethProtocolManager, peer, nextNextAnnouncement);
    peer.respondWhile(responder, peer::hasOutstandingRequests);
    // Broadcast first message
    EthProtocolManagerTestUtil.broadcastMessage(ethProtocolManager, peer, nextAnnouncement);
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    assertThat(blockchain.contains(nextBlock.getHash())).isTrue();
    assertThat(blockchain.contains(nextNextBlock.getHash())).isTrue();
  }

  @Test
  public void importsAnnouncedNewBlocks_aheadOfChainInOrder() {
    blockchainUtil.importFirstBlocks(2);
    final Block nextBlock = blockchainUtil.getBlock(2);
    final Block nextNextBlock = blockchainUtil.getBlock(3);

    // Sanity check
    assertThat(blockchain.contains(nextBlock.getHash())).isFalse();
    assertThat(blockchain.contains(nextNextBlock.getHash())).isFalse();

    blockPropagationManager.start();

    // Setup peer and messages
    final RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 0);
    final NewBlockMessage nextAnnouncement =
        NewBlockMessage.create(
            nextBlock, fullBlockchain.getTotalDifficultyByHash(nextBlock.getHash()).get());
    final NewBlockMessage nextNextAnnouncement =
        NewBlockMessage.create(
            nextNextBlock, fullBlockchain.getTotalDifficultyByHash(nextNextBlock.getHash()).get());
    final Responder responder = RespondingEthPeer.blockchainResponder(fullBlockchain);

    // Broadcast first message
    EthProtocolManagerTestUtil.broadcastMessage(ethProtocolManager, peer, nextAnnouncement);
    peer.respondWhile(responder, peer::hasOutstandingRequests);
    // Broadcast second message
    EthProtocolManagerTestUtil.broadcastMessage(ethProtocolManager, peer, nextNextAnnouncement);
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    assertThat(blockchain.contains(nextBlock.getHash())).isTrue();
    assertThat(blockchain.contains(nextNextBlock.getHash())).isTrue();
  }

  @Test
  public void importsAnnouncedNewBlocks_aheadOfChainOutOfOrder() {
    blockchainUtil.importFirstBlocks(2);
    final Block nextBlock = blockchainUtil.getBlock(2);
    final Block nextNextBlock = blockchainUtil.getBlock(3);

    // Sanity check
    assertThat(blockchain.contains(nextBlock.getHash())).isFalse();
    assertThat(blockchain.contains(nextNextBlock.getHash())).isFalse();

    blockPropagationManager.start();

    // Setup peer and messages
    final RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 0);
    final NewBlockMessage nextAnnouncement =
        NewBlockMessage.create(
            nextBlock, fullBlockchain.getTotalDifficultyByHash(nextBlock.getHash()).get());
    final NewBlockMessage nextNextAnnouncement =
        NewBlockMessage.create(
            nextNextBlock, fullBlockchain.getTotalDifficultyByHash(nextNextBlock.getHash()).get());
    final Responder responder = RespondingEthPeer.blockchainResponder(fullBlockchain);

    // Broadcast second message first
    EthProtocolManagerTestUtil.broadcastMessage(ethProtocolManager, peer, nextNextAnnouncement);
    peer.respondWhile(responder, peer::hasOutstandingRequests);
    // Broadcast first message
    EthProtocolManagerTestUtil.broadcastMessage(ethProtocolManager, peer, nextAnnouncement);
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    assertThat(blockchain.contains(nextBlock.getHash())).isTrue();
    assertThat(blockchain.contains(nextNextBlock.getHash())).isTrue();
  }

  @Test
  public void importsMixedOutOfOrderMessages() {
    blockchainUtil.importFirstBlocks(2);
    final Block block1 = blockchainUtil.getBlock(2);
    final Block block2 = blockchainUtil.getBlock(3);
    final Block block3 = blockchainUtil.getBlock(4);
    final Block block4 = blockchainUtil.getBlock(5);

    // Sanity check
    assertThat(blockchain.contains(block1.getHash())).isFalse();
    assertThat(blockchain.contains(block2.getHash())).isFalse();
    assertThat(blockchain.contains(block3.getHash())).isFalse();
    assertThat(blockchain.contains(block4.getHash())).isFalse();

    blockPropagationManager.start();

    // Setup peer and messages
    final RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 0);
    final NewBlockHashesMessage block1Msg =
        NewBlockHashesMessage.create(
            Collections.singletonList(
                new NewBlockHashesMessage.NewBlockHash(
                    block1.getHash(), block1.getHeader().getNumber())));
    final NewBlockMessage block2Msg =
        NewBlockMessage.create(
            block2, fullBlockchain.getTotalDifficultyByHash(block2.getHash()).get());
    final NewBlockHashesMessage block3Msg =
        NewBlockHashesMessage.create(
            Collections.singletonList(
                new NewBlockHashesMessage.NewBlockHash(
                    block3.getHash(), block3.getHeader().getNumber())));
    final NewBlockMessage block4Msg =
        NewBlockMessage.create(
            block4, fullBlockchain.getTotalDifficultyByHash(block4.getHash()).get());
    final Responder responder = RespondingEthPeer.blockchainResponder(fullBlockchain);

    // Broadcast older blocks
    EthProtocolManagerTestUtil.broadcastMessage(ethProtocolManager, peer, block3Msg);
    EthProtocolManagerTestUtil.broadcastMessage(ethProtocolManager, peer, block4Msg);
    peer.respondWhile(responder, peer::hasOutstandingRequests);
    EthProtocolManagerTestUtil.broadcastMessage(ethProtocolManager, peer, block2Msg);
    peer.respondWhile(responder, peer::hasOutstandingRequests);
    // Broadcast first block
    EthProtocolManagerTestUtil.broadcastMessage(ethProtocolManager, peer, block1Msg);
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    assertThat(blockchain.contains(block1.getHash())).isTrue();
    assertThat(blockchain.contains(block2.getHash())).isTrue();
    assertThat(blockchain.contains(block3.getHash())).isTrue();
    assertThat(blockchain.contains(block4.getHash())).isTrue();
  }

  @Test
  public void handlesDuplicateAnnouncements() {
    final ProtocolSchedule stubProtocolSchedule = spy(protocolSchedule);
    final ProtocolSpec stubProtocolSpec = spy(protocolSchedule.getByBlockNumber(2));
    final BlockImporter stubBlockImporter = spy(stubProtocolSpec.getBlockImporter());
    doReturn(stubProtocolSpec).when(stubProtocolSchedule).getByBlockNumber(anyLong());
    doReturn(stubBlockImporter).when(stubProtocolSpec).getBlockImporter();
    final BlockPropagationManager blockPropagationManager =
        new BlockPropagationManager(
            syncConfig,
            stubProtocolSchedule,
            protocolContext,
            ethProtocolManager.ethContext(),
            syncState,
            pendingBlocksManager,
            metricsSystem,
            blockBroadcaster);

    blockchainUtil.importFirstBlocks(2);
    final Block nextBlock = blockchainUtil.getBlock(2);

    // Sanity check
    assertThat(blockchain.contains(nextBlock.getHash())).isFalse();

    blockPropagationManager.start();

    // Setup peer and messages
    final RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 0);
    final NewBlockHashesMessage newBlockHash =
        NewBlockHashesMessage.create(
            Collections.singletonList(
                new NewBlockHashesMessage.NewBlockHash(
                    nextBlock.getHash(), nextBlock.getHeader().getNumber())));
    final NewBlockMessage newBlock =
        NewBlockMessage.create(
            nextBlock, fullBlockchain.getTotalDifficultyByHash(nextBlock.getHash()).get());
    final Responder responder = RespondingEthPeer.blockchainResponder(fullBlockchain);

    // Broadcast first message
    EthProtocolManagerTestUtil.broadcastMessage(ethProtocolManager, peer, newBlock);
    peer.respondWhile(responder, peer::hasOutstandingRequests);
    // Broadcast duplicate
    EthProtocolManagerTestUtil.broadcastMessage(ethProtocolManager, peer, newBlockHash);
    peer.respondWhile(responder, peer::hasOutstandingRequests);
    // Broadcast duplicate
    EthProtocolManagerTestUtil.broadcastMessage(ethProtocolManager, peer, newBlock);
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    assertThat(blockchain.contains(nextBlock.getHash())).isTrue();
    verify(stubBlockImporter, times(1)).importBlock(eq(protocolContext), eq(nextBlock), any());
  }

  @Test
  public void handlesPendingDuplicateAnnouncements() {
    final ProtocolSchedule stubProtocolSchedule = spy(protocolSchedule);
    final ProtocolSpec stubProtocolSpec = spy(protocolSchedule.getByBlockNumber(2));
    final BlockImporter stubBlockImporter = spy(stubProtocolSpec.getBlockImporter());
    doReturn(stubProtocolSpec).when(stubProtocolSchedule).getByBlockNumber(anyLong());
    doReturn(stubBlockImporter).when(stubProtocolSpec).getBlockImporter();
    final BlockPropagationManager blockPropagationManager =
        new BlockPropagationManager(
            syncConfig,
            stubProtocolSchedule,
            protocolContext,
            ethProtocolManager.ethContext(),
            syncState,
            pendingBlocksManager,
            metricsSystem,
            blockBroadcaster);
    blockchainUtil.importFirstBlocks(2);
    final Block nextBlock = blockchainUtil.getBlock(2);

    // Sanity check
    assertThat(blockchain.contains(nextBlock.getHash())).isFalse();

    blockPropagationManager.start();

    // Setup peer and messages
    final RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 0);
    final NewBlockHashesMessage newBlockHash =
        NewBlockHashesMessage.create(
            Collections.singletonList(
                new NewBlockHashesMessage.NewBlockHash(
                    nextBlock.getHash(), nextBlock.getHeader().getNumber())));
    final NewBlockMessage newBlock =
        NewBlockMessage.create(
            nextBlock, fullBlockchain.getTotalDifficultyByHash(nextBlock.getHash()).get());

    // Broadcast messages
    EthProtocolManagerTestUtil.broadcastMessage(ethProtocolManager, peer, newBlock);
    EthProtocolManagerTestUtil.broadcastMessage(ethProtocolManager, peer, newBlockHash);
    EthProtocolManagerTestUtil.broadcastMessage(ethProtocolManager, peer, newBlock);
    // Respond
    final Responder responder = RespondingEthPeer.blockchainResponder(fullBlockchain);
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    assertThat(blockchain.contains(nextBlock.getHash())).isTrue();
    verify(stubBlockImporter, times(1)).importBlock(eq(protocolContext), eq(nextBlock), any());
  }

  @Test
  public void ignoresFutureNewBlockHashAnnouncement() {
    blockchainUtil.importFirstBlocks(2);
    final Block futureBlock = blockchainUtil.getBlock(11);

    // Sanity check
    assertThat(blockchain.contains(futureBlock.getHash())).isFalse();

    blockPropagationManager.start();

    // Setup peer and messages
    final RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 0);
    final NewBlockHashesMessage futureAnnouncement =
        NewBlockHashesMessage.create(
            Collections.singletonList(
                new NewBlockHashesMessage.NewBlockHash(
                    futureBlock.getHash(), futureBlock.getHeader().getNumber())));

    // Broadcast
    EthProtocolManagerTestUtil.broadcastMessage(ethProtocolManager, peer, futureAnnouncement);
    final Responder responder = RespondingEthPeer.blockchainResponder(fullBlockchain);
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    assertThat(blockchain.contains(futureBlock.getHash())).isFalse();
  }

  @Test
  public void ignoresFutureNewBlockAnnouncement() {
    blockchainUtil.importFirstBlocks(2);
    final Block futureBlock = blockchainUtil.getBlock(11);

    // Sanity check
    assertThat(blockchain.contains(futureBlock.getHash())).isFalse();

    blockPropagationManager.start();

    // Setup peer and messages
    final RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 0);
    final NewBlockMessage futureAnnouncement =
        NewBlockMessage.create(
            futureBlock, fullBlockchain.getTotalDifficultyByHash(futureBlock.getHash()).get());

    // Broadcast
    EthProtocolManagerTestUtil.broadcastMessage(ethProtocolManager, peer, futureAnnouncement);
    final Responder responder = RespondingEthPeer.blockchainResponder(fullBlockchain);
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    assertThat(blockchain.contains(futureBlock.getHash())).isFalse();
  }

  @Test
  public void ignoresOldNewBlockHashAnnouncement() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    blockchainUtil.importFirstBlocks(10);
    final Block blockOne = blockchainUtil.getBlock(1);
    final Block oldBlock = gen.nextBlock(blockOne);

    // Sanity check
    assertThat(blockchain.contains(oldBlock.getHash())).isFalse();

    final BlockPropagationManager propManager = spy(blockPropagationManager);
    propManager.start();

    // Setup peer and messages
    final RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 0);
    final NewBlockHashesMessage oldAnnouncement =
        NewBlockHashesMessage.create(
            Collections.singletonList(
                new NewBlockHashesMessage.NewBlockHash(
                    oldBlock.getHash(), oldBlock.getHeader().getNumber())));

    // Broadcast
    EthProtocolManagerTestUtil.broadcastMessage(ethProtocolManager, peer, oldAnnouncement);
    final Responder responder = RespondingEthPeer.blockchainResponder(fullBlockchain);
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    verify(propManager, times(0)).importOrSavePendingBlock(any(), any(Bytes.class));
    assertThat(blockchain.contains(oldBlock.getHash())).isFalse();
  }

  @Test
  public void ignoresOldNewBlockAnnouncement() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    blockchainUtil.importFirstBlocks(10);
    final Block blockOne = blockchainUtil.getBlock(1);
    final Block oldBlock = gen.nextBlock(blockOne);

    // Sanity check
    assertThat(blockchain.contains(oldBlock.getHash())).isFalse();

    final BlockPropagationManager propManager = spy(blockPropagationManager);
    propManager.start();

    // Setup peer and messages
    final RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 0);
    final NewBlockMessage oldAnnouncement = NewBlockMessage.create(oldBlock, Difficulty.ZERO);

    // Broadcast
    EthProtocolManagerTestUtil.broadcastMessage(ethProtocolManager, peer, oldAnnouncement);
    final Responder responder = RespondingEthPeer.blockchainResponder(fullBlockchain);
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    verify(propManager, times(0)).importOrSavePendingBlock(any(), any(Bytes.class));
    assertThat(blockchain.contains(oldBlock.getHash())).isFalse();
  }

  @Test
  public void purgesOldBlocks() {
    final int oldBlocksToImport = 3;
    syncConfig =
        SynchronizerConfiguration.builder().blockPropagationRange(-oldBlocksToImport, 5).build();
    final BlockPropagationManager blockPropagationManager =
        new BlockPropagationManager(
            syncConfig,
            protocolSchedule,
            protocolContext,
            ethProtocolManager.ethContext(),
            syncState,
            pendingBlocksManager,
            metricsSystem,
            blockBroadcaster);

    final BlockDataGenerator gen = new BlockDataGenerator();
    // Import some blocks
    blockchainUtil.importFirstBlocks(5);
    // Set up test block next to head, that should eventually be purged
    final Block blockToPurge =
        gen.block(BlockOptions.create().setBlockNumber(blockchain.getChainHeadBlockNumber()));

    blockPropagationManager.start();
    final RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 0);
    final NewBlockMessage blockAnnouncementMsg =
        NewBlockMessage.create(blockToPurge, Difficulty.ZERO);

    // Broadcast
    EthProtocolManagerTestUtil.broadcastMessage(ethProtocolManager, peer, blockAnnouncementMsg);
    final Responder responder = RespondingEthPeer.blockchainResponder(fullBlockchain);
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    // Check that we pushed our block into the pending collection
    assertThat(blockchain.contains(blockToPurge.getHash())).isFalse();
    assertThat(pendingBlocksManager.contains(blockToPurge.getHash())).isTrue();

    // Import blocks until we bury the target block far enough to be cleaned up
    for (int i = 0; i < oldBlocksToImport; i++) {
      blockchainUtil.importBlockAtIndex((int) blockchain.getChainHeadBlockNumber() + 1);

      assertThat(blockchain.contains(blockToPurge.getHash())).isFalse();
      assertThat(pendingBlocksManager.contains(blockToPurge.getHash())).isTrue();
    }

    // Import again to trigger cleanup
    blockchainUtil.importBlockAtIndex((int) blockchain.getChainHeadBlockNumber() + 1);
    assertThat(blockchain.contains(blockToPurge.getHash())).isFalse();
    assertThat(pendingBlocksManager.contains(blockToPurge.getHash())).isFalse();
  }

  @Test
  public void updatesChainHeadWhenNewBlockMessageReceived() {
    blockchainUtil.importFirstBlocks(2);
    final Block nextBlock = blockchainUtil.getBlock(2);

    blockPropagationManager.start();

    // Setup peer and messages
    final RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 0);
    final Difficulty parentTotalDifficulty =
        fullBlockchain.getTotalDifficultyByHash(nextBlock.getHeader().getParentHash()).get();
    final Difficulty totalDifficulty =
        fullBlockchain.getTotalDifficultyByHash(nextBlock.getHash()).get();
    final NewBlockMessage nextAnnouncement = NewBlockMessage.create(nextBlock, totalDifficulty);

    // Broadcast message
    EthProtocolManagerTestUtil.broadcastMessage(ethProtocolManager, peer, nextAnnouncement);
    final Responder responder = RespondingEthPeer.blockchainResponder(fullBlockchain);
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    assertThat(peer.getEthPeer().chainState().getBestBlock().getHash())
        .isEqualTo(nextBlock.getHeader().getParentHash());
    assertThat(peer.getEthPeer().chainState().getEstimatedHeight())
        .isEqualTo(nextBlock.getHeader().getNumber() - 1);
    assertThat(peer.getEthPeer().chainState().getBestBlock().getTotalDifficulty())
        .isEqualTo(parentTotalDifficulty);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldNotImportBlocksThatAreAlreadyBeingImported() {
    final EthScheduler ethScheduler = mock(EthScheduler.class);
    when(ethScheduler.scheduleSyncWorkerTask(any(Supplier.class)))
        .thenReturn(new CompletableFuture<>());
    final EthContext ethContext =
        new EthContext(
            new EthPeers("eth", TestClock.fixed(), metricsSystem), new EthMessages(), ethScheduler);
    final BlockPropagationManager blockPropagationManager =
        new BlockPropagationManager(
            syncConfig,
            protocolSchedule,
            protocolContext,
            ethContext,
            syncState,
            pendingBlocksManager,
            metricsSystem,
            blockBroadcaster);

    blockchainUtil.importFirstBlocks(2);
    final Block nextBlock = blockchainUtil.getBlock(2);

    blockPropagationManager.importOrSavePendingBlock(nextBlock, NODE_ID_1);
    blockPropagationManager.importOrSavePendingBlock(nextBlock, NODE_ID_1);

    verify(ethScheduler, times(1)).scheduleSyncWorkerTask(any(Supplier.class));
  }

  @Test
  public void verifyBroadcastBlockInvocation() {
    blockchainUtil.importFirstBlocks(2);
    final Block block = blockchainUtil.getBlock(2);
    blockPropagationManager.start();

    // Setup peer and messages
    final RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 0);

    final Difficulty totalDifficulty =
        fullBlockchain.getTotalDifficultyByHash(block.getHash()).get();
    final NewBlockMessage newBlockMessage = NewBlockMessage.create(block, totalDifficulty);

    // Broadcast message
    EthProtocolManagerTestUtil.broadcastMessage(ethProtocolManager, peer, newBlockMessage);

    final Responder responder = RespondingEthPeer.blockchainResponder(fullBlockchain);
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    verify(blockBroadcaster, times(1)).propagate(block, totalDifficulty);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldDetectAndCacheInvalidBlocks() {
    final EthScheduler ethScheduler = mock(EthScheduler.class);
    when(ethScheduler.scheduleSyncWorkerTask(any(Supplier.class)))
        .thenAnswer(
            new Answer<Object>() {
              @Override
              public Object answer(final InvocationOnMock invocation) throws Throwable {
                return invocation.getArgument(0, Supplier.class).get();
              }
            });
    final EthContext ethContext =
        new EthContext(
            new EthPeers("eth", TestClock.fixed(), metricsSystem), new EthMessages(), ethScheduler);
    final BlockPropagationManager blockPropagationManager =
        new BlockPropagationManager(
            syncConfig,
            protocolSchedule,
            protocolContext,
            ethContext,
            syncState,
            pendingBlocksManager,
            metricsSystem,
            blockBroadcaster);

    blockchainUtil.importFirstBlocks(2);
    final Block firstBlock = blockchainUtil.getBlock(1);
    final BadBlockManager badBlocksManager =
        protocolSchedule.getByBlockNumber(1).getBadBlocksManager();
    final Block badBlock =
        new BlockDataGenerator()
            .block(
                BlockDataGenerator.BlockOptions.create()
                    .setBlockNumber(1)
                    .setParentHash(firstBlock.getHash())
                    .setBlockHeaderFunctions(new MainnetBlockHeaderFunctions()));

    assertThat(badBlocksManager.getBadBlocks()).isEmpty();
    blockPropagationManager.importOrSavePendingBlock(badBlock, NODE_ID_1);
    assertThat(badBlocksManager.getBadBlocks().size()).isEqualTo(1);

    verify(ethScheduler, times(1)).scheduleSyncWorkerTask(any(Supplier.class));
  }
}
