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
package tech.pegasys.pantheon.ethereum.eth.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.InMemoryTestFixture;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManagerTestUtil;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer.Responder;
import tech.pegasys.pantheon.ethereum.eth.manager.ethtaskutils.BlockchainSetupUtil;
import tech.pegasys.pantheon.ethereum.eth.messages.EthPV62;
import tech.pegasys.pantheon.ethereum.eth.messages.GetBlockHeadersMessage;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.ethereum.testutil.BlockDataGenerator;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;

public class DownloaderTest {

  protected ProtocolSchedule<Void> protocolSchedule;
  protected EthProtocolManager ethProtocolManager;
  protected EthContext ethContext;
  protected ProtocolContext<Void> protocolContext;
  private SyncState syncState;

  private BlockDataGenerator gen;
  private BlockchainSetupUtil<Void> localBlockchainSetup;
  protected MutableBlockchain localBlockchain;
  private BlockchainSetupUtil<Void> otherBlockchainSetup;
  protected Blockchain otherBlockchain;

  @Before
  public void setupTest() {
    gen = new BlockDataGenerator();
    localBlockchainSetup = BlockchainSetupUtil.forTesting();
    localBlockchain = spy(localBlockchainSetup.getBlockchain());
    otherBlockchainSetup = BlockchainSetupUtil.forTesting();
    otherBlockchain = otherBlockchainSetup.getBlockchain();

    protocolSchedule = localBlockchainSetup.getProtocolSchedule();
    protocolContext = localBlockchainSetup.getProtocolContext();
    ethProtocolManager = EthProtocolManagerTestUtil.create(localBlockchain);
    ethContext = ethProtocolManager.ethContext();
    syncState = new SyncState(protocolContext.getBlockchain(), ethContext.getEthPeers());
  }

  private Downloader<?> downloader(final SynchronizerConfiguration syncConfig) {
    return new Downloader<>(syncConfig, protocolSchedule, protocolContext, ethContext, syncState);
  }

  private Downloader<?> downloader() {
    final SynchronizerConfiguration syncConfig =
        SynchronizerConfiguration.builder().build().validated(localBlockchain);
    return downloader(syncConfig);
  }

  @Test
  public void syncsToBetterChain_multipleSegments() {
    otherBlockchainSetup.importFirstBlocks(15);
    final long targetBlock = otherBlockchain.getChainHeadBlockNumber();
    // Sanity check
    assertThat(targetBlock).isGreaterThan(localBlockchain.getChainHeadBlockNumber());

    final RespondingEthPeer peer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, otherBlockchain);
    final Responder responder = RespondingEthPeer.blockchainResponder(otherBlockchain);

    final SynchronizerConfiguration syncConfig =
        SynchronizerConfiguration.builder()
            .downloaderChainSegmentSize(10)
            .build()
            .validated(localBlockchain);
    final Downloader<?> downloader = downloader(syncConfig);
    downloader.start();

    while (!syncState.syncTarget().isPresent()) {
      peer.respond(responder);
    }
    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(peer.getEthPeer());

    while (localBlockchain.getChainHeadBlockNumber() < targetBlock) {
      peer.respond(responder);
    }

    assertThat(localBlockchain.getChainHeadBlockNumber()).isEqualTo(targetBlock);
  }

  @Test
  public void syncsToBetterChain_singleSegment() {
    otherBlockchainSetup.importFirstBlocks(5);
    final long targetBlock = otherBlockchain.getChainHeadBlockNumber();
    // Sanity check
    assertThat(targetBlock).isGreaterThan(localBlockchain.getChainHeadBlockNumber());

    final RespondingEthPeer peer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, otherBlockchain);
    final Responder responder = RespondingEthPeer.blockchainResponder(otherBlockchain);

    final SynchronizerConfiguration syncConfig =
        SynchronizerConfiguration.builder()
            .downloaderChainSegmentSize(10)
            .build()
            .validated(localBlockchain);
    final Downloader<?> downloader = downloader(syncConfig);
    downloader.start();

    while (!syncState.syncTarget().isPresent()) {
      peer.respond(responder);
    }
    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(peer.getEthPeer());

    while (localBlockchain.getChainHeadBlockNumber() < targetBlock) {
      peer.respond(responder);
    }

    assertThat(localBlockchain.getChainHeadBlockNumber()).isEqualTo(targetBlock);
  }

  @Test
  public void syncsToBetterChain_singleSegmentOnBoundary() {
    otherBlockchainSetup.importFirstBlocks(5);
    final long targetBlock = otherBlockchain.getChainHeadBlockNumber();
    // Sanity check
    assertThat(targetBlock).isGreaterThan(localBlockchain.getChainHeadBlockNumber());

    final RespondingEthPeer peer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, otherBlockchain);
    final Responder responder = RespondingEthPeer.blockchainResponder(otherBlockchain);

    final SynchronizerConfiguration syncConfig =
        SynchronizerConfiguration.builder()
            .downloaderChainSegmentSize(4)
            .build()
            .validated(localBlockchain);
    final Downloader<?> downloader = downloader(syncConfig);
    downloader.start();

    while (!syncState.syncTarget().isPresent()) {
      peer.respond(responder);
    }
    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(peer.getEthPeer());

    while (localBlockchain.getChainHeadBlockNumber() < targetBlock) {
      peer.respond(responder);
    }

    assertThat(localBlockchain.getChainHeadBlockNumber()).isEqualTo(targetBlock);
  }

  @Test
  public void doesNotSyncToWorseChain() {
    localBlockchainSetup.importFirstBlocks(15);
    // Sanity check
    assertThat(localBlockchain.getChainHeadBlockNumber())
        .isGreaterThan(BlockHeader.GENESIS_BLOCK_NUMBER);

    final RespondingEthPeer peer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, otherBlockchain);
    final Responder responder = RespondingEthPeer.blockchainResponder(otherBlockchain);

    final Downloader<?> downloader = downloader();
    downloader.start();

    peer.respond(responder);
    assertThat(syncState.syncTarget()).isNotPresent();

    while (peer.hasOutstandingRequests()) {
      peer.respond(responder);
    }

    assertThat(syncState.syncTarget()).isNotPresent();
    verify(localBlockchain, times(0)).appendBlock(any(), any());
  }

  @Test
  public void syncsToBetterChain_fromFork() {
    otherBlockchainSetup.importFirstBlocks(15);
    final long targetBlock = otherBlockchain.getChainHeadBlockNumber();

    // Add divergent blocks to local chain
    localBlockchainSetup.importFirstBlocks(3);
    gen = new BlockDataGenerator();
    final Block chainHead = localBlockchain.getChainHeadBlock();
    final Block forkBlock =
        gen.block(gen.nextBlockOptions(chainHead).setDifficulty(UInt256.of(0L)));
    localBlockchain.appendBlock(forkBlock, gen.receipts(forkBlock));

    // Sanity check
    assertThat(targetBlock).isGreaterThan(localBlockchain.getChainHeadBlockNumber());
    assertThat(otherBlockchain.contains(localBlockchain.getChainHead().getHash())).isFalse();

    final RespondingEthPeer peer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, otherBlockchain);
    final Responder responder = RespondingEthPeer.blockchainResponder(otherBlockchain);

    final SynchronizerConfiguration syncConfig =
        SynchronizerConfiguration.builder()
            .downloaderChainSegmentSize(10)
            .build()
            .validated(localBlockchain);
    final Downloader<?> downloader = downloader(syncConfig);
    downloader.start();

    while (localBlockchain.getChainHeadBlockNumber() < targetBlock) {
      peer.respond(responder);
    }

    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(peer.getEthPeer());
    assertThat(localBlockchain.getChainHeadBlockNumber()).isEqualTo(targetBlock);
  }

  @Test
  public void choosesBestPeerAsSyncTarget_byTd() {
    final UInt256 localTd = localBlockchain.getChainHead().getTotalDifficulty();

    final Responder responder = RespondingEthPeer.blockchainResponder(otherBlockchain);
    final RespondingEthPeer peerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, localTd.plus(100));
    final RespondingEthPeer peerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, localTd.plus(200));

    final Downloader<?> downloader = downloader();
    downloader.start();

    // Process until the sync target is selected
    while (!syncState.syncTarget().isPresent()) {
      RespondingEthPeer.respondOnce(responder, peerA, peerB);
    }
    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(peerB.getEthPeer());
  }

  @Test
  public void choosesBestPeerAsSyncTarget_byTdAndHeight() {
    final UInt256 localTd = localBlockchain.getChainHead().getTotalDifficulty();

    final Responder responder = RespondingEthPeer.blockchainResponder(otherBlockchain);
    final RespondingEthPeer peerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, localTd.plus(100), 0);
    peerA.getEthPeer().chainState().update(gen.hash(), 100);
    final RespondingEthPeer peerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, localTd.plus(200), 0);
    peerA.getEthPeer().chainState().update(gen.hash(), 50);

    final Downloader<?> downloader = downloader();
    downloader.start();

    // Process until the sync target is selected
    while (!syncState.syncTarget().isPresent()) {
      RespondingEthPeer.respondOnce(responder, peerA, peerB);
    }
    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(peerA.getEthPeer());
  }

  @Test
  public void switchesSyncTarget_betterHeight() {
    final UInt256 localTd = localBlockchain.getChainHead().getTotalDifficulty();
    final Responder responder = RespondingEthPeer.blockchainResponder(otherBlockchain);

    // Peer A is initially better
    final RespondingEthPeer peerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, localTd.plus(200), 50);
    final RespondingEthPeer peerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, localTd.plus(100), 50);

    final SynchronizerConfiguration syncConfig =
        SynchronizerConfiguration.builder()
            .downloaderChainSegmentSize(5)
            .downloaderChangeTargetThresholdByHeight(10)
            .build()
            .validated(localBlockchain);
    final Downloader<?> downloader = downloader(syncConfig);
    downloader.start();

    // Process until the sync target is selected
    while (!syncState.syncTarget().isPresent()) {
      peerA.respond(responder);
    }
    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(peerA.getEthPeer());

    // Update Peer B so that its a better target and send some responses to push logic forward
    peerB.getEthPeer().chainState().update(gen.hash(), 100);

    // Process through first task cycle
    final CompletableFuture<?> firstTask = downloader.currentTask;
    while (downloader.currentTask == firstTask) {
      RespondingEthPeer.respondOnce(responder, peerA, peerB);
    }

    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(peerB.getEthPeer());
  }

  @Test
  public void doesNotSwitchSyncTarget_betterHeightUnderThreshold() {
    otherBlockchainSetup.importFirstBlocks(8);
    final UInt256 localTd = localBlockchain.getChainHead().getTotalDifficulty();
    final Responder responder = RespondingEthPeer.blockchainResponder(otherBlockchain);

    final RespondingEthPeer bestPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, localTd.plus(200));
    final RespondingEthPeer otherPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, localTd.plus(100));

    final SynchronizerConfiguration syncConfig =
        SynchronizerConfiguration.builder()
            .downloaderChainSegmentSize(5)
            .downloaderChangeTargetThresholdByHeight(1000)
            .build()
            .validated(localBlockchain);
    final Downloader<?> downloader = downloader(syncConfig);
    downloader.start();

    // Process until the sync target is selected
    while (!syncState.syncTarget().isPresent()) {
      bestPeer.respond(responder);
    }
    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(bestPeer.getEthPeer());

    // Update otherPeer so that its a better target, but under the threshold to switch
    otherPeer.getEthPeer().chainState().update(gen.hash(), 100);

    // Process through first task cycle
    final CompletableFuture<?> firstTask = downloader.currentTask;
    while (downloader.currentTask == firstTask) {
      RespondingEthPeer.respondOnce(responder, bestPeer, otherPeer);
    }

    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(bestPeer.getEthPeer());
  }

  @Test
  public void switchesSyncTarget_betterTd() {
    final UInt256 localTd = localBlockchain.getChainHead().getTotalDifficulty();
    final Responder responder = RespondingEthPeer.blockchainResponder(otherBlockchain);

    // Peer A is initially better
    final RespondingEthPeer peerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, localTd.plus(200));
    final RespondingEthPeer peerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, localTd.plus(100));

    final SynchronizerConfiguration syncConfig =
        SynchronizerConfiguration.builder()
            .downloaderChainSegmentSize(5)
            .downloaderChangeTargetThresholdByTd(UInt256.of(10))
            .build()
            .validated(localBlockchain);
    final Downloader<?> downloader = downloader(syncConfig);
    downloader.start();

    // Process until the sync target is selected
    while (!syncState.syncTarget().isPresent()) {
      peerA.respond(responder);
    }
    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(peerA.getEthPeer());

    // Update Peer B so that its a better target and send some responses to push logic forward
    peerB
        .getEthPeer()
        .chainState()
        .update(gen.header(), syncState.chainHeadTotalDifficulty().plus(300));

    // Process through first task cycle
    final CompletableFuture<?> firstTask = downloader.currentTask;
    while (downloader.currentTask == firstTask) {
      RespondingEthPeer.respondOnce(responder, peerA, peerB);
    }

    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(peerB.getEthPeer());
  }

  @Test
  public void doesNotSwitchSyncTarget_betterTdUnderThreshold() {
    final long localChainHeadAtStart = localBlockchain.getChainHeadBlockNumber();
    final UInt256 localTd = localBlockchain.getChainHead().getTotalDifficulty();
    otherBlockchainSetup.importFirstBlocks(8);
    final Responder responder = RespondingEthPeer.blockchainResponder(otherBlockchain);

    // Sanity check
    assertThat(localChainHeadAtStart).isLessThan(otherBlockchain.getChainHeadBlockNumber());

    final RespondingEthPeer bestPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, localTd.plus(200));
    final RespondingEthPeer otherPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, localTd.plus(100));

    final SynchronizerConfiguration syncConfig =
        SynchronizerConfiguration.builder()
            .downloaderChainSegmentSize(5)
            .downloaderChangeTargetThresholdByTd(UInt256.of(100_000_000L))
            .build()
            .validated(localBlockchain);
    final Downloader<?> downloader = downloader(syncConfig);
    downloader.start();

    // Process until the sync target is selected
    while (!syncState.syncTarget().isPresent()) {
      bestPeer.respond(responder);
    }
    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(bestPeer.getEthPeer());

    // Update otherPeer so that its a better target and send some responses to push logic forward
    bestPeer
        .getEthPeer()
        .chainState()
        .update(gen.header(1000), syncState.chainHeadTotalDifficulty().plus(201));
    otherPeer
        .getEthPeer()
        .chainState()
        .update(gen.header(1000), syncState.chainHeadTotalDifficulty().plus(300));

    // Process through first task cycle
    final CompletableFuture<?> firstTask = downloader.currentTask;
    while (downloader.currentTask == firstTask) {
      RespondingEthPeer.respondOnce(responder, bestPeer, otherPeer);
    }

    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(bestPeer.getEthPeer());
  }

  @Test
  public void recoversFromSyncTargetDisconnect() {
    localBlockchainSetup.importFirstBlocks(2);
    final long localChainHeadAtStart = localBlockchain.getChainHeadBlockNumber();
    otherBlockchainSetup.importAllBlocks();
    final long targetBlock = otherBlockchain.getChainHeadBlockNumber();
    // Sanity check
    assertThat(targetBlock).isGreaterThan(localBlockchain.getChainHeadBlockNumber());

    final SynchronizerConfiguration syncConfig =
        SynchronizerConfiguration.builder()
            .downloaderChainSegmentSize(5)
            .downloaderHeadersRequestSize(3)
            .build()
            .validated(localBlockchain);
    final Downloader<?> downloader = downloader(syncConfig);

    final long bestPeerChainHead = otherBlockchain.getChainHeadBlockNumber();
    final RespondingEthPeer bestPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, otherBlockchain);
    final long secondBestPeerChainHead = bestPeerChainHead - 3;
    final Blockchain shorterChain = createShortChain(otherBlockchain, secondBestPeerChainHead);
    final RespondingEthPeer secondBestPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, shorterChain);
    final Responder bestResponder = RespondingEthPeer.blockchainResponder(otherBlockchain);
    final Responder secondBestResponder = RespondingEthPeer.blockchainResponder(shorterChain);
    downloader.start();

    // Process through sync target selection
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              bestPeer.respond(bestResponder);
              assertThat(syncState.syncTarget()).isNotEmpty();
            });

    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(bestPeer.getEthPeer());

    // The next message should be for checkpoint headers from the sync target
    final Optional<MessageData> maybeNextMessage = bestPeer.peekNextOutgoingRequest();
    assertThat(maybeNextMessage).isPresent();
    final MessageData nextMessage = maybeNextMessage.get();
    assertThat(nextMessage.getCode()).isEqualTo(EthPV62.GET_BLOCK_HEADERS);
    final GetBlockHeadersMessage headersMessage = GetBlockHeadersMessage.readFrom(nextMessage);
    assertThat(headersMessage.skip()).isGreaterThan(0);

    // Process through the first import
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              if (!bestPeer.respond(bestResponder)) {
                secondBestPeer.respond(secondBestResponder);
              }
              assertThat(localBlockchain.getChainHeadBlockNumber())
                  .isNotEqualTo(localChainHeadAtStart);
            });

    // Disconnect peer
    ethProtocolManager.handleDisconnect(
        bestPeer.getPeerConnection(), DisconnectReason.TOO_MANY_PEERS, true);

    // Downloader should recover and sync to next best peer
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              secondBestPeer.respond(secondBestResponder);
              assertThat(localBlockchain.getChainHeadBlockNumber())
                  .isEqualTo(secondBestPeerChainHead);
            });
  }

  @Test
  public void requestsCheckpointsFromSyncTarget() {
    localBlockchainSetup.importFirstBlocks(2);
    otherBlockchainSetup.importAllBlocks();
    final long targetBlock = otherBlockchain.getChainHeadBlockNumber();
    // Sanity check
    assertThat(targetBlock).isGreaterThan(localBlockchain.getChainHeadBlockNumber());

    final SynchronizerConfiguration syncConfig =
        SynchronizerConfiguration.builder()
            .downloaderChainSegmentSize(5)
            .downloaderHeadersRequestSize(3)
            .build()
            .validated(localBlockchain);
    final Downloader<?> downloader = downloader(syncConfig);

    // Setup the best peer we should use as our sync target
    final long bestPeerChainHead = otherBlockchain.getChainHeadBlockNumber();
    final RespondingEthPeer bestPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, otherBlockchain);
    final Responder bestResponder = RespondingEthPeer.blockchainResponder(otherBlockchain);

    // Create some other peers that are available to sync from
    final int otherPeersCount = 5;
    final List<RespondingEthPeer> otherPeers = new ArrayList<>(otherPeersCount);
    final long otherChainhead = bestPeerChainHead - 3;
    final Blockchain shorterChain = createShortChain(otherBlockchain, otherChainhead);
    final Responder otherResponder = RespondingEthPeer.blockchainResponder(shorterChain);
    for (int i = 0; i < otherPeersCount; i++) {
      final RespondingEthPeer otherPeer =
          EthProtocolManagerTestUtil.createPeer(ethProtocolManager, shorterChain);
      otherPeers.add(otherPeer);
    }

    downloader.start();

    // Process through sync target selection
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              bestPeer.respond(bestResponder);
              assertThat(syncState.syncTarget()).isNotEmpty();
            });

    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(bestPeer.getEthPeer());

    while (localBlockchain.getChainHeadBlockNumber() < bestPeerChainHead) {
      // Check that any requests for checkpoint headers are only sent to the best peer
      final long checkpointRequestsToOtherPeers =
          otherPeers
              .stream()
              .map(RespondingEthPeer::pendingOutgoingRequests)
              .flatMap(Function.identity())
              .filter(m -> m.getCode() == EthPV62.GET_BLOCK_HEADERS)
              .map(GetBlockHeadersMessage::readFrom)
              .filter(m -> m.skip() > 0)
              .count();
      assertThat(checkpointRequestsToOtherPeers).isEqualTo(0L);

      bestPeer.respond(bestResponder);
      for (final RespondingEthPeer otherPeer : otherPeers) {
        otherPeer.respond(otherResponder);
      }
    }
  }

  private MutableBlockchain createShortChain(
      final Blockchain blockchain, final long truncateAtBlockNumber) {
    final BlockHeader genesisHeader =
        blockchain.getBlockHeader(BlockHeader.GENESIS_BLOCK_NUMBER).get();
    final BlockBody genesisBody = blockchain.getBlockBody(genesisHeader.getHash()).get();
    final Block genesisBlock = new Block(genesisHeader, genesisBody);
    final MutableBlockchain shortChain = InMemoryTestFixture.createInMemoryBlockchain(genesisBlock);
    long nextBlock = genesisHeader.getNumber() + 1;
    while (nextBlock <= truncateAtBlockNumber) {
      final BlockHeader header = blockchain.getBlockHeader(nextBlock).get();
      final BlockBody body = blockchain.getBlockBody(header.getHash()).get();
      final List<TransactionReceipt> receipts = blockchain.getTxReceipts(header.getHash()).get();
      final Block block = new Block(header, body);
      shortChain.appendBlock(block, receipts);
      nextBlock++;
    }
    return shortChain;
  }
}
