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
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiCachedWorldStateStorage.VersionedValue;
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

public class BonsaiCachedWorldStateStorageTest {

  private static final long CACHE_SIZE = 1000;
  private BonsaiWorldStateKeyValueStorage parentStorage;
  private BonsaiCachedWorldStateStorage cachedStorage;

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

    cachedStorage =
        new BonsaiCachedWorldStateStorage(
            parentStorage, CACHE_SIZE, CACHE_SIZE, CACHE_SIZE, CACHE_SIZE, new NoOpMetricsSystem());
  }

  @Test
  public void testAccountCaching_firstReadFromParent() {
    Hash accountHash = Hash.hash(Bytes.of(1));
    Bytes accountData = Bytes.of(1, 2, 3);

    assertThat(cachedStorage.getCacheSize(ACCOUNT_INFO_STATE)).isZero();
    assertThat(cachedStorage.isCached(ACCOUNT_INFO_STATE, accountHash)).isFalse();

    BonsaiWorldStateKeyValueStorage.Updater updater = parentStorage.updater();
    updater.putAccountInfoState(accountHash, accountData);
    updater.commit();

    Optional<Bytes> result1 = cachedStorage.getAccount(accountHash);

    assertThat(result1).isPresent().contains(accountData);
    assertThat(cachedStorage.getCacheSize(ACCOUNT_INFO_STATE)).isEqualTo(1);
    assertThat(cachedStorage.isCached(ACCOUNT_INFO_STATE, accountHash)).isTrue();

    Optional<VersionedValue> cachedValue =
        cachedStorage.getCachedValue(ACCOUNT_INFO_STATE, accountHash);
    assertThat(cachedValue).isPresent();
    assertThat(cachedValue.get().value).isEqualTo(accountData);
    assertThat(cachedValue.get().isRemoval).isFalse();

    Optional<Bytes> result2 = cachedStorage.getAccount(accountHash);
    assertThat(result2).isPresent().contains(accountData);
    assertThat(cachedStorage.getCacheSize(ACCOUNT_INFO_STATE)).isEqualTo(1);
  }

  @Test
  public void testAccountCaching_updatesAreVisible() {
    Hash accountHash = Hash.hash(Bytes.of(1));
    Bytes originalData = Bytes.of(1, 2, 3);
    Bytes updatedData = Bytes.of(4, 5, 6);

    BonsaiWorldStateKeyValueStorage.Updater parentUpdater = parentStorage.updater();
    parentUpdater.putAccountInfoState(accountHash, originalData);
    parentUpdater.commit();

    long v0 = cachedStorage.getCurrentVersion();
    Optional<Bytes> result1 = cachedStorage.getAccount(accountHash);

    assertThat(result1).isPresent().contains(originalData);
    Optional<VersionedValue> cached1 =
        cachedStorage.getCachedValue(ACCOUNT_INFO_STATE, accountHash);
    assertThat(cached1).isPresent();
    assertThat(cached1.get().version).isEqualTo(v0);

    BonsaiCachedWorldStateStorage.CachedUpdater updater =
        (BonsaiCachedWorldStateStorage.CachedUpdater) cachedStorage.updater();
    updater.putAccountInfoState(accountHash, updatedData);
    updater.commit();

    long v1 = cachedStorage.getCurrentVersion();
    Optional<Bytes> result2 = cachedStorage.getAccount(accountHash);

    assertThat(result2).isPresent().contains(updatedData);
    Optional<VersionedValue> cached2 =
        cachedStorage.getCachedValue(ACCOUNT_INFO_STATE, accountHash);
    assertThat(cached2).isPresent();
    assertThat(cached2.get().value).isEqualTo(updatedData);
    assertThat(cached2.get().version).isEqualTo(v1);
    assertThat(v1).isGreaterThan(v0);
  }

  @Test
  public void testCodeCaching_emptyCodeHandled() {
    Hash accountHash = Hash.hash(Bytes.of(1));
    Hash codeHash = Hash.EMPTY;

    assertThat(cachedStorage.getCacheSize(CODE_STORAGE)).isZero();

    Optional<Bytes> result = cachedStorage.getCode(codeHash, accountHash);

    assertThat(result).isPresent().contains(Bytes.EMPTY);
    assertThat(cachedStorage.getCacheSize(CODE_STORAGE)).isZero();
  }

  @Test
  public void testStorageCaching_withConcatenatedKey() {
    Hash accountHash = Hash.hash(Bytes.of(1));
    StorageSlotKey slotKey = new StorageSlotKey(UInt256.fromBytes(Bytes.of(2)));
    Bytes storageValue = Bytes.of(7, 8, 9);
    Bytes concatenatedKey = Bytes.concatenate(accountHash, slotKey.getSlotHash());

    assertThat(cachedStorage.getCacheSize(ACCOUNT_STORAGE_STORAGE)).isZero();
    assertThat(cachedStorage.isCached(ACCOUNT_STORAGE_STORAGE, concatenatedKey)).isFalse();

    BonsaiWorldStateKeyValueStorage.Updater parentUpdater = parentStorage.updater();
    parentUpdater.putStorageValueBySlotHash(accountHash, slotKey.getSlotHash(), storageValue);
    parentUpdater.commit();

    Optional<Bytes> result1 = cachedStorage.getStorageValueByStorageSlotKey(accountHash, slotKey);

    assertThat(result1).isPresent().contains(storageValue);
    assertThat(cachedStorage.getCacheSize(ACCOUNT_STORAGE_STORAGE)).isEqualTo(1);
    assertThat(cachedStorage.isCached(ACCOUNT_STORAGE_STORAGE, concatenatedKey)).isTrue();

    Optional<VersionedValue> cachedValue =
        cachedStorage.getCachedValue(ACCOUNT_STORAGE_STORAGE, concatenatedKey);
    assertThat(cachedValue).isPresent();
    assertThat(cachedValue.get().value).isEqualTo(storageValue);

    Optional<Bytes> result2 = cachedStorage.getStorageValueByStorageSlotKey(accountHash, slotKey);
    assertThat(result2).isPresent().contains(storageValue);
    assertThat(cachedStorage.getCacheSize(ACCOUNT_STORAGE_STORAGE)).isEqualTo(1);
  }

  @Test
  public void testRemoval_markedInCache() {
    Hash accountHash = Hash.hash(Bytes.of(1));
    Bytes accountData = Bytes.of(1, 2, 3);

    BonsaiWorldStateKeyValueStorage.Updater parentUpdater = parentStorage.updater();
    parentUpdater.putAccountInfoState(accountHash, accountData);
    parentUpdater.commit();

    cachedStorage.getAccount(accountHash);
    assertThat(cachedStorage.isCached(ACCOUNT_INFO_STATE, accountHash)).isTrue();

    Optional<VersionedValue> cachedBeforeRemoval =
        cachedStorage.getCachedValue(ACCOUNT_INFO_STATE, accountHash);
    assertThat(cachedBeforeRemoval).isPresent();
    assertThat(cachedBeforeRemoval.get().isRemoval).isFalse();

    BonsaiCachedWorldStateStorage.CachedUpdater updater =
        (BonsaiCachedWorldStateStorage.CachedUpdater) cachedStorage.updater();
    updater.removeAccountInfoState(accountHash);
    updater.commit();

    assertThat(cachedStorage.isCached(ACCOUNT_INFO_STATE, accountHash)).isTrue();

    Optional<VersionedValue> cachedAfterRemoval =
        cachedStorage.getCachedValue(ACCOUNT_INFO_STATE, accountHash);
    assertThat(cachedAfterRemoval).isPresent();
    assertThat(cachedAfterRemoval.get().isRemoval).isTrue();
    assertThat(cachedAfterRemoval.get().value).isNull();

    Optional<Bytes> result = cachedStorage.getAccount(accountHash);
    assertThat(result).isEmpty();
  }

  @Test
  public void testTrieNodeCaching() {
    Bytes location = Bytes.of(1, 2);
    Bytes nodeData = Bytes.of(10, 20, 30);
    Bytes32 nodeHash = Hash.hash(nodeData);

    assertThat(cachedStorage.getCacheSize(TRIE_BRANCH_STORAGE)).isZero();
    assertThat(cachedStorage.isCached(TRIE_BRANCH_STORAGE, nodeHash)).isFalse();

    BonsaiWorldStateKeyValueStorage.Updater parentUpdater = parentStorage.updater();
    parentUpdater.putAccountStateTrieNode(location, nodeHash, nodeData);
    parentUpdater.commit();

    Optional<Bytes> result1 = cachedStorage.getAccountStateTrieNode(location, nodeHash);

    assertThat(result1).isPresent().contains(nodeData);
    assertThat(cachedStorage.getCacheSize(TRIE_BRANCH_STORAGE)).isEqualTo(1);
    assertThat(cachedStorage.isCached(TRIE_BRANCH_STORAGE, nodeHash)).isTrue();

    Optional<Bytes> result2 = cachedStorage.getAccountStateTrieNode(location, nodeHash);
    assertThat(result2).isPresent().contains(nodeData);
    assertThat(cachedStorage.getCacheSize(TRIE_BRANCH_STORAGE)).isEqualTo(1);
  }

  @Test
  public void testStorageTrieNodeCaching() {
    Hash accountHash = Hash.hash(Bytes.of(1));
    Bytes location = Bytes.of(2, 3);
    Bytes nodeData = Bytes.of(30, 40, 50);
    Bytes32 nodeHash = Hash.hash(nodeData);

    assertThat(cachedStorage.getCacheSize(TRIE_BRANCH_STORAGE)).isZero();

    BonsaiWorldStateKeyValueStorage.Updater parentUpdater = parentStorage.updater();
    parentUpdater.putAccountStorageTrieNode(accountHash, location, nodeHash, nodeData);
    parentUpdater.commit();

    Optional<Bytes> result1 =
        cachedStorage.getAccountStorageTrieNode(accountHash, location, nodeHash);

    assertThat(result1).isPresent().contains(nodeData);
    assertThat(cachedStorage.getCacheSize(TRIE_BRANCH_STORAGE)).isEqualTo(1);
    assertThat(cachedStorage.isCached(TRIE_BRANCH_STORAGE, nodeHash)).isTrue();

    Optional<Bytes> result2 =
        cachedStorage.getAccountStorageTrieNode(accountHash, location, nodeHash);
    assertThat(result2).isPresent().contains(nodeData);
    assertThat(cachedStorage.getCacheSize(TRIE_BRANCH_STORAGE)).isEqualTo(1);
  }

  @Test
  public void testMultipleUpdates_versionProgression() {
    long v0 = cachedStorage.getCurrentVersion();

    Hash account1 = Hash.hash(Bytes.of(1));
    Hash account2 = Hash.hash(Bytes.of(2));
    Bytes data1 = Bytes.of(1);
    Bytes data2 = Bytes.of(2);

    BonsaiCachedWorldStateStorage.CachedUpdater updater1 =
        (BonsaiCachedWorldStateStorage.CachedUpdater) cachedStorage.updater();
    updater1.putAccountInfoState(account1, data1);
    updater1.commit();
    long v1 = cachedStorage.getCurrentVersion();

    assertThat(cachedStorage.isCached(ACCOUNT_INFO_STATE, account1)).isTrue();
    Optional<VersionedValue> cached1 = cachedStorage.getCachedValue(ACCOUNT_INFO_STATE, account1);
    assertThat(cached1).isPresent();
    assertThat(cached1.get().version).isEqualTo(v1);

    BonsaiCachedWorldStateStorage.CachedUpdater updater2 =
        (BonsaiCachedWorldStateStorage.CachedUpdater) cachedStorage.updater();
    updater2.putAccountInfoState(account2, data2);
    updater2.commit();
    long v2 = cachedStorage.getCurrentVersion();

    assertThat(cachedStorage.isCached(ACCOUNT_INFO_STATE, account2)).isTrue();
    Optional<VersionedValue> cached2 = cachedStorage.getCachedValue(ACCOUNT_INFO_STATE, account2);
    assertThat(cached2).isPresent();
    assertThat(cached2.get().version).isEqualTo(v2);

    assertThat(v1).isEqualTo(v0 + 1);
    assertThat(v2).isEqualTo(v1 + 1);
    assertThat(cachedStorage.getAccount(account1)).isPresent().contains(data1);
    assertThat(cachedStorage.getAccount(account2)).isPresent().contains(data2);
    assertThat(cachedStorage.getCacheSize(ACCOUNT_INFO_STATE)).isEqualTo(2);
  }
}
