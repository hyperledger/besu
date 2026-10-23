/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.trie.pathbased.bonsai.archive.trienode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE_ARCHIVE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.slf4j.event.Level.WARN;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStoragePrefixedKeyBlockchainStorage;
import org.hyperledger.besu.ethereum.storage.keyvalue.VariablesKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.provider.BonsaiWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.trielog.TrieLogManager;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.worldview.cache.PathBasedWorldStateCacheManager;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.hyperledger.besu.services.kvstore.SegmentedInMemoryKeyValueStorage;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrailingTrieNodeArchiverTest {

  private static final int SHALLOW_INTERVAL = 128;
  private static final int DEEP_INTERVAL = 1024;

  @Mock private BonsaiWorldStateKeyValueStorage worldStateStorage;
  @Mock private BonsaiWorldStateProvider worldStateProvider;
  @Mock private TrieLogManager trieLogManager;

  @Mock private PathBasedWorldStateCacheManager cacheManager;

  // Used by real-archiveBlocks tests only.
  @Mock private BonsaiWorldState archiverWorldState;
  @Mock private BonsaiWorldStateKeyValueStorage archiverStorage;
  private final AtomicBoolean archiverStorageClosed = new AtomicBoolean(false);

  @Mock private TrieLog mockTrieLog;

  private SegmentedInMemoryKeyValueStorage archiveStorage;
  private MutableBlockchain blockchain;
  private BlockDataGenerator blockDataGenerator;
  private final ExecutorService capturePool = Executors.newFixedThreadPool(2);

  @BeforeEach
  void setUp() {
    archiveStorage =
        new SegmentedInMemoryKeyValueStorage(
            List.of(TRIE_BRANCH_STORAGE, TRIE_BRANCH_STORAGE_ARCHIVE));
    blockDataGenerator = new BlockDataGenerator();
    blockchain = createInMemoryBlockchain(blockDataGenerator.genesisBlock());
    when(worldStateStorage.getComposedWorldStateStorage()).thenReturn(archiveStorage);
    when(trieLogManager.getMaxLayersToLoad()).thenReturn(1L);

    // The archiver opens its state through the cache manager and rolls it through the provider.
    when(worldStateProvider.getWorldStateCacheManager()).thenReturn(cacheManager);
    when(cacheManager.getHeadWorldState(any()))
        .thenAnswer(
            invocation -> {
              archiverStorageClosed.set(false);
              return Optional.of(archiverWorldState);
            });
    when(archiverWorldState.getWorldStateStorage()).thenReturn(archiverStorage);
    when(archiverStorage.isClosed()).thenAnswer(invocation -> archiverStorageClosed.get());
    when(archiverWorldState.blockHash()).thenAnswer(invocation -> worldStateHeadHash());
    when(worldStateProvider.rollFullWorldStateToBlockHash(any(), any(), any()))
        .thenReturn(Optional.of(archiverWorldState));
  }

  @AfterEach
  void tearDown() {
    capturePool.shutdownNow();
  }

  @Test
  void archiveBlocksAdvancesCursorAndPositionsStateWhenBeyondRetention() {
    // maxLayers=1 (setUp), 3 blocks → head=3, oldestReachable=2, target=2
    // archiveBlocks(0, 2): archived block number 0 < 2 → advance archived block number to 2,
    // position state at block 2.
    // Loop is empty (effectiveFrom == toInclusive=2), but the state is ready for the next call.
    appendBlocks(3);

    final TrailingTrieNodeArchiver archiver = createRealArchiver();
    archiver.archivedBlockNumber.set(0L);
    archiver.archiveBlocks(0L, 2L, () -> true);

    assertThat(archiver.archivedBlockNumber.get()).isEqualTo(2L);
    verify(archiverWorldState).close();
    verify(worldStateProvider).rollFullWorldStateToBlockHash(archiverWorldState, hashOf(2L), WARN);
  }

  @Test
  void archiveBlocksArchivesNextBlockAfterRetentionGapAdvance() {
    // Same setup; the second call opens a new state at 2 and archives block 3 (archived block
    // number 2 → 3).
    appendBlocks(3);
    when(trieLogManager.getTrieLogLayer(hashOf(3L))).thenReturn(Optional.of(mockTrieLog));

    final TrailingTrieNodeArchiver archiver = createRealArchiver();
    archiver.archivedBlockNumber.set(0L);
    archiver.archiveBlocks(0L, 2L, () -> true); // retention-gap advance, state positioned at 2
    archiver.archiveBlocks(2L, 3L, () -> true); // new state, rolls forward to 3

    assertThat(archiver.archivedBlockNumber.get()).isEqualTo(3L);
    verify(cacheManager, times(2)).getHeadWorldState(any());
    verify(worldStateProvider).rollFullWorldStateToBlockHash(archiverWorldState, hashOf(3L), WARN);
  }

  @Test
  void coldStartAtTargetBoundaryArchivesNextBlock() {
    // maxLayers=1, head=3: the block before the replay target (3) is 2 = head - maxLayers, so a
    // cold archiver can position there and must archive block 3 rather than skip it.
    appendBlocks(3);
    when(trieLogManager.getTrieLogLayer(hashOf(3L))).thenReturn(Optional.of(mockTrieLog));

    final TrailingTrieNodeArchiver archiver = createRealArchiver();
    archiver.archivedBlockNumber.set(2L);
    archiver.archiveBlocks(2L, 3L, () -> true);

    assertThat(archiver.archivedBlockNumber.get()).isEqualTo(3L);
  }

  @Test
  void positionsFromWorldStateBlockNotChainHead() {
    appendBlocks(3);
    setWorldStateHead(2L);
    when(trieLogManager.getTrieLogLayer(hashOf(2L))).thenReturn(Optional.of(mockTrieLog));

    final TrailingTrieNodeArchiver archiver = createRealArchiver();
    archiver.archivedBlockNumber.set(1L);
    archiver.archiveBlocks(
        1L, 2L, () -> true); // maxLayers=1: position at 1 from the state's head, then 2

    verify(trieLogManager, never()).getTrieLogLayer(hashOf(3L));
    final InOrder inOrder = inOrder(worldStateProvider);
    inOrder.verify(worldStateProvider).rollFullWorldStateToBlockHash(any(), eq(hashOf(1L)), any());
    inOrder.verify(worldStateProvider).rollFullWorldStateToBlockHash(any(), eq(hashOf(2L)), any());
    assertThat(archiver.archivedBlockNumber.get()).isEqualTo(2L);
  }

  @Test
  void failedBlockRollsBackItsArchiveTransaction() {
    appendBlocks(3);
    when(trieLogManager.getTrieLogLayer(hashOf(3L))).thenReturn(Optional.of(mockTrieLog));
    when(worldStateProvider.rollFullWorldStateToBlockHash(any(), eq(hashOf(3L)), any()))
        .thenThrow(new IllegalStateException("boom"));

    final SegmentedInMemoryKeyValueStorage spiedStorage = spy(archiveStorage);
    final AtomicReference<SegmentedKeyValueStorageTransaction> openedTx = new AtomicReference<>();
    doAnswer(
            invocation -> {
              final SegmentedKeyValueStorageTransaction tx =
                  spy((SegmentedKeyValueStorageTransaction) invocation.callRealMethod());
              openedTx.set(tx);
              return tx;
            })
        .when(spiedStorage)
        .startLowPriorityTransaction();
    when(worldStateStorage.getComposedWorldStateStorage()).thenReturn(spiedStorage);

    final TrailingTrieNodeArchiver archiver = createRealArchiver();
    archiver.archivedBlockNumber.set(2L);
    assertThatThrownBy(() -> archiver.archiveBlocks(2L, 3L, () -> true))
        .isInstanceOf(IllegalStateException.class);

    assertThat(openedTx.get()).isNotNull();
    verify(openedTx.get()).rollback();
    verify(openedTx.get(), never()).commit();
  }

  @Test
  void coldRebuildAfterHeadAdvancesDoesNotSkipBlocksWithoutFinality() {
    // No finality, maxLayers=4 -> target = head - 2. Steady state: head 10, archived block number
    // at target 8.
    when(trieLogManager.getMaxLayersToLoad()).thenReturn(4L);
    appendBlocks(10);
    when(trieLogManager.getTrieLogLayer(any())).thenReturn(Optional.of(mockTrieLog));

    final TrailingTrieNodeArchiver archiver = createRealArchiver();

    // The head advances two blocks before the archiver is rebuilt cold (restart, error, layer
    // closed by the flat-DB switch). Blocks 9 and 10 must still be archived, not skipped; with
    // the old target of head - maxLayers + 1 the archived block number would already be outside the
    // window.
    appendBlocks(2);
    archiver.archivedBlockNumber.set(8L);
    archiver.archiveBlocks(8L, 10L, () -> true);

    assertThat(archiver.archivedBlockNumber.get()).isEqualTo(10L);
    verify(worldStateProvider).rollFullWorldStateToBlockHash(archiverWorldState, hashOf(9L), WARN);
    verify(worldStateProvider).rollFullWorldStateToBlockHash(archiverWorldState, hashOf(10L), WARN);
  }

  // --- catch-up before live capture ---

  @Test
  void catchUpToWithoutCoverageHasNothingToFill() {
    appendBlocks(10);
    final TrailingTrieNodeArchiver archiver = createRealArchiver();

    assertThat(archiver.catchUpTo(10L, () -> true)).isTrue();

    verify(worldStateProvider, never()).rollFullWorldStateToBlockHash(any(), any(), any());
  }

  @Test
  void catchUpToThrowsWhenRangeCannotBeArchived() {
    recordCoverage(5L);
    appendBlocks(10); // maxLayers=1 -> archived 5 cannot be repositioned

    final TrailingTrieNodeArchiver archiver = createRealArchiver();

    assertThatThrownBy(() -> archiver.catchUpTo(10L, () -> true))
        .isInstanceOf(TrailingTrieNodeArchiver.ArchiveGapException.class);
    assertThat(archiver.archivedBlockNumber.get()).isEqualTo(5L);
  }

  @Test
  void rollsBackBeyondRetentionWhenCoverageExists() {
    // Archive nodes keep every trie log, so an archived block outside the head's retention window
    // (a long outage or finality stall) is still reachable.
    recordCoverage(5L);
    appendBlocks(10); // maxLayers=1 -> oldestReachable = 9
    when(trieLogManager.getTrieLogLayer(any())).thenReturn(Optional.of(mockTrieLog));

    final TrailingTrieNodeArchiver archiver = createRealArchiver();
    archiver.archivedBlockNumber.set(5L);
    archiver.archiveBlocks(5L, 10L, () -> true);

    assertThat(archiver.archivedBlockNumber.get()).isEqualTo(10L);
    final InOrder inOrder = inOrder(worldStateProvider);
    inOrder.verify(worldStateProvider).rollFullWorldStateToBlockHash(any(), eq(hashOf(5L)), any());
    inOrder.verify(worldStateProvider).rollFullWorldStateToBlockHash(any(), eq(hashOf(6L)), any());
  }

  @Test
  void closesItsStateAtTheEndOfEveryRun() {
    when(trieLogManager.getMaxLayersToLoad()).thenReturn(3L);
    appendBlocks(10); // head 10, oldestReachable 7
    when(trieLogManager.getTrieLogLayer(any())).thenReturn(Optional.of(mockTrieLog));

    final TrailingTrieNodeArchiver archiver = createRealArchiver();
    archiver.archivedBlockNumber.set(7L);
    archiver.archiveBlocks(7L, 9L, () -> true);
    verify(archiverWorldState, times(1)).close();

    archiver.archiveBlocks(
        9L, 10L, () -> true); // repositions from a fresh snapshot, nothing is skipped
    assertThat(archiver.archivedBlockNumber.get()).isEqualTo(10L);
    verify(archiverWorldState, times(2)).close();
    verify(cacheManager, times(2)).getHeadWorldState(any());
  }

  @Test
  void failsClearlyWhenWorldStateHeadIsUnknownToTheBlockchain() {
    // e.g. a reorg replaced block 3 after the world state persisted the old one
    appendBlocks(3);
    setWorldStateHead(3L, blockDataGenerator.hash().getBytes());

    final TrailingTrieNodeArchiver archiver = createRealArchiver();
    archiver.archivedBlockNumber.set(2L);

    assertThatThrownBy(() -> archiver.archiveBlocks(2L, 3L, () -> true))
        .isInstanceOf(IllegalStateException.class)
        .isNotInstanceOf(TrailingTrieNodeArchiver.ArchiveGapException.class)
        .hasMessageContaining("is unknown");
    assertThat(archiver.archivedBlockNumber.get()).isEqualTo(2L);
    verify(worldStateProvider, never()).rollFullWorldStateToBlockHash(any(), any(), any());
    verify(archiverWorldState).close();
  }

  @Test
  void failsClearlyWhenSnapshotHeadIsBehindTheCursor() {
    appendBlocks(3);
    setWorldStateHead(1L); // the persisted world state lags behind the archived

    final TrailingTrieNodeArchiver archiver = createRealArchiver();
    archiver.archivedBlockNumber.set(2L);

    assertThatThrownBy(() -> archiver.archiveBlocks(2L, 3L, () -> true))
        .isInstanceOf(IllegalStateException.class)
        .isNotInstanceOf(TrailingTrieNodeArchiver.ArchiveGapException.class)
        .hasMessageContaining("behind the archiver position");
    verify(worldStateProvider, never()).rollFullWorldStateToBlockHash(any(), any(), any());
  }

  @Test
  void failsClearlyWhenWorldStateHasTheTrieDisabled() {
    appendBlocks(3);
    when(archiverWorldState.isTrieDisabled()).thenReturn(true);

    final TrailingTrieNodeArchiver archiver = createRealArchiver();
    archiver.archivedBlockNumber.set(2L);

    assertThatThrownBy(() -> archiver.archiveBlocks(2L, 3L, () -> true))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("trie disabled");
    verify(worldStateProvider, never()).rollFullWorldStateToBlockHash(any(), any(), any());
    verify(archiverWorldState).close();
  }

  @Test
  void archiveToArchivesFromCoverage() {
    when(trieLogManager.getMaxLayersToLoad()).thenReturn(4L);
    appendBlocks(10);
    when(trieLogManager.getTrieLogLayer(any())).thenReturn(Optional.of(mockTrieLog));
    recordCoverage(6L);

    final TrailingTrieNodeArchiver archiver = createRealArchiver();
    archiver.archiveTo(9L, () -> true);

    assertThat(archiver.getArchivedBlockNumber()).isEqualTo(9L);
    final InOrder inOrder = inOrder(worldStateProvider);
    inOrder.verify(worldStateProvider).rollFullWorldStateToBlockHash(any(), eq(hashOf(7L)), any());
    inOrder.verify(worldStateProvider).rollFullWorldStateToBlockHash(any(), eq(hashOf(8L)), any());
    inOrder.verify(worldStateProvider).rollFullWorldStateToBlockHash(any(), eq(hashOf(9L)), any());
  }

  @Test
  void archiveToStopsBetweenBlocksWhenKeepGoingTurnsFalse() {
    when(trieLogManager.getMaxLayersToLoad()).thenReturn(4L);
    appendBlocks(10);
    when(trieLogManager.getTrieLogLayer(any())).thenReturn(Optional.of(mockTrieLog));
    recordCoverage(6L);

    final TrailingTrieNodeArchiver archiver = createRealArchiver();
    final AtomicInteger checks = new AtomicInteger();
    archiver.archiveTo(9L, () -> checks.incrementAndGet() <= 1);

    assertThat(archiver.getArchivedBlockNumber()).isEqualTo(7L);
    verify(archiverWorldState, times(1)).close();
  }

  @Test
  void archiveToPropagatesGapOnMissingTrieLog() {
    when(trieLogManager.getMaxLayersToLoad()).thenReturn(4L);
    appendBlocks(10);
    when(trieLogManager.getTrieLogLayer(any())).thenReturn(Optional.of(mockTrieLog));
    when(trieLogManager.getTrieLogLayer(hashOf(7L))).thenReturn(Optional.empty());
    recordCoverage(6L);

    final TrailingTrieNodeArchiver archiver = createRealArchiver();

    assertThatThrownBy(() -> archiver.archiveTo(9L, () -> true))
        .isInstanceOf(TrailingTrieNodeArchiver.ArchiveGapException.class);
    assertThat(archiver.getArchivedBlockNumber()).isEqualTo(6L);
  }

  @Test
  void catchUpToArchivesFromCoverage() {
    when(trieLogManager.getMaxLayersToLoad()).thenReturn(4L);
    appendBlocks(10);
    when(trieLogManager.getTrieLogLayer(any())).thenReturn(Optional.of(mockTrieLog));
    recordCoverage(8L);

    final TrailingTrieNodeArchiver archiver = createRealArchiver();

    assertThat(archiver.catchUpTo(10L, () -> true)).isTrue();
    assertThat(archiver.getArchivedBlockNumber()).isEqualTo(10L);
  }

  @Test
  void coverageEndReflectsInlineCapture() {
    final TrailingTrieNodeArchiver archiver = createRealArchiver();
    assertThat(archiver.coverageEnd()).isEmpty();

    recordCoverage(12L);
    assertThat(archiver.coverageEnd()).isEqualTo(OptionalLong.of(12L));
  }

  private Hash hashOf(final long blockNumber) {
    return blockchain.getBlockHeader(blockNumber).get().getHash();
  }

  private Hash worldStateHeadHash() {
    return Hash.wrap(
        Bytes32.wrap(
            archiveStorage
                .get(TRIE_BRANCH_STORAGE, BonsaiWorldStateKeyValueStorage.WORLD_BLOCK_HASH_KEY)
                .orElseThrow()));
  }

  private void recordCoverage(final long block) {
    final SegmentedKeyValueStorageTransaction tx = archiveStorage.startTransaction();
    new ArchiveCoverageTracker(archiveStorage).record(tx, block);
    tx.commit();
  }

  // --- helpers ---

  /** Creates an archiver whose capture pool is shut down by teardown. */
  private TrailingTrieNodeArchiver createRealArchiver() {
    return new TrailingTrieNodeArchiver(
        worldStateStorage,
        worldStateProvider,
        trieLogManager,
        blockchain,
        capturePool,
        SHALLOW_INTERVAL,
        DEEP_INTERVAL);
  }

  private MutableBlockchain createInMemoryBlockchain(final Block genesisBlock) {
    return DefaultBlockchain.createMutable(
        genesisBlock,
        new KeyValueStoragePrefixedKeyBlockchainStorage(
            new InMemoryKeyValueStorage(),
            new VariablesKeyValueStorage(new InMemoryKeyValueStorage()),
            new MainnetBlockHeaderFunctions(),
            false),
        new NoOpMetricsSystem(),
        0);
  }

  private void appendBlocks(final int count) {
    final Block head = blockchain.getBlockByNumber(blockchain.getChainHeadBlockNumber()).get();
    final List<Block> blocks = blockDataGenerator.blockSequence(head, count);
    for (final Block block : blocks) {
      blockchain.appendBlock(block, blockDataGenerator.receipts(block));
    }
    // the archiver positions from the world state's head, which tracks the chain head
    setWorldStateHead(blockchain.getChainHeadBlockNumber());
  }

  private void setWorldStateHead(final long block) {
    setWorldStateHead(block, blockchain.getBlockHeader(block).get().getHash().getBytes());
  }

  private void setWorldStateHead(final long block, final Bytes hash) {
    final SegmentedKeyValueStorageTransaction tx = archiveStorage.startTransaction();
    tx.put(
        TRIE_BRANCH_STORAGE,
        BonsaiWorldStateKeyValueStorage.WORLD_BLOCK_NUMBER_KEY,
        Bytes.ofUnsignedLong(block).toArrayUnsafe());
    tx.put(
        TRIE_BRANCH_STORAGE,
        BonsaiWorldStateKeyValueStorage.WORLD_BLOCK_HASH_KEY,
        hash.toArrayUnsafe());
    tx.commit();
  }
}
