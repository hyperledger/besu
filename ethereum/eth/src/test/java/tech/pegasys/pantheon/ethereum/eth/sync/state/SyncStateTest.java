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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.chain.BlockAddedEvent;
import tech.pegasys.pantheon.ethereum.chain.BlockAddedObserver;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.chain.ChainHead;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.eth.manager.ChainState;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeers;
import tech.pegasys.pantheon.plugin.services.PantheonEvents.SyncStatusListener;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.Collections;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class SyncStateTest {

  private static final long OUR_CHAIN_HEAD_NUMBER = 500;
  private static final UInt256 OUR_CHAIN_DIFFICULTY = UInt256.of(500);
  private static final long TARGET_CHAIN_DELTA = 100;
  private static final long TARGET_CHAIN_HEIGHT = OUR_CHAIN_HEAD_NUMBER + TARGET_CHAIN_DELTA;
  private static final UInt256 TARGET_DIFFICULTY = OUR_CHAIN_DIFFICULTY.plus(TARGET_CHAIN_DELTA);

  private final Blockchain blockchain = mock(Blockchain.class);
  private final EthPeers ethPeers = mock(EthPeers.class);
  private final SyncState.InSyncListener inSyncListener = mock(SyncState.InSyncListener.class);
  private final EthPeer syncTargetPeer = mock(EthPeer.class);
  private final ChainState syncTargetPeerChainState = spy(new ChainState());
  private final EthPeer otherPeer = mock(EthPeer.class);
  private final ChainState otherPeerChainState = spy(new ChainState());
  private SyncState syncState;
  private BlockAddedObserver blockAddedObserver;

  @Before
  public void setUp() {
    final ArgumentCaptor<BlockAddedObserver> captor =
        ArgumentCaptor.forClass(BlockAddedObserver.class);

    final ChainHead ourChainHead =
        new ChainHead(Hash.ZERO, OUR_CHAIN_DIFFICULTY, OUR_CHAIN_HEAD_NUMBER);

    when(blockchain.observeBlockAdded(captor.capture())).thenReturn(1L);
    when(blockchain.getChainHeadBlockNumber()).thenReturn(OUR_CHAIN_HEAD_NUMBER);
    when(blockchain.getChainHead()).thenReturn(ourChainHead);
    when(syncTargetPeer.chainState()).thenReturn(syncTargetPeerChainState);
    when(otherPeer.chainState()).thenReturn(otherPeerChainState);
    syncState = new SyncState(blockchain, ethPeers);
    blockAddedObserver = captor.getValue();
    syncState.addInSyncListener(inSyncListener);
  }

  @Test
  public void isInSync_noPeers() {
    assertThat(syncState.isInSync()).isTrue();
  }

  @Test
  public void isInSync_singlePeerWithWorseChainBetterHeight() {
    updateChainState(otherPeerChainState, TARGET_CHAIN_HEIGHT, OUR_CHAIN_DIFFICULTY.minus(1L));
    when(ethPeers.bestPeerWithHeightEstimate()).thenReturn(Optional.of(otherPeer));
    doReturn(false).when(otherPeerChainState).chainIsBetterThan(any());

    assertThat(syncState.syncTarget()).isEmpty(); // Sanity check
    assertThat(syncState.isInSync()).isTrue();
    assertThat(syncState.isInSync(0)).isTrue();
  }

  @Test
  public void isInSync_singlePeerWithWorseChainWorseHeight() {
    updateChainState(
        otherPeerChainState, OUR_CHAIN_HEAD_NUMBER - 1L, OUR_CHAIN_DIFFICULTY.minus(1L));
    when(ethPeers.bestPeerWithHeightEstimate()).thenReturn(Optional.of(otherPeer));
    doReturn(false).when(otherPeerChainState).chainIsBetterThan(any());

    assertThat(syncState.syncTarget()).isEmpty(); // Sanity check
    assertThat(syncState.isInSync()).isTrue();
    assertThat(syncState.isInSync(0)).isTrue();
  }

  @Test
  public void isInSync_singlePeerWithBetterChainWorseHeight() {
    updateChainState(otherPeerChainState, OUR_CHAIN_HEAD_NUMBER - 1L, TARGET_DIFFICULTY);
    when(ethPeers.bestPeerWithHeightEstimate()).thenReturn(Optional.of(otherPeer));
    doReturn(true).when(otherPeerChainState).chainIsBetterThan(any());

    assertThat(syncState.syncTarget()).isEmpty(); // Sanity check
    assertThat(syncState.isInSync()).isTrue();
    assertThat(syncState.isInSync(0)).isTrue();
  }

  @Test
  public void isInSync_singlePeerWithBetterChainBetterHeight() {
    updateChainState(otherPeerChainState, TARGET_CHAIN_HEIGHT, TARGET_DIFFICULTY);
    when(ethPeers.bestPeerWithHeightEstimate()).thenReturn(Optional.of(otherPeer));
    doReturn(true).when(otherPeerChainState).chainIsBetterThan(any());

    assertThat(syncState.syncTarget()).isEmpty(); // Sanity check
    assertThat(syncState.isInSync()).isFalse();
    assertThat(syncState.isInSync(TARGET_CHAIN_DELTA - 1)).isFalse();
    assertThat(syncState.isInSync(TARGET_CHAIN_DELTA)).isTrue();
    assertThat(syncState.isInSync(TARGET_CHAIN_DELTA + 1)).isTrue();
  }

  @Test
  public void isInSync_syncTargetWithBetterHeight() {
    updateChainState(syncTargetPeerChainState, TARGET_CHAIN_HEIGHT, TARGET_DIFFICULTY);
    syncState.setSyncTarget(syncTargetPeer, blockHeaderAt(0L));

    assertThat(syncState.syncTarget()).isPresent(); // Sanity check
    assertThat(syncState.isInSync()).isFalse();
    assertThat(syncState.isInSync(TARGET_CHAIN_DELTA - 1)).isFalse();
    assertThat(syncState.isInSync(TARGET_CHAIN_DELTA)).isTrue();
    assertThat(syncState.isInSync(TARGET_CHAIN_DELTA + 1)).isTrue();
  }

  @Test
  public void isInSync_syncTargetWithWorseHeight() {
    updateChainState(syncTargetPeerChainState, OUR_CHAIN_HEAD_NUMBER - 1L, TARGET_DIFFICULTY);
    syncState.setSyncTarget(syncTargetPeer, blockHeaderAt(0L));

    assertThat(syncState.syncTarget()).isPresent(); // Sanity check
    assertThat(syncState.isInSync()).isTrue();
    assertThat(syncState.isInSync(0)).isTrue();
  }

  @Test
  public void isInSync_outOfSyncWithTargetAndOutOfSyncWithBestPeer() {
    updateChainState(syncTargetPeerChainState, TARGET_CHAIN_HEIGHT, TARGET_DIFFICULTY);
    syncState.setSyncTarget(syncTargetPeer, blockHeaderAt(0L));
    updateChainState(otherPeerChainState, TARGET_CHAIN_HEIGHT, TARGET_DIFFICULTY);
    when(ethPeers.bestPeerWithHeightEstimate()).thenReturn(Optional.of(otherPeer));
    doReturn(true).when(otherPeerChainState).chainIsBetterThan(any());

    assertThat(syncState.isInSync()).isFalse();
    assertThat(syncState.isInSync(0)).isFalse();
    assertThat(syncState.isInSync(TARGET_CHAIN_DELTA - 1)).isFalse();
    assertThat(syncState.isInSync(TARGET_CHAIN_DELTA)).isTrue();
    assertThat(syncState.isInSync(TARGET_CHAIN_DELTA + 1)).isTrue();
  }

  @Test
  public void isInSync_inSyncWithTargetOutOfSyncWithBestPeer() {
    updateChainState(
        syncTargetPeerChainState, OUR_CHAIN_HEAD_NUMBER - 1L, OUR_CHAIN_DIFFICULTY.minus(1L));
    syncState.setSyncTarget(syncTargetPeer, blockHeaderAt(0L));
    updateChainState(otherPeerChainState, TARGET_CHAIN_HEIGHT, TARGET_DIFFICULTY);
    when(ethPeers.bestPeerWithHeightEstimate()).thenReturn(Optional.of(otherPeer));
    doReturn(true).when(otherPeerChainState).chainIsBetterThan(any());

    assertThat(syncState.isInSync()).isFalse();
    assertThat(syncState.isInSync(0)).isFalse();
    assertThat(syncState.isInSync(TARGET_CHAIN_DELTA - 1)).isFalse();
    assertThat(syncState.isInSync(TARGET_CHAIN_DELTA)).isTrue();
    assertThat(syncState.isInSync(TARGET_CHAIN_DELTA + 1)).isTrue();
  }

  @Test
  public void isInSync_inSyncWithTargetInSyncWithBestPeer() {
    updateChainState(
        syncTargetPeerChainState, OUR_CHAIN_HEAD_NUMBER - 1L, OUR_CHAIN_DIFFICULTY.minus(1L));
    syncState.setSyncTarget(syncTargetPeer, blockHeaderAt(0L));
    updateChainState(
        otherPeerChainState, OUR_CHAIN_HEAD_NUMBER - 1L, OUR_CHAIN_DIFFICULTY.minus(1L));
    when(ethPeers.bestPeerWithHeightEstimate()).thenReturn(Optional.of(otherPeer));
    doReturn(false).when(otherPeerChainState).chainIsBetterThan(any());

    assertThat(syncState.isInSync()).isTrue();
    assertThat(syncState.isInSync(0)).isTrue();
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

    when(blockchain.getChainHeadBlockNumber()).thenReturn(TARGET_CHAIN_HEIGHT);
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

    verify(syncStatusListener).onSyncStatusChanged(eq(syncState.syncStatus()));
  }

  private void setupOutOfSyncState() {
    updateChainState(syncTargetPeerChainState, TARGET_CHAIN_HEIGHT, TARGET_DIFFICULTY);
    syncState.setSyncTarget(syncTargetPeer, blockHeaderAt(0L));
    assertThat(syncState.isInSync()).isFalse();
    verify(inSyncListener).onSyncStatusChanged(false);
  }

  /**
   * Updates the chain state, such that the peer will end up with an estimated height of {@code
   * blockHeight} and an estimated total difficulty of {@code totalDifficulty}
   *
   * @param chainState The chain state to update
   * @param blockHeight The target estimated block height
   * @param totalDifficulty The total difficulty
   */
  private void updateChainState(
      final ChainState chainState, final long blockHeight, final UInt256 totalDifficulty) {
    // Chain state is updated based on the parent of the announced block
    // So, increment block number by 1 and set block difficulty to zero
    // in order to update to the values we want
    final BlockHeader header =
        new BlockHeaderTestFixture()
            .number(blockHeight + 1L)
            .difficulty(UInt256.ZERO)
            .buildHeader();
    chainState.updateForAnnouncedBlock(header, totalDifficulty);

    // Sanity check this logic still holds
    assertThat(chainState.getEstimatedHeight()).isEqualTo(blockHeight);
    assertThat(chainState.getEstimatedTotalDifficulty()).isEqualTo(totalDifficulty);
  }

  private BlockHeader targetBlockHeader() {
    return blockHeaderAt(TARGET_CHAIN_HEIGHT);
  }

  private BlockHeader blockHeaderAt(final long blockNumber) {
    return new BlockHeaderTestFixture().number(blockNumber).buildHeader();
  }
}
