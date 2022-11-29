/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.eth.manager.DeterministicEthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncActions;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.DynamicPivotBlockManager.DynamicPivotBlockObserver;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;

import java.util.OptionalLong;

import org.junit.Before;
import org.junit.Test;

public class DynamicPivotBlockManagerTest {

  private final SnapSyncState snapSyncState = mock(SnapSyncState.class);
  private final FastSyncActions fastSyncActions = mock(FastSyncActions.class);
  private final SyncState syncState = mock(SyncState.class);
  private final EthContext ethContext = mock(EthContext.class);
  private DynamicPivotBlockManager dynamicPivotBlockManager;

  @Before
  public void setup() {
    when(fastSyncActions.getSyncState()).thenReturn(syncState);
    when(ethContext.getScheduler()).thenReturn(new DeterministicEthScheduler());
    dynamicPivotBlockManager =
        spy(
            new DynamicPivotBlockManager(
                fastSyncActions,
                snapSyncState,
                SnapSyncConfiguration.DEFAULT_PIVOT_BLOCK_WINDOW_VALIDITY,
                SnapSyncConfiguration.DEFAULT_PIVOT_BLOCK_DISTANCE_BEFORE_CACHING));
  }

  @Test
  public void shouldNotSearchNewPivotBlockWhenCloseToTheHead() {

    when(fastSyncActions.getBestChainHeight()).thenReturn(1000L);

    when(snapSyncState.getPivotBlockNumber()).thenReturn(OptionalLong.of(999));

    DynamicPivotBlockObserver dynamicPivotBlockObserver = mock(DynamicPivotBlockObserver.class);
    dynamicPivotBlockManager.searchPivotBlock();
    verify(dynamicPivotBlockObserver, times(0)).onNewPivotBlock();
  }

  @Test
  public void shouldSearchNewPivotBlockWhenNotCloseToTheHead() {
    final FastSyncState selectPivotBlockState = new FastSyncState(1090);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(1090).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState = new FastSyncState(pivotBlockHeader);
    when(fastSyncActions.selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE))
        .thenReturn(completedFuture(selectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));
    when(fastSyncActions.getBestChainHeight()).thenReturn(1300L);

    when(snapSyncState.getPivotBlockNumber()).thenReturn(OptionalLong.of(939));

    DynamicPivotBlockObserver dynamicPivotBlockObserver = mock(DynamicPivotBlockObserver.class);

    dynamicPivotBlockManager.addObserver(dynamicPivotBlockObserver);
    dynamicPivotBlockManager.searchPivotBlock();

    verify(dynamicPivotBlockObserver, times(1)).onNewPivotBlock();
  }

  @Test
  public void shouldSwitchToNewPivotBlockWhenNeeded() {
    final FastSyncState selectPivotBlockState = new FastSyncState(1060);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(1060).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState = new FastSyncState(pivotBlockHeader);
    when(fastSyncActions.selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE))
        .thenReturn(completedFuture(selectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));

    when(snapSyncState.getPivotBlockNumber()).thenReturn(OptionalLong.of(939));

    DynamicPivotBlockObserver dynamicPivotBlockObserver = mock(DynamicPivotBlockObserver.class);
    dynamicPivotBlockManager.addObserver(dynamicPivotBlockObserver);

    when(fastSyncActions.getBestChainHeight()).thenReturn(1000L);
    dynamicPivotBlockManager.searchPivotBlock();

    verify(dynamicPivotBlockObserver, times(0)).onNewPivotBlock();

    when(fastSyncActions.getBestChainHeight()).thenReturn(1166L);
    dynamicPivotBlockManager.searchPivotBlock();

    verify(dynamicPivotBlockObserver, times(1)).onNewPivotBlock();

    verify(snapSyncState).setCurrentHeader(pivotBlockHeader);
  }
}
