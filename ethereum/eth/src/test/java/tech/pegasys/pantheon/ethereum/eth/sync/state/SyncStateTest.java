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
package tech.pegasys.pantheon.ethereum.eth.sync.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.chain.BlockAddedEvent;
import tech.pegasys.pantheon.ethereum.chain.BlockAddedObserver;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.core.Synchronizer.SyncStatusListener;
import tech.pegasys.pantheon.ethereum.eth.manager.ChainState;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeers;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class SyncStateTest {

  private static final long OUR_CHAIN_HEAD_NUMBER = 500;
  private static final long TARGET_SYNC_NUMBER = OUR_CHAIN_HEAD_NUMBER + 100;
  private final Blockchain blockchain = mock(Blockchain.class);
  private final EthPeers ethPeers = mock(EthPeers.class);
  private final SyncState.InSyncListener inSyncListener = mock(SyncState.InSyncListener.class);
  private final EthPeer peer = mock(EthPeer.class);
  private final ChainState peerChainHead = new ChainState();
  private SyncState syncState;
  private BlockAddedObserver blockAddedObserver;

  @Before
  public void setUp() {
    final ArgumentCaptor<BlockAddedObserver> captor =
        ArgumentCaptor.forClass(BlockAddedObserver.class);
    when(blockchain.observeBlockAdded(captor.capture())).thenReturn(1L);
    when(peer.chainState()).thenReturn(peerChainHead);
    when(blockchain.getChainHeadBlockNumber()).thenReturn(OUR_CHAIN_HEAD_NUMBER);
    syncState = new SyncState(blockchain, ethPeers);
    blockAddedObserver = captor.getValue();
    syncState.addInSyncListener(inSyncListener);
  }

  @Test
  public void shouldBeInSyncWhenNoSyncTargetHasBeenSet() {
    assertThat(syncState.isInSync()).isTrue();
  }

  @Test
  public void shouldSwitchToNotInSyncWhenSyncTargetWithBetterChainSet() {
    final BlockHeader bestBlockHeader = targetBlockHeader();
    peerChainHead.update(bestBlockHeader);
    syncState.setSyncTarget(peer, bestBlockHeader);
    assertThat(syncState.isInSync()).isFalse();
    verify(inSyncListener).onSyncStatusChanged(false);
    verifyNoMoreInteractions(inSyncListener);
  }

  @Test
  public void shouldSwitchToInSyncWhenSyncTargetCleared() {
    setupOutOfSyncState();

    syncState.clearSyncTarget();

    verify(inSyncListener).onSyncStatusChanged(true);
    verifyNoMoreInteractions(inSyncListener);
  }

  @Test
  public void shouldBecomeInSyncWhenOurBlockchainCatchesUp() {
    setupOutOfSyncState();

    when(blockchain.getChainHeadBlockNumber()).thenReturn(TARGET_SYNC_NUMBER);
    blockAddedObserver.onBlockAdded(
        BlockAddedEvent.createForHeadAdvancement(
            new Block(
                targetBlockHeader(),
                new BlockBody(Collections.emptyList(), Collections.emptyList()))),
        blockchain);

    assertThat(syncState.isInSync()).isTrue();
    verify(inSyncListener).onSyncStatusChanged(true);
  }

  @Test
  public void shouldSendSyncStatusWhenBlockIsAddedToTheChain() {
    SyncStatusListener syncStatusListener = mock(SyncStatusListener.class);
    syncState.addSyncStatusListener(syncStatusListener);

    blockAddedObserver.onBlockAdded(
        BlockAddedEvent.createForHeadAdvancement(
            new Block(
                targetBlockHeader(),
                new BlockBody(Collections.emptyList(), Collections.emptyList()))),
        blockchain);

    verify(syncStatusListener).onSyncStatus(eq(syncState.syncStatus()));
  }

  private void setupOutOfSyncState() {
    final BlockHeader bestBlockHeader = targetBlockHeader();
    peerChainHead.update(bestBlockHeader);
    syncState.setSyncTarget(peer, bestBlockHeader);
    assertThat(syncState.isInSync()).isFalse();
    verify(inSyncListener).onSyncStatusChanged(false);
  }

  private BlockHeader targetBlockHeader() {
    return new BlockHeaderTestFixture().number(TARGET_SYNC_NUMBER).buildHeader();
  }
}
