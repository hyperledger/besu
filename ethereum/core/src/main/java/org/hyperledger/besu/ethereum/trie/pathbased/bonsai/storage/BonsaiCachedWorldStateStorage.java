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

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.CODE_STORAGE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.flat.BonsaiFlatDbStrategyProvider;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Cached world state storage with versioning support.
 * Uses separate Caffeine caches per segment with version tracking.
 */
public class BonsaiCachedWorldStateStorage extends BonsaiWorldStateKeyValueStorage {

  private static final AtomicLong GLOBAL_VERSION = new AtomicLong(0);
  
  private final BonsaiWorldStateKeyValueStorage parent;
  private final Map<String, Cache<Bytes, VersionedValue>> caches;
  private final long snapshotVersion;

  /**
   * Create a new cached storage (not a snapshot).
   */
  public BonsaiCachedWorldStateStorage(
      final BonsaiWorldStateKeyValueStorage parent,
      final long accountCacheSize,
      final long codeCacheSize,
      final long storageCacheSize,
      final long trieCacheSize) {
    super(
        parent.flatDbStrategyProvider,
        parent.getComposedWorldStateStorage(),
        parent.getTrieLogStorage());
    this.parent = parent;
    this.caches = new HashMap<>();
    this.snapshotVersion = Long.MAX_VALUE;

    // Create separate cache per segment
    caches.put(ACCOUNT_INFO_STATE.getName(), createCache(accountCacheSize));
    caches.put(CODE_STORAGE.getName(), createCache(codeCacheSize));
    caches.put(ACCOUNT_STORAGE_STORAGE.getName(), createCache(storageCacheSize));
    caches.put(TRIE_BRANCH_STORAGE.getName(), createCache(trieCacheSize));
  }


  public BonsaiCachedWorldStateStorage(
          final BonsaiFlatDbStrategyProvider flatDbStrategyProvider,
          final SegmentedKeyValueStorage composedWorldStateStorage,
          final KeyValueStorage trieLogStorage,
          final BonsaiWorldStateKeyValueStorage parent,
          final Map<String, Cache<Bytes, VersionedValue>> caches,
          final long snapshotVersion) {
    super(flatDbStrategyProvider, composedWorldStateStorage, trieLogStorage);
      this.parent = parent;
      this.caches = caches;
      this.snapshotVersion = snapshotVersion;
  }


  private Cache<Bytes, VersionedValue> createCache(final long maxSize) {
    return Caffeine.newBuilder()
        .maximumSize(maxSize)
        .recordStats()
        .build();
  }

  /**
   * Create a snapshot at current version.
   */
  public BonsaiSnapshotWorldStateKeyValueStorage createSnapshot() {
    return new BonsaiSnapshotCachedWorldStateStorage(
            parent,
            caches,
        GLOBAL_VERSION.get());
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

  private void putInCache(final String segment, final Bytes key, final Bytes value, final long version) {
    final Cache<Bytes, VersionedValue> cache = caches.get(segment);
    if (cache != null) {
      final VersionedValue existing = cache.getIfPresent(key);
      // Only update if this version is newer
      if (existing == null || version > existing.version) {
        cache.put(key, new VersionedValue(value, version, false));
      }
    }
  }

  private void removeFromCache(final String segment, final Bytes key, final long version) {
    final Cache<Bytes, VersionedValue> cache = caches.get(segment);
    if (cache != null) {
      final VersionedValue existing = cache.getIfPresent(key);
      // Only update if this version is newer
      if (existing == null || version > existing.version) {
        cache.put(key, new VersionedValue(null, version, true));
      }
    }
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

  @Override
  public Updater updater() {
    return new CachedUpdater(
        parent.getComposedWorldStateStorage().startTransaction(),
        parent.getTrieLogStorage().startTransaction(),
        getFlatDbStrategy(),
        parent.getComposedWorldStateStorage());
  }

  /**
   * Versioned value stored in cache.
   */
  public static class VersionedValue {
    final Bytes value;
    final long version;
    final boolean isRemoval;

    VersionedValue(final Bytes value, final long version, final boolean isRemoval) {
      this.value = value;
      this.version = version;
      this.isRemoval = isRemoval;
    }
  }

  /**
   * Updater that stages changes and commits them to cache with a version.
   */
  public class CachedUpdater extends BonsaiWorldStateKeyValueStorage.Updater {

    private final Map<String, Map<Bytes, Bytes>> pending = new HashMap<>();
    private final Map<String, Map<Bytes, Boolean>> pendingRemovals = new HashMap<>();

    public CachedUpdater(
        final SegmentedKeyValueStorageTransaction composedWorldStateTransaction,
        final KeyValueStorageTransaction trieLogStorageTransaction,
        final org.hyperledger.besu.ethereum.trie.pathbased.common.storage.flat.FlatDbStrategy flatDbStrategy,
        final SegmentedKeyValueStorage worldStorage) {
      super(composedWorldStateTransaction, trieLogStorageTransaction, flatDbStrategy, worldStorage);
    }

    @Override
    public Updater putCode(final Hash accountHash, final Hash codeHash, final Bytes code) {
      if (!code.isEmpty()) {
        stagePut(CODE_STORAGE.getName(), accountHash, code);
      }
      return super.putCode(accountHash, codeHash, code);
    }

    @Override
    public Updater removeCode(final Hash accountHash, final Hash codeHash) {
      stageRemoval(CODE_STORAGE.getName(), accountHash);
      return super.removeCode(accountHash, codeHash);
    }

    @Override
    public Updater putAccountInfoState(final Hash accountHash, final Bytes accountValue) {
      if (!accountValue.isEmpty()) {
        stagePut(ACCOUNT_INFO_STATE.getName(), accountHash, accountValue);
      }
      return super.putAccountInfoState(accountHash, accountValue);
    }

    @Override
    public Updater removeAccountInfoState(final Hash accountHash) {
      stageRemoval(ACCOUNT_INFO_STATE.getName(), accountHash);
      return super.removeAccountInfoState(accountHash);
    }

    @Override
    public Updater putAccountStateTrieNode(
        final Bytes location, final Bytes32 nodeHash, final Bytes node) {
      stagePut(TRIE_BRANCH_STORAGE.getName(), nodeHash, node);
      return super.putAccountStateTrieNode(location, nodeHash, node);
    }

    @Override
    public Updater removeAccountStateTrieNode(final Bytes location) {
      return super.removeAccountStateTrieNode(location);
    }

    @Override
    public synchronized Updater putAccountStorageTrieNode(
        final Hash accountHash, final Bytes location, final Bytes32 nodeHash, final Bytes node) {
      stagePut(TRIE_BRANCH_STORAGE.getName(), nodeHash, node);
      return super.putAccountStorageTrieNode(accountHash, location, nodeHash, node);
    }

    @Override
    public synchronized Updater putStorageValueBySlotHash(
        final Hash accountHash, final Hash slotHash, final Bytes storageValue) {
      final Bytes key = Bytes.concatenate(accountHash, slotHash);
      stagePut(ACCOUNT_STORAGE_STORAGE.getName(), key, storageValue);
      return super.putStorageValueBySlotHash(accountHash, slotHash, storageValue);
    }

    @Override
    public synchronized void removeStorageValueBySlotHash(
        final Hash accountHash, final Hash slotHash) {
      final Bytes key = Bytes.concatenate(accountHash, slotHash);
      stageRemoval(ACCOUNT_STORAGE_STORAGE.getName(), key);
      super.removeStorageValueBySlotHash(accountHash, slotHash);
    }

    private void stagePut(final String segment, final Bytes key, final Bytes value) {
      pending.computeIfAbsent(segment, k -> new HashMap<>()).put(key, value);
    }

    private void stageRemoval(final String segment, final Bytes key) {
      pendingRemovals.computeIfAbsent(segment, k -> new HashMap<>()).put(key, true);
    }

    @Override
    public void commit() {
      // Apply all pending updates to caches
      final long updateVersion = GLOBAL_VERSION.incrementAndGet();
      for (Map.Entry<String, Map<Bytes, Bytes>> entry : pending.entrySet()) {
        final String segment = entry.getKey();
        for (Map.Entry<Bytes, Bytes> update : entry.getValue().entrySet()) {
          putInCache(segment, update.getKey(), update.getValue(), updateVersion);
        }
      }

      // Apply all pending removals to caches
      for (Map.Entry<String, Map<Bytes, Boolean>> entry : pendingRemovals.entrySet()) {
        final String segment = entry.getKey();
        for (Bytes key : entry.getValue().keySet()) {
          removeFromCache(segment, key, updateVersion);
        }
      }
      pending.clear();
      pendingRemovals.clear();
      // Commit to underlying storage
      super.commit();
    }

    @Override
    public void rollback() {
      pending.clear();
      pendingRemovals.clear();
      super.rollback();
    }
  }
}