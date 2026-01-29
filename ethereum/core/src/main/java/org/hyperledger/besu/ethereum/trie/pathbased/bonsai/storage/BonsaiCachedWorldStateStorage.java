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
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
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

  private final VersionedCacheManager cacheManager;

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

    this.cacheManager =
        new VersionedCacheManager(
            accountCacheSize, codeCacheSize, storageCacheSize, trieCacheSize, metricsSystem);
  }

  /**
   * Create a snapshot at current version. IMPORTANT: Does NOT increment the version - snapshot
   * captures current state.
   */
  public BonsaiSnapshotWorldStateStorage createSnapshot() {
    return new BonsaiCachedSnapshotWorldStateStorage(
        this, cacheManager, cacheManager.getCurrentVersion());
  }

  public long getCurrentVersion() {
    return cacheManager.getCurrentVersion();
  }

  @Override
  public Optional<Bytes> getAccount(final Hash accountHash) {
    return cacheManager.getFromCacheOrStorage(
        ACCOUNT_INFO_STATE,
        accountHash.getBytes().toArrayUnsafe(),
        () -> super.getAccount(accountHash));
  }

  @Override
  public Optional<Bytes> getCode(final Hash codeHash, final Hash accountHash) {
    if (codeHash.equals(Hash.EMPTY)) {
      return Optional.of(Bytes.EMPTY);
    }
    return cacheManager.getFromCacheOrStorage(
        CODE_STORAGE,
        accountHash.getBytes().toArrayUnsafe(),
        () -> super.getCode(codeHash, accountHash));
  }

  @Override
  public Optional<Bytes> getAccountStateTrieNode(final Bytes location, final Bytes32 nodeHash) {
    return cacheManager.getFromCacheOrStorage(
        TRIE_BRANCH_STORAGE,
        nodeHash.toArrayUnsafe(),
        () -> super.getAccountStateTrieNode(location, nodeHash));
  }

  @Override
  public Optional<Bytes> getAccountStorageTrieNode(
      final Hash accountHash, final Bytes location, final Bytes32 nodeHash) {
    return cacheManager.getFromCacheOrStorage(
        TRIE_BRANCH_STORAGE,
        nodeHash.toArrayUnsafe(),
        () -> super.getAccountStorageTrieNode(accountHash, location, nodeHash));
  }

  @Override
  public Optional<Bytes> getStorageValueByStorageSlotKey(
      final Hash accountHash, final StorageSlotKey storageSlotKey) {
    return cacheManager.getFromCacheOrStorage(
        ACCOUNT_STORAGE_STORAGE,
        Bytes.concatenate(accountHash.getBytes(), storageSlotKey.getSlotHash().getBytes())
            .toArrayUnsafe(),
        () -> super.getStorageValueByStorageSlotKey(accountHash, storageSlotKey));
  }

  @Override
  public Optional<Bytes> getStorageValueByStorageSlotKey(
      final Supplier<Optional<Hash>> storageRootSupplier,
      final Hash accountHash,
      final StorageSlotKey storageSlotKey) {
    return cacheManager.getFromCacheOrStorage(
        ACCOUNT_STORAGE_STORAGE,
        Bytes.concatenate(accountHash.getBytes(), storageSlotKey.getSlotHash().getBytes())
            .toArrayUnsafe(),
        () ->
            super.getStorageValueByStorageSlotKey(
                storageRootSupplier, accountHash, storageSlotKey));
  }

  @Override
  public List<Optional<byte[]>> getMultipleKeys(
      final SegmentIdentifier segmentIdentifier, final List<byte[]> keys) {
    if (isClosed.get()) {
      return new ArrayList<>();
    }

    return cacheManager.getMultipleFromCacheOrStorage(
        segmentIdentifier,
        keys,
        keysToFetch -> super.getMultipleKeys(segmentIdentifier, keysToFetch));
  }

  @Override
  public Updater updater() {
    return new CachedUpdater(
        super.getComposedWorldStateStorage().startTransaction(),
        super.getTrieLogStorage().startTransaction(),
        getFlatDbStrategy(),
        super.getComposedWorldStateStorage());
  }

  public long getCacheSize(final SegmentIdentifier segment) {
    return cacheManager.getCacheSize(segment);
  }

  public boolean isCached(final SegmentIdentifier segment, final byte[] key) {
    return cacheManager.isCached(segment, key);
  }

  public Optional<VersionedValue> getCachedValue(
      final SegmentIdentifier segment, final byte[] key) {
    return cacheManager.getCachedValue(segment, key);
  }

  /** Wrapper for byte[] to use as cache key with proper equals/hashCode. */
  public static class ByteArrayWrapper {
    private final byte[] data;
    private final int hashCode;

    public ByteArrayWrapper(final byte[] data) {
      this.data = data;
      this.hashCode = Arrays.hashCode(data);
    }

    public byte[] getData() {
      return data;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof ByteArrayWrapper)) return false;
      return Arrays.equals(data, ((ByteArrayWrapper) o).data);
    }

    @Override
    public int hashCode() {
      return hashCode;
    }
  }

  /** Value wrapper with version and removal flag. */
  public static class VersionedValue {
    final byte[] value;
    final long version;
    final boolean isRemoval;

    VersionedValue(final byte[] value, final long version, final boolean isRemoval) {
      this.value = value;
      this.version = version;
      this.isRemoval = isRemoval;
    }

    public byte[] getValue() {
      return value;
    }

    public long getVersion() {
      return version;
    }

    public boolean isRemoval() {
      return isRemoval;
    }
  }

  /**
   * Manages versioned caching for world state data. Handles cache operations, versioning, and
   * snapshot support. Works entirely with byte[] to avoid conversions.
   */
  public static class VersionedCacheManager {
    private final AtomicLong globalVersion = new AtomicLong(0);
    private final Map<SegmentIdentifier, Cache<ByteArrayWrapper, VersionedValue>> caches;

    private final LabelledMetric<Counter> cacheRequestCounter;
    private final LabelledMetric<Counter> cacheHitCounter;
    private final LabelledMetric<Counter> cacheMissCounter;
    private final LabelledMetric<Counter> cacheReadThroughCounter;
    private final LabelledMetric<Counter> cacheInsertCounter;
    private final LabelledMetric<Counter> cacheRemovalCounter;

    public VersionedCacheManager(
        final long accountCacheSize,
        final long codeCacheSize,
        final long storageCacheSize,
        final long trieCacheSize,
        final MetricsSystem metricsSystem) {

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

    private Cache<ByteArrayWrapper, VersionedValue> createCache(final long maxSize) {
      return Caffeine.newBuilder()
          .initialCapacity((int) maxSize)
          .maximumSize(maxSize)
          .expireAfterAccess(10, TimeUnit.MINUTES)
          .build();
    }

    public long getCurrentVersion() {
      return globalVersion.get();
    }

    public long incrementAndGetVersion() {
      return globalVersion.incrementAndGet();
    }

    /** Get from cache or storage with read-through caching. Always uses current global version. */
    public Optional<Bytes> getFromCacheOrStorage(
        final SegmentIdentifier segment,
        final byte[] key,
        final Supplier<Optional<Bytes>> storageGetter) {

      return getFromCacheOrStorageInternal(segment, key, globalVersion.get(), storageGetter, true);
    }

    /**
     * Get from cache or storage for a snapshot at a specific version. Only updates cache if
     * snapshot version matches current global version.
     */
    public Optional<Bytes> getFromCacheOrSnapshotStorage(
        final SegmentIdentifier segment,
        final byte[] key,
        final long snapshotVersion,
        final Supplier<Optional<Bytes>> storageGetter) {

      return getFromCacheOrStorageInternal(
          segment, key, snapshotVersion, storageGetter, snapshotVersion == globalVersion.get());
    }

    /** Internal method for single key cache/storage access. */
    private Optional<Bytes> getFromCacheOrStorageInternal(
        final SegmentIdentifier segment,
        final byte[] key,
        final long version,
        final Supplier<Optional<Bytes>> storageGetter,
        final boolean shouldUpdateCache) {

      final String segmentName = segment.getName();
      final Cache<ByteArrayWrapper, VersionedValue> cache = caches.get(segment);

      cacheRequestCounter.labels(segmentName).inc();

      if (cache == null) {
        cacheMissCounter.labels(segmentName).inc();
        return storageGetter.get();
      }

      final ByteArrayWrapper wrapper = new ByteArrayWrapper(key);
      final VersionedValue versionedValue = cache.getIfPresent(wrapper);

      // Cache hit if value exists and is visible at requested version
      if (versionedValue != null && versionedValue.version <= version) {
        cacheHitCounter.labels(segmentName).inc();
        return versionedValue.isRemoval
            ? Optional.empty()
            : Optional.of(Bytes.wrap(versionedValue.value));
      }

      // Cache miss - read from storage
      cacheMissCounter.labels(segmentName).inc();
      final Optional<Bytes> result = storageGetter.get();

      // Update cache only if allowed
      if (shouldUpdateCache) {
        cacheReadThroughCounter.labels(segmentName).inc();
        if (result.isPresent()) {
          cache.put(wrapper, new VersionedValue(result.get().toArrayUnsafe(), version, false));
          cacheInsertCounter.labels(segmentName).inc();
        } else {
          // Cache negative results (key doesn't exist)
          cache.put(wrapper, new VersionedValue(null, version, true));
          cacheInsertCounter.labels(segmentName).inc();
        }
      }

      return result;
    }

    /**
     * Get multiple keys with cache-first strategy and single batch DB fetch for misses. Always uses
     * current global version.
     */
    public List<Optional<byte[]>> getMultipleFromCacheOrStorage(
        final SegmentIdentifier segment,
        final List<byte[]> keys,
        final Function<List<byte[]>, List<Optional<byte[]>>> batchFetcher) {

      return getMultipleFromCacheOrStorageInternal(
          segment, keys, globalVersion.get(), batchFetcher, true);
    }

    /**
     * Get multiple keys for a snapshot at a specific version. Only updates cache if snapshot
     * version matches current global version.
     */
    public List<Optional<byte[]>> getMultipleFromCacheOrSnapshotStorage(
        final SegmentIdentifier segment,
        final List<byte[]> keys,
        final long snapshotVersion,
        final Function<List<byte[]>, List<Optional<byte[]>>> batchFetcher) {

      return getMultipleFromCacheOrStorageInternal(
          segment, keys, snapshotVersion, batchFetcher, snapshotVersion == globalVersion.get());
    }

    /**
     * Internal method for batch cache/storage access. Works entirely with byte[] - NO conversions.
     */
    private List<Optional<byte[]>> getMultipleFromCacheOrStorageInternal(
        final SegmentIdentifier segment,
        final List<byte[]> keys,
        final long version,
        final Function<List<byte[]>, List<Optional<byte[]>>> batchFetcher,
        final boolean shouldUpdateCache) {

      final String segmentName = segment.getName();
      final Cache<ByteArrayWrapper, VersionedValue> cache = caches.get(segment);

      if (cache == null) {
        // No cache - go directly to storage
        keys.forEach(k -> cacheMissCounter.labels(segmentName).inc());
        return batchFetcher.apply(keys);
      }

      final List<Optional<byte[]>> results = new ArrayList<>(keys.size());
      final List<byte[]> keysToFetch = new ArrayList<>();
      final List<Integer> indicesToFetch = new ArrayList<>();

      // First pass: check cache
      for (int i = 0; i < keys.size(); i++) {
        final byte[] key = keys.get(i);
        cacheRequestCounter.labels(segmentName).inc();

        final ByteArrayWrapper wrapper = new ByteArrayWrapper(key);
        final VersionedValue versionedValue = cache.getIfPresent(wrapper);

        if (versionedValue != null && versionedValue.version <= version) {
          // Cache hit
          cacheHitCounter.labels(segmentName).inc();
          results.add(
              versionedValue.isRemoval ? Optional.empty() : Optional.of(versionedValue.value));
        } else {
          // Cache miss
          cacheMissCounter.labels(segmentName).inc();
          results.add(null); // Placeholder
          keysToFetch.add(key);
          indicesToFetch.add(i);
        }
      }

      // Second pass: single batch fetch for missing keys
      if (!keysToFetch.isEmpty()) {
        final List<Optional<byte[]>> fetchedValues = batchFetcher.apply(keysToFetch);

        // Third pass: populate results and update cache
        for (int i = 0; i < fetchedValues.size(); i++) {
          final Optional<byte[]> fetchedValue = fetchedValues.get(i);
          final int resultIndex = indicesToFetch.get(i);
          final byte[] key = keysToFetch.get(i);

          results.set(resultIndex, fetchedValue);

          if (shouldUpdateCache) {
            cacheReadThroughCounter.labels(segmentName).inc();
            final ByteArrayWrapper wrapper = new ByteArrayWrapper(key);
            if (fetchedValue.isPresent()) {
              cache.put(wrapper, new VersionedValue(fetchedValue.get(), version, false));
            } else {
              // Cache negative results
              cache.put(wrapper, new VersionedValue(null, version, true));
            }
            cacheInsertCounter.labels(segmentName).inc();
          }
        }
      }

      return results;
    }

    public void putInCache(
        final SegmentIdentifier segment, final byte[] key, final byte[] value, final long version) {
      final Cache<ByteArrayWrapper, VersionedValue> cache = caches.get(segment);
      if (cache != null) {
        cache.put(new ByteArrayWrapper(key), new VersionedValue(value, version, false));
        cacheInsertCounter.labels(segment.getName()).inc();
      }
    }

    public void removeFromCache(
        final SegmentIdentifier segment, final byte[] key, final long version) {
      final Cache<ByteArrayWrapper, VersionedValue> cache = caches.get(segment);
      if (cache != null) {
        cache.put(new ByteArrayWrapper(key), new VersionedValue(null, version, true));
        cacheRemovalCounter.labels(segment.getName()).inc();
      }
    }

    public long getCacheSize(final SegmentIdentifier segment) {
      final Cache<ByteArrayWrapper, VersionedValue> cache = caches.get(segment);
      return cache != null ? cache.estimatedSize() : 0;
    }

    public boolean isCached(final SegmentIdentifier segment, final byte[] key) {
      final Cache<ByteArrayWrapper, VersionedValue> cache = caches.get(segment);
      return cache != null && cache.getIfPresent(new ByteArrayWrapper(key)) != null;
    }

    public Optional<VersionedValue> getCachedValue(
        final SegmentIdentifier segment, final byte[] key) {
      final Cache<ByteArrayWrapper, VersionedValue> cache = caches.get(segment);
      return cache != null
          ? Optional.ofNullable(cache.getIfPresent(new ByteArrayWrapper(key)))
          : Optional.empty();
    }
  }

  /** Cached updater that stages changes and updates cache on commit. */
  public class CachedUpdater extends BonsaiWorldStateKeyValueStorage.Updater {

    private final Map<SegmentIdentifier, Map<ByteArrayWrapper, byte[]>> pending = new HashMap<>();
    private final Map<SegmentIdentifier, Map<ByteArrayWrapper, Boolean>> pendingRemovals =
        new HashMap<>();

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
      if (!code.isEmpty()) {
        stagePut(CODE_STORAGE, accountHash.getBytes().toArrayUnsafe(), code.toArrayUnsafe());
      }
      return super.putCode(accountHash, codeHash, code);
    }

    @Override
    public Updater removeCode(final Hash accountHash, final Hash codeHash) {
      stageRemoval(CODE_STORAGE, accountHash.getBytes().toArrayUnsafe());
      return super.removeCode(accountHash, codeHash);
    }

    @Override
    public Updater putAccountInfoState(final Hash accountHash, final Bytes accountValue) {
      if (!accountValue.isEmpty()) {
        stagePut(
            ACCOUNT_INFO_STATE,
            accountHash.getBytes().toArrayUnsafe(),
            accountValue.toArrayUnsafe());
      }
      return super.putAccountInfoState(accountHash, accountValue);
    }

    @Override
    public Updater removeAccountInfoState(final Hash accountHash) {
      stageRemoval(ACCOUNT_INFO_STATE, accountHash.getBytes().toArrayUnsafe());
      return super.removeAccountInfoState(accountHash);
    }

    @Override
    public Updater putAccountStateTrieNode(
        final Bytes location, final Bytes32 nodeHash, final Bytes node) {
      stagePut(TRIE_BRANCH_STORAGE, nodeHash.toArrayUnsafe(), node.toArrayUnsafe());
      return super.putAccountStateTrieNode(location, nodeHash, node);
    }

    @Override
    public synchronized Updater putAccountStorageTrieNode(
        final Hash accountHash, final Bytes location, final Bytes32 nodeHash, final Bytes node) {
      stagePut(TRIE_BRANCH_STORAGE, nodeHash.toArrayUnsafe(), node.toArrayUnsafe());
      return super.putAccountStorageTrieNode(accountHash, location, nodeHash, node);
    }

    @Override
    public synchronized Updater putStorageValueBySlotHash(
        final Hash accountHash, final Hash slotHash, final Bytes storageValue) {
      final byte[] key =
          Bytes.concatenate(accountHash.getBytes(), slotHash.getBytes()).toArrayUnsafe();
      stagePut(ACCOUNT_STORAGE_STORAGE, key, storageValue.toArrayUnsafe());
      return super.putStorageValueBySlotHash(accountHash, slotHash, storageValue);
    }

    @Override
    public synchronized void removeStorageValueBySlotHash(
        final Hash accountHash, final Hash slotHash) {
      final byte[] key =
          Bytes.concatenate(accountHash.getBytes(), slotHash.getBytes()).toArrayUnsafe();
      stageRemoval(ACCOUNT_STORAGE_STORAGE, key);
      super.removeStorageValueBySlotHash(accountHash, slotHash);
    }

    @Override
    public SegmentedKeyValueStorageTransaction getWorldStateTransaction() {
      final SegmentedKeyValueStorageTransaction parentTransaction =
          super.getWorldStateTransaction();

      return new SegmentedKeyValueStorageTransaction() {
        @Override
        public void put(
            final SegmentIdentifier segmentIdentifier, final byte[] key, final byte[] value) {
          stagePut(segmentIdentifier, key, value);
          parentTransaction.put(segmentIdentifier, key, value);
        }

        @Override
        public void remove(final SegmentIdentifier segmentIdentifier, final byte[] key) {
          stageRemoval(segmentIdentifier, key);
          parentTransaction.remove(segmentIdentifier, key);
        }

        @Override
        public void commit() throws StorageException {
          updateCache();
          parentTransaction.commit();
        }

        @Override
        public void rollback() {
          clearStaged();
          parentTransaction.rollback();
        }

        @Override
        public void close() {
          parentTransaction.close();
        }
      };
    }

    private void stagePut(final SegmentIdentifier segment, final byte[] key, final byte[] value) {
      pending.computeIfAbsent(segment, k -> new HashMap<>()).put(new ByteArrayWrapper(key), value);
    }

    private void stageRemoval(final SegmentIdentifier segment, final byte[] key) {
      pendingRemovals
          .computeIfAbsent(segment, k -> new HashMap<>())
          .put(new ByteArrayWrapper(key), true);
    }

    private void clearStaged() {
      pending.clear();
      pendingRemovals.clear();
    }

    private void updateCache() {
      final long updateVersion = cacheManager.incrementAndGetVersion();

      // Apply all pending updates
      pending.forEach(
          (segment, updates) ->
              updates.forEach(
                  (wrapper, value) ->
                      cacheManager.putInCache(segment, wrapper.getData(), value, updateVersion)));

      // Apply all pending removals
      pendingRemovals.forEach(
          (segment, removals) ->
              removals
                  .keySet()
                  .forEach(
                      wrapper ->
                          cacheManager.removeFromCache(segment, wrapper.getData(), updateVersion)));

      clearStaged();
    }

    @Override
    public void commit() {
      updateCache();
      super.commit();
    }

    @Override
    public void commitTrieLogOnly() {
      clearStaged();
      super.commitTrieLogOnly();
    }

    @Override
    public void commitComposedOnly() {
      updateCache();
      super.commitComposedOnly();
    }

    @Override
    public void rollback() {
      clearStaged();
      super.rollback();
    }
  }
}
