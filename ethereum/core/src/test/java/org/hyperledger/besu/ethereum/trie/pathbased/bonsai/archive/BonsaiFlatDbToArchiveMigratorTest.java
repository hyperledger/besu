/*
 * Copyright contributors to Hyperledger Besu.
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE_ARCHIVE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_STORAGE_ARCHIVE;
import static org.hyperledger.besu.ethereum.trie.pathbased.bonsai.archive.BonsaiArchiveFlatDbStrategy.calculateArchiveKeyWithMinSuffix;
import static org.hyperledger.besu.ethereum.trie.pathbased.bonsai.archive.BonsaiArchiveFlatDbStrategy.calculateNaturalSlotKey;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.BlockAddedObserver;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStoragePrefixedKeyBlockchainStorage;
import org.hyperledger.besu.ethereum.storage.keyvalue.VariablesKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.common.PatriciaTrieAccountValue;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.code.CodeHashCodeStorageStrategy;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.trielog.TrieLogLayer;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.trielog.TrieLogManager;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.hyperledger.besu.services.kvstore.SegmentedInMemoryKeyValueStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class BonsaiFlatDbToArchiveMigratorTest {

  private static final Address TEST_ADDRESS =
      Address.fromHexString("0x95cD8499051f7FE6a2F53749eC1e9F4a81cafa13");
  private static final long BOUNDARY_DISABLED = 0L;
  private static final long MIGRATION_TIMEOUT_SECONDS = 10L;
  private static final long AWAIT_TIMEOUT_SECONDS = 5L;

  @Mock private BonsaiWorldStateKeyValueStorage worldStateStorage;
  @Mock private TrieLogManager trieLogManager;
  private MutableBlockchain blockchain;
  private SegmentedKeyValueStorage storage;
  private BlockDataGenerator blockDataGenerator;
  private final List<BonsaiFlatDbToArchiveMigrator> migrators = new ArrayList<>();

  @BeforeEach
  public void setup() {
    storage = new SegmentedInMemoryKeyValueStorage();
    blockDataGenerator = new BlockDataGenerator();
    blockchain = createInMemoryBlockchain(blockDataGenerator.genesisBlock());
    when(worldStateStorage.getComposedWorldStateStorage()).thenReturn(storage);
    when(trieLogManager.getTrieLogLayer(any()))
        .thenReturn(Optional.of(createAccountTrieLog(Wei.ONE)));
  }

  @AfterEach
  public void tearDown() {
    migrators.forEach(
        m -> {
          try {
            m.close();
          } catch (final Exception ignored) {
            // Ignore exceptions during close
          }
        });
  }

  @Test
  public void migratesAccountChangesFromTrieLogs() throws Exception {
    appendBlocks(2);

    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();
    migrator.migrate().get(MIGRATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

    assertThat(getArchivedAccountKey(1L)).isPresent();
    assertThat(getArchivedAccountKey(2L)).isPresent();
  }

  @Test
  public void migratesStorageChangesFromTrieLogs() throws Exception {
    appendBlocks(1);
    final StorageSlotKey slotKey = new StorageSlotKey(UInt256.ONE);
    final TrieLogLayer trieLog = new TrieLogLayer();
    trieLog.addStorageChange(TEST_ADDRESS, slotKey, UInt256.ZERO, UInt256.valueOf(42));
    when(trieLogManager.getTrieLogLayer(hashAt(1L))).thenReturn(Optional.of(trieLog));

    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();
    migrator.migrate().get(MIGRATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

    assertThat(getArchivedStorageKey(1L, slotKey)).isPresent();
  }

  @Test
  public void futureCompletesExceptionallyOnFailure() {
    appendBlocks(1);

    when(trieLogManager.getTrieLogLayer(any())).thenThrow(new RuntimeException("Test failure"));

    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();

    assertThat(migrator.migrate())
        .failsWithin(1, TimeUnit.SECONDS)
        .withThrowableThat()
        .havingRootCause()
        .withMessage("Test failure");
  }

  @Test
  public void rejectsConcurrentMigrations() throws Exception {
    appendBlocks(1);
    final PausedMigration paused = pauseAtTrieLogLookup(hashAt(1L));

    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();
    final CompletableFuture<Void> first = migrator.migrate();

    paused.awaitStart();
    assertThat(migrator.migrationRunning).isTrue();

    // Second migration should return immediately without running
    final CompletableFuture<Void> second = migrator.migrate();
    second.get(1, TimeUnit.SECONDS);

    // Second migration must not have interacted with the database
    verify(trieLogManager).getTrieLogLayer(hashAt(1L));

    paused.release();
    first.get(MIGRATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

    assertThat(migrator.migrationRunning).isFalse();
  }

  @Test
  public void tracksRunningState() throws Exception {
    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();
    assertThat(migrator.migrationRunning).isFalse();

    migrator.migrate().get(MIGRATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    assertThat(migrator.migrationRunning).isFalse();
  }

  @Test
  public void savesProgressToStorage() throws Exception {
    appendBlocks(5);

    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();
    assertThat(migrator.getMigrationProgress()).isEmpty();

    migrator.migrate().get(MIGRATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    assertThat(migrator.getMigrationProgress()).hasValue(5L);
  }

  @Test
  public void failsMigrationWhenTrieLogIsMissing() {
    appendBlocks(1);
    when(trieLogManager.getTrieLogLayer(hashAt(1L))).thenReturn(Optional.empty());

    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();

    assertThat(migrator.migrate())
        .failsWithin(MIGRATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .withThrowableThat()
        .havingRootCause()
        .withMessage("No trie log found for block 1");
    verify(worldStateStorage, never()).upgradeToArchiveFlatDbMode();
  }

  @Test
  public void migratesNewBlocksAddedDuringMigration() throws Exception {
    appendBlocks(2);
    final PausedMigration paused = pauseAtTrieLogLookup(hashAt(1L));

    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();
    final CompletableFuture<Void> future = migrator.migrate();

    paused.awaitStart();

    // Append block 3 while migration is paused — target should update to cover it
    appendBlocks(1);

    paused.release();
    future.get(MIGRATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

    assertThat(getArchivedAccountKey(1L)).isPresent();
    assertThat(getArchivedAccountKey(2L)).isPresent();
    assertThat(getArchivedAccountKey(3L)).isPresent();
  }

  @Test
  public void switchesToArchiveModeOnCompletion() throws Exception {
    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();
    migrator.migrate().get(MIGRATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

    verify(worldStateStorage).upgradeToArchiveFlatDbMode();
  }

  @Test
  public void resumesFromNextBlockAfterSavedProgress() throws Exception {
    appendBlocks(3);

    // Run first migration over blocks 1-3
    final BonsaiFlatDbToArchiveMigrator firstMigrator = createMigrator();
    firstMigrator.migrate().get(MIGRATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    assertThat(firstMigrator.getMigrationProgress()).hasValue(3L);
    firstMigrator.close(); // simulate node restart — deregisters ongoing observer

    // Append a new block and run a second migrator (simulating a restart)
    appendBlocks(1);

    final BonsaiFlatDbToArchiveMigrator secondMigrator = createMigrator();
    secondMigrator.migrate().get(MIGRATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

    // Blocks 1-3 must not be re-processed — each queried exactly once across both migrations
    verify(trieLogManager, times(1)).getTrieLogLayer(hashAt(1L));
    verify(trieLogManager, times(1)).getTrieLogLayer(hashAt(2L));
    verify(trieLogManager, times(1)).getTrieLogLayer(hashAt(3L));
    // Block 4 must be processed by the second migration
    verify(trieLogManager, times(1)).getTrieLogLayer(hashAt(4L));
  }

  @Test
  public void usesLowPriorityTransactionsForMigration() throws Exception {
    appendBlocks(1);
    final SegmentedInMemoryKeyValueStorage spyStorage = spy(new SegmentedInMemoryKeyValueStorage());
    when(worldStateStorage.getComposedWorldStateStorage()).thenReturn(spyStorage);

    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();
    migrator.migrate().get(MIGRATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

    verify(spyStorage, atLeastOnce()).startLowPriorityTransaction();
  }

  @Test
  public void doesNotSwitchToArchiveModeOnFailure() {
    appendBlocks(1);
    when(trieLogManager.getTrieLogLayer(any())).thenThrow(new RuntimeException("Test failure"));
    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();

    assertThat(migrator.migrate()).failsWithin(MIGRATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    verify(worldStateStorage, never()).upgradeToArchiveFlatDbMode();
  }

  @Test
  public void migrationStopsAtHeadMinusBoundaryDistance() throws Exception {
    // head=5, boundaryDistance=3 → target = 5-3 = 2; blocks 1 and 2 migrated, 3 not
    appendBlocks(5);
    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator(3);
    migrator.migrate().get(MIGRATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

    assertThat(getArchivedAccountKey(2L)).isPresent();
    assertThat(getArchivedAccountKey(3L)).isEmpty();
  }

  @Test
  public void migrateRemovesItsObserverOnCompletion() throws Exception {
    appendBlocks(3);
    final MutableBlockchain spyBlockchain = spy(blockchain);
    final BonsaiFlatDbToArchiveMigrator migrator =
        createMigrator(spyBlockchain, /*boundaryDistance*/ 1);

    migrator.migrate().get(MIGRATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

    // Bulk registers one observer and leaves no other behind: ongoing catch-up is the
    // coordinator's.
    verify(spyBlockchain, times(1)).observeBlockAdded(any());
    verify(spyBlockchain, times(1)).removeObserver(anyLong());
  }

  @Test
  public void migrateObserverIgnoresForkEvents() throws Exception {
    appendBlocks(5); // canonical head = 5
    final MutableBlockchain spyBlockchain = spy(blockchain);
    final PausedMigration paused = pauseAtAnyTrieLogLookup();

    final BonsaiFlatDbToArchiveMigrator migrator =
        createMigrator(spyBlockchain, /*boundaryDistance*/ 2);
    final CompletableFuture<Void> future = migrator.migrate();

    final ArgumentCaptor<BlockAddedObserver> captor =
        ArgumentCaptor.forClass(BlockAddedObserver.class);
    verify(spyBlockchain, atLeastOnce()).observeBlockAdded(captor.capture());
    final BlockAddedObserver migrateObserver = captor.getAllValues().get(0);

    paused.awaitStart();

    // Fire a FORK event at block height 1
    final Block forkBlock =
        blockDataGenerator.block(BlockDataGenerator.BlockOptions.create().setBlockNumber(1L));
    migrateObserver.onBlockAdded(BlockAddedEvent.createForFork(forkBlock));

    paused.release();
    future.get(MIGRATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

    // head=5, boundaryDistance=2 → canonical target = 3; block 3 must still be migrated.
    assertThat(getArchivedAccountKey(3L)).isPresent();
  }

  @Test
  public void closeDuringMigrationInterruptsAndSkipsArchiveUpgrade() throws Exception {
    appendBlocks(3);
    final PausedMigration paused = pauseAtAnyTrieLogLookup();

    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator(BOUNDARY_DISABLED);
    final CompletableFuture<Void> future = migrator.migrate();

    paused.awaitStart();
    assertThat(migrator.blockObserverId).isPresent();

    migrator.close();

    assertThatThrownBy(() -> future.get(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class);
    assertThat(migrator.blockObserverId).isEmpty();
    assertThat(migrator.migrationRunning.get()).isFalse();
    verify(worldStateStorage, never()).upgradeToArchiveFlatDbMode();
  }

  @Test
  public void closeStopsBulkMigrationWithoutUpgradingToArchiveMode() throws Exception {
    appendBlocks(3);
    final CountDownLatch started = new CountDownLatch(1);
    final CountDownLatch proceed = new CountDownLatch(1);
    // Ignores the interrupt from close() so the loop itself must notice the closed flag.
    when(trieLogManager.getTrieLogLayer(any()))
        .thenAnswer(
            invocation -> {
              started.countDown();
              boolean released = false;
              while (!released) {
                try {
                  released = proceed.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                  if (!released) {
                    throw new AssertionError("release was not called in time");
                  }
                } catch (final InterruptedException e) {
                  // keep waiting
                }
              }
              return Optional.of(createAccountTrieLog(Wei.ONE));
            });

    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator(BOUNDARY_DISABLED);
    final CompletableFuture<Void> future = migrator.migrate();
    assertThat(started.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

    final Thread closer = new Thread(migrator::close);
    closer.start();
    // close() sets the closed flag before waiting for the executor
    while (!migrator.closed) {
      Thread.sleep(10);
    }
    proceed.countDown();
    closer.join(TimeUnit.SECONDS.toMillis(AWAIT_TIMEOUT_SECONDS));

    future.get(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    assertThat(migrator.getMigratedBlockNumber()).isLessThan(3L);
    verify(worldStateStorage, never()).upgradeToArchiveFlatDbMode();
    assertThat(migrator.blockObserverId).isEmpty();
  }

  @Test
  public void migrateAfterCloseIsNoOp() throws Exception {
    appendBlocks(3);
    final MutableBlockchain spyBlockchain = spy(blockchain);
    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator(spyBlockchain, BOUNDARY_DISABLED);
    migrator.close();

    final CompletableFuture<Void> future = migrator.migrate();

    assertThat(future).isCompletedWithValue(null);
    assertThat(migrator.blockObserverId).isEmpty();
    assertThat(migrator.migrationRunning.get()).isFalse();
    verify(spyBlockchain, never()).observeBlockAdded(any());
  }

  @Test
  public void migrateToMigratesUpToTarget() {
    appendBlocks(5);
    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator(BOUNDARY_DISABLED);

    migrator.migrateTo(3, () -> true);

    assertThat(getArchivedAccountKey(3L)).isPresent();
    assertThat(getArchivedAccountKey(4L)).isEmpty();
    assertThat(migrator.getMigrationProgress()).isEqualTo(Optional.of(3L));
  }

  @Test
  public void migrateToStopsBetweenBlocksWhenKeepGoingTurnsFalse() {
    appendBlocks(5);
    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator(BOUNDARY_DISABLED);
    final AtomicInteger checks = new AtomicInteger();

    migrator.migrateTo(5, () -> checks.incrementAndGet() <= 2);

    assertThat(migrator.getMigratedBlockNumber()).isEqualTo(2L);
  }

  @Test
  public void migrateToThrowsOnMissingTrieLog() {
    appendBlocks(3);
    when(trieLogManager.getTrieLogLayer(hashAt(2L))).thenReturn(Optional.empty());
    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator(BOUNDARY_DISABLED);

    assertThatThrownBy(() -> migrator.migrateTo(3, () -> true))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("block 2");
    assertThat(migrator.getMigratedBlockNumber()).isEqualTo(1L);
  }

  @Test
  public void loadMigrationProgressSeedsFromSavedProgress() throws Exception {
    appendBlocks(5);
    final BonsaiFlatDbToArchiveMigrator firstMigrator = createMigrator(BOUNDARY_DISABLED);
    firstMigrator.migrate().get(MIGRATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    firstMigrator.close();

    final BonsaiFlatDbToArchiveMigrator secondMigrator = createMigrator(BOUNDARY_DISABLED);
    secondMigrator.loadMigrationProgress();

    assertThat(secondMigrator.getMigratedBlockNumber()).isEqualTo(5L);
  }

  // --- test helpers ---

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
    for (Block block : blocks) {
      blockchain.appendBlock(block, blockDataGenerator.receipts(block));
    }
  }

  private Hash hashAt(final long blockNumber) {
    return blockchain.getBlockHeader(blockNumber).orElseThrow().getHash();
  }

  private BonsaiFlatDbToArchiveMigrator createMigrator() {
    return createMigrator(BOUNDARY_DISABLED);
  }

  private BonsaiFlatDbToArchiveMigrator createMigrator(final long boundaryDistance) {
    return createMigrator(this.blockchain, boundaryDistance);
  }

  private BonsaiFlatDbToArchiveMigrator createMigrator(
      final MutableBlockchain blockchain, final long boundaryDistance) {
    when(trieLogManager.getMaxLayersToLoad()).thenReturn(boundaryDistance);
    final NoOpMetricsSystem metricsSystem = new NoOpMetricsSystem();
    final BonsaiArchiveFlatDbStrategy archiveStrategy =
        new BonsaiArchiveFlatDbStrategy(metricsSystem, new CodeHashCodeStorageStrategy());
    final BonsaiFlatDbToArchiveMigrator migrator =
        new BonsaiFlatDbToArchiveMigrator(
            worldStateStorage,
            trieLogManager,
            blockchain,
            Executors.newSingleThreadExecutor(),
            metricsSystem,
            archiveStrategy);
    migrators.add(migrator);
    return migrator;
  }

  private TrieLogLayer createAccountTrieLog(final Wei balance) {
    final TrieLogLayer trieLog = new TrieLogLayer();
    final PatriciaTrieAccountValue value =
        new PatriciaTrieAccountValue(1, balance, Hash.EMPTY, Hash.EMPTY);
    trieLog.addAccountChange(TEST_ADDRESS, null, value);
    return trieLog;
  }

  private Optional<byte[]> getArchivedAccountKey(final long blockNumber) {
    final byte[] key =
        calculateArchiveKeyWithMinSuffix(
            new BonsaiArchiveContext(blockNumber),
            TEST_ADDRESS.addressHash().getBytes().toArrayUnsafe());
    return storage.get(ACCOUNT_INFO_STATE_ARCHIVE, key);
  }

  private Optional<byte[]> getArchivedStorageKey(
      final long blockNumber, final StorageSlotKey slotKey) {
    final byte[] naturalKey =
        calculateNaturalSlotKey(TEST_ADDRESS.addressHash(), slotKey.getSlotHash());
    final byte[] key =
        calculateArchiveKeyWithMinSuffix(new BonsaiArchiveContext(blockNumber), naturalKey);
    return storage.get(ACCOUNT_STORAGE_ARCHIVE, key);
  }

  private PausedMigration pauseAtTrieLogLookup(final Hash blockHash) {
    final CountDownLatch started = new CountDownLatch(1);
    final CountDownLatch proceed = new CountDownLatch(1);
    when(trieLogManager.getTrieLogLayer(blockHash))
        .thenAnswer(invocation -> waitThenReturnTrieLog(started, proceed));
    return new PausedMigration(started, proceed);
  }

  private PausedMigration pauseAtAnyTrieLogLookup() {
    final CountDownLatch started = new CountDownLatch(1);
    final CountDownLatch proceed = new CountDownLatch(1);
    when(trieLogManager.getTrieLogLayer(any()))
        .thenAnswer(invocation -> waitThenReturnTrieLog(started, proceed));
    return new PausedMigration(started, proceed);
  }

  private Optional<TrieLogLayer> waitThenReturnTrieLog(
      final CountDownLatch started, final CountDownLatch proceed) {
    started.countDown();
    try {
      if (!proceed.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        throw new AssertionError("release() was not called within " + AWAIT_TIMEOUT_SECONDS + "s");
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("migration interrupted", e);
    }
    return Optional.of(createAccountTrieLog(Wei.ONE));
  }

  private record PausedMigration(CountDownLatch started, CountDownLatch proceed) {

    void awaitStart() throws InterruptedException {
      if (!started.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        throw new AssertionError("Migration did not reach the paused trie-log lookup in time");
      }
    }

    void release() {
      proceed.countDown();
    }
  }
}
