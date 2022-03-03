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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.ProtocolScheduleFixture;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncTarget;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class FullSyncTargetManagerTest {

  private EthProtocolManager ethProtocolManager;

  private MutableBlockchain localBlockchain;
  private final WorldStateArchive localWorldState = mock(WorldStateArchive.class);
  private RespondingEthPeer.Responder responder;
  private FullSyncTargetManager syncTargetManager;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {{DataStorageFormat.BONSAI}, {DataStorageFormat.FOREST}});
  }

  private final DataStorageFormat storageFormat;

  public FullSyncTargetManagerTest(final DataStorageFormat storageFormat) {
    this.storageFormat = storageFormat;
  }

  @Before
  public void setup() {
    final BlockchainSetupUtil otherBlockchainSetup = BlockchainSetupUtil.forTesting(storageFormat);
    final Blockchain otherBlockchain = otherBlockchainSetup.getBlockchain();
    responder = RespondingEthPeer.blockchainResponder(otherBlockchain);

    final BlockchainSetupUtil localBlockchainSetup = BlockchainSetupUtil.forTesting(storageFormat);
    localBlockchain = localBlockchainSetup.getBlockchain();

    final ProtocolSchedule protocolSchedule = ProtocolScheduleFixture.MAINNET;
    final ProtocolContext protocolContext =
        new ProtocolContext(localBlockchain, localWorldState, null);
    ethProtocolManager =
        EthProtocolManagerTestUtil.create(
            localBlockchain,
            new EthScheduler(1, 1, 1, 1, new NoOpMetricsSystem()),
            localWorldState,
            localBlockchainSetup.getTransactionPool(),
            EthProtocolConfiguration.defaultConfig());
    final EthContext ethContext = ethProtocolManager.ethContext();
    localBlockchainSetup.importFirstBlocks(5);
    otherBlockchainSetup.importFirstBlocks(20);
    syncTargetManager =
        new FullSyncTargetManager(
            SynchronizerConfiguration.builder().build(),
            protocolSchedule,
            protocolContext,
            ethContext,
            new NoOpMetricsSystem(),
            SyncTerminationCondition.never());
  }

  @After
  public void tearDown() {
    ethProtocolManager.stop();
  }

  @Test
  public void findSyncTarget_withHeightEstimates() {
    final BlockHeader chainHeadHeader = localBlockchain.getChainHeadHeader();
    when(localWorldState.isWorldStateAvailable(
            chainHeadHeader.getStateRoot(), chainHeadHeader.getHash()))
        .thenReturn(true);
    final RespondingEthPeer bestPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, Difficulty.MAX_VALUE, 4);

    final CompletableFuture<SyncTarget> result = syncTargetManager.findSyncTarget();
    bestPeer.respond(responder);

    assertThat(result)
        .isCompletedWithValue(
            new SyncTarget(bestPeer.getEthPeer(), localBlockchain.getBlockHeader(4L).get()));
  }

  @Test
  public void findSyncTarget_noHeightEstimates() {
    final BlockHeader chainHeadHeader = localBlockchain.getChainHeadHeader();
    when(localWorldState.isWorldStateAvailable(
            chainHeadHeader.getStateRoot(), chainHeadHeader.getHash()))
        .thenReturn(true);
    final RespondingEthPeer bestPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, Difficulty.MAX_VALUE, 0);

    final CompletableFuture<SyncTarget> result = syncTargetManager.findSyncTarget();
    bestPeer.respond(responder);

    assertThat(result).isNotCompleted();
  }

  @Test
  public void shouldDisconnectPeerIfWorldStateIsUnavailableForCommonAncestor() {
    final BlockHeader chainHeadHeader = localBlockchain.getChainHeadHeader();
    when(localWorldState.isWorldStateAvailable(
            chainHeadHeader.getStateRoot(), chainHeadHeader.getHash()))
        .thenReturn(false);
    final RespondingEthPeer bestPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 20);

    final CompletableFuture<SyncTarget> result = syncTargetManager.findSyncTarget();

    bestPeer.respond(responder);

    assertThat(result).isNotCompleted();
    assertThat(bestPeer.getPeerConnection().isDisconnected()).isTrue();
  }

  @Test
  public void shouldAllowSyncTargetWhenIfWorldStateIsAvailableForCommonAncestor() {
    final BlockHeader chainHeadHeader = localBlockchain.getChainHeadHeader();
    when(localWorldState.isWorldStateAvailable(
            chainHeadHeader.getStateRoot(), chainHeadHeader.getHash()))
        .thenReturn(true);
    final RespondingEthPeer bestPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 20);

    final CompletableFuture<SyncTarget> result = syncTargetManager.findSyncTarget();

    bestPeer.respond(responder);

    assertThat(result)
        .isCompletedWithValue(
            new SyncTarget(bestPeer.getEthPeer(), localBlockchain.getChainHeadHeader()));
    assertThat(bestPeer.getPeerConnection().isDisconnected()).isFalse();
  }
}
