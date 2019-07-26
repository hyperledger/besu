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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManagerTestUtil;
import tech.pegasys.pantheon.ethereum.eth.manager.EthScheduler;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer.Responder;
import tech.pegasys.pantheon.ethereum.eth.manager.ethtaskutils.BlockchainSetupUtil;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncTarget;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FullSyncTargetManagerTest {

  private EthProtocolManager ethProtocolManager;

  private MutableBlockchain localBlockchain;
  private final WorldStateArchive localWorldState = mock(WorldStateArchive.class);
  private Responder responder;
  private FullSyncTargetManager<Void> syncTargetManager;

  @Before
  public void setup() {
    final BlockchainSetupUtil<Void> otherBlockchainSetup = BlockchainSetupUtil.forTesting();
    final Blockchain otherBlockchain = otherBlockchainSetup.getBlockchain();
    responder = RespondingEthPeer.blockchainResponder(otherBlockchain);

    final BlockchainSetupUtil<Void> localBlockchainSetup = BlockchainSetupUtil.forTesting();
    localBlockchain = localBlockchainSetup.getBlockchain();

    final ProtocolSchedule<Void> protocolSchedule = MainnetProtocolSchedule.create();
    final ProtocolContext<Void> protocolContext =
        new ProtocolContext<>(localBlockchain, localWorldState, null);
    ethProtocolManager =
        EthProtocolManagerTestUtil.create(
            localBlockchain, localWorldState, new EthScheduler(1, 1, 1, new NoOpMetricsSystem()));
    final EthContext ethContext = ethProtocolManager.ethContext();
    localBlockchainSetup.importFirstBlocks(5);
    otherBlockchainSetup.importFirstBlocks(20);
    syncTargetManager =
        new FullSyncTargetManager<>(
            SynchronizerConfiguration.builder().build(),
            protocolSchedule,
            protocolContext,
            ethContext,
            new NoOpMetricsSystem());
  }

  @After
  public void tearDown() {
    ethProtocolManager.stop();
  }

  @Test
  public void shouldDisconnectPeerIfWorldStateIsUnavailableForCommonAncestor() {
    when(localWorldState.isWorldStateAvailable(localBlockchain.getChainHeadHeader().getStateRoot()))
        .thenReturn(false);
    final RespondingEthPeer bestPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 20);

    final CompletableFuture<SyncTarget> result = syncTargetManager.findSyncTarget(Optional.empty());

    bestPeer.respond(responder);

    assertThat(result).isNotCompleted();
    assertThat(bestPeer.getPeerConnection().isDisconnected()).isTrue();
  }

  @Test
  public void shouldAllowSyncTargetWhenIfWorldStateIsAvailableForCommonAncestor() {
    when(localWorldState.isWorldStateAvailable(localBlockchain.getChainHeadHeader().getStateRoot()))
        .thenReturn(true);
    final RespondingEthPeer bestPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 20);

    final CompletableFuture<SyncTarget> result = syncTargetManager.findSyncTarget(Optional.empty());

    bestPeer.respond(responder);

    assertThat(result)
        .isCompletedWithValue(
            new SyncTarget(bestPeer.getEthPeer(), localBlockchain.getChainHeadHeader()));
    assertThat(bestPeer.getPeerConnection().isDisconnected()).isFalse();
  }
}
