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
package tech.pegasys.pantheon.ethereum.eth.sync.fullsync;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.BlockchainSetupUtil;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManagerTestUtil;
import tech.pegasys.pantheon.ethereum.eth.manager.EthScheduler;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.TrailingPeerRequirements;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FullSyncDownloaderTest {

  protected ProtocolSchedule<Void> protocolSchedule;
  protected EthProtocolManager ethProtocolManager;
  protected EthContext ethContext;
  protected ProtocolContext<Void> protocolContext;
  private SyncState syncState;

  private BlockchainSetupUtil<Void> localBlockchainSetup;
  protected MutableBlockchain localBlockchain;
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  @Before
  public void setupTest() {
    localBlockchainSetup = BlockchainSetupUtil.forTesting();
    localBlockchain = localBlockchainSetup.getBlockchain();

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

  private FullSyncDownloader<Void> downloader(final SynchronizerConfiguration syncConfig) {
    return new FullSyncDownloader<>(
        syncConfig, protocolSchedule, protocolContext, ethContext, syncState, metricsSystem);
  }

  @Test
  public void shouldLimitTrailingPeersWhenBehindChain() {
    localBlockchainSetup.importFirstBlocks(2);
    final int maxTailingPeers = 5;
    final FullSyncDownloader<Void> synchronizer =
        downloader(SynchronizerConfiguration.builder().maxTrailingPeers(maxTailingPeers).build());

    final RespondingEthPeer bestPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 100);
    syncState.setSyncTarget(bestPeer.getEthPeer(), localBlockchain.getChainHeadHeader());

    final TrailingPeerRequirements expected =
        new TrailingPeerRequirements(localBlockchain.getChainHeadBlockNumber(), maxTailingPeers);
    assertThat(synchronizer.calculateTrailingPeerRequirements()).isEqualTo(expected);
  }

  @Test
  public void shouldNotLimitTrailingPeersWhenInSync() {
    localBlockchainSetup.importFirstBlocks(2);
    final int maxTailingPeers = 5;
    final FullSyncDownloader<Void> synchronizer =
        downloader(SynchronizerConfiguration.builder().maxTrailingPeers(maxTailingPeers).build());

    final RespondingEthPeer bestPeer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 2);
    syncState.setSyncTarget(bestPeer.getEthPeer(), localBlockchain.getChainHeadHeader());

    assertThat(synchronizer.calculateTrailingPeerRequirements())
        .isEqualTo(TrailingPeerRequirements.UNRESTRICTED);
  }
}
