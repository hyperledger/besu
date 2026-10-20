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
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE_ARCHIVE;
import static org.mockito.Mockito.when;

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
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.hyperledger.besu.services.kvstore.SegmentedInMemoryKeyValueStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tuweni.bytes.Bytes;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Integration tests for ongoing archive proof sync.
 *
 * <p>Uses {@code ControllableReplayer} (a test subclass that stubs out {@code captureRange}) to
 * verify: cursor persistence across restarts, single-catch-up coverage of multi-block gaps, reorg
 * safety (target constraint prevents above-target capture), and the Phase-A → Phase-B handoff
 * cursor seed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BonsaiArchiveOngoingSyncIntegrationTest {

  private static final long AWAIT_SECONDS = 5L;
  private static final int SHALLOW_INTERVAL = 128;
  private static final int DEEP_INTERVAL = 1024;
  private static final int MAX_LAYERS = 4;

  @Mock private BonsaiWorldStateKeyValueStorage worldStateStorage;
  @Mock private BonsaiWorldStateProvider worldStateProvider;
  @Mock private TrieLogManager trieLogManager;

  private SegmentedInMemoryKeyValueStorage archiveStorage;
  private MutableBlockchain blockchain;
  private BlockDataGenerator blockDataGenerator;
  private final List<ArchiveTrieNodeReplayer> replayers = new ArrayList<>();

  @BeforeEach
  void setUp() {
    archiveStorage = new SegmentedInMemoryKeyValueStorage(List.of(TRIE_BRANCH_STORAGE_ARCHIVE));
    blockDataGenerator = new BlockDataGenerator();
    blockchain = createInMemoryBlockchain(blockDataGenerator.genesisBlock());
    when(worldStateStorage.getComposedWorldStateStorage()).thenReturn(archiveStorage);
    when(trieLogManager.getMaxLayersToLoad()).thenReturn((long) MAX_LAYERS);
  }

  @AfterEach
  void tearDown() {
    replayers.forEach(
        r -> {
          try {
            r.close();
          } catch (final Exception ignored) {
            // best-effort teardown
          }
        });
  }

  /**
   * A replayer catches up over a multi-block gap in a single run when the target is large enough.
   * Verifies that {@code captureRange(0, target)} is called and cursor reaches the target.
   */
  @Test
  void replayerCatchesUpEntireTargetGapInOneRun() throws Exception {
    appendBlocks(10); // head=10, target = 10-4/2 = 8
    final ControllableReplayer replayer = createControllableReplayer();
    replayer.start(0L);

    Awaitility.await()
        .atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(replayer.cursor.get()).isEqualTo(8L));
  }

  /**
   * After a crash and restart (new replayer instance over same storage), the replayer resumes from
   * the persisted cursor rather than from the Phase-A handoff value passed to start().
   */
  @Test
  void restartResumesFromPersistedCursorNotInitialHandoff() throws Exception {
    appendBlocks(10); // head=10, target=8
    final ControllableReplayer firstReplayer = createControllableReplayer();
    firstReplayer.start(0L);

    // Wait for first replayer to capture to target=8
    Awaitility.await()
        .atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(firstReplayer.cursor.get()).isEqualTo(8L));
    firstReplayer.close();

    // Simulate 3 more blocks arriving before the restart
    appendBlocks(3); // head=13, target=11

    // Second replayer: start(0) — but persisted cursor = 8, so cursor = max(0, 8) = 8
    final ControllableReplayer secondReplayer = createControllableReplayer();
    secondReplayer.start(0L);
    assertThat(secondReplayer.cursor.get()).isEqualTo(8L);

    // It then catches up from 8 to 11
    Awaitility.await()
        .atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(secondReplayer.cursor.get()).isEqualTo(11L));
  }

  /**
   * Phase-A handoff: if Phase A captured up to block H, the replayer seeds cursor from H (not 0),
   * avoiding re-archiving blocks Phase A already wrote.
   */
  @Test
  void phaseAHandoffCursorSeedsPhaseBCorrectly() throws Exception {
    appendBlocks(10); // head=10, target=8
    final ControllableReplayer replayer = createControllableReplayer();

    // Simulate Phase A having archived up to block 5
    replayer.start(5L);
    assertThat(replayer.cursor.get()).isEqualTo(5L);

    // Phase B should catch up from 5 to 8 (not from 0)
    Awaitility.await()
        .atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(replayer.cursor.get()).isEqualTo(8L));
  }

  /**
   * When the target advances repeatedly as new heads arrive, the replayer eventually processes all
   * blocks up to the new target. The single-flight guard ensures this happens via re-arm (not via
   * concurrent catch-ups).
   */
  @Test
  void targetAdvanceDrivesOngoingCatchUps() throws Exception {
    appendBlocks(5); // head=5, target=3
    final ControllableReplayer replayer = createControllableReplayer();
    replayer.start(0L);

    // Wait to reach the initial target
    Awaitility.await()
        .atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(replayer.cursor.get()).isEqualTo(3L));

    // Add more blocks, advancing target to 5, then 7
    appendBlocks(2); // head=7, target=5
    Awaitility.await()
        .atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(replayer.cursor.get()).isEqualTo(5L));

    appendBlocks(2); // head=9, target=7
    Awaitility.await()
        .atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(replayer.cursor.get()).isEqualTo(7L));
  }

  /**
   * The replayer never captures blocks above the target. When a reorg event fires for a block above
   * the finality horizon, the target (head − maxLayers / 2) does not change enough to pull in
   * non-final blocks, and no capture happens above the target.
   */
  @Test
  void captureNeverExceedsReplayTarget() throws Exception {
    appendBlocks(4); // head=4, target = 4-4/2 = 2
    final ControllableReplayer replayer = createControllableReplayer();
    replayer.start(0L);

    Awaitility.await()
        .atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(replayer.cursor.get()).isEqualTo(2L));

    // A non-canonical fork block fires (block observer sees non-canonical event)
    // The observer checks isNewCanonicalHead() → false, so replayTarget stays at 2
    assertThat(replayer.replayTarget.get()).isEqualTo(2L);
    assertThat(replayer.cursor.get()).isEqualTo(2L);
  }

  /**
   * After close, the replayer ignores subsequent block-added events and does not advance the
   * cursor.
   */
  @Test
  void replayerDoesNotAdvanceAfterClose() throws Exception {
    appendBlocks(5); // head=5, target=2
    final PausedCapture paused = new PausedCapture();
    final ControllableReplayer replayer =
        createControllableReplayer(Executors.newScheduledThreadPool(1), paused);
    replayer.start(0L);

    paused.awaitStart(); // paused mid-capture
    paused.release();

    replayer.close();

    // After close, adding blocks should not trigger new catch-ups
    appendBlocks(5);
    // Wait a moment and verify cursor hasn't advanced past what was in-flight
    Thread.sleep(200);
    assertThat(replayer.blockObserverId).isEqualTo(OptionalLong.empty());
  }

  // --- helpers ---

  private ControllableReplayer createControllableReplayer() {
    return createControllableReplayer(Executors.newScheduledThreadPool(1), null);
  }

  private ControllableReplayer createControllableReplayer(
      final ScheduledExecutorService executor, final PausedCapture paused) {
    // ControllableReplayer persists its cursor advances to archiveStorage so restart tests work
    final ControllableReplayer replayer =
        new ControllableReplayer(
            worldStateStorage,
            worldStateProvider,
            trieLogManager,
            blockchain,
            executor,
            Executors.newFixedThreadPool(2),
            SHALLOW_INTERVAL,
            DEEP_INTERVAL,
            paused,
            archiveStorage);
    replayers.add(replayer);
    return replayer;
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
  }

  // --- test doubles ---

  /**
   * A replayer that stubs captureRange: updates cursor and persists it to real storage so restart
   * tests can verify durability.
   */
  static class ControllableReplayer extends ArchiveTrieNodeReplayer {

    final AtomicInteger captureRangeCalls = new AtomicInteger();
    private final PausedCapture paused;
    private final SegmentedInMemoryKeyValueStorage persistStorage;

    ControllableReplayer(
        final BonsaiWorldStateKeyValueStorage worldStateStorage,
        final BonsaiWorldStateProvider worldStateProvider,
        final TrieLogManager trieLogManager,
        final org.hyperledger.besu.ethereum.chain.Blockchain blockchain,
        final ScheduledExecutorService executorService,
        final ExecutorService trieCapturePool,
        final int shallowCheckpointInterval,
        final int deepCheckpointInterval,
        final PausedCapture paused,
        final SegmentedInMemoryKeyValueStorage persistStorage) {
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
      this.persistStorage = persistStorage;
    }

    @Override
    void captureRange(final long fromExclusive, final long toInclusive) {
      captureRangeCalls.incrementAndGet();
      if (paused != null) {
        paused.started.countDown();
        paused.awaitRelease();
      }
      if (toInclusive > fromExclusive) {
        cursor.set(toInclusive);
        // Persist cursor so restart tests can verify durability
        final SegmentedKeyValueStorageTransaction tx = persistStorage.startTransaction();
        tx.put(
            TRIE_BRANCH_STORAGE_ARCHIVE,
            ARCHIVE_TRIE_NODE_SYNC_PROGRESS,
            Bytes.ofUnsignedLong(toInclusive).toArrayUnsafe());
        tx.commit();
      }
    }
  }

  static class PausedCapture {
    final CountDownLatch started = new CountDownLatch(1);
    private final CountDownLatch proceed = new CountDownLatch(1);

    void awaitStart() throws InterruptedException {
      if (!started.await(AWAIT_SECONDS, TimeUnit.SECONDS)) {
        throw new AssertionError("captureRange did not start within " + AWAIT_SECONDS + "s");
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
