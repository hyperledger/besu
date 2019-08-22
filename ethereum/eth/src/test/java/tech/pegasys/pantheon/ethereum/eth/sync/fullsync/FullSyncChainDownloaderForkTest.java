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
package tech.pegasys.pantheon.ethereum.eth.sync.fullsync;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.BlockchainSetupUtil;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManagerTestUtil;
import tech.pegasys.pantheon.ethereum.eth.manager.EthScheduler;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer.Responder;
import tech.pegasys.pantheon.ethereum.eth.sync.ChainDownloader;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.util.uint.UInt256;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FullSyncChainDownloaderForkTest {

  protected ProtocolSchedule<Void> protocolSchedule;
  protected EthProtocolManager ethProtocolManager;
  protected EthContext ethContext;
  protected ProtocolContext<Void> protocolContext;
  private SyncState syncState;

  private BlockchainSetupUtil<Void> localBlockchainSetup;
  protected MutableBlockchain localBlockchain;
  private BlockchainSetupUtil<Void> otherBlockchainSetup;
  protected Blockchain otherBlockchain;
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  @Before
  public void setupTest() {
    localBlockchainSetup = BlockchainSetupUtil.forUpgradedFork();
    localBlockchain = localBlockchainSetup.getBlockchain();
    otherBlockchainSetup = BlockchainSetupUtil.forOutdatedFork();
    otherBlockchain = otherBlockchainSetup.getBlockchain();

    protocolSchedule = localBlockchainSetup.getProtocolSchedule();
    protocolContext = localBlockchainSetup.getProtocolContext();
    ethProtocolManager =
        EthProtocolManagerTestUtil.create(
            localBlockchain,
            localBlockchainSetup.getWorldArchive(),
            new EthScheduler(1, 1, 1, new NoOpMetricsSystem()));
    ethContext = ethProtocolManager.ethContext();
    syncState = new SyncState(protocolContext.getBlockchain(), ethContext.getEthPeers());
  }

  @After
  public void tearDown() {
    ethProtocolManager.stop();
  }

  private ChainDownloader downloader(final SynchronizerConfiguration syncConfig) {
    return FullSyncChainDownloader.create(
        syncConfig, protocolSchedule, protocolContext, ethContext, syncState, metricsSystem);
  }

  private ChainDownloader downloader() {
    final SynchronizerConfiguration syncConfig = syncConfigBuilder().build();
    return downloader(syncConfig);
  }

  private SynchronizerConfiguration.Builder syncConfigBuilder() {
    return SynchronizerConfiguration.builder();
  }

  @Test
  public void disconnectsFromPeerOnBadFork() {
    otherBlockchainSetup.importAllBlocks();
    final UInt256 localTd = localBlockchain.getChainHead().getTotalDifficulty();

    final Responder responder = RespondingEthPeer.blockchainResponder(otherBlockchain);
    final RespondingEthPeer peer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, localTd.plus(100), 100);

    final ChainDownloader downloader = downloader();
    downloader.start();

    // Process until the sync target is selected
    peer.respondWhileOtherThreadsWork(responder, () -> syncState.syncTarget().isEmpty());

    // Check that we picked our peer
    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(peer.getEthPeer());

    // Process until the sync target is cleared
    peer.respondWhileOtherThreadsWork(responder, () -> syncState.syncTarget().isPresent());

    // We should have disconnected from our peer on the invalid chain
    assertThat(peer.getEthPeer().isDisconnected()).isTrue();
    assertThat(peer.getPeerConnection().getDisconnectReason())
        .contains(DisconnectReason.BREACH_OF_PROTOCOL);
    assertThat(syncState.syncTarget()).isEmpty();
  }
}
