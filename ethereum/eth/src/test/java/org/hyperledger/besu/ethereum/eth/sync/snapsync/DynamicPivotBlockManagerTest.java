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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncActions;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.PivotBlockProposal;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.PivotHolder;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;

import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;

public class DynamicPivotBlockManagerTest {

  private final SnapSyncState snapSyncState = mock(SnapSyncState.class, Answers.RETURNS_DEEP_STUBS);
  private final FastSyncActions fastSyncActions = mock(FastSyncActions.class);
  private final SyncState syncState = mock(SyncState.class);

  private final DynamicPivotBlockManager dynamicPivotBlockManager =
      new DynamicPivotBlockManager(
          fastSyncActions,
          snapSyncState.getFastSyncState(),
          SnapSyncConfiguration.DEFAULT_PIVOT_BLOCK_WINDOW_VALIDITY,
          SnapSyncConfiguration.DEFAULT_PIVOT_BLOCK_DISTANCE_BEFORE_CACHING);

  @Before
  public void setup() {
    when(fastSyncActions.getSyncState()).thenReturn(syncState);
  }

  @Test
  public void shouldNotSearchNewPivotBlockWhenCloseToTheHead() {

    when(syncState.bestChainHeight()).thenReturn(1000L);

    when(snapSyncState.getFastSyncState().getPivotBlockNumber()).thenReturn(999L);
    dynamicPivotBlockManager.check(
        (blockHeader, newBlockFound) -> assertThat(newBlockFound).isFalse());
    verify(fastSyncActions, never()).waitForSuitablePeers();
  }

  @Test
  public void shouldSearchNewPivotBlockWhenNotCloseToTheHead() {

    final CompletableFuture<PivotBlockProposal> COMPLETE =
        completedFuture(PivotBlockProposal.EMPTY_SYNC_STATE);
    final PivotBlockProposal selectPivotBlockState = new PivotBlockProposal(1090);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(1090).buildHeader();
    final PivotHolder downloadPivotBlockHeaderState = new PivotHolder(pivotBlockHeader);
    when(fastSyncActions.waitForSuitablePeers()).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock(PivotBlockProposal.EMPTY_SYNC_STATE))
        .thenReturn(completedFuture(selectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));

    when(syncState.bestChainHeight()).thenReturn(1000L);

    when(snapSyncState.getFastSyncState().getPivotBlockNumber()).thenReturn(939L);
    dynamicPivotBlockManager.check(
        (blockHeader, newBlockFound) -> assertThat(newBlockFound).isFalse());
    verify(fastSyncActions).waitForSuitablePeers();
  }

  @Test
  public void shouldSwitchToNewPivotBlockWhenNeeded() {

    final CompletableFuture<PivotBlockProposal> COMPLETE =
        completedFuture(PivotBlockProposal.EMPTY_SYNC_STATE);
    final PivotBlockProposal selectPivotBlockState = new PivotBlockProposal(1060);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(1060).buildHeader();
    final PivotHolder downloadPivotBlockHeaderState = new PivotHolder(pivotBlockHeader);
    when(fastSyncActions.waitForSuitablePeers()).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock(PivotBlockProposal.EMPTY_SYNC_STATE))
        .thenReturn(completedFuture(selectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));

    when(syncState.bestChainHeight()).thenReturn(1000L);

    when(snapSyncState.getFastSyncState().getPivotBlockNumber()).thenReturn(939L);
    dynamicPivotBlockManager.check(
        (blockHeader, newBlockFound) -> {
          assertThat(blockHeader.getNumber()).isEqualTo(939);
          assertThat(newBlockFound).isFalse();
        });

    when(syncState.bestChainHeight()).thenReturn(1066L);

    dynamicPivotBlockManager.check(
        (blockHeader, newBlockFound) -> {
          assertThat(blockHeader.getNumber()).isEqualTo(pivotBlockHeader.getNumber());
          assertThat(newBlockFound).isTrue();
        });

    verify(snapSyncState.getFastSyncState()).setCurrentHeader(pivotBlockHeader);
    verify(fastSyncActions).waitForSuitablePeers();
  }
}
