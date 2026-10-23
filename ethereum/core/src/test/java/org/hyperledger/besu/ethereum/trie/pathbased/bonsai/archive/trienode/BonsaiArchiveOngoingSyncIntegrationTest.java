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

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStoragePrefixedKeyBlockchainStorage;
import org.hyperledger.besu.ethereum.storage.keyvalue.VariablesKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.archive.BonsaiArchiveTrailingSync;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.archive.BonsaiFlatDbToArchiveMigrator;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.provider.BonsaiWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.trielog.TrieLogManager;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.hyperledger.besu.services.kvstore.SegmentedInMemoryKeyValueStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

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
 * <p>Drives a real {@link BonsaiArchiveTrailingSync} over a {@code ControllableArchiver} (a test
 * subclass that stubs out {@code archiveBlocks}) to verify: archived block number persistence
 * across restarts, catch-up of multi-block gaps, that the shared target is never exceeded, and that
 * nothing runs after close.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BonsaiArchiveOngoingSyncIntegrationTest {

  private static final long AWAIT_SECONDS = 5L;
  private static final int SHALLOW_INTERVAL = 128;
  private static final int DEEP_INTERVAL = 1024;
  // The shared target is head - 8 and a proofs run needs a batch of 10 blocks (maxLayers / 4).
  private static final int MAX_LAYERS = 40;
  private static final long TARGET_LAG = 8L;
  private static final long BATCH_BLOCKS = 10L;

  @Mock private BonsaiWorldStateKeyValueStorage worldStateStorage;
  @Mock private BonsaiWorldStateProvider worldStateProvider;
  @Mock private TrieLogManager trieLogManager;
  @Mock private BonsaiFlatDbToArchiveMigrator migrator;

  private SegmentedInMemoryKeyValueStorage archiveStorage;
  private MutableBlockchain blockchain;
  private BlockDataGenerator blockDataGenerator;
  private final List<BonsaiArchiveTrailingSync> syncs = new ArrayList<>();
  private final List<ExecutorService> capturePools = new ArrayList<>();

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
    syncs.forEach(BonsaiArchiveTrailingSync::close);
    capturePools.forEach(ExecutorService::shutdownNow);
  }

  /** The archiver catches up over a multi-block gap and reaches the shared target. */
  @Test
  void archiverCatchesUpEntireTargetGap() {
    final ControllableArchiver archiver = createControllableArchiver(null);
    startProofsSync(archiver);

    appendBlocks(50); // head=50, target=42

    awaitArchived(archiver, 50 - TARGET_LAG);
  }

  /**
   * After a restart (new coordinator and archiver over the same storage), the archiver resumes from
   * the archive's coverage record.
   */
  @Test
  void restartResumesFromCoverageCursor() {
    final ControllableArchiver firstArchiver = createControllableArchiver(null);
    final BonsaiArchiveTrailingSync firstSync = startProofsSync(firstArchiver);
    appendBlocks(50); // head=50, target=42
    awaitArchived(firstArchiver, 42L);
    firstSync.close();
    final long archivedBeforeRestart = firstArchiver.getArchivedBlockNumber();

    // Simulate 13 more blocks arriving before the restart.
    appendBlocks(13); // head=63

    final ControllableArchiver secondArchiver = createControllableArchiver(null);
    startProofsSync(secondArchiver);
    appendBlocks(1); // head=64, target=56
    awaitArchived(secondArchiver, 56L);

    assertThat(secondArchiver.firstFromExclusive.get()).isEqualTo(archivedBeforeRestart);
  }

  /**
   * If live capture archived up to block H, the coverage record says so and the archiver continues
   * from H (not 0), avoiding re-archiving blocks already written.
   */
  @Test
  void inlineCaptureCoverageSeedsTrailingArchiver() {
    final SegmentedKeyValueStorageTransaction coverageTx = archiveStorage.startTransaction();
    new ArchiveCoverageTracker(archiveStorage).record(coverageTx, 5L);
    coverageTx.commit();
    final ControllableArchiver archiver = createControllableArchiver(null);
    startProofsSync(archiver);

    appendBlocks(50); // head=50, target=42

    awaitArchived(archiver, 42L);
    assertThat(archiver.firstFromExclusive.get()).isEqualTo(5L);
  }

  /** When the target advances as new heads arrive, the archiver keeps processing batches. */
  @Test
  void targetAdvanceDrivesOngoingCatchUps() {
    final ControllableArchiver archiver = createControllableArchiver(null);
    startProofsSync(archiver);

    appendBlocks(50); // target=42
    awaitArchived(archiver, 42L);

    appendBlocks(10); // head=60, target=52
    awaitArchived(archiver, 52L);

    appendBlocks(10); // head=70, target=62
    awaitArchived(archiver, 62L);
  }

  /** The archiver never archives above the shared target. */
  @Test
  void captureNeverExceedsSharedTarget() throws Exception {
    final ControllableArchiver archiver = createControllableArchiver(null);
    startProofsSync(archiver);

    appendBlocks(50);
    awaitArchived(archiver, blockchain.getChainHeadBlockNumber() - TARGET_LAG);

    // More head movement may start another run; no run may go above the target at its time.
    appendBlocks(3);
    Thread.sleep(200);
    assertThat(archiver.targetViolations).isEmpty();
    assertThat(archiver.getArchivedBlockNumber())
        .isLessThanOrEqualTo(blockchain.getChainHeadBlockNumber() - TARGET_LAG);
  }

  /** After close, the coordinator ignores new heads and the archiver is not run again. */
  @Test
  void archiverDoesNotAdvanceAfterClose() throws Exception {
    final PausedCapture paused = new PausedCapture();
    final ControllableArchiver archiver = createControllableArchiver(paused);
    final BonsaiArchiveTrailingSync sync = startProofsSync(archiver);
    appendBlocks(50);

    paused.awaitStart(); // paused mid-capture
    paused.release();
    sync.close();
    final int callsAtClose = archiver.archiveBlocksCalls.get();

    appendBlocks(30);
    Thread.sleep(200);
    assertThat(archiver.archiveBlocksCalls.get()).isEqualTo(callsAtClose);
  }

  // --- helpers ---

  private BonsaiArchiveTrailingSync startProofsSync(final ControllableArchiver archiver) {
    final BonsaiArchiveTrailingSync sync =
        new BonsaiArchiveTrailingSync(
            blockchain,
            MAX_LAYERS,
            migrator,
            Optional.of(archiver),
            Executors.newSingleThreadScheduledExecutor());
    syncs.add(sync);
    sync.start();
    sync.setProofsArchiving(true);
    return sync;
  }

  // A run starts only once a batch of blocks is pending, so the archiver ends up within one batch
  // of the target, never above it.
  private void awaitArchived(final ControllableArchiver archiver, final long target) {
    Awaitility.await()
        .atMost(AWAIT_SECONDS, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(archiver.getArchivedBlockNumber())
                    .isBetween(target - (BATCH_BLOCKS - 1), target));
  }

  private ControllableArchiver createControllableArchiver(final PausedCapture paused) {
    // ControllableArchiver persists its archived block number advances to archiveStorage so restart
    // tests work
    final ExecutorService capturePool = Executors.newFixedThreadPool(2);
    capturePools.add(capturePool);
    return new ControllableArchiver(
        worldStateStorage,
        worldStateProvider,
        trieLogManager,
        blockchain,
        capturePool,
        SHALLOW_INTERVAL,
        DEEP_INTERVAL,
        paused,
        archiveStorage);
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
   * An archiver that stubs archiveBlocks: updates archived block number and persists it to real
   * storage so restart tests can verify durability.
   */
  static class ControllableArchiver extends TrailingTrieNodeArchiver {

    final AtomicInteger archiveBlocksCalls = new AtomicInteger();
    // -1 until the first call.
    final AtomicLong firstFromExclusive = new AtomicLong(-1L);
    // Calls whose toInclusive was above the shared target for the chain head at call time.
    final List<String> targetViolations = new CopyOnWriteArrayList<>();
    private final Blockchain chain;
    private final PausedCapture paused;
    private final SegmentedInMemoryKeyValueStorage persistStorage;

    ControllableArchiver(
        final BonsaiWorldStateKeyValueStorage worldStateStorage,
        final BonsaiWorldStateProvider worldStateProvider,
        final TrieLogManager trieLogManager,
        final Blockchain blockchain,
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
          trieCapturePool,
          shallowCheckpointInterval,
          deepCheckpointInterval);
      this.chain = blockchain;
      this.paused = paused;
      this.persistStorage = persistStorage;
    }

    @Override
    void archiveBlocks(
        final long fromExclusive, final long toInclusive, final BooleanSupplier keepGoing) {
      archiveBlocksCalls.incrementAndGet();
      firstFromExclusive.compareAndSet(-1L, fromExclusive);
      final long sharedTarget = chain.getChainHeadBlockNumber() - TARGET_LAG;
      if (toInclusive > sharedTarget) {
        targetViolations.add("archive to " + toInclusive + " above target " + sharedTarget);
      }
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
