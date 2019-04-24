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
import static org.mockito.Mockito.spy;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManagerTestUtil;
import tech.pegasys.pantheon.ethereum.eth.manager.EthScheduler;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.ethtaskutils.BlockchainSetupUtil;
import tech.pegasys.pantheon.ethereum.eth.sync.ChainDownloader;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.metrics.MetricCategory;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.Observation;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration;
import tech.pegasys.pantheon.metrics.prometheus.PrometheusMetricsSystem;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class IncrementerTest {
  private final MetricsConfiguration metricsConfiguration = MetricsConfiguration.createDefault();
  private ProtocolSchedule<Void> protocolSchedule;
  private EthContext ethContext;
  private ProtocolContext<Void> protocolContext;
  private SyncState syncState;
  private MutableBlockchain localBlockchain;
  private MetricsSystem metricsSystem;
  private EthProtocolManager ethProtocolManager;
  private Blockchain otherBlockchain;
  private long targetBlock;

  @Before
  public void setUp() {
    metricsConfiguration.setEnabled(true);
    metricsSystem = PrometheusMetricsSystem.init(metricsConfiguration);

    final BlockchainSetupUtil<Void> localBlockchainSetup = BlockchainSetupUtil.forTesting();
    localBlockchain = spy(localBlockchainSetup.getBlockchain());
    final BlockchainSetupUtil<Void> otherBlockchainSetup = BlockchainSetupUtil.forTesting();
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

    otherBlockchainSetup.importFirstBlocks(15);
    targetBlock = otherBlockchain.getChainHeadBlockNumber();
    // Sanity check
    assertThat(targetBlock).isGreaterThan(localBlockchain.getChainHeadBlockNumber());
  }

  @After
  public void tearDown() {
    ethProtocolManager.stop();
  }

  @Test
  public void parallelDownloadPipelineCounterShouldIncrement() {
    final RespondingEthPeer peer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, otherBlockchain);
    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(otherBlockchain);

    final SynchronizerConfiguration syncConfig =
        SynchronizerConfiguration.builder().downloaderChainSegmentSize(10).build();
    final ChainDownloader downloader = downloader(syncConfig);
    downloader.start();

    peer.respondWhileOtherThreadsWork(responder, () -> !syncState.syncTarget().isPresent());
    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(peer.getEthPeer());

    peer.respondWhileOtherThreadsWork(
        responder, () -> localBlockchain.getChainHeadBlockNumber() < targetBlock);

    assertThat(localBlockchain.getChainHeadBlockNumber()).isEqualTo(targetBlock);

    final List<Observation> metrics =
        metricsSystem.getMetrics(MetricCategory.SYNCHRONIZER).collect(Collectors.toList());

    // the first iteration gets the genesis block, which results in no data
    // being passed downstream.  So observed value is 2.
    final Observation headerInboundObservation =
        new Observation(
            MetricCategory.SYNCHRONIZER,
            "inboundQueueCounter",
            2.0,
            Collections.singletonList("ParallelDownloadHeadersTask"));
    final Observation headerOutboundObservation =
        new Observation(
            MetricCategory.SYNCHRONIZER,
            "outboundQueueCounter",
            1.0,
            Collections.singletonList("ParallelDownloadHeadersTask"));
    assertThat(metrics).contains(headerInboundObservation, headerOutboundObservation);

    for (final String label :
        Arrays.asList(
            "ParallelValidateHeadersTask",
            "ParallelDownloadBodiesTask",
            "ParallelExtractTxSignaturesTask",
            "ParallelValidateAndImportBodiesTask")) {
      final Observation inboundObservation =
          new Observation(
              MetricCategory.SYNCHRONIZER,
              "inboundQueueCounter",
              1.0,
              Collections.singletonList(label));
      final Observation outboundObservation =
          new Observation(
              MetricCategory.SYNCHRONIZER,
              "outboundQueueCounter",
              1.0,
              Collections.singletonList(label));
      assertThat(metrics).contains(inboundObservation, outboundObservation);
    }
  }

  private ChainDownloader downloader(final SynchronizerConfiguration syncConfig) {
    return FullSyncChainDownloader.create(
        syncConfig, protocolSchedule, protocolContext, ethContext, syncState, metricsSystem);
  }
}
