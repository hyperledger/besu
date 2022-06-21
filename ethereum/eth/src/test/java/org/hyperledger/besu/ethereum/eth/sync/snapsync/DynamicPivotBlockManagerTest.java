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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncActions;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;

import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.Test;

public class DynamicPivotBlockManagerTest {

  private final SnapSyncState snapSyncState = mock(SnapSyncState.class);
  private final FastSyncActions fastSyncActions = mock(FastSyncActions.class);
  private final SyncState syncState = mock(SyncState.class);

  private final DynamicPivotBlockManager dynamicPivotBlockManager =
      new DynamicPivotBlockManager(
          fastSyncActions,
          snapSyncState,
          SnapSyncConfiguration.DEFAULT_PIVOT_BLOCK_WINDOW_VALIDITY,
          SnapSyncConfiguration.DEFAULT_PIVOT_BLOCK_DISTANCE_BEFORE_CACHING);

  @Before
  public void setup() {
    when(fastSyncActions.getSyncState()).thenReturn(syncState);
  }

  @Test
  public void shouldNotSearchNewPivotBlockWhenCloseToTheHead() {

    when(syncState.bestChainHeight()).thenReturn(1000L);

    when(snapSyncState.getPivotBlockNumber()).thenReturn(OptionalLong.of(999));
    dynamicPivotBlockManager.check(
        (blockHeader, newBlockFound) -> assertThat(newBlockFound).isFalse());
    verify(fastSyncActions, never()).waitForSuitablePeers(any());
  }

  @Test
  public void shouldSearchNewPivotBlockWhenNotCloseToTheHead() {

    final CompletableFuture<FastSyncState> COMPLETE =
        completedFuture(FastSyncState.EMPTY_SYNC_STATE);
    final FastSyncState selectPivotBlockState = new FastSyncState(1090);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(1090).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState = new FastSyncState(pivotBlockHeader);
    when(fastSyncActions.waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE)).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE))
        .thenReturn(completedFuture(selectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));

    when(syncState.bestChainHeight()).thenReturn(1000L);

    when(snapSyncState.getPivotBlockNumber()).thenReturn(OptionalLong.of(939));
    dynamicPivotBlockManager.check(
        (blockHeader, newBlockFound) -> assertThat(newBlockFound).isFalse());
    verify(fastSyncActions).waitForSuitablePeers(any());
  }

  @Test
  public void shouldSwitchToNewPivotBlockWhenNeeded() {

    final CompletableFuture<FastSyncState> COMPLETE =
        completedFuture(FastSyncState.EMPTY_SYNC_STATE);
    final FastSyncState selectPivotBlockState = new FastSyncState(1060);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(1060).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState = new FastSyncState(pivotBlockHeader);
    when(fastSyncActions.waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE)).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE))
        .thenReturn(completedFuture(selectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));

    when(syncState.bestChainHeight()).thenReturn(1000L);

    when(snapSyncState.getPivotBlockNumber()).thenReturn(OptionalLong.of(939));
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

    verify(snapSyncState).setCurrentHeader(pivotBlockHeader);
    verify(fastSyncActions).waitForSuitablePeers(any());
  }

  @Test
  public void shouldSwitchToNewPivotOnlyOnce() {

    final CompletableFuture<FastSyncState> COMPLETE =
        completedFuture(FastSyncState.EMPTY_SYNC_STATE);
    final FastSyncState selectPivotBlockState = new FastSyncState(1060);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(1060).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState = new FastSyncState(pivotBlockHeader);
    when(fastSyncActions.waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE)).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE))
        .thenReturn(completedFuture(selectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));

    when(syncState.bestChainHeight()).thenReturn(1066L);

    dynamicPivotBlockManager.check(
        (blockHeader, newBlockFound) -> {
          assertThat(blockHeader.getNumber()).isEqualTo(pivotBlockHeader.getNumber());
          assertThat(newBlockFound).isTrue();
          dynamicPivotBlockManager.check(
              (blockHeader1, aBoolean) -> {
                assertThat(blockHeader1.getNumber()).isEqualTo(939);
                assertThat(aBoolean).isFalse();
              });
        });
  }
}
