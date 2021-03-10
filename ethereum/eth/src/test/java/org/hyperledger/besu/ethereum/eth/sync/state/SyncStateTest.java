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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
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
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.core.Synchronizer.InSyncListener;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.manager.ChainHeadEstimate;
import org.hyperledger.besu.ethereum.eth.manager.ChainState;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.plugin.data.SyncStatus;
import org.hyperledger.besu.plugin.services.BesuEvents.SyncStatusListener;

import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SyncStateTest {

  private static final Difficulty standardDifficultyPerBlock = Difficulty.ONE;
  private static final long OUR_CHAIN_HEAD_NUMBER = 20;
  private static final Difficulty OUR_CHAIN_DIFFICULTY =
      standardDifficultyPerBlock.multiply(OUR_CHAIN_HEAD_NUMBER);
  private static final long TARGET_CHAIN_DELTA = 20;
  private static final long TARGET_CHAIN_HEIGHT = OUR_CHAIN_HEAD_NUMBER + TARGET_CHAIN_DELTA;
  private static final Difficulty TARGET_DIFFICULTY =
      standardDifficultyPerBlock.multiply(TARGET_CHAIN_HEIGHT);

  private final InSyncListener inSyncListener = mock(InSyncListener.class);
  private final InSyncListener inSyncListenerExact = mock(InSyncListener.class);
  private final SyncStatusListener syncStatusListener = mock(SyncStatusListener.class);

  private final BlockDataGenerator gen = new BlockDataGenerator(1);
  private final Block genesisBlock =
      gen.genesisBlock(new BlockOptions().setDifficulty(Difficulty.ZERO));
  private final MutableBlockchain blockchain =
      InMemoryKeyValueStorageProvider.createInMemoryBlockchain(genesisBlock);

  @Captor ArgumentCaptor<Optional<SyncStatus>> syncStatusCaptor;

  private EthProtocolManager ethProtocolManager;
  private EthPeers ethPeers;
  private RespondingEthPeer syncTargetPeer;
  private RespondingEthPeer otherPeer;
  private SyncState syncState;

  @Before
  public void setUp() {
    ethProtocolManager = EthProtocolManagerTestUtil.create(blockchain);
    ethPeers = spy(ethProtocolManager.ethContext().getEthPeers());
    syncTargetPeer = createPeer(TARGET_DIFFICULTY, TARGET_CHAIN_HEIGHT);
    otherPeer = createPeer(Difficulty.ZERO, 0);

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
    updateChainState(
        otherPeer.getEthPeer(), TARGET_CHAIN_HEIGHT, OUR_CHAIN_DIFFICULTY.subtract(1L));
    final EthPeer peer = mockWorseChain(otherPeer.getEthPeer());
    doReturn(Optional.of(peer)).when(ethPeers).bestPeerWithHeightEstimate();

    assertThat(syncState.syncTarget()).isEmpty(); // Sanity check
    assertThat(syncState.isInSync()).isTrue();
    assertThat(syncState.isInSync(0)).isTrue();
  }

  @Test
  public void isInSync_singlePeerWithWorseChainWorseHeight() {
    updateChainState(
        otherPeer.getEthPeer(), OUR_CHAIN_HEAD_NUMBER - 1L, OUR_CHAIN_DIFFICULTY.subtract(1L));
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
    otherPeer.disconnect(DisconnectReason.REQUESTED);
    setupOutOfSyncState();

    assertThat(syncState.syncTarget()).isPresent(); // Sanity check
    assertThat(syncState.isInSync()).isFalse();
    assertThat(syncState.isInSync(TARGET_CHAIN_DELTA - 1)).isFalse();
    assertThat(syncState.isInSync(TARGET_CHAIN_DELTA)).isTrue();
    assertThat(syncState.isInSync(TARGET_CHAIN_DELTA + 1)).isTrue();
  }

  @Test
  public void isInSync_syncTargetWithWorseHeight() {
    otherPeer.disconnect(DisconnectReason.REQUESTED);
    final long heightDifference = 20L;
    advanceLocalChain(TARGET_CHAIN_HEIGHT + heightDifference);
    setupOutOfSyncState();

    assertThat(syncState.syncTarget()).isPresent(); // Sanity check
    assertThat(syncState.isInSync()).isTrue();
    assertThat(syncState.isInSync(0)).isTrue();
  }

  @Test
  public void isInSync_outOfSyncWithTargetAndOutOfSyncWithBestPeer() {
    setupOutOfSyncState();
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
    setupOutOfSyncState();
    advanceLocalChain(TARGET_CHAIN_HEIGHT);
    final long heightDifference = 20L;
    updateChainState(
        otherPeer.getEthPeer(),
        TARGET_CHAIN_HEIGHT + heightDifference,
        TARGET_DIFFICULTY.add(heightDifference));
    doReturn(Optional.of(otherPeer.getEthPeer())).when(ethPeers).bestPeerWithHeightEstimate();

    assertThat(syncState.isInSync()).isFalse();
    assertThat(syncState.isInSync(0)).isFalse();
    assertThat(syncState.isInSync(heightDifference - 1)).isFalse();
    assertThat(syncState.isInSync(heightDifference)).isTrue();
    assertThat(syncState.isInSync(heightDifference + 1)).isTrue();
  }

  @Test
  public void isInSync_inSyncWithTargetInSyncWithBestPeer() {
    setupOutOfSyncState();
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

    verify(inSyncListener).onInSyncStatusChange(true);
    verify(inSyncListenerExact).onInSyncStatusChange(true);
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
    verify(inSyncListener).onInSyncStatusChange(true);
    verify(inSyncListenerExact, never()).onInSyncStatusChange(true);

    // Advance one more block
    advanceLocalChain(TARGET_CHAIN_HEIGHT - Synchronizer.DEFAULT_IN_SYNC_TOLERANCE + 1);
    // We should register as in-sync with default tolerance, out-of-sync with exact tolerance
    assertThat(syncState.isInSync()).isTrue();
    assertThat(syncState.isInSync(0)).isFalse();
    verifyNoMoreInteractions(inSyncListener);
    verify(inSyncListenerExact, never()).onInSyncStatusChange(true);

    // Catch all the way up
    advanceLocalChain(TARGET_CHAIN_HEIGHT);
    // We should register as in-sync
    assertThat(syncState.isInSync()).isTrue();
    assertThat(syncState.isInSync(0)).isTrue();
    verifyNoMoreInteractions(inSyncListener);
    verify(inSyncListenerExact).onInSyncStatusChange(true);
  }

  @Test
  public void addInSyncListener_whileOutOfSync() {
    setupOutOfSyncState();

    // Add listener
    InSyncListener newListener = mock(InSyncListener.class);
    syncState.subscribeInSync(newListener);
    verify(newListener, never()).onInSyncStatusChange(false);
    verify(newListener, never()).onInSyncStatusChange(true);

    // Catch all the way up
    advanceLocalChain(TARGET_CHAIN_HEIGHT);

    // Fall out of sync
    updateChainState(
        syncTargetPeer.getEthPeer(),
        TARGET_CHAIN_HEIGHT + Synchronizer.DEFAULT_IN_SYNC_TOLERANCE + 1L,
        TARGET_DIFFICULTY.add(10L));

    final ArgumentCaptor<Boolean> inSyncEventCaptor = ArgumentCaptor.forClass(Boolean.class);
    verify(newListener, times(3)).onInSyncStatusChange(inSyncEventCaptor.capture());

    final List<Boolean> syncChanges = inSyncEventCaptor.getAllValues();
    assertThat(syncChanges.get(0)).isEqualTo(false);
    assertThat(syncChanges.get(1)).isEqualTo(true);
    assertThat(syncChanges.get(2)).isEqualTo(false);
  }

  @Test
  public void addInSyncListener_whileOutOfSync_withDistinctSyncTolerance() {
    setupOutOfSyncState();

    // Add listener
    final long syncTolerance = Synchronizer.DEFAULT_IN_SYNC_TOLERANCE * 2;
    InSyncListener newListener = mock(InSyncListener.class);
    syncState.subscribeInSync(newListener, syncTolerance);
    verify(newListener, never()).onInSyncStatusChange(false);
    verify(newListener, never()).onInSyncStatusChange(true);

    // Catch all the way up
    advanceLocalChain(TARGET_CHAIN_HEIGHT);

    // Fall out of sync
    updateChainState(
        syncTargetPeer.getEthPeer(),
        TARGET_CHAIN_HEIGHT + syncTolerance + 1L,
        TARGET_DIFFICULTY.add(10L));

    final ArgumentCaptor<Boolean> inSyncEventCaptor = ArgumentCaptor.forClass(Boolean.class);
    verify(newListener, times(3)).onInSyncStatusChange(inSyncEventCaptor.capture());

    final List<Boolean> syncChanges = inSyncEventCaptor.getAllValues();
    assertThat(syncChanges.get(0)).isEqualTo(false);
    assertThat(syncChanges.get(1)).isEqualTo(true);
    assertThat(syncChanges.get(2)).isEqualTo(false);
  }

  @Test
  public void addInSyncListener_whileInSync() {
    setupOutOfSyncState();
    // Catch all the way up
    advanceLocalChain(TARGET_CHAIN_HEIGHT);

    // Add listener
    InSyncListener newListener = mock(InSyncListener.class);
    syncState.subscribeInSync(newListener);
    verify(newListener, never()).onInSyncStatusChange(false);
    verify(newListener, never()).onInSyncStatusChange(true);
    // Fall out of sync
    updateChainState(
        syncTargetPeer.getEthPeer(),
        TARGET_CHAIN_HEIGHT + Synchronizer.DEFAULT_IN_SYNC_TOLERANCE + 1L,
        TARGET_DIFFICULTY.add(10L));
    verify(newListener).onInSyncStatusChange(false);
    verify(newListener, never()).onInSyncStatusChange(true);

    // Catch up
    advanceLocalChain(TARGET_CHAIN_HEIGHT + 1L);
    verify(newListener).onInSyncStatusChange(false);
    verify(newListener).onInSyncStatusChange(true);
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
    verify(newListener, never()).onInSyncStatusChange(false);
    verify(newListener, never()).onInSyncStatusChange(true);

    // Fall out of sync
    updateChainState(
        syncTargetPeer.getEthPeer(),
        TARGET_CHAIN_HEIGHT + syncTolerance + 1L,
        TARGET_DIFFICULTY.add(10L));
    verify(newListener).onInSyncStatusChange(false);
    verify(newListener, never()).onInSyncStatusChange(true);

    // Catch up
    advanceLocalChain(TARGET_CHAIN_HEIGHT + 1L);
    verify(newListener).onInSyncStatusChange(false);
    verify(newListener).onInSyncStatusChange(true);
  }

  @Test
  public void removeInSyncListener_doesntReceiveSubsequentEvents() {
    final long syncTolerance = Synchronizer.DEFAULT_IN_SYNC_TOLERANCE + 1L;
    setupOutOfSyncState();

    // Add listener
    InSyncListener newListener = mock(InSyncListener.class);
    final long subscriberId = syncState.subscribeInSync(newListener, syncTolerance);
    verify(newListener, never()).onInSyncStatusChange(anyBoolean());

    // Remove listener
    syncState.unsubscribeInSync(subscriberId);

    // Catch all the way up
    advanceLocalChain(TARGET_CHAIN_HEIGHT);

    // We should not register the in-sync event
    verify(newListener, never()).onInSyncStatusChange(anyBoolean());

    // Fall out of sync
    updateChainState(
        syncTargetPeer.getEthPeer(),
        TARGET_CHAIN_HEIGHT + syncTolerance + 1L,
        TARGET_DIFFICULTY.add(10L));

    // We should not register the sync event
    verify(newListener, never()).onInSyncStatusChange(anyBoolean());

    // Other listeners should keep running
    verify(inSyncListenerExact, times(2)).onInSyncStatusChange(false);
    verify(inSyncListenerExact).onInSyncStatusChange(true);
  }

  @Test
  public void removeInSyncListener_addAdditionalListenerBeforeRemoving() {
    final long syncTolerance = Synchronizer.DEFAULT_IN_SYNC_TOLERANCE + 1L;
    setupOutOfSyncState();

    // Add listener
    InSyncListener listenerToRemove = mock(InSyncListener.class);
    InSyncListener otherListener = mock(InSyncListener.class);
    final long subscriberId = syncState.subscribeInSync(listenerToRemove, syncTolerance);
    syncState.subscribeInSync(otherListener, syncTolerance);

    // Remove listener
    syncState.unsubscribeInSync(subscriberId);

    // Catch all the way up
    advanceLocalChain(TARGET_CHAIN_HEIGHT);

    // We should not register the in-sync event
    verify(listenerToRemove, never()).onInSyncStatusChange(anyBoolean());

    // Fall out of sync
    updateChainState(
        syncTargetPeer.getEthPeer(),
        TARGET_CHAIN_HEIGHT + syncTolerance + 1L,
        TARGET_DIFFICULTY.add(10L));

    // We should not register the in-sync event
    verify(listenerToRemove, never()).onInSyncStatusChange(anyBoolean());

    final ArgumentCaptor<Boolean> inSyncEventCaptor = ArgumentCaptor.forClass(Boolean.class);
    verify(otherListener, times(3)).onInSyncStatusChange(inSyncEventCaptor.capture());

    final List<Boolean> syncChanges = inSyncEventCaptor.getAllValues();
    assertThat(syncChanges.get(0)).isEqualTo(false);
    assertThat(syncChanges.get(1)).isEqualTo(true);
    assertThat(syncChanges.get(2)).isEqualTo(false);

    // Other listeners should keep running
    verify(inSyncListenerExact).onInSyncStatusChange(true);
    verify(inSyncListenerExact, times(2)).onInSyncStatusChange(false);
  }

  @Test
  public void removeInSyncListener_addAdditionalListenerAfterRemoving() {
    final long syncTolerance = Synchronizer.DEFAULT_IN_SYNC_TOLERANCE + 1L;
    setupOutOfSyncState();

    // Add listener
    InSyncListener listenerToRemove = mock(InSyncListener.class);
    InSyncListener otherListener = mock(InSyncListener.class);
    final long subscriberId = syncState.subscribeInSync(listenerToRemove, syncTolerance);

    // Remove listener
    syncState.unsubscribeInSync(subscriberId);

    // Add new listener
    syncState.subscribeInSync(otherListener, syncTolerance);

    // Catch all the way up
    advanceLocalChain(TARGET_CHAIN_HEIGHT);

    // We should not register the sync event
    verify(listenerToRemove, never()).onInSyncStatusChange(anyBoolean());

    // Fall out of sync
    updateChainState(
        syncTargetPeer.getEthPeer(),
        TARGET_CHAIN_HEIGHT + syncTolerance + 1L,
        TARGET_DIFFICULTY.add(10L));

    // We should not register the sync event
    verify(listenerToRemove, never()).onInSyncStatusChange(anyBoolean());

    final ArgumentCaptor<Boolean> inSyncEventCaptor = ArgumentCaptor.forClass(Boolean.class);
    verify(otherListener, times(3)).onInSyncStatusChange(inSyncEventCaptor.capture());

    final List<Boolean> syncChanges = inSyncEventCaptor.getAllValues();
    assertThat(syncChanges.get(0)).isEqualTo(false);
    assertThat(syncChanges.get(1)).isEqualTo(true);
    assertThat(syncChanges.get(2)).isEqualTo(false);

    // Other listeners should keep running
    verify(inSyncListenerExact, times(2)).onInSyncStatusChange(false);
    verify(inSyncListenerExact).onInSyncStatusChange(true);
  }

  @Test
  public void syncStatusListener_receivesEventWhenSyncTargetSet() {
    syncState.setSyncTarget(syncTargetPeer.getEthPeer(), blockchain.getBlockHeader(3L).get());

    verify(syncStatusListener).onSyncStatusChanged(syncStatusCaptor.capture());

    assertThat(syncStatusCaptor.getAllValues()).hasSize(1);
    final Optional<SyncStatus> syncStatus = syncStatusCaptor.getValue();

    assertThat(syncStatus).isPresent();
    assertThat(syncStatus.get().getStartingBlock()).isEqualTo(3L);
    assertThat(syncStatus.get().getCurrentBlock()).isEqualTo(OUR_CHAIN_HEAD_NUMBER);
    assertThat(syncStatus.get().getHighestBlock()).isEqualTo(TARGET_CHAIN_HEIGHT);
  }

  @Test
  public void syncStatusListener_receivesEventWhenSyncTargetCleared() {
    syncState.setSyncTarget(syncTargetPeer.getEthPeer(), blockchain.getBlockHeader(3L).get());
    syncState.clearSyncTarget();

    verify(syncStatusListener, times(2)).onSyncStatusChanged(syncStatusCaptor.capture());

    List<Optional<SyncStatus>> events = syncStatusCaptor.getAllValues();
    assertThat(events).hasSize(2);

    // Check first value
    final Optional<SyncStatus> syncingEvent = events.get(0);
    assertThat(syncingEvent).isPresent();
    assertThat(syncingEvent.get().getStartingBlock()).isEqualTo(3L);
    assertThat(syncingEvent.get().getCurrentBlock()).isEqualTo(OUR_CHAIN_HEAD_NUMBER);
    assertThat(syncingEvent.get().getHighestBlock()).isEqualTo(TARGET_CHAIN_HEIGHT);

    // Check second value
    final Optional<SyncStatus> clearedEvent = events.get(1);
    assertThat(clearedEvent).isEmpty();
  }

  @Test
  public void syncStatusListener_ignoreNoopChangesToSyncTarget() {
    syncState.clearSyncTarget();
    syncState.setSyncTarget(syncTargetPeer.getEthPeer(), blockchain.getBlockHeader(3L).get());
    syncState.setSyncTarget(syncTargetPeer.getEthPeer(), blockchain.getBlockHeader(3L).get());
    syncState.clearSyncTarget();
    syncState.clearSyncTarget();

    verify(syncStatusListener, times(2)).onSyncStatusChanged(syncStatusCaptor.capture());

    List<Optional<SyncStatus>> events = syncStatusCaptor.getAllValues();
    assertThat(events).hasSize(2);

    // Check first value
    final Optional<SyncStatus> syncingEvent = events.get(0);
    assertThat(syncingEvent).isPresent();
    assertThat(syncingEvent.get().getStartingBlock()).isEqualTo(3L);
    assertThat(syncingEvent.get().getCurrentBlock()).isEqualTo(OUR_CHAIN_HEAD_NUMBER);
    assertThat(syncingEvent.get().getHighestBlock()).isEqualTo(TARGET_CHAIN_HEIGHT);

    // Check second value
    final Optional<SyncStatus> clearedEvent = events.get(1);
    assertThat(clearedEvent).isEmpty();
  }

  private RespondingEthPeer createPeer(final Difficulty totalDifficulty, final long blockHeight) {
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
    final ChainHeadEstimate chainStateSnapshot = spy(peer.chainStateSnapshot());
    lenient().doReturn(isBetter).when(chainState).chainIsBetterThan(any());
    lenient().doReturn(isBetter).when(chainStateSnapshot).chainIsBetterThan(any());
    final EthPeer mockedPeer = spy(peer);
    lenient().doReturn(chainStateSnapshot).when(chainState).getSnapshot();
    lenient().doReturn(chainStateSnapshot).when(mockedPeer).chainStateSnapshot();
    lenient().doReturn(chainState).when(mockedPeer).chainState();
    return mockedPeer;
  }

  private void setupOutOfSyncState() {
    syncState.setSyncTarget(syncTargetPeer.getEthPeer(), blockchain.getGenesisBlock().getHeader());
    verify(inSyncListener).onInSyncStatusChange(false);
    verify(inSyncListenerExact).onInSyncStatusChange(false);
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

  /**
   * Updates the chain state, such that the peer will end up with an estimated height of {@code
   * blockHeight} and an estimated total difficulty of {@code totalDifficulty}
   *
   * @param peer The peer whose chain should be updated
   * @param blockHeight The target estimated block height
   * @param totalDifficulty The total difficulty
   */
  private void updateChainState(
      final EthPeer peer, final long blockHeight, final Difficulty totalDifficulty) {
    // Chain state is updated based on the parent of the announced block
    // So, increment block number by 1 and set block difficulty to zero
    // in order to update to the values we want
    final BlockHeader header =
        new BlockHeaderTestFixture()
            .number(blockHeight + 1L)
            .difficulty(Difficulty.ZERO)
            .buildHeader();
    peer.chainState().updateForAnnouncedBlock(header, totalDifficulty);

    // Sanity check this logic still holds
    assertThat(peer.chainState().getEstimatedHeight()).isEqualTo(blockHeight);
    assertThat(peer.chainState().getEstimatedTotalDifficulty()).isEqualTo(totalDifficulty);
  }
}
