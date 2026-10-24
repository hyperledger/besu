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
package org.hyperledger.besu.ethereum.trie.pathbased.bonsai.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.BlockAddedObserver;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.archive.trienode.TrailingTrieNodeArchiver;
import org.hyperledger.besu.plugin.data.AddedBlockContext.EventType;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class BonsaiArchiveTrailingSyncTest {

  private static final long MAX_LAYERS = 512L;
  private static final long OBSERVER_ID = 7L;
  private static final long IDLE_MILLIS = 200L;

  private final Blockchain blockchain = mock(Blockchain.class);
  private final BonsaiFlatDbToArchiveMigrator migrator = mock(BonsaiFlatDbToArchiveMigrator.class);
  private final TrailingTrieNodeArchiver archiver = mock(TrailingTrieNodeArchiver.class);
  // Fake worker positions: a successful step reaches its target unless a test overrides it.
  private final AtomicLong migrated = new AtomicLong(0L);
  private final AtomicLong archived = new AtomicLong(0L);

  private BonsaiArchiveTrailingSync sync;
  private BlockAddedObserver observer;

  @BeforeEach
  void setUp() {
    when(blockchain.observeBlockAdded(any())).thenReturn(OBSERVER_ID);
    when(migrator.getMigratedBlockNumber()).thenAnswer(inv -> migrated.get());
    when(archiver.getArchivedBlockNumber()).thenAnswer(inv -> archived.get());
    doAnswer(
            inv -> {
              migrated.set(inv.getArgument(0));
              return null;
            })
        .when(migrator)
        .migrateTo(anyLong(), any());
    doAnswer(
            inv -> {
              archived.set(inv.getArgument(0));
              return null;
            })
        .when(archiver)
        .archiveTo(anyLong(), any());
    sync = create(Optional.of(archiver));
  }

  @AfterEach
  void tearDown() {
    sync.close();
  }

  private BonsaiArchiveTrailingSync create(final Optional<TrailingTrieNodeArchiver> maybeArchiver) {
    final BonsaiArchiveTrailingSync created =
        new BonsaiArchiveTrailingSync(
            blockchain,
            MAX_LAYERS,
            migrator,
            maybeArchiver,
            Executors.newSingleThreadScheduledExecutor());
    created.setRetryDelaysMillis(20, 100);
    return created;
  }

  private void start() {
    sync.start();
    final ArgumentCaptor<BlockAddedObserver> captor =
        ArgumentCaptor.forClass(BlockAddedObserver.class);
    verify(blockchain).observeBlockAdded(captor.capture());
    observer = captor.getValue();
  }

  private void head(final long number) {
    final BlockAddedEvent event = mock(BlockAddedEvent.class);
    final BlockHeader header = mock(BlockHeader.class);
    when(header.getNumber()).thenReturn(number);
    when(event.isNewCanonicalHead()).thenReturn(true);
    when(event.getEventType()).thenReturn(EventType.HEAD_ADVANCED);
    when(event.getHeader()).thenReturn(header);
    observer.onBlockAdded(event);
  }

  private void reorg(final long newHead, final long ancestor, final boolean ancestorKnown) {
    final Hash ancestorHash = Hash.fromHexStringLenient("0xabc");
    final BlockAddedEvent event = mock(BlockAddedEvent.class);
    final BlockHeader header = mock(BlockHeader.class);
    when(header.getNumber()).thenReturn(newHead);
    when(event.isNewCanonicalHead()).thenReturn(true);
    when(event.getEventType()).thenReturn(EventType.CHAIN_REORG);
    when(event.getHeader()).thenReturn(header);
    when(event.getCommonAncestorHash()).thenReturn(ancestorHash);
    if (ancestorKnown) {
      final BlockHeader ancestorHeader = mock(BlockHeader.class);
      when(ancestorHeader.getNumber()).thenReturn(ancestor);
      when(blockchain.getBlockHeader(ancestorHash)).thenReturn(Optional.of(ancestorHeader));
    } else {
      when(blockchain.getBlockHeader(ancestorHash)).thenReturn(Optional.empty());
    }
    observer.onBlockAdded(event);
  }

  private void reorg(final long newHead, final long ancestor) {
    reorg(newHead, ancestor, true);
  }

  private static void idle() throws InterruptedException {
    Thread.sleep(IDLE_MILLIS);
  }

  @Test
  void sharedTargetIsHeadMinusLayersPlusBuffer() {
    assertThat(sync.sharedTarget(1000)).isEqualTo(520);
  }

  @Test
  void sharedTargetClampsToZero() {
    assertThat(sync.sharedTarget(100)).isEqualTo(0);
  }

  @Test
  void noRunsWhileTargetIsZero() throws InterruptedException {
    start();
    sync.startFlatArchive();
    sync.setProofsArchiving(true);
    head(100);
    idle();
    verify(migrator, never()).migrateTo(anyLong(), any());
    verify(archiver, never()).archiveTo(anyLong(), any());
  }

  @Test
  void flatWorkerIdleUntilStarted() throws InterruptedException {
    start();
    head(1000);
    idle();
    verify(migrator, never()).migrateTo(anyLong(), any());

    sync.startFlatArchive();
    verify(migrator, timeout(5_000)).migrateTo(eq(520L), any());
    final InOrder order = inOrder(migrator);
    order.verify(migrator).loadMigrationProgress();
    order.verify(migrator).migrateTo(eq(520L), any());
  }

  @Test
  void proofsRunOnlyWhileEnabledAndDue() throws InterruptedException {
    // 40 blocks behind the first target, so the proofs worker is due once enabled.
    archived.set(480L);
    start();
    head(1000);
    idle();
    verify(archiver, never()).archiveTo(anyLong(), any());

    sync.setProofsArchiving(true);
    verify(archiver, timeout(5_000)).archiveTo(eq(520L), any());

    // Target 530 is only 10 blocks past the archive, below the 32-block batch.
    head(1010);
    idle();
    verify(archiver, times(1)).archiveTo(anyLong(), any());

    sync.setProofsArchiving(false);
    head(1100);
    idle();
    verify(archiver, times(1)).archiveTo(anyLong(), any());
  }

  @Test
  void oneRunInFlightForABurstOfHeads() throws InterruptedException {
    final CountDownLatch entered = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    final List<Long> targets = new CopyOnWriteArrayList<>();
    doAnswer(
            inv -> {
              final long target = inv.getArgument(0);
              targets.add(target);
              entered.countDown();
              release.await();
              migrated.set(target);
              return null;
            })
        .when(migrator)
        .migrateTo(anyLong(), any());
    start();
    sync.startFlatArchive();

    for (long head = 1000; head < 1020; head++) {
      head(head);
    }
    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
    release.countDown();

    await().atMost(Duration.ofSeconds(5)).until(() -> migrated.get() == 539L);
    idle();
    verify(migrator, atMost(2)).migrateTo(anyLong(), any());
    assertThat(targets.getLast()).isEqualTo(539L);
  }

  @Test
  void flatFailureBacksOffWithoutStoppingProofs() {
    doThrow(new IllegalStateException("missing trie log"))
        .when(migrator)
        .migrateTo(anyLong(), any());
    start();
    sync.startFlatArchive();
    sync.setProofsArchiving(true);

    head(1000);
    verify(archiver, timeout(5_000)).archiveTo(eq(520L), any());
    head(1100);
    verify(archiver, timeout(5_000)).archiveTo(eq(620L), any());

    verify(migrator, timeout(5_000).atLeast(2)).migrateTo(anyLong(), any());
    assertThat(sync.isFlatHalted()).isFalse();
  }

  @Test
  void proofsGapHaltsProofsOnly() throws InterruptedException {
    doThrow(new TrailingTrieNodeArchiver.ArchiveGapException("x"))
        .when(archiver)
        .archiveTo(anyLong(), any());
    start();
    sync.startFlatArchive();
    sync.setProofsArchiving(true);

    head(1000);
    verify(archiver, timeout(5_000)).archiveTo(eq(520L), any());
    await().atMost(Duration.ofSeconds(5)).until(sync::isProofsHalted);

    head(1100);
    verify(migrator, timeout(5_000)).migrateTo(eq(620L), any());
    head(1200);
    verify(migrator, timeout(5_000)).migrateTo(eq(720L), any());
    idle();
    verify(archiver, times(1)).archiveTo(anyLong(), any());
    assertThat(sync.isFlatHalted()).isFalse();
  }

  @Test
  void catchUpPreemptsRunningStep() throws Exception {
    final CountDownLatch entered = new CountDownLatch(1);
    final AtomicBoolean sawStop = new AtomicBoolean(false);
    final AtomicBoolean stoppedBeforeCatchUp = new AtomicBoolean(false);
    doAnswer(
            inv -> {
              final BooleanSupplier keepGoing = inv.getArgument(1);
              entered.countDown();
              while (keepGoing.getAsBoolean()) {
                Thread.sleep(5);
              }
              sawStop.set(true);
              return null;
            })
        .when(migrator)
        .migrateTo(anyLong(), any());
    when(archiver.catchUpTo(anyLong(), any()))
        .thenAnswer(
            inv -> {
              stoppedBeforeCatchUp.set(sawStop.get());
              return true;
            });
    start();
    sync.startFlatArchive();
    head(1000);
    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

    final CompletableFuture<Boolean> caughtUp =
        CompletableFuture.supplyAsync(() -> sync.catchUpProofsTo(700));

    assertThat(caughtUp.get(5, TimeUnit.SECONDS)).isTrue();
    verify(archiver).catchUpTo(eq(700L), any());
    assertThat(stoppedBeforeCatchUp).isTrue();
  }

  @Test
  void catchUpRunsWhileProofsDisabled() {
    when(archiver.catchUpTo(anyLong(), any())).thenReturn(true);
    start();
    assertThat(sync.catchUpProofsTo(10)).isTrue();
    verify(archiver).catchUpTo(eq(10L), any());
  }

  @Test
  void catchUpWithoutArchiverReturnsFalse() {
    sync.close();
    sync = create(Optional.empty());
    assertThat(sync.catchUpProofsTo(10)).isFalse();
  }

  @Test
  void closeReleasesPendingCatchUp() throws Exception {
    final CountDownLatch entered = new CountDownLatch(1);
    final CountDownLatch neverReleased = new CountDownLatch(1);
    doAnswer(
            inv -> {
              entered.countDown();
              try {
                neverReleased.await();
              } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return null;
            })
        .when(migrator)
        .migrateTo(anyLong(), any());
    start();
    sync.startFlatArchive();
    head(1000);
    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

    final CompletableFuture<Boolean> caughtUp =
        CompletableFuture.supplyAsync(() -> sync.catchUpProofsTo(700));
    // Let the catch-up queue behind the blocked step.
    idle();
    sync.close();

    assertThat(caughtUp.get(5, TimeUnit.SECONDS)).isFalse();
    verify(blockchain).removeObserver(OBSERVER_ID);
    verify(archiver, never()).catchUpTo(anyLong(), any());
  }

  @Test
  void reorgBelowFlatPositionHaltsFlat() {
    migrated.set(600L);
    when(archiver.coverageEnd()).thenReturn(OptionalLong.of(580L));
    start();
    reorg(1100, 590);
    assertThat(sync.isFlatHalted()).isTrue();
    assertThat(sync.isProofsHalted()).isFalse();
  }

  @Test
  void reorgBelowProofsCoverageHaltsProofs() {
    migrated.set(580L);
    when(archiver.coverageEnd()).thenReturn(OptionalLong.of(600L));
    start();
    sync.setProofsArchiving(true);
    reorg(1100, 590);
    assertThat(sync.isProofsHalted()).isTrue();
    assertThat(sync.isFlatHalted()).isFalse();
  }

  @Test
  void reorgAboveBothHaltsNothing() {
    migrated.set(600L);
    when(archiver.coverageEnd()).thenReturn(OptionalLong.of(600L));
    start();
    reorg(1100, 900);
    assertThat(sync.isFlatHalted()).isFalse();
    assertThat(sync.isProofsHalted()).isFalse();
  }

  @Test
  void reorgWithUnknownAncestorHaltsNothing() {
    migrated.set(600L);
    when(archiver.coverageEnd()).thenReturn(OptionalLong.of(600L));
    start();
    reorg(1100, 590, false);
    assertThat(sync.isFlatHalted()).isFalse();
    assertThat(sync.isProofsHalted()).isFalse();
  }

  @Test
  void reorgBelowInlineCoverageWhileProofsDisabledHaltsNothing() {
    migrated.set(0L);
    when(archiver.coverageEnd()).thenReturn(OptionalLong.of(600L));
    start();
    reorg(1100, 590);
    assertThat(sync.isProofsHalted()).isFalse();
    assertThat(sync.isFlatHalted()).isFalse();
  }

  @Test
  void reorgAtWorkerPositionHaltsNothing() {
    migrated.set(600L);
    start();
    reorg(1100, 600);
    assertThat(sync.isFlatHalted()).isFalse();
  }

  @Test
  @SuppressWarnings("BannedMethod")
  void repeatedReorgLogsHaltOnce() {
    final List<String> errors = new CopyOnWriteArrayList<>();
    final org.apache.logging.log4j.core.Logger logger =
        (org.apache.logging.log4j.core.Logger)
            LogManager.getLogger(BonsaiArchiveTrailingSync.class);
    final AbstractAppender appender =
        new AbstractAppender("halt-capture", null, null, true, Property.EMPTY_ARRAY) {
          @Override
          public void append(final LogEvent event) {
            if (event.getLevel().isMoreSpecificThan(org.apache.logging.log4j.Level.ERROR)) {
              errors.add(event.getMessage().getFormattedMessage());
            }
          }
        };
    appender.start();
    logger.addAppender(appender);
    try {
      migrated.set(600L);
      start();
      reorg(1100, 590);
      reorg(1101, 580);
      assertThat(sync.isFlatHalted()).isTrue();
      assertThat(errors).hasSize(1);
      assertThat(errors.getFirst()).contains("halting it until restart");
    } finally {
      logger.removeAppender(appender);
      appender.stop();
    }
  }

  @Test
  void haltStopsTheRunningStep() throws Exception {
    final CountDownLatch entered = new CountDownLatch(1);
    final AtomicBoolean sawStop = new AtomicBoolean(false);
    migrated.set(600L);
    doAnswer(
            inv -> {
              final BooleanSupplier keepGoing = inv.getArgument(1);
              entered.countDown();
              while (keepGoing.getAsBoolean()) {
                Thread.sleep(5);
              }
              sawStop.set(true);
              return null;
            })
        .when(migrator)
        .migrateTo(anyLong(), any());
    start();
    sync.startFlatArchive();
    head(2000);
    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

    reorg(2100, 590);

    await().atMost(Duration.ofSeconds(5)).untilTrue(sawStop);
    assertThat(sync.isFlatHalted()).isTrue();
  }

  @Test
  void errorBacksOffWithoutStoppingProofs() {
    final List<Long> callNanos = new CopyOnWriteArrayList<>();
    doAnswer(
            inv -> {
              callNanos.add(System.nanoTime());
              if (callNanos.size() == 1) {
                throw new AssertionError("x");
              }
              migrated.set(inv.getArgument(0));
              return null;
            })
        .when(migrator)
        .migrateTo(anyLong(), any());
    start();
    sync.startFlatArchive();
    sync.setProofsArchiving(true);

    head(1000);
    verify(archiver, timeout(5_000)).archiveTo(eq(520L), any());
    verify(migrator, timeout(5_000).times(2)).migrateTo(eq(520L), any());
    // The retry waited out the 20 ms initial backoff instead of looping straight back.
    assertThat(TimeUnit.NANOSECONDS.toMillis(callNanos.get(1) - callNanos.get(0)))
        .isGreaterThanOrEqualTo(20L);
  }

  @Test
  void backoffRetriesWithoutAHeadEvent() {
    doThrow(new IllegalStateException("missing trie log"))
        .doAnswer(
            inv -> {
              migrated.set(inv.getArgument(0));
              return null;
            })
        .when(migrator)
        .migrateTo(anyLong(), any());
    start();
    sync.startFlatArchive();

    head(1000);
    verify(migrator, timeout(5_000).times(2)).migrateTo(eq(520L), any());
  }

  @Test
  void startSeedsTargetFromChainHead() {
    when(blockchain.getChainHeadBlockNumber()).thenReturn(1000L);
    start();
    sync.startFlatArchive();
    verify(migrator, timeout(5_000)).migrateTo(eq(520L), any());
  }
}
