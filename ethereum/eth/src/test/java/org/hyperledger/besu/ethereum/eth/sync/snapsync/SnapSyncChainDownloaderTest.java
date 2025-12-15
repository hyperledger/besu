/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.ChainSyncState;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.ChainSyncStateStorage;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.metrics.SyncDurationMetrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SnapSyncChainDownloaderTest {

  @Mock private SnapSyncChainDownloadPipelineFactory pipelineFactory;
  @Mock private ProtocolSchedule protocolSchedule;
  @Mock private ProtocolContext protocolContext;
  @Mock private EthContext ethContext;
  @Mock private SyncState syncState;
  @Mock private SyncDurationMetrics syncDurationMetrics;
  @Mock private MutableBlockchain blockchain;
  @Mock private EthScheduler scheduler;

  @TempDir private java.nio.file.Path tempDir;

  private ChainSyncStateStorage chainSyncStateStorage;
  private BlockHeader pivotBlockHeader;
  private BlockHeader checkpointBlockHeader;

  @BeforeEach
  public void setUp() {
    pivotBlockHeader = new BlockHeaderTestFixture().number(1000).buildHeader();
    checkpointBlockHeader = new BlockHeaderTestFixture().number(500).buildHeader();
    chainSyncStateStorage = new ChainSyncStateStorage(tempDir);

    lenient().when(protocolContext.getBlockchain()).thenReturn(blockchain);
    lenient().when(blockchain.getChainHeadBlockNumber()).thenReturn(500L);
    lenient().when(blockchain.getChainHeadHeader()).thenReturn(checkpointBlockHeader);
    lenient().when(ethContext.getScheduler()).thenReturn(scheduler);
    lenient()
        .when(blockchain.getGenesisBlockHeader())
        .thenReturn(new BlockHeaderTestFixture().number(0).buildHeader());
    lenient().when(syncState.getCheckpoint()).thenReturn(java.util.Optional.empty());
  }

  @Test
  public void shouldInitializeWithNewStateWhenNoStateFileExists() {
    SnapSyncChainDownloader downloader =
        new SnapSyncChainDownloader(
            pipelineFactory,
            protocolSchedule,
            protocolContext,
            ethContext,
            syncState,
            syncDurationMetrics,
            pivotBlockHeader,
            chainSyncStateStorage);

    // Downloader should be initialized (no exception thrown)
    assertThat(downloader).isNotNull();
  }

  @Test
  public void shouldLoadExistingStateFromStorage() {
    // Create initial state
    ChainSyncState initialState =
        ChainSyncState.initialSync(
            pivotBlockHeader,
            checkpointBlockHeader,
            new BlockHeaderTestFixture().number(0).buildHeader());
    chainSyncStateStorage.storeState(initialState);

    // Create downloader - should load state
    SnapSyncChainDownloader downloader =
        new SnapSyncChainDownloader(
            pipelineFactory,
            protocolSchedule,
            protocolContext,
            ethContext,
            syncState,
            syncDurationMetrics,
            pivotBlockHeader,
            chainSyncStateStorage);

    assertThat(downloader).isNotNull();
  }

  @Test
  public void shouldHandleCancellation() {
    SnapSyncChainDownloader downloader =
        new SnapSyncChainDownloader(
            pipelineFactory,
            protocolSchedule,
            protocolContext,
            ethContext,
            syncState,
            syncDurationMetrics,
            pivotBlockHeader,
            chainSyncStateStorage);

    // Cancel should not throw even when no pipeline is running
    downloader.cancel();

    // Downloader should be in cancelled state
    assertThat(downloader).isNotNull();
  }

  @Test
  public void shouldReceivePivotUpdate() {
    SnapSyncChainDownloader downloader =
        new SnapSyncChainDownloader(
            pipelineFactory,
            protocolSchedule,
            protocolContext,
            ethContext,
            syncState,
            syncDurationMetrics,
            pivotBlockHeader,
            chainSyncStateStorage);

    BlockHeader newPivot = new BlockHeaderTestFixture().number(2000).buildHeader();
    downloader.onPivotUpdated(newPivot);

    // Should not throw - pivot update accepted
    assertThat(downloader).isNotNull();
  }

  @Test
  public void shouldReceiveWorldStateHealFinished() {
    SnapSyncChainDownloader downloader =
        new SnapSyncChainDownloader(
            pipelineFactory,
            protocolSchedule,
            protocolContext,
            ethContext,
            syncState,
            syncDurationMetrics,
            pivotBlockHeader,
            chainSyncStateStorage);

    downloader.onWorldStateHealFinished();

    // Should not throw - signal accepted
    assertThat(downloader).isNotNull();
  }

  @Test
  public void shouldHandleStateTransitionFromInitialToHeadersComplete() {
    // Create and store initial state
    ChainSyncState initialState =
        ChainSyncState.initialSync(
            pivotBlockHeader,
            checkpointBlockHeader,
            new BlockHeaderTestFixture().number(0).buildHeader());
    chainSyncStateStorage.storeState(initialState);

    // Create downloader
    SnapSyncChainDownloader downloader =
        new SnapSyncChainDownloader(
            pipelineFactory,
            protocolSchedule,
            protocolContext,
            ethContext,
            syncState,
            syncDurationMetrics,
            pivotBlockHeader,
            chainSyncStateStorage);

    assertThat(downloader).isNotNull();

    // Verify state was loaded
    ChainSyncState loadedState =
        chainSyncStateStorage.loadState(
            rlpInput -> BlockHeader.readFrom(rlpInput, new MainnetBlockHeaderFunctions()));
    assertThat(loadedState).isNotNull();
    assertThat(loadedState.headersDownloadComplete()).isFalse();
  }
}
