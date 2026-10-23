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
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atMost;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.awaitility.Awaitility;
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

  private static final long AWAIT_SECONDS = 5L;
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
  private final List<TrailingTrieNodeArchiver> archivers = new ArrayList<>();

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
    archivers.forEach(
        r -> {
          try {
            r.close();
          } catch (final Exception ignored) {
            // best-effort teardown
          }
        });
  }

  // --- start archived block number seeding ---

  @Test
  void startSeedsCursorFromCoverage() throws Exception {
    recordCoverage(5L);
    appendBlocks(10); // head=10, target=9
    // hold the first catch-up so the assertion sees the archived block number start() chose, not
    // its progress
    final PausedCapture paused = new PausedCapture();
    final ControllableArchiver archiver =
        createControllableArchiver(Executors.newScheduledThreadPool(1), paused);
    archiver.start();
    paused.awaitStart();
    assertThat(archiver.archivedBlockNumber.get()).isEqualTo(5L);
    paused.release();
  }

  @Test
  void startBeginsAtGenesisWithoutCoverage() throws Exception {
    appendBlocks(10); // head=10, target=9
    final PausedCapture paused = new PausedCapture();
    final ControllableArchiver archiver =
        createControllableArchiver(Executors.newScheduledThreadPool(1), paused);
    archiver.start();
    paused.awaitStart();
    assertThat(archiver.archivedBlockNumber.get()).isEqualTo(0L);
    paused.release();
  }

  // --- block observer registration ---

  @Test
  void startRegistersBlockObserver() {
    appendBlocks(3); // head=3
    final ControllableArchiver archiver = createControllableArchiver();
    assertThat(archiver.observerId()).isEqualTo(OptionalLong.empty());

    archiver.start();
    assertThat(archiver.observerId()).isNotEqualTo(OptionalLong.empty());
  }

  @Test
  void closeRemovesBlockObserverAndClearsId() {
    appendBlocks(3);
    final ControllableArchiver archiver = createControllableArchiver();
    archiver.start();
    assertThat(archiver.observerId()).isNotEqualTo(OptionalLong.empty());

    archiver.close();
    assertThat(archiver.observerId()).isEqualTo(OptionalLong.empty());
  }

  @Test
  void startAfterCloseIsNoOp() {
    final ControllableArchiver archiver = createControllableArchiver();
    archiver.close();

    archiver.start();
    assertThat(archiver.observerId()).isEqualTo(OptionalLong.empty());
  }

  // --- single-flight coalescing ---

  @Test
  void singleFlightGuardPreventsConcurrentCatchUps() throws Exception {
    final ScheduledExecutorService spyExecutor = spy(Executors.newScheduledThreadPool(1));
    appendBlocks(2); // head=2, target=2
    final PausedCapture paused = new PausedCapture();
    final ControllableArchiver archiver = createControllableArchiver(spyExecutor, paused);
    archiver.start();

    paused.awaitStart();
    assertThat(archiver.isCatchUpRunning()).isTrue();

    // Burst of head events while catch-up is paused — each bumps replayTarget, CAS rejects
    // additional submissions
    appendBlocks(10);
    Awaitility.await()
        .atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(archiver.targetBlock()).isEqualTo(12L));

    // Only one submission despite 10+ head events
    verify(spyExecutor, times(1)).submit(any(Runnable.class));

    // Release: archived block number advances to first run's toInclusive, then re-arm catches
    // remaining blocks
    paused.release();
    Awaitility.await()
        .atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(archiver.archivedBlockNumber.get()).isEqualTo(12L));
  }

  // --- re-arm after target advance during run ---

  @Test
  void catchUpReArmsWhenTargetAdvancedDuringRun() throws Exception {
    appendBlocks(3); // head=3, target=3
    final PausedCapture paused = new PausedCapture();
    final ControllableArchiver archiver =
        createControllableArchiver(Executors.newScheduledThreadPool(1), paused);
    archiver.start();

    paused.awaitStart(); // first archiveBlocks(0, 3) running

    // Advance target while paused
    appendBlocks(2); // head=5, target=5
    Awaitility.await()
        .atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(archiver.targetBlock()).isEqualTo(5L));

    paused.release(); // first run sets archived=3, then re-arms
    Awaitility.await()
        .atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(archiver.archivedBlockNumber.get()).isEqualTo(5L));
  }

  // --- cold-start advance in the real archiveBlocks ---
  //
  // These tests exercise the actual archiveBlocks / openArchiveState path (not
  // ControllableArchiver). With no coverage and archived block number < head - maxLayers, the
  // archiver must advance the archived block number AND position its state there, otherwise it
  // would spin without archiving anything.

  @Test
  void archiveBlocksAdvancesCursorAndPositionsStateWhenBeyondRetention() {
    // maxLayers=1 (setUp), 3 blocks → head=3, oldestReachable=2, target=2
    // archiveBlocks(0, 2): archived block number 0 < 2 → advance archived block number to 2,
    // position state at block 2.
    // Loop is empty (effectiveFrom == toInclusive=2), but the state is ready for the next call.
    appendBlocks(3);

    final TrailingTrieNodeArchiver archiver = createRealArchiver();
    archiver.archivedBlockNumber.set(0L);
    archiver.archiveBlocks(0L, 2L);

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
    archiver.archiveBlocks(0L, 2L); // retention-gap advance, state positioned at 2
    archiver.archiveBlocks(2L, 3L); // new state, rolls forward to 3

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
    archiver.archiveBlocks(2L, 3L);

    assertThat(archiver.archivedBlockNumber.get()).isEqualTo(3L);
  }

  @Test
  void positionsFromWorldStateBlockNotChainHead() {
    appendBlocks(3);
    setWorldStateHead(2L);
    when(trieLogManager.getTrieLogLayer(hashOf(2L))).thenReturn(Optional.of(mockTrieLog));

    final TrailingTrieNodeArchiver archiver = createRealArchiver();
    archiver.archivedBlockNumber.set(1L);
    archiver.archiveBlocks(1L, 2L); // maxLayers=1: position at 1 from the state's head, then 2

    verify(trieLogManager, never()).getTrieLogLayer(hashOf(3L));
    final InOrder inOrder = inOrder(worldStateProvider);
    inOrder.verify(worldStateProvider).rollFullWorldStateToBlockHash(any(), eq(hashOf(1L)), any());
    inOrder.verify(worldStateProvider).rollFullWorldStateToBlockHash(any(), eq(hashOf(2L)), any());
    assertThat(archiver.archivedBlockNumber.get()).isEqualTo(2L);
  }

  @Test
  void failedCatchUpBacksOffInsteadOfRetryingImmediately() throws Exception {
    appendBlocks(3);
    when(trieLogManager.getTrieLogLayer(hashOf(3L))).thenReturn(Optional.of(mockTrieLog));
    when(worldStateProvider.rollFullWorldStateToBlockHash(any(), eq(hashOf(3L)), any()))
        .thenThrow(new IllegalStateException("boom"));

    final TrailingTrieNodeArchiver archiver = createRealArchiver();
    archiver.setRetryDelaysMillis(100L, 400L);
    recordCoverage(2L);
    archiver.start(); // archived 2, target 3

    Thread.sleep(1_000L);

    // 100 + 200 + 400 + 400 ms delays: about 5 attempts in a second; a retry loop with no delay
    // makes thousands, so the upper bound is generous enough not to flake on a slow runner.
    verify(cacheManager, atLeast(2)).getHeadWorldState(any());
    verify(cacheManager, atMost(50)).getHeadWorldState(any());
    assertThat(archiver.archivedBlockNumber.get()).isEqualTo(2L);
  }

  @Test
  void catchUpRecoversAfterTransientFailure() {
    appendBlocks(3);
    when(trieLogManager.getTrieLogLayer(hashOf(3L))).thenReturn(Optional.of(mockTrieLog));
    when(worldStateProvider.rollFullWorldStateToBlockHash(any(), eq(hashOf(3L)), any()))
        .thenThrow(new IllegalStateException("transient"))
        .thenReturn(Optional.of(archiverWorldState));

    final TrailingTrieNodeArchiver archiver = createRealArchiver();
    archiver.setRetryDelaysMillis(50L, 60_000L);
    recordCoverage(2L);
    archiver.start();

    Awaitility.await()
        .atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(archiver.archivedBlockNumber.get()).isEqualTo(3L));
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
    assertThatThrownBy(() -> archiver.archiveBlocks(2L, 3L))
        .isInstanceOf(IllegalStateException.class);

    assertThat(openedTx.get()).isNotNull();
    verify(openedTx.get()).rollback();
    verify(openedTx.get(), never()).commit();
  }

  @Test
  void closeWaitsForInFlightBlockBeforeReleasingState() throws Exception {
    appendBlocks(3);
    when(trieLogManager.getTrieLogLayer(hashOf(3L))).thenReturn(Optional.of(mockTrieLog));

    final CountDownLatch inBlock = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              inBlock.countDown();
              // Ignore the interrupt from shutdownNow: the block is mid-commit and must finish.
              boolean interrupted = false;
              while (release.getCount() > 0) {
                try {
                  release.await();
                } catch (final InterruptedException e) {
                  interrupted = true;
                }
              }
              if (interrupted) {
                Thread.currentThread().interrupt();
              }
              return Optional.of(archiverWorldState);
            })
        .when(worldStateProvider)
        .rollFullWorldStateToBlockHash(any(), eq(hashOf(3L)), any());

    final TrailingTrieNodeArchiver archiver = createRealArchiver();
    recordCoverage(2L);
    archiver.start(); // archived 2, target 3
    assertThat(inBlock.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

    final CompletableFuture<Void> closing = CompletableFuture.runAsync(archiver::close);
    Thread.sleep(300L);
    assertThat(closing).as("close() must wait for the in-flight catch-up").isNotDone();
    verify(archiverWorldState, never()).close();

    release.countDown();
    closing.get(AWAIT_SECONDS, TimeUnit.SECONDS);

    // positioning + the in-flight block
    verify(worldStateProvider, times(2)).rollFullWorldStateToBlockHash(any(), any(), any());
    assertThat(archiver.archivedBlockNumber.get()).isEqualTo(3L);
    verify(archiverWorldState).close();
  }

  @Test
  void coldRebuildAfterHeadAdvancesDoesNotSkipBlocksWithoutFinality() {
    // No finality, maxLayers=4 -> target = head - 2. Steady state: head 10, archived block number
    // at target 8.
    when(trieLogManager.getMaxLayersToLoad()).thenReturn(4L);
    appendBlocks(10);
    when(trieLogManager.getTrieLogLayer(any())).thenReturn(Optional.of(mockTrieLog));

    final TrailingTrieNodeArchiver archiver = createRealArchiver();
    assertThat(archiver.archiveTarget(blockchain.getChainHeadBlockNumber())).isEqualTo(8L);

    // The head advances two blocks before the archiver is rebuilt cold (restart, error, layer
    // closed by the flat-DB switch). Blocks 9 and 10 must still be archived, not skipped; with
    // the old target of head - maxLayers + 1 the archived block number would already be outside the
    // window.
    appendBlocks(2);
    archiver.archivedBlockNumber.set(8L);
    archiver.archiveBlocks(8L, archiver.archiveTarget(blockchain.getChainHeadBlockNumber()));

    assertThat(archiver.archivedBlockNumber.get()).isEqualTo(10L);
    verify(worldStateProvider).rollFullWorldStateToBlockHash(archiverWorldState, hashOf(9L), WARN);
    verify(worldStateProvider).rollFullWorldStateToBlockHash(archiverWorldState, hashOf(10L), WARN);
  }

  // --- catch-up before live capture ---

  @Test
  void catchUpToArchivesFromCoverageWithoutBeingStarted() {
    // Live capture resumes at block 11 after a restart; coverage ended at 8 when the node stopped.
    when(trieLogManager.getMaxLayersToLoad()).thenReturn(4L);
    appendBlocks(10);
    when(trieLogManager.getTrieLogLayer(any())).thenReturn(Optional.of(mockTrieLog));
    recordCoverage(8L);
    final TrailingTrieNodeArchiver archiver = createRealArchiver();

    assertThat(archiver.catchUpTo(10L)).isTrue();

    assertThat(archiver.archivedBlockNumber.get()).isEqualTo(10L);
    verify(worldStateProvider).rollFullWorldStateToBlockHash(archiverWorldState, hashOf(9L), WARN);
    verify(worldStateProvider).rollFullWorldStateToBlockHash(archiverWorldState, hashOf(10L), WARN);
  }

  @Test
  void catchUpContinuesFromCoverageLiveCaptureExtendedInsteadOfHalting() throws Exception {
    // maxLayers=1 (setUp), so only the block before the head can be repositioned to.
    appendBlocks(6);
    recordCoverage(5L);
    when(trieLogManager.getTrieLogLayer(any())).thenReturn(Optional.of(mockTrieLog));
    final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
    final TrailingTrieNodeArchiver archiver =
        new TrailingTrieNodeArchiver(
            worldStateStorage,
            worldStateProvider,
            trieLogManager,
            blockchain,
            executor,
            Executors.newFixedThreadPool(2),
            SHALLOW_INTERVAL,
            DEEP_INTERVAL);
    archivers.add(archiver);
    archiver.start();
    Awaitility.await()
        .atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(archiver.archivedBlockNumber.get()).isEqualTo(6L));

    // Hold the archiver while the node falls behind and live capture archives blocks 7-9.
    final CountDownLatch release = new CountDownLatch(1);
    executor.submit(
        () -> {
          release.await();
          return null;
        });
    appendBlocks(4); // head 10; the archiver's catch-up queues behind the held task
    recordCoverage(9L);
    release.countDown();

    // From its stale archived block number 6 the archiver would see blocks 7-9 as an unrecoverable
    // gap.
    Awaitility.await()
        .atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(archiver.archivedBlockNumber.get()).isEqualTo(10L));
    assertThat(archiver.isHalted()).isFalse();
    verify(worldStateProvider, never()).rollFullWorldStateToBlockHash(any(), eq(hashOf(7L)), any());
    verify(worldStateProvider).rollFullWorldStateToBlockHash(archiverWorldState, hashOf(10L), WARN);
  }

  @Test
  void closeReleasesCatchUpToQueuedBehindARun() throws Exception {
    appendBlocks(3);
    recordCoverage(1L);
    final PausedCapture paused = new PausedCapture();
    final ControllableArchiver archiver =
        createControllableArchiver(Executors.newScheduledThreadPool(1), paused);
    archiver.start();
    paused.awaitStart();

    final CompletableFuture<Boolean> caughtUp =
        CompletableFuture.supplyAsync(() -> archiver.catchUpTo(3L));
    Thread.sleep(200L); // let it queue behind the paused run
    archiver.close();

    assertThat(caughtUp.get(AWAIT_SECONDS, TimeUnit.SECONDS)).isFalse();
  }

  @Test
  void catchUpToWithoutCoverageHasNothingToFill() {
    appendBlocks(10);
    final TrailingTrieNodeArchiver archiver = createRealArchiver();

    assertThat(archiver.catchUpTo(10L)).isTrue();

    verify(worldStateProvider, never()).rollFullWorldStateToBlockHash(any(), any(), any());
  }

  @Test
  void catchUpToReportsFailureWhenRangeCannotBeArchived() {
    recordCoverage(5L);
    appendBlocks(10); // maxLayers=1 -> archived 5 cannot be repositioned

    final TrailingTrieNodeArchiver archiver = createRealArchiver();

    assertThat(archiver.catchUpTo(10L)).isFalse();
    assertThat(archiver.archivedBlockNumber.get()).isEqualTo(5L);
  }

  @Test
  void catchUpToReportsFailureAfterClose() {
    recordCoverage(5L);
    appendBlocks(10);
    final TrailingTrieNodeArchiver archiver = createRealArchiver();
    archiver.close();

    assertThat(archiver.catchUpTo(10L)).isFalse();
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
    archiver.archiveBlocks(5L, 10L);

    assertThat(archiver.archivedBlockNumber.get()).isEqualTo(10L);
    final InOrder inOrder = inOrder(worldStateProvider);
    inOrder.verify(worldStateProvider).rollFullWorldStateToBlockHash(any(), eq(hashOf(5L)), any());
    inOrder.verify(worldStateProvider).rollFullWorldStateToBlockHash(any(), eq(hashOf(6L)), any());
  }

  @Test
  void keepsRunningAfterFallingBeyondRetention() {
    recordCoverage(5L);
    appendBlocks(10);
    when(trieLogManager.getTrieLogLayer(any())).thenReturn(Optional.of(mockTrieLog));

    final TrailingTrieNodeArchiver archiver = createRealArchiver();
    archiver.start();

    Awaitility.await()
        .atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(archiver.archivedBlockNumber.get()).isEqualTo(10L));
    assertThat(archiver.isHalted()).isFalse();
  }

  // --- start / stop for capture mode hand-offs ---

  @Test
  void stopRemovesBlockObserverAndIgnoresNewHeads() throws Exception {
    when(trieLogManager.getMaxLayersToLoad()).thenReturn(4L);
    appendBlocks(10); // target 8
    final ControllableArchiver archiver = createControllableArchiver();
    archiver.start();
    Awaitility.await()
        .atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(archiver.archivedBlockNumber.get()).isEqualTo(8L));

    archiver.stop();
    assertThat(archiver.observerId()).isEqualTo(OptionalLong.empty());
    appendBlocks(5);
    Thread.sleep(200L);
    assertThat(archiver.archiveBlocksCalls.get()).isEqualTo(1);
  }

  @Test
  void restartResumesFromCoverageInlineCaptureExtended() {
    when(trieLogManager.getMaxLayersToLoad()).thenReturn(4L);
    appendBlocks(10);
    final ControllableArchiver archiver = createControllableArchiver();
    archiver.start();
    archiver.stop();

    recordCoverage(9L); // inline capture archived up to 9 while the archiver was stopped
    archiver.start();

    assertThat(archiver.archivedBlockNumber.get()).isEqualTo(9L);
    assertThat(archiver.observerId()).isNotEqualTo(OptionalLong.empty());
  }

  @Test
  void secondStartWhileRunningKeepsOneObserver() {
    appendBlocks(3);
    final ControllableArchiver archiver = createControllableArchiver();
    archiver.start();
    final OptionalLong observer = archiver.observerId();

    archiver.start();

    assertThat(archiver.observerId()).isEqualTo(observer);
  }

  @Test
  void catchUpToRunsWhileStopped() {
    // Inline capture asks for a catch-up before the archiver has ever run, or after it stopped.
    when(trieLogManager.getMaxLayersToLoad()).thenReturn(4L);
    appendBlocks(10);
    when(trieLogManager.getTrieLogLayer(any())).thenReturn(Optional.of(mockTrieLog));
    recordCoverage(8L);
    final TrailingTrieNodeArchiver archiver = createRealArchiver();
    archiver.start();
    archiver.stop();

    assertThat(archiver.catchUpTo(10L)).isTrue();
    assertThat(archiver.archivedBlockNumber.get()).isEqualTo(10L);
  }

  @Test
  void startSeedsCursorFromCoverageWhenHandoffAndProgressAreLost() throws Exception {
    recordCoverage(9L);
    appendBlocks(10); // maxLayers=1 -> oldestReachable = 9
    when(trieLogManager.getTrieLogLayer(any())).thenReturn(Optional.of(mockTrieLog));

    final TrailingTrieNodeArchiver archiver = createRealArchiver();
    archiver.start();

    assertThat(archiver.archivedBlockNumber.get()).isEqualTo(9L);
    Thread.sleep(200L);
    assertThat(archiver.isHalted()).isFalse();
  }

  @Test
  void closesItsStateAtTheEndOfEveryRun() {
    when(trieLogManager.getMaxLayersToLoad()).thenReturn(3L);
    appendBlocks(10); // head 10, oldestReachable 7
    when(trieLogManager.getTrieLogLayer(any())).thenReturn(Optional.of(mockTrieLog));

    final TrailingTrieNodeArchiver archiver = createRealArchiver();
    archiver.archivedBlockNumber.set(7L);
    archiver.archiveBlocks(7L, 9L);
    verify(archiverWorldState, times(1)).close();

    archiver.archiveBlocks(9L, 10L); // repositions from a fresh snapshot, nothing is skipped
    assertThat(archiver.archivedBlockNumber.get()).isEqualTo(10L);
    verify(archiverWorldState, times(2)).close();
    verify(cacheManager, times(2)).getHeadWorldState(any());
  }

  @Test
  void headEventsWaitForABatchOfBlocksBeforeStartingARun() throws Exception {
    when(trieLogManager.getMaxLayersToLoad()).thenReturn(200L);
    appendBlocks(110); // head 110, target 10
    final ControllableArchiver archiver = createControllableArchiver();
    archiver.start(); // the initial run is not batched
    Awaitility.await()
        .atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(archiver.archivedBlockNumber.get()).isEqualTo(10L));

    appendBlocks(10); // target 20: 10 pending, fewer than a batch
    Thread.sleep(200L);
    assertThat(archiver.archiveBlocksCalls.get()).isEqualTo(1);

    appendBlocks(30); // 40 pending
    Awaitility.await()
        .atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(archiver.archiveBlocksCalls.get()).isEqualTo(2));
    assertThat(archiver.archivedBlockNumber.get()).isGreaterThanOrEqualTo(42L);
  }

  @Test
  void haltsWithoutRetryWhenTrieLogMissingForBlockToArchive() {
    when(trieLogManager.getMaxLayersToLoad()).thenReturn(3L);
    appendBlocks(10); // head 10, target 9
    when(trieLogManager.getTrieLogLayer(any())).thenReturn(Optional.of(mockTrieLog));
    // block 8 is the first block to roll forward from archived block number 7, and its log is gone
    when(trieLogManager.getTrieLogLayer(hashOf(8L))).thenReturn(Optional.empty());

    final TrailingTrieNodeArchiver archiver = createRealArchiver();
    archiver.setRetryDelaysMillis(20L, 60_000L);
    recordCoverage(7L);
    archiver.start();

    Awaitility.await()
        .atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(archiver.isHalted()).isTrue());
    assertThat(archiver.archivedBlockNumber.get()).isEqualTo(7L);
    verify(cacheManager, times(1)).getHeadWorldState(any());
    verify(worldStateProvider, never()).rollFullWorldStateToBlockHash(any(), eq(hashOf(8L)), any());
  }

  @Test
  void failsClearlyWhenWorldStateHeadIsUnknownToTheBlockchain() {
    // e.g. a reorg replaced block 3 after the world state persisted the old one
    appendBlocks(3);
    setWorldStateHead(3L, blockDataGenerator.hash().getBytes());

    final TrailingTrieNodeArchiver archiver = createRealArchiver();
    archiver.archivedBlockNumber.set(2L);

    assertThatThrownBy(() -> archiver.archiveBlocks(2L, 3L))
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

    assertThatThrownBy(() -> archiver.archiveBlocks(2L, 3L))
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

    assertThatThrownBy(() -> archiver.archiveBlocks(2L, 3L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("trie disabled");
    verify(worldStateProvider, never()).rollFullWorldStateToBlockHash(any(), any(), any());
    verify(archiverWorldState).close();
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

  // --- replay target ---

  @Test
  void replayTargetUsesFinalizedBlockWhenPresent() {
    appendBlocks(5);
    blockchain.setFinalized(blockchain.getBlockHeader(2L).get().getHash());

    assertThat(createRealArchiver().archiveTarget(blockchain.getChainHeadBlockNumber()))
        .isEqualTo(2L);
  }

  @Test
  void replayTargetFallsBackToHeadMinusHalfMaxLayers() {
    when(trieLogManager.getMaxLayersToLoad()).thenReturn(4L);
    appendBlocks(10);

    assertThat(createRealArchiver().archiveTarget(blockchain.getChainHeadBlockNumber()))
        .isEqualTo(8L);
  }

  @Test
  void replayTargetLeavesBlockBeforeTargetInsideRetentionWindow() {
    when(trieLogManager.getMaxLayersToLoad()).thenReturn(3L);
    appendBlocks(10);

    final long target = createRealArchiver().archiveTarget(blockchain.getChainHeadBlockNumber());

    // A cold archiver positions at target - 1 by rolling back head - (target - 1) layers, and the
    // head may advance a block before it does.
    assertThat(11L - (target - 1)).isLessThanOrEqualTo(3L);
  }

  @Test
  void replayTargetClampsToZeroOnShortChain() {
    when(trieLogManager.getMaxLayersToLoad()).thenReturn(512L);
    appendBlocks(3);

    assertThat(createRealArchiver().archiveTarget(blockchain.getChainHeadBlockNumber()))
        .isEqualTo(0L);
  }

  // --- helpers ---

  private ControllableArchiver createControllableArchiver() {
    return createControllableArchiver(Executors.newScheduledThreadPool(1), null);
  }

  /**
   * Creates a real (non-overriding) archiver for tests that need to exercise {@code archiveBlocks}.
   */
  private TrailingTrieNodeArchiver createRealArchiver() {
    final TrailingTrieNodeArchiver archiver =
        new TrailingTrieNodeArchiver(
            worldStateStorage,
            worldStateProvider,
            trieLogManager,
            blockchain,
            Executors.newScheduledThreadPool(1),
            Executors.newFixedThreadPool(2),
            SHALLOW_INTERVAL,
            DEEP_INTERVAL);
    archivers.add(archiver);
    return archiver;
  }

  private ControllableArchiver createControllableArchiver(
      final ScheduledExecutorService executor, final PausedCapture paused) {
    final ControllableArchiver archiver =
        new ControllableArchiver(
            worldStateStorage,
            worldStateProvider,
            trieLogManager,
            blockchain,
            executor,
            Executors.newFixedThreadPool(2),
            SHALLOW_INTERVAL,
            DEEP_INTERVAL,
            paused);
    archivers.add(archiver);
    return archiver;
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

  // --- test doubles ---

  /**
   * A archiver subclass that overrides {@code archiveBlocks} so tests focus on scheduling /
   * archived block number behaviour without requiring a real world-state stack. Each call advances
   * the archived block number to {@code toInclusive}, optionally pausing first (for concurrency
   * tests).
   */
  static class ControllableArchiver extends TrailingTrieNodeArchiver {

    final AtomicInteger archiveBlocksCalls = new AtomicInteger();
    private final PausedCapture paused;

    ControllableArchiver(
        final BonsaiWorldStateKeyValueStorage worldStateStorage,
        final BonsaiWorldStateProvider worldStateProvider,
        final TrieLogManager trieLogManager,
        final org.hyperledger.besu.ethereum.chain.Blockchain blockchain,
        final ScheduledExecutorService executorService,
        final ExecutorService trieCapturePool,
        final int shallowCheckpointInterval,
        final int deepCheckpointInterval,
        final PausedCapture paused) {
      super(
          worldStateStorage,
          worldStateProvider,
          trieLogManager,
          blockchain,
          executorService,
          trieCapturePool,
          shallowCheckpointInterval,
          deepCheckpointInterval);
      this.paused = paused;
    }

    @Override
    void archiveBlocks(final long fromExclusive, final long toInclusive) {
      archiveBlocksCalls.incrementAndGet();
      if (paused != null) {
        paused.started.countDown();
        paused.awaitRelease();
      }
      if (toInclusive > fromExclusive) {
        archivedBlockNumber.set(toInclusive);
      }
    }
  }

  static class PausedCapture {
    final CountDownLatch started = new CountDownLatch(1);
    private final CountDownLatch proceed = new CountDownLatch(1);

    void awaitStart() throws InterruptedException {
      if (!started.await(AWAIT_SECONDS, TimeUnit.SECONDS)) {
        throw new AssertionError("archiveBlocks did not start within " + AWAIT_SECONDS + "s");
      }
    }

    void release() {
      proceed.countDown();
    }

    void awaitRelease() {
      try {
        if (!proceed.await(AWAIT_SECONDS, TimeUnit.SECONDS)) {
          throw new AssertionError("release() not called within " + AWAIT_SECONDS + "s");
        }
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("interrupted while waiting for release", e);
      }
    }
  }
}
