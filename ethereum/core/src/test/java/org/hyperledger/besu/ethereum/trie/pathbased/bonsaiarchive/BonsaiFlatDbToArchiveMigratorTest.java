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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
    worldStateStorage = Mockito.mock(BonsaiWorldStateKeyValueStorage.class);
    trieLogManager = Mockito.mock(TrieLogManager.class);
    executorService = Executors.newScheduledThreadPool(1);
    blockDataGenerator = new BlockDataGenerator();
    blockchain = createInMemoryBlockchain(blockDataGenerator.genesisBlock());
    when(worldStateStorage.getComposedWorldStateStorage()).thenReturn(storage);
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

    migrator.migrate(1L, 2L).get(10, TimeUnit.SECONDS);

    // Verify account state was written for both blocks
    assertThat(getArchivedAccountKey(1L)).isPresent();
    assertThat(getArchivedAccountKey(2L)).isPresent();
    assertThat(migrator.getSyncState()).isEqualTo(ArchiveSyncState.READY);
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

    migrator.migrate(1L, 1L).get(10, TimeUnit.SECONDS);

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
  public void notifiesListenersOnCompletion() throws Exception {
    when(trieLogManager.getTrieLogLayer(Mockito.any())).thenReturn(Optional.empty());

    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();

    final AtomicBoolean completionCalled = new AtomicBoolean(false);
    final CountDownLatch latch = new CountDownLatch(1);

    migrator.subscribe(
        new BonsaiFlatDbToArchiveMigrator.MigrationCompletionListener() {
          @Override
          public void onMigrationComplete() {
            completionCalled.set(true);
            latch.countDown();
          }

          @Override
          public void onMigrationFailed(final Throwable error) {
            latch.countDown();
          }
        });

    migrator.migrate(0L, 0L);

    assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    assertThat(completionCalled.get()).isTrue();
  }

  @Test
  public void notifiesListenersOnFailure() throws Exception {
    when(trieLogManager.getTrieLogLayer(Mockito.any()))
        .thenThrow(new RuntimeException("Test failure"));

    appendBlocks(1);

    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();

    final AtomicBoolean failureCalled = new AtomicBoolean(false);
    final AtomicReference<Throwable> capturedError = new AtomicReference<>();
    final CountDownLatch latch = new CountDownLatch(1);

    migrator.subscribe(
        new BonsaiFlatDbToArchiveMigrator.MigrationCompletionListener() {
          @Override
          public void onMigrationComplete() {
            latch.countDown();
          }

          @Override
          public void onMigrationFailed(final Throwable error) {
            failureCalled.set(true);
            capturedError.set(error);
            latch.countDown();
          }
        });

    migrator.migrate(1L, 1L);

    assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    assertThat(failureCalled.get()).isTrue();
    assertThat(capturedError.get()).isNotNull();
  }

  @Test
  public void rejectsConcurrentMigrations() throws Exception {
    final CountDownLatch migrationStartedLatch = new CountDownLatch(1);
    final CountDownLatch allowMigrationToFinishLatch = new CountDownLatch(1);

    appendBlocks(1);

    // Block the trie log manager to pause migration
    when(trieLogManager.getTrieLogLayer(Mockito.any()))
        .thenAnswer(
            invocation -> {
              migrationStartedLatch.countDown();
              allowMigrationToFinishLatch.await(10, TimeUnit.SECONDS);
              return Optional.empty();
            });

    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();

    // Start first migration
    final CompletableFuture<Void> first = migrator.migrate(1L, 1L);

    // Wait for migration to start
    assertThat(migrationStartedLatch.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(migrator.isMigrationRunning()).isTrue();

    // Second migration should return immediately without running
    final CompletableFuture<Void> second = migrator.migrate(0L, 10L);
    second.get(1, TimeUnit.SECONDS);

    // Allow first migration to complete
    allowMigrationToFinishLatch.countDown();
    first.get(10, TimeUnit.SECONDS);

    assertThat(migrator.isMigrationRunning()).isFalse();
  }

  @Test
  public void tracksRunningState() throws Exception {
    when(trieLogManager.getTrieLogLayer(Mockito.any())).thenReturn(Optional.empty());

    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();
    assertThat(migrator.isMigrationRunning()).isFalse();

    migrator.migrate(0L, 0L).get(10, TimeUnit.SECONDS);
    assertThat(migrator.isMigrationRunning()).isFalse();
  }

  @Test
  public void savesProgressToStorage() throws Exception {
    appendBlocks(5);

    when(trieLogManager.getTrieLogLayer(Mockito.any())).thenReturn(Optional.empty());

    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();
    assertThat(migrator.getMigrationProgress()).isEmpty();

    migrator.migrate(0L, 5L).get(10, TimeUnit.SECONDS);
    assertThat(migrator.getMigrationProgress()).isPresent();
    assertThat(migrator.getMigrationProgress().get()).isEqualTo(5L);
  }

  @Test
  public void transitionsToReadyStateOnCompletion() throws Exception {
    when(trieLogManager.getTrieLogLayer(Mockito.any())).thenReturn(Optional.empty());

    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();
    assertThat(migrator.getSyncState()).isEqualTo(ArchiveSyncState.SYNCING);

    migrator.migrate(0L, 0L).get(10, TimeUnit.SECONDS);
    assertThat(migrator.getSyncState()).isEqualTo(ArchiveSyncState.READY);
  }

  @Test
  public void skipsBlocksWithNoTrieLog() throws Exception {
    appendBlocks(2);

    final Hash hash1 = blockchain.getBlockHeader(1L).get().getHash();
    final Hash hash2 = blockchain.getBlockHeader(2L).get().getHash();

    // No trie log for block 1, only for block 2
    when(trieLogManager.getTrieLogLayer(hash1)).thenReturn(Optional.empty());

    final TrieLogLayer trieLog2 = createAccountTrieLog(Wei.ONE);
    when(trieLogManager.getTrieLogLayer(hash2)).thenReturn(Optional.of(trieLog2));

    final BonsaiFlatDbToArchiveMigrator migrator = createMigrator();
    migrator.migrate(1L, 2L).get(10, TimeUnit.SECONDS);

    // Only block 2 should have state written
    assertThat(getArchivedAccountKey(2L)).isPresent();
    assertThat(migrator.getSyncState()).isEqualTo(ArchiveSyncState.READY);
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
    final List<Block> blocks =
        blockDataGenerator.blockSequence(blockchain.getGenesisBlock(), count);
    for (Block block : blocks) {
      blockchain.appendBlock(block, blockDataGenerator.receipts(block));
    }
  }

  private BonsaiFlatDbToArchiveMigrator createMigrator() {
    return new BonsaiFlatDbToArchiveMigrator(
        worldStateStorage, trieLogManager, blockchain, executorService, new NoOpMetricsSystem());
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
