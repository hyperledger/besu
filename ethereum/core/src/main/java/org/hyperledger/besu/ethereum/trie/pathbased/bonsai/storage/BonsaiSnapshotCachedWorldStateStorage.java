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
package org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiCachedWorldStateStorage.VersionedValue;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.flat.BonsaiFlatDbStrategyProvider;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.CODE_STORAGE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE;

/**
 * Cached world state storage with versioning support.
 * Uses separate Caffeine caches per segment with version tracking.
 */
public class BonsaiSnapshotCachedWorldStateStorage extends BonsaiSnapshotWorldStateKeyValueStorage {
  private final BonsaiWorldStateKeyValueStorage parent;
  private final Map<String, Cache<Bytes, VersionedValue>> caches;
  private final long snapshotVersion;


  public BonsaiSnapshotCachedWorldStateStorage(
          final BonsaiWorldStateKeyValueStorage parent,
          final Map<String, Cache<Bytes, VersionedValue>> caches,
          final long snapshotVersion) {
    super(parent);
      this.parent = parent;
      this.caches = caches;
      this.snapshotVersion = snapshotVersion;
  }

  private Optional<Bytes> getFromCache(final String segment, final Bytes key) {
    final Cache<Bytes, VersionedValue> cache = caches.get(segment);
    if (cache == null) {
      return null; // Not in cache
    }

    final VersionedValue versionedValue = cache.getIfPresent(key);
    if (versionedValue != null && versionedValue.version < snapshotVersion) {
      // Found and version is valid for this snapshot
      return versionedValue.isRemoval ? Optional.empty() : Optional.of(versionedValue.value);
    }

    return null; // Not in cache or version too new
  }

  @Override
  public Optional<Bytes> getAccount(final Hash accountHash) {
    final Optional<Bytes> cached = getFromCache(ACCOUNT_INFO_STATE.getName(), accountHash);
    return cached != null ? cached : parent.getAccount(accountHash);
  }

  @Override
  public Optional<Bytes> getCode(final Hash codeHash, final Hash accountHash) {
    if (codeHash.equals(Hash.EMPTY)) {
      return Optional.of(Bytes.EMPTY);
    }
    final Optional<Bytes> cached = getFromCache(CODE_STORAGE.getName(), accountHash);
    return cached != null ? cached : parent.getCode(codeHash, accountHash);
  }

  @Override
  public Optional<Bytes> getAccountStateTrieNode(final Bytes location, final Bytes32 nodeHash) {
    final Optional<Bytes> cached = getFromCache(TRIE_BRANCH_STORAGE.getName(), nodeHash);
    return cached != null ? cached : parent.getAccountStateTrieNode(location, nodeHash);
  }

  @Override
  public Optional<Bytes> getAccountStorageTrieNode(
      final Hash accountHash, final Bytes location, final Bytes32 nodeHash) {
    final Optional<Bytes> cached = getFromCache(TRIE_BRANCH_STORAGE.getName(), nodeHash);
    return cached != null ? cached : parent.getAccountStorageTrieNode(accountHash, location, nodeHash);
  }

  @Override
  public Optional<Bytes> getStorageValueByStorageSlotKey(
      final Hash accountHash, final StorageSlotKey storageSlotKey) {
    final Bytes key = Bytes.concatenate(accountHash, storageSlotKey.getSlotHash());
    final Optional<Bytes> cached = getFromCache(ACCOUNT_STORAGE_STORAGE.getName(), key);
    return cached != null ? cached : parent.getStorageValueByStorageSlotKey(accountHash, storageSlotKey);
  }

  @Override
  public Optional<Bytes> getStorageValueByStorageSlotKey(
      final Supplier<Optional<Hash>> storageRootSupplier,
      final Hash accountHash,
      final StorageSlotKey storageSlotKey) {
    final Bytes key = Bytes.concatenate(accountHash, storageSlotKey.getSlotHash());
    final Optional<Bytes> cached = getFromCache(ACCOUNT_STORAGE_STORAGE.getName(), key);
    return cached != null ? cached : parent.getStorageValueByStorageSlotKey(storageRootSupplier, accountHash, storageSlotKey);
  }
}