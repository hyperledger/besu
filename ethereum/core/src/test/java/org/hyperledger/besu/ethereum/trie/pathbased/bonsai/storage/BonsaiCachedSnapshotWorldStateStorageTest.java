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
    assertThat(liveStorage.isCached(ACCOUNT_INFO_STATE, accountHash)).isTrue();

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
        liveStorage.getCachedValue(ACCOUNT_INFO_STATE, accountHash);
    assertThat(liveValue).isPresent();
    assertThat(liveValue.get().value).isEqualTo(updatedData);
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

    assertThat(liveStorage.isCached(ACCOUNT_INFO_STATE, account1)).isTrue();
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
    assertThat(liveStorage.isCached(ACCOUNT_INFO_STATE, account1)).isTrue();
    assertThat(liveStorage.isCached(ACCOUNT_INFO_STATE, account2)).isTrue();
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
        liveStorage.getCachedValue(ACCOUNT_INFO_STATE, accountHash);
    assertThat(cachedValue).isPresent();
    assertThat(cachedValue.get().version).isEqualTo(v3);
  }

  @Test
  public void testSnapshot_storageSlotIsolation() {
    Hash accountHash = Hash.hash(Bytes.of(1));
    StorageSlotKey slotKey = new StorageSlotKey(UInt256.fromBytes(Bytes.of(2)));
    Bytes originalValue = Bytes.of(10);
    Bytes updatedValue = Bytes.of(20);
    Bytes concatenatedKey = Bytes.concatenate(accountHash, slotKey.getSlotHash());

    BonsaiWorldStateKeyValueStorage.Updater parentUpdater = parentStorage.updater();
    parentUpdater.putStorageValueBySlotHash(accountHash, slotKey.getSlotHash(), originalValue);
    parentUpdater.commit();

    liveStorage.getStorageValueByStorageSlotKey(accountHash, slotKey);
    assertThat(liveStorage.isCached(ACCOUNT_STORAGE_STORAGE, concatenatedKey)).isTrue();

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
        liveStorage.getCachedValue(ACCOUNT_STORAGE_STORAGE, concatenatedKey);
    assertThat(liveValue).isPresent();
    assertThat(liveValue.get().value).isEqualTo(updatedValue);
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
    assertThat(liveStorage.isCached(ACCOUNT_INFO_STATE, accountHash)).isTrue();

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
        liveStorage.getCachedValue(ACCOUNT_INFO_STATE, accountHash);
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
    assertThat(liveStorage.isCached(CODE_STORAGE, accountHash)).isTrue();

    BonsaiSnapshotWorldStateStorage snapshot = liveStorage.createSnapshot();

    assertThat(snapshot.getCode(codeHash, accountHash)).isPresent().contains(codeData);
    assertThat(snapshot.getCode(Hash.EMPTY, accountHash)).isPresent().contains(Bytes.EMPTY);
  }

  @Test
  public void testSnapshot_trieNodeIsolation() {
    Bytes location = Bytes.of(1, 2);
    Bytes nodeData1 = Bytes.of(10, 20, 30);
    Bytes32 nodeHash1 = Hash.hash(nodeData1);
    Bytes nodeData2 = Bytes.of(40, 50, 60);
    Bytes32 nodeHash2 = Hash.hash(nodeData2);

    BonsaiWorldStateKeyValueStorage.Updater parentUpdater = parentStorage.updater();
    parentUpdater.putAccountStateTrieNode(location, nodeHash1, nodeData1);
    parentUpdater.commit();

    liveStorage.getAccountStateTrieNode(location, nodeHash1);
    assertThat(liveStorage.isCached(TRIE_BRANCH_STORAGE, nodeHash1)).isTrue();

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
    Bytes32 nodeHash1 = Hash.hash(nodeData1);
    Bytes nodeData2 = Bytes.of(60, 70, 80);
    Bytes32 nodeHash2 = Hash.hash(nodeData2);

    BonsaiWorldStateKeyValueStorage.Updater parentUpdater = parentStorage.updater();
    parentUpdater.putAccountStorageTrieNode(accountHash, location, nodeHash1, nodeData1);
    parentUpdater.commit();

    liveStorage.getAccountStorageTrieNode(accountHash, location, nodeHash1);
    assertThat(liveStorage.isCached(TRIE_BRANCH_STORAGE, nodeHash1)).isTrue();

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
  public void testSnapshot_doesNotPolluteCacheWithNewReads() {
    Hash accountHash = Hash.hash(Bytes.of(99));
    Bytes accountData = Bytes.of(99, 99, 99);

    BonsaiWorldStateKeyValueStorage.Updater parentUpdater = parentStorage.updater();
    parentUpdater.putAccountInfoState(accountHash, accountData);
    parentUpdater.commit();

    long initialCacheSize = liveStorage.getCacheSize(ACCOUNT_INFO_STATE);
    assertThat(liveStorage.isCached(ACCOUNT_INFO_STATE, accountHash)).isFalse();

    BonsaiSnapshotWorldStateStorage snapshot = liveStorage.createSnapshot();

    snapshot.getAccount(accountHash);

    assertThat(liveStorage.getCacheSize(ACCOUNT_INFO_STATE)).isEqualTo(initialCacheSize);
    assertThat(liveStorage.isCached(ACCOUNT_INFO_STATE, accountHash)).isFalse();
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
}
