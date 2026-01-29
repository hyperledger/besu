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
package org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.CODE_STORAGE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.worldstate.ImmutableDataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.ImmutablePathBasedExtraStorageConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BonsaiCachedSnapshotWorldStateStorageTest {

  private BonsaiWorldStateKeyValueStorage parentStorage;
  private BonsaiCachedWorldStateStorage liveStorage;

  @BeforeEach
  public void setup() {
    parentStorage =
        new BonsaiWorldStateKeyValueStorage(
            new InMemoryKeyValueStorageProvider(),
            new NoOpMetricsSystem(),
            ImmutableDataStorageConfiguration.builder()
                .dataStorageFormat(DataStorageFormat.X_BONSAI_ARCHIVE)
                .pathBasedExtraStorageConfiguration(
                    ImmutablePathBasedExtraStorageConfiguration.builder()
                        .maxLayersToLoad(3L)
                        .limitTrieLogsEnabled(true)
                        .unstable(
                            ImmutablePathBasedExtraStorageConfiguration.PathBasedUnstable.builder()
                                .codeStoredByCodeHashEnabled(true)
                                .build())
                        .build())
                .build());

    liveStorage =
        new BonsaiCachedWorldStateStorage(
            parentStorage, 1000, 1000, 1000, 1000, new NoOpMetricsSystem());
  }

  @Test
  public void testSnapshot_isolatesChanges() {
    Hash accountHash = Hash.hash(Bytes.of(1));
    Bytes originalData = Bytes.of(1, 2, 3);
    Bytes updatedData = Bytes.of(4, 5, 6);

    BonsaiWorldStateKeyValueStorage.Updater parentUpdater = parentStorage.updater();
    parentUpdater.putAccountInfoState(accountHash, originalData);
    parentUpdater.commit();

    liveStorage.getAccount(accountHash);
    assertThat(liveStorage.isCached(ACCOUNT_INFO_STATE, accountHash.getBytes().toArrayUnsafe()))
        .isTrue();

    BonsaiSnapshotWorldStateStorage snapshot = liveStorage.createSnapshot();
    long snapshotVersion = liveStorage.getCurrentVersion();

    BonsaiCachedWorldStateStorage.CachedUpdater updater =
        (BonsaiCachedWorldStateStorage.CachedUpdater) liveStorage.updater();
    updater.putAccountInfoState(accountHash, updatedData);
    updater.commit();

    long newVersion = liveStorage.getCurrentVersion();
    assertThat(newVersion).isGreaterThan(snapshotVersion);

    assertThat(liveStorage.getAccount(accountHash)).isPresent().contains(updatedData);
    assertThat(snapshot.getAccount(accountHash)).isPresent().contains(originalData);

    Optional<BonsaiCachedWorldStateStorage.VersionedValue> liveValue =
        liveStorage.getCachedValue(ACCOUNT_INFO_STATE, accountHash.getBytes().toArrayUnsafe());
    assertThat(liveValue).isPresent();
    assertThat(Bytes.wrap(liveValue.get().value)).isEqualTo(updatedData);
    assertThat(liveValue.get().version).isEqualTo(newVersion);
  }

  @Test
  public void testSnapshot_seesOnlyCommittedChanges() {
    Hash account1 = Hash.hash(Bytes.of(1));
    Hash account2 = Hash.hash(Bytes.of(2));
    Bytes data1 = Bytes.of(1, 1, 1);
    Bytes data2 = Bytes.of(2, 2, 2);

    BonsaiCachedWorldStateStorage.CachedUpdater updater1 =
        (BonsaiCachedWorldStateStorage.CachedUpdater) liveStorage.updater();
    updater1.putAccountInfoState(account1, data1);
    updater1.commit();

    assertThat(liveStorage.isCached(ACCOUNT_INFO_STATE, account1.getBytes().toArrayUnsafe()))
        .isTrue();
    long v1 = liveStorage.getCurrentVersion();

    BonsaiSnapshotWorldStateStorage snapshot = liveStorage.createSnapshot();

    BonsaiCachedWorldStateStorage.CachedUpdater updater2 =
        (BonsaiCachedWorldStateStorage.CachedUpdater) liveStorage.updater();
    updater2.putAccountInfoState(account2, data2);
    updater2.commit();

    long v2 = liveStorage.getCurrentVersion();
    assertThat(v2).isGreaterThan(v1);

    assertThat(snapshot.getAccount(account1)).isPresent().contains(data1);
    assertThat(snapshot.getAccount(account2)).isEmpty();

    assertThat(liveStorage.getAccount(account1)).isPresent().contains(data1);
    assertThat(liveStorage.getAccount(account2)).isPresent().contains(data2);

    assertThat(liveStorage.getCacheSize(ACCOUNT_INFO_STATE)).isEqualTo(2);
    assertThat(liveStorage.isCached(ACCOUNT_INFO_STATE, account1.getBytes().toArrayUnsafe()))
        .isTrue();
    assertThat(liveStorage.isCached(ACCOUNT_INFO_STATE, account2.getBytes().toArrayUnsafe()))
        .isTrue();
  }

  @Test
  public void testMultipleSnapshots_independent() {
    Hash accountHash = Hash.hash(Bytes.of(1));
    Bytes data1 = Bytes.of(1);
    Bytes data2 = Bytes.of(2);
    Bytes data3 = Bytes.of(3);

    BonsaiWorldStateKeyValueStorage.Updater parentUpdater = parentStorage.updater();
    parentUpdater.putAccountInfoState(accountHash, data1);
    parentUpdater.commit();

    liveStorage.getAccount(accountHash);
    long v1 = liveStorage.getCurrentVersion();
    BonsaiSnapshotWorldStateStorage snapshot1 = liveStorage.createSnapshot();

    BonsaiCachedWorldStateStorage.CachedUpdater updater1 =
        (BonsaiCachedWorldStateStorage.CachedUpdater) liveStorage.updater();
    updater1.putAccountInfoState(accountHash, data2);
    updater1.commit();

    long v2 = liveStorage.getCurrentVersion();
    assertThat(v2).isGreaterThan(v1);
    BonsaiSnapshotWorldStateStorage snapshot2 = liveStorage.createSnapshot();

    BonsaiCachedWorldStateStorage.CachedUpdater updater2 =
        (BonsaiCachedWorldStateStorage.CachedUpdater) liveStorage.updater();
    updater2.putAccountInfoState(accountHash, data3);
    updater2.commit();

    long v3 = liveStorage.getCurrentVersion();
    assertThat(v3).isGreaterThan(v2);

    assertThat(snapshot1.getAccount(accountHash)).isPresent().contains(data1);
    assertThat(snapshot2.getAccount(accountHash)).isPresent().contains(data2);
    assertThat(liveStorage.getAccount(accountHash)).isPresent().contains(data3);

    Optional<BonsaiCachedWorldStateStorage.VersionedValue> cachedValue =
        liveStorage.getCachedValue(ACCOUNT_INFO_STATE, accountHash.getBytes().toArrayUnsafe());
    assertThat(cachedValue).isPresent();
    assertThat(cachedValue.get().version).isEqualTo(v3);
  }

  @Test
  public void testSnapshot_storageSlotIsolation() {
    Hash accountHash = Hash.hash(Bytes.of(1));
    StorageSlotKey slotKey = new StorageSlotKey(UInt256.fromBytes(Bytes.of(2)));
    Bytes originalValue = Bytes.of(10);
    Bytes updatedValue = Bytes.of(20);
    Bytes concatenatedKey =
        Bytes.concatenate(accountHash.getBytes(), slotKey.getSlotHash().getBytes());

    BonsaiWorldStateKeyValueStorage.Updater parentUpdater = parentStorage.updater();
    parentUpdater.putStorageValueBySlotHash(accountHash, slotKey.getSlotHash(), originalValue);
    parentUpdater.commit();

    liveStorage.getStorageValueByStorageSlotKey(accountHash, slotKey);
    assertThat(liveStorage.isCached(ACCOUNT_STORAGE_STORAGE, concatenatedKey.toArrayUnsafe()))
        .isTrue();

    long v1 = liveStorage.getCurrentVersion();
    BonsaiSnapshotWorldStateStorage snapshot = liveStorage.createSnapshot();

    BonsaiCachedWorldStateStorage.CachedUpdater updater =
        (BonsaiCachedWorldStateStorage.CachedUpdater) liveStorage.updater();
    updater.putStorageValueBySlotHash(accountHash, slotKey.getSlotHash(), updatedValue);
    updater.commit();

    long v2 = liveStorage.getCurrentVersion();
    assertThat(v2).isGreaterThan(v1);

    assertThat(liveStorage.getStorageValueByStorageSlotKey(accountHash, slotKey))
        .isPresent()
        .contains(updatedValue);
    assertThat(snapshot.getStorageValueByStorageSlotKey(accountHash, slotKey))
        .isPresent()
        .contains(originalValue);

    Optional<BonsaiCachedWorldStateStorage.VersionedValue> liveValue =
        liveStorage.getCachedValue(ACCOUNT_STORAGE_STORAGE, concatenatedKey.toArrayUnsafe());
    assertThat(liveValue).isPresent();
    assertThat(Bytes.wrap(liveValue.get().value)).isEqualTo(updatedValue);
    assertThat(liveValue.get().version).isEqualTo(v2);
  }

  @Test
  public void testSnapshot_accountRemovalIsolation() {
    Hash accountHash = Hash.hash(Bytes.of(1));
    Bytes accountData = Bytes.of(1, 2, 3);

    BonsaiWorldStateKeyValueStorage.Updater parentUpdater = parentStorage.updater();
    parentUpdater.putAccountInfoState(accountHash, accountData);
    parentUpdater.commit();

    liveStorage.getAccount(accountHash);
    assertThat(liveStorage.isCached(ACCOUNT_INFO_STATE, accountHash.getBytes().toArrayUnsafe()))
        .isTrue();

    long v1 = liveStorage.getCurrentVersion();
    BonsaiSnapshotWorldStateStorage snapshot = liveStorage.createSnapshot();

    BonsaiCachedWorldStateStorage.CachedUpdater updater =
        (BonsaiCachedWorldStateStorage.CachedUpdater) liveStorage.updater();
    updater.removeAccountInfoState(accountHash);
    updater.commit();

    long v2 = liveStorage.getCurrentVersion();
    assertThat(v2).isGreaterThan(v1);

    assertThat(liveStorage.getAccount(accountHash)).isEmpty();
    assertThat(snapshot.getAccount(accountHash)).isPresent().contains(accountData);

    Optional<BonsaiCachedWorldStateStorage.VersionedValue> liveValue =
        liveStorage.getCachedValue(ACCOUNT_INFO_STATE, accountHash.getBytes().toArrayUnsafe());
    assertThat(liveValue).isPresent();
    assertThat(liveValue.get().isRemoval).isTrue();
    assertThat(liveValue.get().version).isEqualTo(v2);
  }

  @Test
  public void testSnapshot_codeHandling() {
    Hash accountHash = Hash.hash(Bytes.of(1));
    Hash codeHash = Hash.hash(Bytes.of(10, 20, 30));
    Bytes codeData = Bytes.of(10, 20, 30);

    BonsaiWorldStateKeyValueStorage.Updater parentUpdater = parentStorage.updater();
    parentUpdater.putCode(accountHash, codeHash, codeData);
    parentUpdater.commit();

    liveStorage.getCode(codeHash, accountHash);
    assertThat(liveStorage.isCached(CODE_STORAGE, accountHash.getBytes().toArrayUnsafe())).isTrue();

    BonsaiSnapshotWorldStateStorage snapshot = liveStorage.createSnapshot();

    assertThat(snapshot.getCode(codeHash, accountHash)).isPresent().contains(codeData);
    assertThat(snapshot.getCode(Hash.EMPTY, accountHash)).isPresent().contains(Bytes.EMPTY);
  }

  @Test
  public void testSnapshot_trieNodeIsolation() {
    Bytes location = Bytes.of(1, 2);
    Bytes nodeData1 = Bytes.of(10, 20, 30);
    Bytes32 nodeHash1 = Bytes32.wrap(Hash.hash(nodeData1).getBytes());
    Bytes nodeData2 = Bytes.of(40, 50, 60);
    Bytes32 nodeHash2 = Bytes32.wrap(Hash.hash(nodeData2).getBytes());

    BonsaiWorldStateKeyValueStorage.Updater parentUpdater = parentStorage.updater();
    parentUpdater.putAccountStateTrieNode(location, nodeHash1, nodeData1);
    parentUpdater.commit();

    liveStorage.getAccountStateTrieNode(location, nodeHash1);
    assertThat(liveStorage.isCached(TRIE_BRANCH_STORAGE, nodeHash1.toArrayUnsafe())).isTrue();

    BonsaiSnapshotWorldStateStorage snapshot = liveStorage.createSnapshot();

    BonsaiCachedWorldStateStorage.CachedUpdater updater =
        (BonsaiCachedWorldStateStorage.CachedUpdater) liveStorage.updater();
    updater.putAccountStateTrieNode(location, nodeHash2, nodeData2);
    updater.commit();

    assertThat(liveStorage.getAccountStateTrieNode(location, nodeHash2))
        .isPresent()
        .contains(nodeData2);
    assertThat(snapshot.getAccountStateTrieNode(location, nodeHash1))
        .isPresent()
        .contains(nodeData1);
    assertThat(snapshot.getAccountStateTrieNode(location, nodeHash2)).isEmpty();
  }

  @Test
  public void testSnapshot_storageTrieNodeIsolation() {
    Hash accountHash = Hash.hash(Bytes.of(1));
    Bytes location = Bytes.of(2, 3);
    Bytes nodeData1 = Bytes.of(30, 40, 50);
    Bytes32 nodeHash1 = Bytes32.wrap(Hash.hash(nodeData1).getBytes());
    Bytes nodeData2 = Bytes.of(60, 70, 80);
    Bytes32 nodeHash2 = Bytes32.wrap(Hash.hash(nodeData2).getBytes());

    BonsaiWorldStateKeyValueStorage.Updater parentUpdater = parentStorage.updater();
    parentUpdater.putAccountStorageTrieNode(accountHash, location, nodeHash1, nodeData1);
    parentUpdater.commit();

    liveStorage.getAccountStorageTrieNode(accountHash, location, nodeHash1);
    assertThat(liveStorage.isCached(TRIE_BRANCH_STORAGE, nodeHash1.toArrayUnsafe())).isTrue();

    BonsaiSnapshotWorldStateStorage snapshot = liveStorage.createSnapshot();

    BonsaiCachedWorldStateStorage.CachedUpdater updater =
        (BonsaiCachedWorldStateStorage.CachedUpdater) liveStorage.updater();
    updater.putAccountStorageTrieNode(accountHash, location, nodeHash2, nodeData2);
    updater.commit();

    assertThat(liveStorage.getAccountStorageTrieNode(accountHash, location, nodeHash2))
        .isPresent()
        .contains(nodeData2);
    assertThat(snapshot.getAccountStorageTrieNode(accountHash, location, nodeHash1))
        .isPresent()
        .contains(nodeData1);
    assertThat(snapshot.getAccountStorageTrieNode(accountHash, location, nodeHash2)).isEmpty();
  }

  @Test
  public void testSnapshot_multipleReadsFromSnapshot() {
    Hash accountHash = Hash.hash(Bytes.of(1));
    Bytes accountData = Bytes.of(1, 2, 3);

    BonsaiWorldStateKeyValueStorage.Updater parentUpdater = parentStorage.updater();
    parentUpdater.putAccountInfoState(accountHash, accountData);
    parentUpdater.commit();

    liveStorage.getAccount(accountHash);
    BonsaiSnapshotWorldStateStorage snapshot = liveStorage.createSnapshot();

    Optional<Bytes> result1 = snapshot.getAccount(accountHash);
    Optional<Bytes> result2 = snapshot.getAccount(accountHash);
    Optional<Bytes> result3 = snapshot.getAccount(accountHash);

    assertThat(result1).isPresent().contains(accountData);
    assertThat(result2).isPresent().contains(accountData);
    assertThat(result3).isPresent().contains(accountData);
    assertThat(liveStorage.getCacheSize(ACCOUNT_INFO_STATE)).isEqualTo(1);
  }

  @Test
  public void testSnapshot_withStorageRootSupplier() {
    Hash accountHash = Hash.hash(Bytes.of(1));
    StorageSlotKey slotKey = new StorageSlotKey(UInt256.fromBytes(Bytes.of(2)));
    Bytes storageValue = Bytes.of(10);
    Hash storageRoot = Hash.hash(Bytes.of(100));

    BonsaiWorldStateKeyValueStorage.Updater parentUpdater = parentStorage.updater();
    parentUpdater.putStorageValueBySlotHash(accountHash, slotKey.getSlotHash(), storageValue);
    parentUpdater.commit();

    liveStorage.getStorageValueByStorageSlotKey(
        () -> Optional.of(storageRoot), accountHash, slotKey);

    BonsaiSnapshotWorldStateStorage snapshot = liveStorage.createSnapshot();

    Optional<Bytes> result =
        snapshot.getStorageValueByStorageSlotKey(
            () -> Optional.of(storageRoot), accountHash, slotKey);

    assertThat(result).isPresent().contains(storageValue);
  }

  @Test
  public void testSnapshot_sequentialVersionProgression() {
    Hash account = Hash.hash(Bytes.of(1));

    long v0 = liveStorage.getCurrentVersion();
    BonsaiSnapshotWorldStateStorage snap0 = liveStorage.createSnapshot();

    BonsaiCachedWorldStateStorage.CachedUpdater updater1 =
        (BonsaiCachedWorldStateStorage.CachedUpdater) liveStorage.updater();
    updater1.putAccountInfoState(account, Bytes.of(1));
    updater1.commit();
    long v1 = liveStorage.getCurrentVersion();
    BonsaiSnapshotWorldStateStorage snap1 = liveStorage.createSnapshot();

    BonsaiCachedWorldStateStorage.CachedUpdater updater2 =
        (BonsaiCachedWorldStateStorage.CachedUpdater) liveStorage.updater();
    updater2.putAccountInfoState(account, Bytes.of(2));
    updater2.commit();
    long v2 = liveStorage.getCurrentVersion();
    BonsaiSnapshotWorldStateStorage snap2 = liveStorage.createSnapshot();

    assertThat(v1).isEqualTo(v0 + 1);
    assertThat(v2).isEqualTo(v1 + 1);

    assertThat(snap0.getAccount(account)).isEmpty();
    assertThat(snap1.getAccount(account)).isPresent().contains(Bytes.of(1));
    assertThat(snap2.getAccount(account)).isPresent().contains(Bytes.of(2));
  }

  // ========== TESTS ADDITIONNELS ==========

  @Test
  public void testSnapshot_codeRemovalIsolation() {
    Hash accountHash = Hash.hash(Bytes.of(1));
    Hash codeHash = Hash.hash(Bytes.of(10, 20, 30));
    Bytes codeData = Bytes.of(10, 20, 30);

    BonsaiWorldStateKeyValueStorage.Updater parentUpdater = parentStorage.updater();
    parentUpdater.putCode(accountHash, codeHash, codeData);
    parentUpdater.commit();

    liveStorage.getCode(codeHash, accountHash);
    assertThat(liveStorage.isCached(CODE_STORAGE, accountHash.getBytes().toArrayUnsafe())).isTrue();

    BonsaiSnapshotWorldStateStorage snapshot = liveStorage.createSnapshot();

    BonsaiCachedWorldStateStorage.CachedUpdater updater =
        (BonsaiCachedWorldStateStorage.CachedUpdater) liveStorage.updater();
    updater.removeCode(accountHash, codeHash);
    updater.commit();

    assertThat(liveStorage.getCode(codeHash, accountHash)).isEmpty();
    assertThat(snapshot.getCode(codeHash, accountHash)).isPresent().contains(codeData);

    Optional<BonsaiCachedWorldStateStorage.VersionedValue> liveValue =
        liveStorage.getCachedValue(CODE_STORAGE, accountHash.getBytes().toArrayUnsafe());
    assertThat(liveValue).isPresent();
    assertThat(liveValue.get().isRemoval).isTrue();
  }

  @Test
  public void testSnapshot_storageRemovalIsolation() {
    Hash accountHash = Hash.hash(Bytes.of(1));
    StorageSlotKey slotKey = new StorageSlotKey(UInt256.fromBytes(Bytes.of(2)));
    Bytes storageValue = Bytes.of(10);
    Bytes concatenatedKey =
        Bytes.concatenate(accountHash.getBytes(), slotKey.getSlotHash().getBytes());

    BonsaiWorldStateKeyValueStorage.Updater parentUpdater = parentStorage.updater();
    parentUpdater.putStorageValueBySlotHash(accountHash, slotKey.getSlotHash(), storageValue);
    parentUpdater.commit();

    liveStorage.getStorageValueByStorageSlotKey(accountHash, slotKey);
    BonsaiSnapshotWorldStateStorage snapshot = liveStorage.createSnapshot();

    BonsaiCachedWorldStateStorage.CachedUpdater updater =
        (BonsaiCachedWorldStateStorage.CachedUpdater) liveStorage.updater();
    updater.removeStorageValueBySlotHash(accountHash, slotKey.getSlotHash());
    updater.commit();

    assertThat(liveStorage.getStorageValueByStorageSlotKey(accountHash, slotKey)).isEmpty();
    assertThat(snapshot.getStorageValueByStorageSlotKey(accountHash, slotKey))
        .isPresent()
        .contains(storageValue);

    Optional<BonsaiCachedWorldStateStorage.VersionedValue> liveValue =
        liveStorage.getCachedValue(ACCOUNT_STORAGE_STORAGE, concatenatedKey.toArrayUnsafe());
    assertThat(liveValue).isPresent();
    assertThat(liveValue.get().isRemoval).isTrue();
  }

  @Test
  public void testSnapshot_readThroughCaching() {
    Hash accountHash = Hash.hash(Bytes.of(1));
    Bytes accountData = Bytes.of(1, 2, 3);

    // Put data only in parent, not in live cache
    BonsaiWorldStateKeyValueStorage.Updater parentUpdater = parentStorage.updater();
    parentUpdater.putAccountInfoState(accountHash, accountData);
    parentUpdater.commit();

    // Verify not cached yet
    assertThat(liveStorage.isCached(ACCOUNT_INFO_STATE, accountHash.getBytes().toArrayUnsafe()))
        .isFalse();

    BonsaiSnapshotWorldStateStorage snapshot = liveStorage.createSnapshot();

    // Read through snapshot should cache in live storage
    Optional<Bytes> result = snapshot.getAccount(accountHash);
    assertThat(result).isPresent().contains(accountData);

    // Should now be cached
    assertThat(liveStorage.isCached(ACCOUNT_INFO_STATE, accountHash.getBytes().toArrayUnsafe()))
        .isTrue();
  }

  @Test
  public void testSnapshot_negativeCaching() {
    Hash accountHash = Hash.hash(Bytes.of(10));

    // Try to read non-existent account
    Optional<Bytes> result1 = liveStorage.getAccount(accountHash);
    assertThat(result1).isEmpty();

    // Should cache the negative result
    assertThat(liveStorage.isCached(ACCOUNT_INFO_STATE, accountHash.getBytes().toArrayUnsafe()))
        .isTrue();
    Optional<BonsaiCachedWorldStateStorage.VersionedValue> cachedValue =
        liveStorage.getCachedValue(ACCOUNT_INFO_STATE, accountHash.getBytes().toArrayUnsafe());
    assertThat(cachedValue).isPresent();
    assertThat(cachedValue.get().isRemoval).isTrue();

    // Create snapshot after negative caching
    BonsaiSnapshotWorldStateStorage snapshot = liveStorage.createSnapshot();

    // Snapshot should also see empty result (from cache)
    Optional<Bytes> result2 = snapshot.getAccount(accountHash);
    assertThat(result2).isEmpty();
  }

  @Test
  public void testSnapshot_cacheSizeTracking() {
    Hash acc1 = Hash.hash(Bytes.of(1));
    Hash acc2 = Hash.hash(Bytes.of(2));
    Hash acc3 = Hash.hash(Bytes.of(3));

    assertThat(liveStorage.getCacheSize(ACCOUNT_INFO_STATE)).isEqualTo(0);

    BonsaiCachedWorldStateStorage.CachedUpdater updater1 =
        (BonsaiCachedWorldStateStorage.CachedUpdater) liveStorage.updater();
    updater1.putAccountInfoState(acc1, Bytes.of(1));
    updater1.putAccountInfoState(acc2, Bytes.of(2));
    updater1.commit();

    assertThat(liveStorage.getCacheSize(ACCOUNT_INFO_STATE)).isEqualTo(2);

    BonsaiSnapshotWorldStateStorage snapshot = liveStorage.createSnapshot();

    BonsaiCachedWorldStateStorage.CachedUpdater updater2 =
        (BonsaiCachedWorldStateStorage.CachedUpdater) liveStorage.updater();
    updater2.putAccountInfoState(acc3, Bytes.of(3));
    updater2.commit();

    assertThat(liveStorage.getCacheSize(ACCOUNT_INFO_STATE)).isEqualTo(3);

    // Snapshot reads don't change cache size
    snapshot.getAccount(acc1);
    snapshot.getAccount(acc2);
    assertThat(liveStorage.getCacheSize(ACCOUNT_INFO_STATE)).isEqualTo(3);
  }

  @Test
  public void testSnapshot_snapshotDoesNotUpdateCacheForOldVersions() {
    Hash accountHash = Hash.hash(Bytes.of(1));
    Bytes data1 = Bytes.of(1);
    Bytes data2 = Bytes.of(2);

    // Commit data1
    BonsaiCachedWorldStateStorage.CachedUpdater updater1 =
        (BonsaiCachedWorldStateStorage.CachedUpdater) liveStorage.updater();
    updater1.putAccountInfoState(accountHash, data1);
    updater1.commit();

    BonsaiSnapshotWorldStateStorage snapshot = liveStorage.createSnapshot();

    // Commit data2 (snapshot still at v1)
    BonsaiCachedWorldStateStorage.CachedUpdater updater2 =
        (BonsaiCachedWorldStateStorage.CachedUpdater) liveStorage.updater();
    updater2.putAccountInfoState(accountHash, data2);
    updater2.commit();

    long v2 = liveStorage.getCurrentVersion();

    // Clear cache to force read from storage
    Optional<BonsaiCachedWorldStateStorage.VersionedValue> cachedValue =
        liveStorage.getCachedValue(ACCOUNT_INFO_STATE, accountHash.getBytes().toArrayUnsafe());
    assertThat(cachedValue).isPresent();
    assertThat(cachedValue.get().version).isEqualTo(v2);

    // Snapshot reading should NOT update cache since its version < current version
    Hash newAccountHash = Hash.hash(Bytes.of(10));
    BonsaiWorldStateKeyValueStorage.Updater parentUpdater = parentStorage.updater();
    parentUpdater.putAccountInfoState(newAccountHash, Bytes.of(99));
    parentUpdater.commit();

    snapshot.getAccount(newAccountHash);

    // Should not be cached because snapshot version != current version
    assertThat(liveStorage.isCached(ACCOUNT_INFO_STATE, newAccountHash.getBytes().toArrayUnsafe()))
        .isFalse();
  }

  @Test
  public void testSnapshot_emptyValueHandling() {
    Hash accountHash = Hash.hash(Bytes.of(1));

    // Try to put empty bytes (should be ignored)
    BonsaiCachedWorldStateStorage.CachedUpdater updater =
        (BonsaiCachedWorldStateStorage.CachedUpdater) liveStorage.updater();
    updater.putAccountInfoState(accountHash, Bytes.EMPTY);
    updater.commit();

    // Should not be cached (empty values are not stored)
    assertThat(liveStorage.getAccount(accountHash)).isEmpty();
  }

  @Test
  public void testSnapshot_concurrentSnapshots() {
    Hash accountHash = Hash.hash(Bytes.of(1));

    // Create multiple snapshots at same version
    BonsaiCachedWorldStateStorage.CachedUpdater updater =
        (BonsaiCachedWorldStateStorage.CachedUpdater) liveStorage.updater();
    updater.putAccountInfoState(accountHash, Bytes.of(1));
    updater.commit();

    BonsaiSnapshotWorldStateStorage snap1 = liveStorage.createSnapshot();
    BonsaiSnapshotWorldStateStorage snap2 = liveStorage.createSnapshot();
    BonsaiSnapshotWorldStateStorage snap3 = liveStorage.createSnapshot();

    // All snapshots should see same data
    assertThat(snap1.getAccount(accountHash)).isPresent().contains(Bytes.of(1));
    assertThat(snap2.getAccount(accountHash)).isPresent().contains(Bytes.of(1));
    assertThat(snap3.getAccount(accountHash)).isPresent().contains(Bytes.of(1));
  }

  @Test
  public void testSnapshot_rollbackDoesNotUpdateCache() {
    Hash accountHash = Hash.hash(Bytes.of(1));
    Bytes data = Bytes.of(1, 2, 3);

    long v1 = liveStorage.getCurrentVersion();

    BonsaiCachedWorldStateStorage.CachedUpdater updater =
        (BonsaiCachedWorldStateStorage.CachedUpdater) liveStorage.updater();
    updater.putAccountInfoState(accountHash, data);
    updater.rollback();

    // Version should not have incremented
    long v2 = liveStorage.getCurrentVersion();
    assertThat(v2).isEqualTo(v1);

    // Data should not be in cache
    assertThat(liveStorage.isCached(ACCOUNT_INFO_STATE, accountHash.getBytes().toArrayUnsafe()))
        .isFalse();
    assertThat(liveStorage.getAccount(accountHash)).isEmpty();
  }
}
