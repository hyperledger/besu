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
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * Cached world state storage with versioning support.
 *
 * <p>Version semantics: - Version 0: Initial state (values read from parent but never modified) -
 * Version 1+: Each commit increments the version for all modified values
 *
 * <p>Snapshots capture the state at a specific version and only see values with version <=
 * snapshotVersion.
 */
public class BonsaiCachedWorldStateStorage extends BonsaiWorldStateKeyValueStorage {

  // Global version counter - incremented ONLY on commit
  private final AtomicLong globalVersion = new AtomicLong(0);

  private final BonsaiWorldStateKeyValueStorage parent;
  private final Map<SegmentIdentifier, Cache<Bytes, VersionedValue>> caches;

  private final LabelledMetric<Counter> cacheRequestCounter;
  private final LabelledMetric<Counter> cacheHitCounter;
  private final LabelledMetric<Counter> cacheMissCounter;
  private final LabelledMetric<Counter> cacheReadThroughCounter;
  private final LabelledMetric<Counter> cacheInsertCounter;
  private final LabelledMetric<Counter> cacheRemovalCounter;

  public BonsaiCachedWorldStateStorage(
      final BonsaiWorldStateKeyValueStorage parent,
      final long accountCacheSize,
      final long codeCacheSize,
      final long storageCacheSize,
      final long trieCacheSize,
      final MetricsSystem metricsSystem) {
    super(
        parent.flatDbStrategyProvider,
        parent.getComposedWorldStateStorage(),
        parent.getTrieLogStorage());
    this.parent = parent;
    this.caches = new HashMap<>();

    caches.put(ACCOUNT_INFO_STATE, createCache(accountCacheSize));
    caches.put(CODE_STORAGE, createCache(codeCacheSize));
    caches.put(ACCOUNT_STORAGE_STORAGE, createCache(storageCacheSize));
    caches.put(TRIE_BRANCH_STORAGE, createCache(trieCacheSize));

    this.cacheRequestCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "bonsai_cache_requests_total",
            "Total number of cache requests",
            "segment");

    this.cacheHitCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "bonsai_cache_hits_total",
            "Total number of cache hits",
            "segment");

    this.cacheMissCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "bonsai_cache_misses_total",
            "Total number of cache misses",
            "segment");

    this.cacheReadThroughCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "bonsai_cache_read_through_total",
            "Total number of cache read-through operations",
            "segment");

    this.cacheInsertCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "bonsai_cache_inserts_total",
            "Total number of cache insertions",
            "segment");

    this.cacheRemovalCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "bonsai_cache_removals_total",
            "Total number of cache removals",
            "segment");
  }

  private Cache<Bytes, VersionedValue> createCache(final long maxSize) {
    return Caffeine.newBuilder().initialCapacity((int) maxSize).maximumSize(maxSize).expireAfterAccess(10, TimeUnit.MINUTES).build();
  }

  /**
   * Create a snapshot at current version. IMPORTANT: Does NOT increment the version - snapshot
   * captures current state.
   */
  public BonsaiSnapshotWorldStateStorage createSnapshot() {
    return new BonsaiCachedSnapshotWorldStateStorage(
        parent, caches, globalVersion.get()); // Use current version, don't increment
  }

  public long getCurrentVersion() {
    return globalVersion.get();
  }

  /**
   * Get from cache or parent. Read-through values are cached with version 0 (READ_THROUGH_VERSION).
   */
  private Optional<Bytes> getFromCacheOrParent(
      final SegmentIdentifier segment,
      final Bytes key,
      final Supplier<Optional<Bytes>> parentGetter) {

    final String segmentName = segment.getName();
    final Cache<Bytes, VersionedValue> cache = caches.get(segment);

    // Record cache request
    cacheRequestCounter.labels(segmentName).inc();

    if (cache == null) {
      // No cache for this segment, go directly to parent
      cacheMissCounter.labels(segmentName).inc();
      return parentGetter.get();
    }

    final VersionedValue versionedValue = cache.getIfPresent(key);

    // If in cache, return it (cache hit)
    if (versionedValue != null) {
      cacheHitCounter.labels(segmentName).inc();
      return versionedValue.isRemoval ? Optional.empty() : Optional.of(versionedValue.value);
    }

    // Cache miss - read from parent and cache the result
    cacheMissCounter.labels(segmentName).inc();
    cacheReadThroughCounter.labels(segmentName).inc();

    final Optional<Bytes> result = parentGetter.get();
    if (result.isPresent()) {
      cache.put(key, new VersionedValue(result.get(), globalVersion.get(), false));
      cacheInsertCounter.labels(segmentName).inc();
    } else {
      // Cache the fact that the key doesn't exist (negative caching)
      cache.put(key, new VersionedValue(null, globalVersion.get(), true));
      cacheInsertCounter.labels(segmentName).inc();
    }

    return result;
  }

  private void putInCache(
      final SegmentIdentifier segment, final Bytes key, final Bytes value, final long version) {
    final Cache<Bytes, VersionedValue> cache = caches.get(segment);
    if (cache != null) {
      cache.put(key, new VersionedValue(value, version, false));
      cacheInsertCounter.labels(segment.getName()).inc();
    }
  }

  private void removeFromCache(
      final SegmentIdentifier segment, final Bytes key, final long version) {
    final Cache<Bytes, VersionedValue> cache = caches.get(segment);
    if (cache != null) {
      cache.put(key, new VersionedValue(null, version, true));
      cacheRemovalCounter.labels(segment.getName()).inc();
    }
  }

  @Override
  public Optional<Bytes> getAccount(final Hash accountHash) {
    return getFromCacheOrParent(
        ACCOUNT_INFO_STATE, accountHash, () -> parent.getAccount(accountHash));
  }

  @Override
  public Optional<Bytes> getCode(final Hash codeHash, final Hash accountHash) {
    if (codeHash.equals(Hash.EMPTY)) {
      return Optional.of(Bytes.EMPTY);
    }
    return parent.getCode(codeHash, accountHash);
  }

  @Override
  public Optional<Bytes> getAccountStateTrieNode(final Bytes location, final Bytes32 nodeHash) {
    return parent.getAccountStateTrieNode(location, nodeHash);
  }

  @Override
  public Optional<Bytes> getAccountStorageTrieNode(
      final Hash accountHash, final Bytes location, final Bytes32 nodeHash) {
    return  parent.getAccountStorageTrieNode(accountHash, location, nodeHash);
  }

  @Override
  public Optional<Bytes> getStorageValueByStorageSlotKey(
      final Hash accountHash, final StorageSlotKey storageSlotKey) {
    final Bytes key = Bytes.concatenate(accountHash, storageSlotKey.getSlotHash());
    return getFromCacheOrParent(
        ACCOUNT_STORAGE_STORAGE,
        key,
        () -> parent.getStorageValueByStorageSlotKey(accountHash, storageSlotKey));
  }

  @Override
  public Optional<Bytes> getStorageValueByStorageSlotKey(
      final Supplier<Optional<Hash>> storageRootSupplier,
      final Hash accountHash,
      final StorageSlotKey storageSlotKey) {
    final Bytes key = Bytes.concatenate(accountHash, storageSlotKey.getSlotHash());
    return getFromCacheOrParent(
        ACCOUNT_STORAGE_STORAGE,
        key,
        () ->
            parent.getStorageValueByStorageSlotKey(
                storageRootSupplier, accountHash, storageSlotKey));
  }

  @Override
  public Updater updater() {
    return new CachedUpdater(
        parent.getComposedWorldStateStorage().startTransaction(),
        parent.getTrieLogStorage().startTransaction(),
        getFlatDbStrategy(),
        parent.getComposedWorldStateStorage());
  }

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

  public class CachedUpdater extends BonsaiWorldStateKeyValueStorage.Updater {

    private final Map<SegmentIdentifier, Map<Bytes, Bytes>> pending = new HashMap<>();
    private final Map<SegmentIdentifier, Map<Bytes, Boolean>> pendingRemovals = new HashMap<>();

    public CachedUpdater(
        final SegmentedKeyValueStorageTransaction composedWorldStateTransaction,
        final KeyValueStorageTransaction trieLogStorageTransaction,
        final org.hyperledger.besu.ethereum.trie.pathbased.common.storage.flat.FlatDbStrategy
            flatDbStrategy,
        final SegmentedKeyValueStorage worldStorage) {
      super(composedWorldStateTransaction, trieLogStorageTransaction, flatDbStrategy, worldStorage);
    }

    @Override
    public Updater putCode(final Hash accountHash, final Hash codeHash, final Bytes code) {
      /*if (!code.isEmpty()) {
        stagePut(CODE_STORAGE, accountHash, code);
      }*/
      return super.putCode(accountHash, codeHash, code);
    }

    @Override
    public Updater removeCode(final Hash accountHash, final Hash codeHash) {
      //stageRemoval(CODE_STORAGE, accountHash);
      return super.removeCode(accountHash, codeHash);
    }

    @Override
    public Updater putAccountInfoState(final Hash accountHash, final Bytes accountValue) {
      if (!accountValue.isEmpty()) {
        stagePut(ACCOUNT_INFO_STATE, accountHash, accountValue);
      }
      return super.putAccountInfoState(accountHash, accountValue);
    }

    @Override
    public Updater removeAccountInfoState(final Hash accountHash) {
      stageRemoval(ACCOUNT_INFO_STATE, accountHash);
      return super.removeAccountInfoState(accountHash);
    }

    @Override
    public Updater putAccountStateTrieNode(
        final Bytes location, final Bytes32 nodeHash, final Bytes node) {
      //stagePut(TRIE_BRANCH_STORAGE, nodeHash, node);
      return super.putAccountStateTrieNode(location, nodeHash, node);
    }

    @Override
    public Updater removeAccountStateTrieNode(final Bytes location) {
      return super.removeAccountStateTrieNode(location);
    }

    @Override
    public synchronized Updater putAccountStorageTrieNode(
        final Hash accountHash, final Bytes location, final Bytes32 nodeHash, final Bytes node) {
      //stagePut(TRIE_BRANCH_STORAGE, nodeHash, node);
      return super.putAccountStorageTrieNode(accountHash, location, nodeHash, node);
    }

    @Override
    public synchronized Updater putStorageValueBySlotHash(
        final Hash accountHash, final Hash slotHash, final Bytes storageValue) {
      final Bytes key = Bytes.concatenate(accountHash, slotHash);
      stagePut(ACCOUNT_STORAGE_STORAGE, key, storageValue);
      return super.putStorageValueBySlotHash(accountHash, slotHash, storageValue);
    }

    @Override
    public synchronized void removeStorageValueBySlotHash(
        final Hash accountHash, final Hash slotHash) {
      final Bytes key = Bytes.concatenate(accountHash, slotHash);
      stageRemoval(ACCOUNT_STORAGE_STORAGE, key);
      super.removeStorageValueBySlotHash(accountHash, slotHash);
    }

    private void stagePut(final SegmentIdentifier segment, final Bytes key, final Bytes value) {
      pending.computeIfAbsent(segment, k -> new HashMap<>()).put(key, value);
    }

    private void stageRemoval(final SegmentIdentifier segment, final Bytes key) {
      pendingRemovals.computeIfAbsent(segment, k -> new HashMap<>()).put(key, true);
    }

    private void updateCache() {
      // Increment version for this commit
      final long updateVersion = globalVersion.incrementAndGet();

      // Apply all pending updates with the new version
      for (Map.Entry<SegmentIdentifier, Map<Bytes, Bytes>> entry : pending.entrySet()) {
        final SegmentIdentifier segment = entry.getKey();
        for (Map.Entry<Bytes, Bytes> update : entry.getValue().entrySet()) {
          putInCache(segment, update.getKey(), update.getValue(), updateVersion);
        }
      }

      // Apply all pending removals with the new version
      for (Map.Entry<SegmentIdentifier, Map<Bytes, Boolean>> entry : pendingRemovals.entrySet()) {
        final SegmentIdentifier segment = entry.getKey();
        for (Bytes key : entry.getValue().keySet()) {
          removeFromCache(segment, key, updateVersion);
        }
      }

      pending.clear();
      pendingRemovals.clear();
    }

    @Override
    public void commit() {
      updateCache();
      // Commit to underlying storage
      super.commit();
    }

    @Override
    public void commitTrieLogOnly() {
      pending.clear();
      pendingRemovals.clear();
      super.commitTrieLogOnly();
    }

    @Override
    public void commitComposedOnly() {
      updateCache();
      super.commitComposedOnly();
    }

    @Override
    public void rollback() {
      pending.clear();
      pendingRemovals.clear();
      super.rollback();
    }
  }

  public long getCacheSize(final SegmentIdentifier segment) {
    final Cache<Bytes, VersionedValue> cache = caches.get(segment);
    return cache != null ? cache.estimatedSize() : 0;
  }

  public boolean isCached(final SegmentIdentifier segment, final Bytes key) {
    final Cache<Bytes, VersionedValue> cache = caches.get(segment);
    return cache != null && cache.getIfPresent(key) != null;
  }

  public Optional<VersionedValue> getCachedValue(final SegmentIdentifier segment, final Bytes key) {
    final Cache<Bytes, VersionedValue> cache = caches.get(segment);
    return cache != null ? Optional.ofNullable(cache.getIfPresent(key)) : Optional.empty();
  }
}
