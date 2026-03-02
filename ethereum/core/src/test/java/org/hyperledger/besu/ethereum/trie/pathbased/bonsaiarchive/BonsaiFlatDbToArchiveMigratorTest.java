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
package org.hyperledger.besu.ethereum.trie.pathbased.bonsaiarchive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStoragePrefixedKeyBlockchainStorage;
import org.hyperledger.besu.ethereum.storage.keyvalue.VariablesKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.common.PmtStateTrieAccountValue;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.flat.BonsaiArchiveFlatDbStrategy;
import org.hyperledger.besu.ethereum.trie.pathbased.common.BonsaiContext;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.flat.CodeHashCodeStorageStrategy;
import org.hyperledger.besu.ethereum.trie.pathbased.common.trielog.TrieLogLayer;
import org.hyperledger.besu.ethereum.trie.pathbased.common.trielog.TrieLogManager;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.hyperledger.besu.services.kvstore.SegmentedInMemoryKeyValueStorage;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BonsaiFlatDbToArchiveMigratorTest {

  // Test address used for account and storage changes
  private static final Address TEST_ADDRESS =
      Address.fromHexString("0x95cD8499051f7FE6a2F53749eC1e9F4a81cafa13");

  private BonsaiWorldStateKeyValueStorage worldStateStorage;
  private TrieLogManager trieLogManager;
  private MutableBlockchain blockchain;
  private ScheduledExecutorService executorService;
  private SegmentedKeyValueStorage storage;
  private BlockDataGenerator blockDataGenerator;

  @BeforeEach
  public void setup() {
    storage = new SegmentedInMemoryKeyValueStorage();
    worldStateStorage = mock(BonsaiWorldStateKeyValueStorage.class);
    trieLogManager = mock(TrieLogManager.class);
    executorService = Executors.newScheduledThreadPool(1);
    blockDataGenerator = new BlockDataGenerator();
    blockchain = createInMemoryBlockchain(blockDataGenerator.genesisBlock());
    when(worldStateStorage.getComposedWorldStateStorage()).thenReturn(storage);
    when(trieLogManager.getTrieLogLayer(any())).thenReturn(Optional.empty());
  }

  @AfterEach
  public void tearDown() {
    executorService.shutdownNow();
  }

  @Test
  public void migratesAccountChangesFromTrieLogs() throws Exception {
    appendBlocks(2);

    final Hash hash1 = blockchain.getBlockHeader(1L).get().getHash();
    final Hash hash2 = blockchain.getBlockHeader(2L).get().getHash();

    // Create trie logs with account changes
    final TrieLogLayer trieLog1 = createAccountTrieLog(Wei.fromHexString("0x100"));
    final TrieLogLayer trieLog2 = createAccountTrieLog(Wei.fromHexString("0x200"));

    when(trieLogManager.getTrieLogLayer(hash1)).thenReturn(Optional.of(trieLog1));
    when(trieLogManager.getTrieLogLayer(hash2)).thenReturn(Optional.of(trieLog2));

    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();

    migrator.migrate().get(10, TimeUnit.SECONDS);

    // Verify account state was written for both blocks
    assertThat(getArchivedAccountKey(1L)).isPresent();
    assertThat(getArchivedAccountKey(2L)).isPresent();
  }

  @Test
  public void migratesStorageChangesFromTrieLogs() throws Exception {
    appendBlocks(1);

    final Hash hash1 = blockchain.getBlockHeader(1L).get().getHash();
    final StorageSlotKey slotKey = new StorageSlotKey(UInt256.ONE);

    final TrieLogLayer trieLog1 = new TrieLogLayer();
    trieLog1.addStorageChange(TEST_ADDRESS, slotKey, UInt256.ZERO, UInt256.valueOf(42));

    when(trieLogManager.getTrieLogLayer(hash1)).thenReturn(Optional.of(trieLog1));

    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();

    migrator.migrate().get(10, TimeUnit.SECONDS);

    // Verify storage was written
    final byte[] naturalKey =
        BonsaiArchiveFlatDbStrategy.calculateNaturalSlotKey(
            TEST_ADDRESS.addressHash(), slotKey.getSlotHash());
    final byte[] key =
        BonsaiArchiveFlatDbStrategy.calculateArchiveKeyWithMinSuffix(
            new BonsaiContext(1L), naturalKey);
    assertThat(storage.get(ACCOUNT_STORAGE_STORAGE, key)).isPresent();
  }

  @Test
  public void futureCompletesExceptionallyOnFailure() throws Exception {
    when(trieLogManager.getTrieLogLayer(any())).thenThrow(new RuntimeException("Test failure"));

    appendBlocks(1);

    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();

    assertThat(migrator.migrate())
        .failsWithin(10, TimeUnit.SECONDS)
        .withThrowableThat()
        .havingRootCause()
        .withMessage("Test failure");
  }

  @Test
  public void rejectsConcurrentMigrations() throws Exception {
    final CountDownLatch migrationStartedLatch = new CountDownLatch(1);
    final CountDownLatch allowMigrationToFinishLatch = new CountDownLatch(1);

    appendBlocks(1);

    // Block the trie log manager to pause migration
    when(trieLogManager.getTrieLogLayer(any()))
        .thenAnswer(
            invocation -> {
              migrationStartedLatch.countDown();
              allowMigrationToFinishLatch.await(10, TimeUnit.SECONDS);
              return Optional.empty();
            });

    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();

    // Start first migration
    final CompletableFuture<Void> first = migrator.migrate();

    // Wait for migration to start
    assertThat(migrationStartedLatch.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(migrator.migrationRunning).isTrue();

    // Second migration should return immediately without running
    final CompletableFuture<Void> second = migrator.migrate();
    second.get(1, TimeUnit.SECONDS);

    // Allow first migration to complete
    allowMigrationToFinishLatch.countDown();
    first.get(10, TimeUnit.SECONDS);

    assertThat(migrator.migrationRunning).isFalse();
  }

  @Test
  public void tracksRunningState() throws Exception {
    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();
    assertThat(migrator.migrationRunning).isFalse();

    migrator.migrate().get(10, TimeUnit.SECONDS);
    assertThat(migrator.migrationRunning).isFalse();
  }

  @Test
  public void savesProgressToStorage() throws Exception {
    appendBlocks(5);

    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();
    assertThat(migrator.getMigrationProgress()).isEmpty();

    migrator.migrate().get(10, TimeUnit.SECONDS);
    assertThat(migrator.getMigrationProgress()).isPresent();
    assertThat(migrator.getMigrationProgress().get()).isEqualTo(5L);
  }

  @Test
  public void skipsBlocksWithNoTrieLog() throws Exception {
    appendBlocks(2);

    final Hash hash2 = blockchain.getBlockHeader(2L).get().getHash();
    when(trieLogManager.getTrieLogLayer(hash2))
        .thenReturn(Optional.of(createAccountTrieLog(Wei.ONE)));

    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();
    migrator.migrate().get(10, TimeUnit.SECONDS);

    // Only block 2 should have state written
    assertThat(getArchivedAccountKey(2L)).isPresent();
  }

  @Test
  public void migratesNewBlocksAddedDuringMigration() throws Exception {
    appendBlocks(2);

    final Hash hash1 = blockchain.getBlockHeader(1L).get().getHash();
    final Hash hash2 = blockchain.getBlockHeader(2L).get().getHash();

    final CountDownLatch block1ProcessingLatch = new CountDownLatch(1);
    final CountDownLatch allowBlock1ToFinishLatch = new CountDownLatch(1);

    // Pause migration after block 1 starts processing
    when(trieLogManager.getTrieLogLayer(hash1))
        .thenAnswer(
            invocation -> {
              block1ProcessingLatch.countDown();
              allowBlock1ToFinishLatch.await(10, TimeUnit.SECONDS);
              return Optional.of(createAccountTrieLog(Wei.fromHexString("0x100")));
            });
    when(trieLogManager.getTrieLogLayer(hash2))
        .thenReturn(Optional.of(createAccountTrieLog(Wei.fromHexString("0x200"))));

    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();
    final CompletableFuture<Void> future = migrator.migrate();

    // Wait for migration to reach block 1
    assertThat(block1ProcessingLatch.await(5, TimeUnit.SECONDS)).isTrue();

    // Append block 3 while migration is paused, target should update
    appendBlocks(1);
    final Hash hash3 = blockchain.getBlockHeader(3L).get().getHash();
    when(trieLogManager.getTrieLogLayer(hash3))
        .thenReturn(Optional.of(createAccountTrieLog(Wei.fromHexString("0x300"))));

    // Let migration continue
    allowBlock1ToFinishLatch.countDown();
    future.get(10, TimeUnit.SECONDS);

    // All three blocks should have been migrated
    assertThat(getArchivedAccountKey(1L)).isPresent();
    assertThat(getArchivedAccountKey(2L)).isPresent();
    assertThat(getArchivedAccountKey(3L)).isPresent();
  }

  @Test
  public void switchesToArchiveModeOnCompletion() throws Exception {
    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();
    migrator.migrate().get(10, TimeUnit.SECONDS);

    verify(worldStateStorage).upgradeToFullFlatDbMode();
  }

  @Test
  public void doesNotSwitchToArchiveModeOnFailure() throws Exception {
    appendBlocks(1);

    when(trieLogManager.getTrieLogLayer(any())).thenThrow(new RuntimeException("Test failure"));

    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();

    assertThat(migrator.migrate()).failsWithin(10, TimeUnit.SECONDS);

    verify(worldStateStorage, never()).upgradeToFullFlatDbMode();
  }

  @Test
  public void tailModeDoesNotActivateBeforeThreshold() throws Exception {
    final int initialBlocks = BonsaiFlatDbToArchiveMigrator.NEAR_HEAD_THRESHOLD + 10;
    appendBlocks(initialBlocks);

    // Pause well before the tail threshold (block 1)
    final Hash pauseHash = blockchain.getBlockHeader(1L).get().getHash();

    final CountDownLatch migrationPausedLatch = new CountDownLatch(1);
    final CountDownLatch allowMigrationToFinishLatch = new CountDownLatch(1);

    when(trieLogManager.getTrieLogLayer(pauseHash))
        .thenAnswer(
            invocation -> {
              migrationPausedLatch.countDown();
              allowMigrationToFinishLatch.await(10, TimeUnit.SECONDS);
              return Optional.of(createAccountTrieLog(Wei.fromHexString("0x100")));
            });

    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();
    final CompletableFuture<Void> future = migrator.migrate();

    assertThat(migrationPausedLatch.await(5, TimeUnit.SECONDS)).isTrue();

    // Append a block while paused far from tail — target should update (not frozen)
    final Block head = blockchain.getBlockByNumber(blockchain.getChainHeadBlockNumber()).get();
    final List<Block> newBlocks = blockDataGenerator.blockSequence(head, 1);
    final Block newBlock = newBlocks.get(0);
    final long newBlockNumber = initialBlocks + 1;
    final Hash newHash = newBlock.getHeader().getHash();
    when(trieLogManager.getTrieLogLayer(newHash))
        .thenReturn(Optional.of(createAccountTrieLog(Wei.fromHexString("0xABC"))));
    blockchain.appendBlock(newBlock, blockDataGenerator.receipts(newBlock));

    allowMigrationToFinishLatch.countDown();
    future.get(30, TimeUnit.SECONDS);

    // The new block should have been migrated by the main loop (target was updated, not frozen)
    assertThat(migrator.getMigrationProgress()).hasValue(newBlockNumber);
    assertThat(getArchivedAccountKey(newBlockNumber)).isPresent();
  }

  @Test
  public void tailModeActivatesNearEndBlock() throws Exception {
    final int totalBlocks = BonsaiFlatDbToArchiveMigrator.NEAR_HEAD_THRESHOLD + 10;
    appendBlocks(totalBlocks);

    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();
    migrator.migrate().get(30, TimeUnit.SECONDS);

    assertThat(migrator.getMigrationProgress()).isPresent();
    assertThat(migrator.getMigrationProgress().get()).isEqualTo((long) totalBlocks);
  }

  @Test
  public void observerWritesArchiveKeysInTailMode() throws Exception {
    final int initialBlocks = BonsaiFlatDbToArchiveMigrator.NEAR_HEAD_THRESHOLD + 5;
    appendBlocks(initialBlocks);

    final long pauseBlock = initialBlocks - BonsaiFlatDbToArchiveMigrator.NEAR_HEAD_THRESHOLD + 1;
    final Hash pauseHash = blockchain.getBlockHeader(pauseBlock).get().getHash();

    final CountDownLatch migrationReachedTailLatch = new CountDownLatch(1);
    final CountDownLatch allowMigrationToFinishLatch = new CountDownLatch(1);

    when(trieLogManager.getTrieLogLayer(pauseHash))
        .thenAnswer(
            invocation -> {
              migrationReachedTailLatch.countDown();
              allowMigrationToFinishLatch.await(10, TimeUnit.SECONDS);
              return Optional.of(createAccountTrieLog(Wei.fromHexString("0x999")));
            });

    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();
    final CompletableFuture<Void> future = migrator.migrate();

    assertThat(migrationReachedTailLatch.await(10, TimeUnit.SECONDS)).isTrue();

    // Pre-generate the block so we can set up the mock BEFORE appending
    final Block head = blockchain.getBlockByNumber(blockchain.getChainHeadBlockNumber()).get();
    final List<Block> newBlocks = blockDataGenerator.blockSequence(head, 1);
    final Block newBlock = newBlocks.get(0);
    final long newBlockNumber = initialBlocks + 1;
    final Hash newHash = newBlock.getHeader().getHash();
    when(trieLogManager.getTrieLogLayer(newHash))
        .thenReturn(Optional.of(createAccountTrieLog(Wei.fromHexString("0xABC"))));

    // Now append — the observer will fire and find the mock ready
    blockchain.appendBlock(newBlock, blockDataGenerator.receipts(newBlock));

    allowMigrationToFinishLatch.countDown();
    future.get(30, TimeUnit.SECONDS);

    assertThat(getArchivedAccountKey(newBlockNumber)).isPresent();
  }

  @Test
  public void targetFrozenInTailMode() throws Exception {
    final int initialBlocks = BonsaiFlatDbToArchiveMigrator.NEAR_HEAD_THRESHOLD + 5;
    appendBlocks(initialBlocks);

    final long pauseBlock = initialBlocks - BonsaiFlatDbToArchiveMigrator.NEAR_HEAD_THRESHOLD + 1;
    final Hash pauseHash = blockchain.getBlockHeader(pauseBlock).get().getHash();

    final CountDownLatch migrationReachedTailLatch = new CountDownLatch(1);
    final CountDownLatch allowMigrationToFinishLatch = new CountDownLatch(1);

    when(trieLogManager.getTrieLogLayer(pauseHash))
        .thenAnswer(
            invocation -> {
              migrationReachedTailLatch.countDown();
              allowMigrationToFinishLatch.await(10, TimeUnit.SECONDS);
              return Optional.of(createAccountTrieLog(Wei.fromHexString("0x999")));
            });

    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();
    final CompletableFuture<Void> future = migrator.migrate();

    assertThat(migrationReachedTailLatch.await(10, TimeUnit.SECONDS)).isTrue();

    appendBlocks(3);

    allowMigrationToFinishLatch.countDown();
    future.get(30, TimeUnit.SECONDS);

    assertThat(migrator.getMigrationProgress()).isPresent();
    assertThat(migrator.getMigrationProgress().get()).isEqualTo((long) initialBlocks);
  }

  @Test
  public void observerFailureDoesNotCrashMigration() throws Exception {
    final int initialBlocks = BonsaiFlatDbToArchiveMigrator.NEAR_HEAD_THRESHOLD + 5;
    appendBlocks(initialBlocks);

    final long pauseBlock = initialBlocks - BonsaiFlatDbToArchiveMigrator.NEAR_HEAD_THRESHOLD + 1;
    final Hash pauseHash = blockchain.getBlockHeader(pauseBlock).get().getHash();

    final CountDownLatch migrationReachedTailLatch = new CountDownLatch(1);
    final CountDownLatch allowMigrationToFinishLatch = new CountDownLatch(1);

    when(trieLogManager.getTrieLogLayer(pauseHash))
        .thenAnswer(
            invocation -> {
              migrationReachedTailLatch.countDown();
              allowMigrationToFinishLatch.await(10, TimeUnit.SECONDS);
              return Optional.of(createAccountTrieLog(Wei.fromHexString("0x999")));
            });

    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();
    final CompletableFuture<Void> future = migrator.migrate();

    assertThat(migrationReachedTailLatch.await(10, TimeUnit.SECONDS)).isTrue();

    // Pre-generate block so we can set up the throwing mock BEFORE appending
    final Block head = blockchain.getBlockByNumber(blockchain.getChainHeadBlockNumber()).get();
    final List<Block> newBlocks = blockDataGenerator.blockSequence(head, 1);
    final Block failBlock = newBlocks.get(0);
    final Hash failHash = failBlock.getHeader().getHash();
    when(trieLogManager.getTrieLogLayer(failHash))
        .thenThrow(new RuntimeException("Observer failure"));

    // Now append — the observer will fire, hit the exception, and catch it
    blockchain.appendBlock(failBlock, blockDataGenerator.receipts(failBlock));

    // Let migration finish — should complete despite observer failure
    allowMigrationToFinishLatch.countDown();
    future.get(30, TimeUnit.SECONDS);

    // Migration should have completed successfully
    assertThat(migrator.getMigrationProgress()).isPresent();
    verify(worldStateStorage).upgradeToFullFlatDbMode();
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
    for (Block block : blocks) {
      blockchain.appendBlock(block, blockDataGenerator.receipts(block));
    }
  }

  private BonsaiFlatDbToArchiveMigrator createMigrator() {
    final NoOpMetricsSystem metricsSystem = new NoOpMetricsSystem();
    final BonsaiArchiveFlatDbStrategy archiveStrategy =
        new BonsaiArchiveFlatDbStrategy(metricsSystem, new CodeHashCodeStorageStrategy());
    return new BonsaiFlatDbToArchiveMigrator(
        worldStateStorage,
        trieLogManager,
        blockchain,
        executorService,
        metricsSystem,
        archiveStrategy);
  }

  private TrieLogLayer createAccountTrieLog(final Wei balance) {
    final TrieLogLayer trieLog = new TrieLogLayer();
    final PmtStateTrieAccountValue value =
        new PmtStateTrieAccountValue(1, balance, Hash.EMPTY, Hash.EMPTY);
    trieLog.addAccountChange(TEST_ADDRESS, null, value);
    return trieLog;
  }

  private Optional<byte[]> getArchivedAccountKey(final long blockNumber) {
    final byte[] key =
        BonsaiArchiveFlatDbStrategy.calculateArchiveKeyWithMinSuffix(
            new BonsaiContext(blockNumber), TEST_ADDRESS.addressHash().getBytes().toArrayUnsafe());
    return storage.get(ACCOUNT_INFO_STATE, key);
  }
}
