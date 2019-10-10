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
package org.hyperledger.besu.ethereum.eth.sync.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator.BlockOptions;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.InMemoryStorageProvider;
import org.hyperledger.besu.ethereum.core.SyncStatus;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.core.Synchronizer.InSyncListener;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.manager.ChainState;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.plugin.services.BesuEvents.SyncStatusListener;
import org.hyperledger.besu.util.Subscribers.Unsubscriber;
import org.hyperledger.besu.util.uint.UInt256;

import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class SyncStateTest {

  private static final UInt256 standardDifficultyPerBlock = UInt256.of(1L);
  private static final long OUR_CHAIN_HEAD_NUMBER = 20;
  private static final UInt256 OUR_CHAIN_DIFFICULTY =
      standardDifficultyPerBlock.times(OUR_CHAIN_HEAD_NUMBER);
  private static final long TARGET_CHAIN_DELTA = 20;
  private static final long TARGET_CHAIN_HEIGHT = OUR_CHAIN_HEAD_NUMBER + TARGET_CHAIN_DELTA;
  private static final UInt256 TARGET_DIFFICULTY =
      standardDifficultyPerBlock.times(TARGET_CHAIN_HEIGHT);

  private final InSyncListener inSyncListener = mock(InSyncListener.class);
  private final InSyncListener inSyncListenerExact = mock(InSyncListener.class);
  private final SyncStatusListener syncStatusListener = mock(SyncStatusListener.class);

  private EthProtocolManager ethProtocolManager;
  private final BlockDataGenerator gen = new BlockDataGenerator(1);
  private MutableBlockchain blockchain =
      InMemoryStorageProvider.createInMemoryBlockchain(gen.genesisBlock());
  private EthPeers ethPeers;
  private RespondingEthPeer syncTargetPeer;
  private RespondingEthPeer otherPeer;
  private SyncState syncState;

  @Before
  public void setUp() {
    ethProtocolManager =
        EthProtocolManagerTestUtil.create(
            blockchain, InMemoryStorageProvider.createInMemoryWorldStateArchive());
    ethPeers = spy(ethProtocolManager.ethContext().getEthPeers());
    syncTargetPeer = createPeer(TARGET_DIFFICULTY, TARGET_CHAIN_HEIGHT);
    otherPeer = createPeer(UInt256.ZERO, 0);

    advanceLocalChain(OUR_CHAIN_HEAD_NUMBER);

    syncState = new SyncState(blockchain, ethPeers);
    syncState.subscribeInSync(inSyncListener);
    syncState.subscribeInSync(inSyncListenerExact, 0);
    syncState.subscribeSyncStatus(syncStatusListener);
  }

  @Test
  public void isInSync_noPeers() {
    otherPeer.disconnect(DisconnectReason.REQUESTED);
    syncTargetPeer.disconnect(DisconnectReason.REQUESTED);
    syncState.clearSyncTarget();
    assertThat(syncState.isInSync()).isTrue();
  }

  @Test
  public void isInSync_singlePeerWithWorseChainBetterHeight() {
    updateChainState(otherPeer.getEthPeer(), TARGET_CHAIN_HEIGHT, OUR_CHAIN_DIFFICULTY.minus(1L));
    final EthPeer peer = mockWorseChain(otherPeer.getEthPeer());
    doReturn(Optional.of(peer)).when(ethPeers).bestPeerWithHeightEstimate();

    assertThat(syncState.syncTarget()).isEmpty(); // Sanity check
    assertThat(syncState.isInSync()).isTrue();
    assertThat(syncState.isInSync(0)).isTrue();
  }

  @Test
  public void isInSync_singlePeerWithWorseChainWorseHeight() {
    updateChainState(
        otherPeer.getEthPeer(), OUR_CHAIN_HEAD_NUMBER - 1L, OUR_CHAIN_DIFFICULTY.minus(1L));
    final EthPeer peer = mockWorseChain(otherPeer.getEthPeer());
    doReturn(Optional.of(peer)).when(ethPeers).bestPeerWithHeightEstimate();

    assertThat(syncState.syncTarget()).isEmpty(); // Sanity check
    assertThat(syncState.isInSync()).isTrue();
    assertThat(syncState.isInSync(0)).isTrue();
  }

  @Test
  public void isInSync_singlePeerWithBetterChainWorseHeight() {
    updateChainState(otherPeer.getEthPeer(), OUR_CHAIN_HEAD_NUMBER - 1L, TARGET_DIFFICULTY);
    final EthPeer peer = mockBetterChain(otherPeer.getEthPeer());
    doReturn(Optional.of(peer)).when(ethPeers).bestPeerWithHeightEstimate();

    assertThat(syncState.syncTarget()).isEmpty(); // Sanity check
    assertThat(syncState.isInSync()).isTrue();
    assertThat(syncState.isInSync(0)).isTrue();
  }

  @Test
  public void isInSync_singlePeerWithBetterChainBetterHeight() {
    updateChainState(otherPeer.getEthPeer(), TARGET_CHAIN_HEIGHT, TARGET_DIFFICULTY);
    final EthPeer peer = mockBetterChain(otherPeer.getEthPeer());
    doReturn(Optional.of(peer)).when(ethPeers).bestPeerWithHeightEstimate();

    assertThat(syncState.syncTarget()).isEmpty(); // Sanity check
    assertThat(syncState.isInSync()).isFalse();
    assertThat(syncState.isInSync(TARGET_CHAIN_DELTA - 1)).isFalse();
    assertThat(syncState.isInSync(TARGET_CHAIN_DELTA)).isTrue();
    assertThat(syncState.isInSync(TARGET_CHAIN_DELTA + 1)).isTrue();
  }

  @Test
  public void isInSync_syncTargetWithBetterHeight() {
    setupSyncTarget();

    assertThat(syncState.syncTarget()).isPresent(); // Sanity check
    assertThat(syncState.isInSync()).isFalse();
    assertThat(syncState.isInSync(TARGET_CHAIN_DELTA - 1)).isFalse();
    assertThat(syncState.isInSync(TARGET_CHAIN_DELTA)).isTrue();
    assertThat(syncState.isInSync(TARGET_CHAIN_DELTA + 1)).isTrue();
  }

  @Test
  public void isInSync_syncTargetWithWorseHeight() {
    final long heightDifference = 20L;
    advanceLocalChain(TARGET_CHAIN_HEIGHT + heightDifference);
    setupSyncTarget();

    assertThat(syncState.syncTarget()).isPresent(); // Sanity check
    assertThat(syncState.isInSync()).isTrue();
    assertThat(syncState.isInSync(0)).isTrue();
  }

  @Test
  public void isInSync_outOfSyncWithTargetAndOutOfSyncWithBestPeer() {
    setupSyncTarget();
    updateChainState(otherPeer.getEthPeer(), TARGET_CHAIN_HEIGHT, TARGET_DIFFICULTY);
    doReturn(Optional.of(otherPeer.getEthPeer())).when(ethPeers).bestPeerWithHeightEstimate();

    assertThat(syncState.isInSync()).isFalse();
    assertThat(syncState.isInSync(0)).isFalse();
    assertThat(syncState.isInSync(TARGET_CHAIN_DELTA - 1)).isFalse();
    assertThat(syncState.isInSync(TARGET_CHAIN_DELTA)).isTrue();
    assertThat(syncState.isInSync(TARGET_CHAIN_DELTA + 1)).isTrue();
  }

  @Test
  public void isInSync_inSyncWithTargetOutOfSyncWithBestPeer() {
    setupSyncTarget();
    advanceLocalChain(TARGET_CHAIN_HEIGHT);
    final long heightDifference = 20L;
    updateChainState(
        otherPeer.getEthPeer(),
        TARGET_CHAIN_HEIGHT + heightDifference,
        TARGET_DIFFICULTY.plus(heightDifference));
    doReturn(Optional.of(otherPeer.getEthPeer())).when(ethPeers).bestPeerWithHeightEstimate();

    assertThat(syncState.isInSync()).isFalse();
    assertThat(syncState.isInSync(0)).isFalse();
    assertThat(syncState.isInSync(heightDifference - 1)).isFalse();
    assertThat(syncState.isInSync(heightDifference)).isTrue();
    assertThat(syncState.isInSync(heightDifference + 1)).isTrue();
  }

  @Test
  public void isInSync_inSyncWithTargetInSyncWithBestPeer() {
    setupSyncTarget();
    advanceLocalChain(TARGET_CHAIN_HEIGHT);
    updateChainState(otherPeer.getEthPeer(), TARGET_CHAIN_HEIGHT, TARGET_DIFFICULTY);
    doReturn(Optional.of(otherPeer.getEthPeer())).when(ethPeers).bestPeerWithHeightEstimate();

    assertThat(syncState.isInSync()).isTrue();
    assertThat(syncState.isInSync(0)).isTrue();
  }

  @Test
  public void shouldSwitchToInSyncWhenNoBetterPeersAreAvailable() {
    setupOutOfSyncState();

    otherPeer.disconnect(DisconnectReason.REQUESTED);
    syncTargetPeer.disconnect(DisconnectReason.REQUESTED);
    syncState.clearSyncTarget();

    verify(inSyncListener).onSyncStatusChanged(true);
    verify(inSyncListenerExact).onSyncStatusChanged(true);
    verifyNoMoreInteractions(inSyncListener);
    verifyNoMoreInteractions(inSyncListenerExact);
  }

  @Test
  public void shouldBecomeInSyncWhenOurBlockchainCatchesUp() {
    setupOutOfSyncState();

    // Update to just within the default sync threshold
    advanceLocalChain(TARGET_CHAIN_HEIGHT - Synchronizer.DEFAULT_IN_SYNC_TOLERANCE);
    // We should register as in-sync with default tolerance, out-of-sync with exact tolerance
    assertThat(syncState.isInSync()).isTrue();
    assertThat(syncState.isInSync(0)).isFalse();
    verify(inSyncListener, times(1)).onSyncStatusChanged(true);
    verify(inSyncListenerExact, never()).onSyncStatusChanged(true);

    // Advance one more block
    advanceLocalChain(TARGET_CHAIN_HEIGHT - Synchronizer.DEFAULT_IN_SYNC_TOLERANCE + 1);
    // We should register as in-sync with default tolerance, out-of-sync with exact tolerance
    assertThat(syncState.isInSync()).isTrue();
    assertThat(syncState.isInSync(0)).isFalse();
    verifyNoMoreInteractions(inSyncListener);
    verify(inSyncListenerExact, never()).onSyncStatusChanged(true);

    // Catch all the way up
    advanceLocalChain(TARGET_CHAIN_HEIGHT);
    // We should register as in-sync
    assertThat(syncState.isInSync()).isTrue();
    assertThat(syncState.isInSync(0)).isTrue();
    verifyNoMoreInteractions(inSyncListener);
    verify(inSyncListenerExact, times(1)).onSyncStatusChanged(true);
  }

  @Test
  public void addInSyncListener_whileOutOfSync() {
    setupOutOfSyncState();

    // Add listener
    InSyncListener newListener = mock(InSyncListener.class);
    syncState.subscribeInSync(newListener);
    verify(newListener, times(1)).onSyncStatusChanged(false);
    verify(newListener, never()).onSyncStatusChanged(true);

    // Catch all the way up
    advanceLocalChain(TARGET_CHAIN_HEIGHT);

    // We should register as in-sync
    verify(newListener, times(1)).onSyncStatusChanged(true);
    verify(newListener, times(1)).onSyncStatusChanged(false);

    // Fall out of sync
    updateChainState(
        syncTargetPeer.getEthPeer(),
        TARGET_CHAIN_HEIGHT + Synchronizer.DEFAULT_IN_SYNC_TOLERANCE + 1L,
        TARGET_DIFFICULTY.plus(10L));
    verify(newListener, times(2)).onSyncStatusChanged(false);
    verify(newListener, times(1)).onSyncStatusChanged(true);
  }

  @Test
  public void addInSyncListener_whileOutOfSync_withDistinctSyncTolerance() {
    setupOutOfSyncState();

    // Add listener
    final long syncTolerance = Synchronizer.DEFAULT_IN_SYNC_TOLERANCE * 2;
    InSyncListener newListener = mock(InSyncListener.class);
    syncState.subscribeInSync(newListener, syncTolerance);
    verify(newListener, times(1)).onSyncStatusChanged(false);
    verify(newListener, never()).onSyncStatusChanged(true);

    // Catch all the way up
    advanceLocalChain(TARGET_CHAIN_HEIGHT);

    // We should register as in-sync
    verify(newListener, times(1)).onSyncStatusChanged(true);
    verify(newListener, times(1)).onSyncStatusChanged(false);

    // Fall out of sync
    updateChainState(
        syncTargetPeer.getEthPeer(),
        TARGET_CHAIN_HEIGHT + syncTolerance + 1L,
        TARGET_DIFFICULTY.plus(10L));
    verify(newListener, times(2)).onSyncStatusChanged(false);
    verify(newListener, times(1)).onSyncStatusChanged(true);
  }

  @Test
  public void addInSyncListener_whileInSync() {
    setupOutOfSyncState();
    // Catch all the way up
    advanceLocalChain(TARGET_CHAIN_HEIGHT);

    // Add listener
    InSyncListener newListener = mock(InSyncListener.class);
    syncState.subscribeInSync(newListener);
    verify(newListener, never()).onSyncStatusChanged(anyBoolean());

    // Fall out of sync
    updateChainState(
        syncTargetPeer.getEthPeer(),
        TARGET_CHAIN_HEIGHT + Synchronizer.DEFAULT_IN_SYNC_TOLERANCE + 1L,
        TARGET_DIFFICULTY.plus(10L));
    verify(newListener, times(1)).onSyncStatusChanged(false);
    verify(newListener, never()).onSyncStatusChanged(true);

    // Catch up
    advanceLocalChain(TARGET_CHAIN_HEIGHT + 1L);
    verify(newListener, times(1)).onSyncStatusChanged(false);
    verify(newListener, times(1)).onSyncStatusChanged(true);
  }

  @Test
  public void addInSyncListener_whileInSync_withDistinctSyncTolerance() {
    final long syncTolerance = Synchronizer.DEFAULT_IN_SYNC_TOLERANCE * 2;
    setupOutOfSyncState();

    // Catch all the way up
    advanceLocalChain(TARGET_CHAIN_HEIGHT);

    // Add listener
    InSyncListener newListener = mock(InSyncListener.class);
    syncState.subscribeInSync(newListener, syncTolerance);
    verify(newListener, never()).onSyncStatusChanged(anyBoolean());

    // Fall out of sync
    updateChainState(
        syncTargetPeer.getEthPeer(),
        TARGET_CHAIN_HEIGHT + syncTolerance + 1L,
        TARGET_DIFFICULTY.plus(10L));
    verify(newListener, times(1)).onSyncStatusChanged(false);
    verify(newListener, never()).onSyncStatusChanged(true);

    // Catch up
    advanceLocalChain(TARGET_CHAIN_HEIGHT + 1L);
    verify(newListener, times(1)).onSyncStatusChanged(false);
    verify(newListener, times(1)).onSyncStatusChanged(true);
  }

  @Test
  public void removeInSyncListener_doesntReceiveSubsequentEvents() {
    final long syncTolerance = Synchronizer.DEFAULT_IN_SYNC_TOLERANCE + 1L;
    setupOutOfSyncState();

    // Add listener
    InSyncListener newListener = mock(InSyncListener.class);
    final Unsubscriber unsubscriber = syncState.subscribeInSync(newListener, syncTolerance);
    verify(newListener).onSyncStatusChanged(false);
    verify(newListener, never()).onSyncStatusChanged(true);

    // Remove listener
    unsubscriber.unsubscribe();

    // Catch all the way up
    advanceLocalChain(TARGET_CHAIN_HEIGHT);

    // We should not register the in-sync event
    verify(newListener, never()).onSyncStatusChanged(true);
    verify(newListener, times(1)).onSyncStatusChanged(false);

    // Fall out of sync
    updateChainState(
        syncTargetPeer.getEthPeer(),
        TARGET_CHAIN_HEIGHT + syncTolerance + 1L,
        TARGET_DIFFICULTY.plus(10L));

    // We should not register the in-sync event
    verify(newListener, never()).onSyncStatusChanged(true);
    verify(newListener, times(1)).onSyncStatusChanged(false);

    // Other listeners should keep running
    verify(inSyncListenerExact, times(2)).onSyncStatusChanged(false);
    verify(inSyncListenerExact, times(1)).onSyncStatusChanged(true);
  }

  @Test
  public void removeInSyncListener_addAdditionalListenerBeforeRemoving() {
    final long syncTolerance = Synchronizer.DEFAULT_IN_SYNC_TOLERANCE + 1L;
    setupOutOfSyncState();

    // Add listener
    InSyncListener listenerToRemove = mock(InSyncListener.class);
    InSyncListener otherListener = mock(InSyncListener.class);
    final Unsubscriber unsubscriber = syncState.subscribeInSync(listenerToRemove, syncTolerance);
    syncState.subscribeInSync(otherListener, syncTolerance);
    // Events should be received
    verify(listenerToRemove).onSyncStatusChanged(false);
    verify(listenerToRemove, never()).onSyncStatusChanged(true);
    verify(otherListener).onSyncStatusChanged(false);
    verify(otherListener, never()).onSyncStatusChanged(true);

    // Remove listener
    unsubscriber.unsubscribe();

    // Catch all the way up
    advanceLocalChain(TARGET_CHAIN_HEIGHT);

    // We should not register the in-sync event
    verify(listenerToRemove, never()).onSyncStatusChanged(true);
    verify(listenerToRemove, times(1)).onSyncStatusChanged(false);
    // Other listener should receive events
    verify(otherListener, times(1)).onSyncStatusChanged(true);
    verify(otherListener, times(1)).onSyncStatusChanged(false);

    // Fall out of sync
    updateChainState(
        syncTargetPeer.getEthPeer(),
        TARGET_CHAIN_HEIGHT + syncTolerance + 1L,
        TARGET_DIFFICULTY.plus(10L));

    // We should not register the in-sync event
    verify(listenerToRemove, never()).onSyncStatusChanged(true);
    verify(listenerToRemove, times(1)).onSyncStatusChanged(false);
    // Other listener should receive events
    verify(otherListener, times(1)).onSyncStatusChanged(true);
    verify(otherListener, times(2)).onSyncStatusChanged(false);

    // Other listeners should keep running
    verify(inSyncListenerExact, times(1)).onSyncStatusChanged(true);
    verify(inSyncListenerExact, times(2)).onSyncStatusChanged(false);
  }

  @Test
  public void removeInSyncListener_addAdditionalListenerAfterRemoving() {
    final long syncTolerance = Synchronizer.DEFAULT_IN_SYNC_TOLERANCE + 1L;
    setupOutOfSyncState();

    // Add listener
    InSyncListener listenerToRemove = mock(InSyncListener.class);
    InSyncListener otherListener = mock(InSyncListener.class);
    final Unsubscriber unsubscriber = syncState.subscribeInSync(listenerToRemove, syncTolerance);
    // Events should be received
    verify(listenerToRemove).onSyncStatusChanged(false);
    verify(listenerToRemove, never()).onSyncStatusChanged(true);

    // Remove listener
    unsubscriber.unsubscribe();

    // Add new listener
    syncState.subscribeInSync(otherListener, syncTolerance);
    verify(otherListener).onSyncStatusChanged(false);
    verify(otherListener, never()).onSyncStatusChanged(true);

    // Catch all the way up
    advanceLocalChain(TARGET_CHAIN_HEIGHT);

    // We should not register the in-sync event
    verify(listenerToRemove, never()).onSyncStatusChanged(true);
    verify(listenerToRemove, times(1)).onSyncStatusChanged(false);
    // Other listener should receive events
    verify(otherListener, times(1)).onSyncStatusChanged(true);
    verify(otherListener, times(1)).onSyncStatusChanged(false);

    // Fall out of sync
    updateChainState(
        syncTargetPeer.getEthPeer(),
        TARGET_CHAIN_HEIGHT + syncTolerance + 1L,
        TARGET_DIFFICULTY.plus(10L));

    // We should not register the in-sync event
    verify(listenerToRemove, never()).onSyncStatusChanged(true);
    verify(listenerToRemove, times(1)).onSyncStatusChanged(false);
    // Other listener should receive events
    verify(otherListener, times(1)).onSyncStatusChanged(true);
    verify(otherListener, times(2)).onSyncStatusChanged(false);

    // Other listeners should keep running
    verify(inSyncListenerExact, times(2)).onSyncStatusChanged(false);
    verify(inSyncListenerExact, times(1)).onSyncStatusChanged(true);
  }

  @Test
  public void shouldSendSyncStatusWhenBlockIsAddedToTheChain() {
    final SyncStatusListener syncStatusListener = mock(SyncStatusListener.class);
    syncState.subscribeSyncStatus(syncStatusListener);

    advanceLocalChain(OUR_CHAIN_HEAD_NUMBER + 1L);

    verify(syncStatusListener).onSyncStatusChanged(eq(syncState.syncStatus()));
  }

  @Test
  public void shouldHandleSyncThenReorg() {
    // Sync up to the target
    final int expectedSyncEvents = (int) TARGET_CHAIN_DELTA;
    advanceLocalChain(TARGET_CHAIN_HEIGHT);
    // Perform a shallow reorg
    final int expectedReorgEvents = 2;
    reorgLocalChain(TARGET_CHAIN_HEIGHT - 1, TARGET_CHAIN_HEIGHT, UInt256.of(2L));

    assertThat(syncState.isInSync()).isTrue();
    final ArgumentCaptor<SyncStatus> captor = ArgumentCaptor.forClass(SyncStatus.class);
    verify(syncStatusListener, times(expectedSyncEvents + expectedReorgEvents))
        .onSyncStatusChanged(captor.capture());

    final List<SyncStatus> eventValues = captor.getAllValues();

    // Check the initial set of events corresponding to block advancement while we're out of sync
    for (int i = 0; i < eventValues.size(); i++) {

      final SyncStatus syncStatus = eventValues.get(i);
      if (i == eventValues.size() - 1) {
        // Last event should be the in-sync reorg event
        assertThat(syncStatus.inSync()).isTrue();
        assertThat(syncStatus.getCurrentBlock()).isEqualTo(TARGET_CHAIN_HEIGHT);
        // TODO - finalize the start, current, and highest block values should be
      } else if (i == eventValues.size() - 2) {
        // Second-to-last event should be the in-sync reorg event
        assertThat(syncStatus.inSync()).isFalse();
        assertThat(syncStatus.getCurrentBlock()).isEqualTo(TARGET_CHAIN_HEIGHT);
        // TODO - finalize the start, current, and highest block values should be
      } else if (i == eventValues.size() - 3) {
        // Third-to-last event should be the event when the node finally reaches sync
        assertThat(syncStatus.inSync()).isTrue();
        assertThat(syncStatus.getCurrentBlock()).isEqualTo(TARGET_CHAIN_HEIGHT);
        assertThat(syncStatus.getHighestBlock()).isEqualTo(TARGET_CHAIN_HEIGHT);
        // TODO - verify desired startingBlock value
      } else {
        // All previous events should correspond to the initial sync
        assertThat(syncStatus.inSync()).isFalse();
        assertThat(syncStatus.getCurrentBlock()).isEqualTo(OUR_CHAIN_HEAD_NUMBER + i + 1);
        assertThat(syncStatus.getHighestBlock()).isEqualTo(TARGET_CHAIN_HEIGHT);
        // TODO - verify desired startingBlock value
      }
    }
  }

  private RespondingEthPeer createPeer(final UInt256 totalDifficulty, final long blockHeight) {
    return EthProtocolManagerTestUtil.createPeer(ethProtocolManager, totalDifficulty, blockHeight);
  }

  private EthPeer mockWorseChain(final EthPeer peer) {
    return mockChainIsBetterThan(peer, false);
  }

  private EthPeer mockBetterChain(final EthPeer peer) {
    return mockChainIsBetterThan(peer, true);
  }

  private EthPeer mockChainIsBetterThan(final EthPeer peer, final boolean isBetter) {
    final ChainState chainState = spy(peer.chainState());
    doReturn(isBetter).when(chainState).chainIsBetterThan(any());
    final EthPeer mockedPeer = spy(peer);
    doReturn(chainState).when(mockedPeer).chainState();
    return mockedPeer;
  }

  private void setupSyncTarget() {
    syncState.setSyncTarget(syncTargetPeer.getEthPeer(), blockchain.getGenesisBlock().getHeader());
  }

  private void setupOutOfSyncState() {
    setupSyncTarget();
    assertThat(syncState.isInSync()).isFalse();
    verify(inSyncListener).onSyncStatusChanged(false);
    verify(inSyncListenerExact).onSyncStatusChanged(false);
  }

  private void advanceLocalChain(final long newChainHeight) {
    while (blockchain.getChainHeadBlockNumber() < newChainHeight) {
      final BlockHeader parent = blockchain.getChainHeadHeader();
      final Block block =
          gen.block(
              BlockOptions.create()
                  .setDifficulty(standardDifficultyPerBlock)
                  .setParentHash(parent.getHash())
                  .setBlockNumber(parent.getNumber() + 1L));
      final List<TransactionReceipt> receipts = gen.receipts(block);
      blockchain.appendBlock(block, receipts);
    }
  }

  private void reorgLocalChain(
      final long commonAncestor, final long newHeight, final UInt256 difficultyPerBlock) {
    BlockHeader currentBlock = blockchain.getBlockHeader(commonAncestor).get();
    while (currentBlock.getNumber() < newHeight) {
      final Block block =
          gen.block(
              BlockOptions.create()
                  .setDifficulty(difficultyPerBlock)
                  .setParentHash(currentBlock.getHash())
                  .setBlockNumber(currentBlock.getNumber() + 1L));
      final List<TransactionReceipt> receipts = gen.receipts(block);
      blockchain.appendBlock(block, receipts);
      currentBlock = block.getHeader();
    }
  }

  /**
   * Updates the chain state, such that the peer will end up with an estimated height of {@code
   * blockHeight} and an estimated total difficulty of {@code totalDifficulty}
   *
   * @param peer The peer whose chain should be updated
   * @param blockHeight The target estimated block height
   * @param totalDifficulty The total difficulty
   */
  private void updateChainState(
      final EthPeer peer, final long blockHeight, final UInt256 totalDifficulty) {
    // Chain state is updated based on the parent of the announced block
    // So, increment block number by 1 and set block difficulty to zero
    // in order to update to the values we want
    final BlockHeader header =
        new BlockHeaderTestFixture()
            .number(blockHeight + 1L)
            .difficulty(UInt256.ZERO)
            .buildHeader();
    peer.chainState().updateForAnnouncedBlock(header, totalDifficulty);

    // Sanity check this logic still holds
    assertThat(peer.chainState().getEstimatedHeight()).isEqualTo(blockHeight);
    assertThat(peer.chainState().getEstimatedTotalDifficulty()).isEqualTo(totalDifficulty);
  }
}
