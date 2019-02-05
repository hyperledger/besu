/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.eth.sync.fastsync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode.LIGHT_SKIP_DETACHED;
import static tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason.TOO_MANY_PEERS;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManagerTestUtil;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer.Responder;
import tech.pegasys.pantheon.ethereum.eth.manager.ethtaskutils.BlockchainSetupUtil;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;

import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.Test;

public class FastSyncChainDownloaderTest {

  private final FastSyncValidationPolicy validationPolicy = mock(FastSyncValidationPolicy.class);

  protected ProtocolSchedule<Void> protocolSchedule;
  protected EthProtocolManager ethProtocolManager;
  protected EthContext ethContext;
  protected ProtocolContext<Void> protocolContext;
  private SyncState syncState;

  protected MutableBlockchain localBlockchain;
  private BlockchainSetupUtil<Void> otherBlockchainSetup;
  protected Blockchain otherBlockchain;

  @Before
  public void setup() {
    when(validationPolicy.getValidationModeForNextBlock()).thenReturn(LIGHT_SKIP_DETACHED);
    final BlockchainSetupUtil<Void> localBlockchainSetup = BlockchainSetupUtil.forTesting();
    localBlockchain = localBlockchainSetup.getBlockchain();
    otherBlockchainSetup = BlockchainSetupUtil.forTesting();
    otherBlockchain = otherBlockchainSetup.getBlockchain();

    protocolSchedule = localBlockchainSetup.getProtocolSchedule();
    protocolContext = localBlockchainSetup.getProtocolContext();
    ethProtocolManager =
        EthProtocolManagerTestUtil.create(localBlockchain, localBlockchainSetup.getWorldArchive());
    ethContext = ethProtocolManager.ethContext();
    syncState = new SyncState(protocolContext.getBlockchain(), ethContext.getEthPeers());
  }

  private FastSyncChainDownloader<?> downloader(
      final SynchronizerConfiguration syncConfig, final long pivotBlockNumber) {
    return new FastSyncChainDownloader<>(
        syncConfig,
        protocolSchedule,
        protocolContext,
        ethContext,
        syncState,
        NoOpMetricsSystem.NO_OP_LABELLED_TIMER,
        NoOpMetricsSystem.NO_OP_LABELLED_COUNTER,
        otherBlockchain.getBlockHeader(pivotBlockNumber).get());
  }

  @Test
  public void shouldSyncToPivotBlockInMultipleSegments() {
    otherBlockchainSetup.importFirstBlocks(30);

    final RespondingEthPeer peer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, otherBlockchain);
    final Responder responder = RespondingEthPeer.blockchainResponder(otherBlockchain);

    final SynchronizerConfiguration syncConfig =
        SynchronizerConfiguration.builder()
            .downloaderChainSegmentSize(5)
            .downloaderHeadersRequestSize(3)
            .build()
            .validated(localBlockchain);
    final long pivotBlockNumber = 25;
    final FastSyncChainDownloader<?> downloader = downloader(syncConfig, pivotBlockNumber);
    final CompletableFuture<Void> result = downloader.start();

    peer.respondWhile(responder, () -> !result.isDone());

    assertThat(result).isCompleted();
    assertThat(localBlockchain.getChainHeadBlockNumber()).isEqualTo(pivotBlockNumber);
    assertThat(localBlockchain.getChainHeadHeader())
        .isEqualTo(otherBlockchain.getBlockHeader(pivotBlockNumber).get());
  }

  @Test
  public void shouldSyncToPivotBlockInSingleSegment() {
    otherBlockchainSetup.importFirstBlocks(30);

    final RespondingEthPeer peer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, otherBlockchain);
    final Responder responder = RespondingEthPeer.blockchainResponder(otherBlockchain);

    final long pivotBlockNumber = 5;
    final SynchronizerConfiguration syncConfig =
        SynchronizerConfiguration.builder().build().validated(localBlockchain);
    final FastSyncChainDownloader<?> downloader = downloader(syncConfig, pivotBlockNumber);
    final CompletableFuture<Void> result = downloader.start();

    peer.respondWhile(responder, () -> !result.isDone());

    assertThat(result).isCompleted();
    assertThat(localBlockchain.getChainHeadBlockNumber()).isEqualTo(pivotBlockNumber);
    assertThat(localBlockchain.getChainHeadHeader())
        .isEqualTo(otherBlockchain.getBlockHeader(pivotBlockNumber).get());
  }

  @Test
  public void recoversFromSyncTargetDisconnect() {
    final BlockchainSetupUtil<Void> shorterChainUtil = BlockchainSetupUtil.forTesting();
    final MutableBlockchain shorterChain = shorterChainUtil.getBlockchain();

    otherBlockchainSetup.importFirstBlocks(30);
    shorterChainUtil.importFirstBlocks(28);

    final RespondingEthPeer bestPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, otherBlockchain);
    final Responder bestResponder = RespondingEthPeer.blockchainResponder(otherBlockchain);
    final RespondingEthPeer secondBestPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, shorterChain);
    final Responder shorterResponder = RespondingEthPeer.blockchainResponder(shorterChain);

    final SynchronizerConfiguration syncConfig =
        SynchronizerConfiguration.builder()
            .downloaderChainSegmentSize(5)
            .downloaderHeadersRequestSize(3)
            .build()
            .validated(localBlockchain);
    final long pivotBlockNumber = 25;
    final FastSyncChainDownloader<?> downloader = downloader(syncConfig, pivotBlockNumber);
    final CompletableFuture<Void> result = downloader.start();

    while (localBlockchain.getChainHeadBlockNumber() < 15) {
      bestPeer.respond(bestResponder);
      secondBestPeer.respond(shorterResponder);
    }

    assertThat(localBlockchain.getChainHeadBlockNumber()).isEqualTo(15);
    assertThat(result).isNotCompleted();

    ethProtocolManager.handleDisconnect(bestPeer.getPeerConnection(), TOO_MANY_PEERS, true);

    secondBestPeer.respondWhile(shorterResponder, () -> !result.isDone());

    assertThat(result).isCompleted();
    assertThat(localBlockchain.getChainHeadBlockNumber()).isEqualTo(pivotBlockNumber);
    assertThat(localBlockchain.getChainHeadHeader())
        .isEqualTo(otherBlockchain.getBlockHeader(pivotBlockNumber).get());
  }
}
