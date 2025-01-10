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
package org.hyperledger.besu.ethereum.eth.sync.fullsync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestBuilder;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.ethereum.eth.messages.EthPV62;
import org.hyperledger.besu.ethereum.eth.messages.GetBlockHeadersMessage;
import org.hyperledger.besu.ethereum.eth.sync.ChainDownloader;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.metrics.SyncDurationMetrics;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

public class FullSyncChainDownloaderTest {

  protected ProtocolSchedule protocolSchedule;
  protected EthProtocolManager ethProtocolManager;
  protected EthContext ethContext;
  protected ProtocolContext protocolContext;
  private SyncState syncState;

  private BlockDataGenerator gen;
  private BlockchainSetupUtil localBlockchainSetup;
  protected MutableBlockchain localBlockchain;
  private BlockchainSetupUtil otherBlockchainSetup;
  protected Blockchain otherBlockchain;
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  static class FullSyncChainDownloaderTestArguments implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
      return Stream.of(
          Arguments.of(DataStorageFormat.BONSAI), Arguments.of(DataStorageFormat.FOREST));
    }
  }

  public void setupTest(final DataStorageFormat storageFormat) {
    gen = new BlockDataGenerator();
    localBlockchainSetup = BlockchainSetupUtil.forTesting(storageFormat);
    localBlockchain = localBlockchainSetup.getBlockchain();
    otherBlockchainSetup = BlockchainSetupUtil.forTesting(storageFormat);
    otherBlockchain = otherBlockchainSetup.getBlockchain();

    protocolSchedule = localBlockchainSetup.getProtocolSchedule();
    protocolContext = localBlockchainSetup.getProtocolContext();
    ethProtocolManager =
        EthProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(localBlockchain)
            .setEthScheduler(new EthScheduler(1, 1, 1, 1, new NoOpMetricsSystem()))
            .setWorldStateArchive(localBlockchainSetup.getWorldArchive())
            .setTransactionPool(localBlockchainSetup.getTransactionPool())
            .setEthereumWireProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
            .build();
    ethContext = ethProtocolManager.ethContext();
    syncState = new SyncState(protocolContext.getBlockchain(), ethContext.getEthPeers());
  }

  @AfterEach
  public void tearDown() {
    if (ethProtocolManager != null) {
      ethProtocolManager.stop();
    }
  }

  private ChainDownloader downloader(final SynchronizerConfiguration syncConfig) {
    return FullSyncChainDownloader.create(
        syncConfig,
        protocolSchedule,
        protocolContext,
        ethContext,
        syncState,
        metricsSystem,
        SyncTerminationCondition.never(),
        SyncDurationMetrics.NO_OP_SYNC_DURATION_METRICS,
        mock(PeerTaskExecutor.class));
  }

  private ChainDownloader downloader() {
    final SynchronizerConfiguration syncConfig = syncConfigBuilder().build();
    return downloader(syncConfig);
  }

  private SynchronizerConfiguration.Builder syncConfigBuilder() {
    return SynchronizerConfiguration.builder();
  }

  @ParameterizedTest
  @ArgumentsSource(FullSyncChainDownloaderTestArguments.class)
  public void syncsToBetterChain_multipleSegments(final DataStorageFormat storageFormat) {
    setupTest(storageFormat);
    otherBlockchainSetup.importFirstBlocks(15);
    final long targetBlock = otherBlockchain.getChainHeadBlockNumber();
    // Sanity check
    assertThat(targetBlock).isGreaterThan(localBlockchain.getChainHeadBlockNumber());

    final RespondingEthPeer peer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, otherBlockchain);
    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(otherBlockchain);

    final SynchronizerConfiguration syncConfig =
        syncConfigBuilder().downloaderChainSegmentSize(10).build();
    final ChainDownloader downloader = downloader(syncConfig);
    downloader.start();

    peer.respondWhileOtherThreadsWork(responder, () -> !syncState.syncTarget().isPresent());
    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(peer.getEthPeer());

    peer.respondWhileOtherThreadsWork(
        responder, () -> localBlockchain.getChainHeadBlockNumber() < targetBlock);

    assertThat(localBlockchain.getChainHeadBlockNumber()).isEqualTo(targetBlock);
  }

  @ParameterizedTest
  @ArgumentsSource(FullSyncChainDownloaderTestArguments.class)
  public void syncsToBetterChain_singleSegment(final DataStorageFormat storageFormat) {
    setupTest(storageFormat);
    otherBlockchainSetup.importFirstBlocks(5);
    final long targetBlock = otherBlockchain.getChainHeadBlockNumber();
    // Sanity check
    assertThat(targetBlock).isGreaterThan(localBlockchain.getChainHeadBlockNumber());

    final RespondingEthPeer peer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, otherBlockchain);
    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(otherBlockchain);

    final SynchronizerConfiguration syncConfig =
        syncConfigBuilder().downloaderChainSegmentSize(10).build();
    final ChainDownloader downloader = downloader(syncConfig);
    downloader.start();

    peer.respondWhileOtherThreadsWork(responder, () -> !syncState.syncTarget().isPresent());
    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(peer.getEthPeer());

    peer.respondWhileOtherThreadsWork(
        responder, () -> localBlockchain.getChainHeadBlockNumber() < targetBlock);

    assertThat(localBlockchain.getChainHeadBlockNumber()).isEqualTo(targetBlock);
  }

  @ParameterizedTest
  @ArgumentsSource(FullSyncChainDownloaderTestArguments.class)
  public void syncsToBetterChain_singleSegmentOnBoundary(final DataStorageFormat storageFormat) {
    setupTest(storageFormat);
    otherBlockchainSetup.importFirstBlocks(5);
    final long targetBlock = otherBlockchain.getChainHeadBlockNumber();
    // Sanity check
    assertThat(targetBlock).isGreaterThan(localBlockchain.getChainHeadBlockNumber());

    final RespondingEthPeer peer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, otherBlockchain);
    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(otherBlockchain);

    final SynchronizerConfiguration syncConfig =
        syncConfigBuilder().downloaderChainSegmentSize(4).build();
    final ChainDownloader downloader = downloader(syncConfig);
    downloader.start();

    peer.respondWhileOtherThreadsWork(responder, () -> !syncState.syncTarget().isPresent());
    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(peer.getEthPeer());

    peer.respondWhileOtherThreadsWork(
        responder, () -> localBlockchain.getChainHeadBlockNumber() < targetBlock);

    assertThat(localBlockchain.getChainHeadBlockNumber()).isEqualTo(targetBlock);
  }

  @ParameterizedTest
  @ArgumentsSource(FullSyncChainDownloaderTestArguments.class)
  public void doesNotSyncToWorseChain(final DataStorageFormat storageFormat) {
    setupTest(storageFormat);
    localBlockchainSetup.importFirstBlocks(15);
    // Sanity check
    assertThat(localBlockchain.getChainHeadBlockNumber())
        .isGreaterThan(BlockHeader.GENESIS_BLOCK_NUMBER);

    final RespondingEthPeer peer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, otherBlockchain);
    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(otherBlockchain);

    final ChainDownloader downloader = downloader();
    downloader.start();

    peer.respond(responder);
    assertThat(syncState.syncTarget()).isNotPresent();

    peer.respondWhileOtherThreadsWork(responder, peer::hasOutstandingRequests);

    assertThat(syncState.syncTarget()).isNotPresent();
  }

  @ParameterizedTest
  @ArgumentsSource(FullSyncChainDownloaderTestArguments.class)
  public void syncsToBetterChain_fromFork(final DataStorageFormat storageFormat) {
    setupTest(storageFormat);
    otherBlockchainSetup.importFirstBlocks(15);
    final long targetBlock = otherBlockchain.getChainHeadBlockNumber();

    // Add divergent blocks to local chain
    localBlockchainSetup.importFirstBlocks(3);
    gen = new BlockDataGenerator();
    final Block chainHead = localBlockchain.getChainHeadBlock();
    final Block forkBlock =
        gen.block(gen.nextBlockOptions(chainHead).setDifficulty(Difficulty.ZERO));
    localBlockchain.appendBlock(forkBlock, gen.receipts(forkBlock));

    // Sanity check
    assertThat(targetBlock).isGreaterThan(localBlockchain.getChainHeadBlockNumber());
    assertThat(otherBlockchain.contains(localBlockchain.getChainHead().getHash())).isFalse();

    final RespondingEthPeer peer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, otherBlockchain);
    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(otherBlockchain);

    final SynchronizerConfiguration syncConfig =
        syncConfigBuilder().downloaderChainSegmentSize(10).build();
    final ChainDownloader downloader = downloader(syncConfig);
    downloader.start();

    peer.respondWhileOtherThreadsWork(
        responder,
        () ->
            localBlockchain.getChainHeadBlockNumber() < targetBlock
                || syncState.syncTarget().isPresent());

    // Synctarget should not exist as chain has fully downloaded.
    assertThat(syncState.syncTarget().isPresent()).isFalse();
    assertThat(localBlockchain.getChainHeadBlockNumber()).isEqualTo(targetBlock);
  }

  @ParameterizedTest
  @ArgumentsSource(FullSyncChainDownloaderTestArguments.class)
  public void choosesBestPeerAsSyncTarget_byTd(final DataStorageFormat storageFormat) {
    setupTest(storageFormat);
    final Difficulty localTd = localBlockchain.getChainHead().getTotalDifficulty();

    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(otherBlockchain);
    final RespondingEthPeer peerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, localTd.add(100));
    final RespondingEthPeer peerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, localTd.add(200));

    final ChainDownloader downloader = downloader();
    downloader.start();

    // Process until the sync target is selected
    while (!syncState.syncTarget().isPresent()) {
      RespondingEthPeer.respondOnce(responder, peerA, peerB);
    }
    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(peerB.getEthPeer());
  }

  @ParameterizedTest
  @ArgumentsSource(FullSyncChainDownloaderTestArguments.class)
  public void choosesBestPeerAsSyncTarget_byTdAndHeight(final DataStorageFormat storageFormat) {
    setupTest(storageFormat);
    final Difficulty localTd = localBlockchain.getChainHead().getTotalDifficulty();

    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(otherBlockchain);
    final RespondingEthPeer peerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, localTd.add(100), 100);
    final RespondingEthPeer peerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, localTd.add(200), 50);

    final ChainDownloader downloader = downloader();
    downloader.start();

    // Process until the sync target is selected
    while (!syncState.syncTarget().isPresent()) {
      RespondingEthPeer.respondOnce(responder, peerA, peerB);
    }
    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(peerB.getEthPeer());
  }

  @ParameterizedTest
  @ArgumentsSource(FullSyncChainDownloaderTestArguments.class)
  public void recoversFromSyncTargetDisconnect(final DataStorageFormat storageFormat) {
    setupTest(storageFormat);
    localBlockchainSetup.importFirstBlocks(2);
    final long localChainHeadAtStart = localBlockchain.getChainHeadBlockNumber();
    otherBlockchainSetup.importAllBlocks();
    final long targetBlock = otherBlockchain.getChainHeadBlockNumber();
    // Sanity check
    assertThat(targetBlock).isGreaterThan(localBlockchain.getChainHeadBlockNumber());

    final SynchronizerConfiguration syncConfig =
        syncConfigBuilder().downloaderChainSegmentSize(5).downloaderHeadersRequestSize(3).build();
    final ChainDownloader downloader = downloader(syncConfig);

    final long bestPeerChainHead = otherBlockchain.getChainHeadBlockNumber();
    final RespondingEthPeer bestPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, otherBlockchain);
    final long secondBestPeerChainHead = bestPeerChainHead - 3;
    final Blockchain shorterChain = createShortChain(otherBlockchain, secondBestPeerChainHead);
    final RespondingEthPeer secondBestPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, shorterChain);
    final RespondingEthPeer.Responder bestResponder =
        RespondingEthPeer.blockchainResponder(otherBlockchain);
    final RespondingEthPeer.Responder secondBestResponder =
        RespondingEthPeer.blockchainResponder(shorterChain);
    downloader.start();

    // Process through sync target selection
    bestPeer.respondWhileOtherThreadsWork(bestResponder, () -> !syncState.syncTarget().isPresent());

    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(bestPeer.getEthPeer());

    // The next message should be for checkpoint headers from the sync target
    Awaitility.waitAtMost(10, TimeUnit.SECONDS)
        .until(() -> bestPeer.peekNextOutgoingRequest().isPresent());
    final Optional<MessageData> maybeNextMessage = bestPeer.peekNextOutgoingRequest();
    assertThat(maybeNextMessage).isPresent();
    final MessageData nextMessage = maybeNextMessage.get();
    assertThat(nextMessage.getCode()).isEqualTo(EthPV62.GET_BLOCK_HEADERS);
    final GetBlockHeadersMessage headersMessage = GetBlockHeadersMessage.readFrom(nextMessage);
    assertThat(headersMessage.skip()).isGreaterThan(0);

    // Process through the first import
    await()
        .atMost(10, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () -> {
              if (!bestPeer.respond(bestResponder)) {
                secondBestPeer.respond(secondBestResponder);
              }
              assertThat(localBlockchain.getChainHeadBlockNumber())
                  .isNotEqualTo(localChainHeadAtStart);
            });

    // Sanity check that we haven't already passed the second best peer
    assertThat(localBlockchain.getChainHeadBlockNumber()).isLessThan(secondBestPeerChainHead);

    // Disconnect peer
    ethProtocolManager.handleDisconnect(
        bestPeer.getPeerConnection(), DisconnectReason.TOO_MANY_PEERS, true);

    // Downloader should recover and sync to next best peer, but it may stall
    // for 10 seconds first (by design).
    secondBestPeer.respondWhileOtherThreadsWork(
        secondBestResponder,
        () -> localBlockchain.getChainHeadBlockNumber() != secondBestPeerChainHead);

    assertThat(localBlockchain.getChainHeadBlockNumber()).isEqualTo(secondBestPeerChainHead);
  }

  @ParameterizedTest
  @ArgumentsSource(FullSyncChainDownloaderTestArguments.class)
  public void requestsCheckpointsFromSyncTarget(final DataStorageFormat storageFormat) {
    setupTest(storageFormat);
    localBlockchainSetup.importFirstBlocks(2);
    otherBlockchainSetup.importAllBlocks();
    final long targetBlock = otherBlockchain.getChainHeadBlockNumber();
    // Sanity check
    assertThat(targetBlock).isGreaterThan(localBlockchain.getChainHeadBlockNumber());

    final SynchronizerConfiguration syncConfig =
        syncConfigBuilder().downloaderChainSegmentSize(5).downloaderHeadersRequestSize(3).build();
    final ChainDownloader downloader = downloader(syncConfig);

    // Setup the best peer we should use as our sync target
    final long bestPeerChainHead = otherBlockchain.getChainHeadBlockNumber();
    final RespondingEthPeer bestPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, otherBlockchain);
    final RespondingEthPeer.Responder bestResponder =
        RespondingEthPeer.blockchainResponder(otherBlockchain);

    // Create some other peers that are available to sync from
    final int otherPeersCount = 5;
    final List<RespondingEthPeer> otherPeers = new ArrayList<>(otherPeersCount);
    final long otherChainhead = bestPeerChainHead - 3;
    final Blockchain shorterChain = createShortChain(otherBlockchain, otherChainhead);
    final RespondingEthPeer.Responder otherResponder =
        RespondingEthPeer.blockchainResponder(shorterChain);
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
      // Wait until there is a request to respond to (or we reached chain head).
      // If we don't get a new request within 30 seconds the test will fail because we've probably
      // stalled.
      Awaitility.await()
          .atMost(30, TimeUnit.SECONDS)
          .until(
              () ->
                  bestPeer.hasOutstandingRequests()
                      || otherPeers.stream().anyMatch(RespondingEthPeer::hasOutstandingRequests)
                      || localBlockchain.getChainHeadBlockNumber() >= bestPeerChainHead);

      // Check that any requests for checkpoint headers are only sent to the best peer
      final long checkpointRequestsToOtherPeers =
          otherPeers.stream()
              .map(RespondingEthPeer::streamPendingOutgoingRequests)
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
    final MutableBlockchain shortChain = createInMemoryBlockchain(genesisBlock);
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

  @Test
  void dryRunDetector() {
    assertThat(true)
        .withFailMessage("This test is here so gradle --dry-run executes this class")
        .isTrue();
  }
}
