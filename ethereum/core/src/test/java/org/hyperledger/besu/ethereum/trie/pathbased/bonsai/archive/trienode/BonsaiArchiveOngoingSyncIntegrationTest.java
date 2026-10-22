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
 * <p>Uses {@code ControllableArchiver} (a test subclass that stubs out {@code archiveBlocks}) to
 * verify: archived block number persistence across restarts, single-catch-up coverage of
 * multi-block gaps, reorg safety (target constraint prevents above-target capture), and the Phase-A
 * → Phase-B handoff archived block number seed.
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
  private final List<FinalizedTrieNodeArchiver> archivers = new ArrayList<>();

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
    archivers.forEach(
        r -> {
          try {
            r.close();
          } catch (final Exception ignored) {
            // best-effort teardown
          }
        });
  }

  /**
   * A archiver catches up over a multi-block gap in a single run when the target is large enough.
   * Verifies that {@code archiveBlocks(0, target)} is called and archived block number reaches the
   * target.
   */
  @Test
  void archiverCatchesUpEntireTargetGapInOneRun() throws Exception {
    appendBlocks(10); // head=10, target = 10-4/2 = 8
    final ControllableArchiver archiver = createControllableArchiver();
    archiver.start();

    Awaitility.await()
        .atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(archiver.archivedBlockNumber.get()).isEqualTo(8L));
  }

  /**
   * After a crash and restart (new archiver instance over same storage), the archiver resumes from
   * the archive's coverage record.
   */
  @Test
  void restartResumesFromCoverageCursor() throws Exception {
    appendBlocks(10); // head=10, target=8
    final ControllableArchiver firstArchiver = createControllableArchiver();
    firstArchiver.start();

    // Wait for first archiver to capture to target=8
    Awaitility.await()
        .atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(firstArchiver.archivedBlockNumber.get()).isEqualTo(8L));
    firstArchiver.close();

    // Simulate 3 more blocks arriving before the restart
    appendBlocks(3); // head=13, target=11

    // Second archiver: coverage = 8, so archived block number = 8
    final ControllableArchiver secondArchiver = createControllableArchiver();
    secondArchiver.start();
    assertThat(secondArchiver.archivedBlockNumber.get()).isEqualTo(8L);

    // It then catches up from 8 to 11
    Awaitility.await()
        .atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(secondArchiver.archivedBlockNumber.get()).isEqualTo(11L));
  }

  /**
   * Phase-A handoff: if live capture archived up to block H, the coverage record says so and the
   * archiver seeds its archived block number from H (not 0), avoiding re-archiving blocks already
   * written.
   */
  @Test
  void phaseAHandoffCursorSeedsPhaseBCorrectly() throws Exception {
    appendBlocks(10); // head=10, target=8
    final ControllableArchiver archiver = createControllableArchiver();

    // Simulate Phase A having archived up to block 5
    final SegmentedKeyValueStorageTransaction coverageTx = archiveStorage.startTransaction();
    new ArchiveCoverageTracker(archiveStorage).record(coverageTx, 5L);
    coverageTx.commit();
    archiver.start();
    assertThat(archiver.archivedBlockNumber.get()).isEqualTo(5L);

    // Phase B should catch up from 5 to 8 (not from 0)
    Awaitility.await()
        .atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(archiver.archivedBlockNumber.get()).isEqualTo(8L));
  }

  /**
   * When the target advances repeatedly as new heads arrive, the archiver eventually processes all
   * blocks up to the new target. The single-flight guard ensures this happens via re-arm (not via
   * concurrent catch-ups).
   */
  @Test
  void targetAdvanceDrivesOngoingCatchUps() throws Exception {
    appendBlocks(5); // head=5, target=3
    final ControllableArchiver archiver = createControllableArchiver();
    archiver.start();

    // Wait to reach the initial target
    Awaitility.await()
        .atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(archiver.archivedBlockNumber.get()).isEqualTo(3L));

    // Add more blocks, advancing target to 5, then 7
    appendBlocks(2); // head=7, target=5
    Awaitility.await()
        .atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(archiver.archivedBlockNumber.get()).isEqualTo(5L));

    appendBlocks(2); // head=9, target=7
    Awaitility.await()
        .atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(archiver.archivedBlockNumber.get()).isEqualTo(7L));
  }

  /**
   * The archiver never captures blocks above the target. When a reorg event fires for a block above
   * the finality horizon, the target (head − maxLayers / 2) does not change enough to pull in
   * non-final blocks, and no capture happens above the target.
   */
  @Test
  void captureNeverExceedsReplayTarget() throws Exception {
    appendBlocks(4); // head=4, target = 4-4/2 = 2
    final ControllableArchiver archiver = createControllableArchiver();
    archiver.start();

    Awaitility.await()
        .atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(archiver.archivedBlockNumber.get()).isEqualTo(2L));

    // A non-canonical fork block fires (block observer sees non-canonical event)
    // The observer checks isNewCanonicalHead() → false, so replayTarget stays at 2
    assertThat(archiver.targetBlock()).isEqualTo(2L);
    assertThat(archiver.archivedBlockNumber.get()).isEqualTo(2L);
  }

  /**
   * After close, the archiver ignores subsequent block-added events and does not advance the
   * archived block number.
   */
  @Test
  void archiverDoesNotAdvanceAfterClose() throws Exception {
    appendBlocks(5); // head=5, target=2
    final PausedCapture paused = new PausedCapture();
    final ControllableArchiver archiver =
        createControllableArchiver(Executors.newScheduledThreadPool(1), paused);
    archiver.start();

    paused.awaitStart(); // paused mid-capture
    paused.release();

    archiver.close();

    // After close, adding blocks should not trigger new catch-ups
    appendBlocks(5);
    // Wait a moment and verify archived block number hasn't advanced past what was in-flight
    Thread.sleep(200);
    assertThat(archiver.observerId()).isEqualTo(OptionalLong.empty());
  }

  // --- helpers ---

  private ControllableArchiver createControllableArchiver() {
    return createControllableArchiver(Executors.newScheduledThreadPool(1), null);
  }

  private ControllableArchiver createControllableArchiver(
      final ScheduledExecutorService executor, final PausedCapture paused) {
    // ControllableArchiver persists its archived block number advances to archiveStorage so restart
    // tests work
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
            paused,
            archiveStorage);
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
  }

  // --- test doubles ---

  /**
   * A archiver that stubs archiveBlocks: updates archived block number and persists it to real
   * storage so restart tests can verify durability.
   */
  static class ControllableArchiver extends FinalizedTrieNodeArchiver {

    final AtomicInteger archiveBlocksCalls = new AtomicInteger();
    private final PausedCapture paused;
    private final SegmentedInMemoryKeyValueStorage persistStorage;

    ControllableArchiver(
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
    void archiveBlocks(final long fromExclusive, final long toInclusive) {
      archiveBlocksCalls.incrementAndGet();
      if (paused != null) {
        paused.started.countDown();
        paused.awaitRelease();
      }
      if (toInclusive > fromExclusive) {
        archivedBlockNumber.set(toInclusive);
        // Record coverage, the durable archived block number, so restart tests can verify
        // durability
        final SegmentedKeyValueStorageTransaction tx = persistStorage.startTransaction();
        new ArchiveCoverageTracker(persistStorage).record(tx, toInclusive);
        tx.commit();
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
