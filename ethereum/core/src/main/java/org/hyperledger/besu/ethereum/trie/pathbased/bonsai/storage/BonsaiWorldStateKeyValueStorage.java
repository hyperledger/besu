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
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.common.PmtStateTrieAccountValue;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.flat.BonsaiFlatDbStrategy;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.flat.BonsaiFlatDbStrategyProvider;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.PathBasedWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.flat.FlatDbStrategy;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.FlatDbMode;
import org.hyperledger.besu.ethereum.worldstate.WorldStateKeyValueStorage;
import org.hyperledger.besu.evm.account.AccountStorageEntry;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * Bonsai world state storage with integrated cache maintenance.
 *
 * <p>Cache behavior: - BonsaiWorldStateKeyValueStorage: Maintains cache (writes only), doesn't read
 * from it - BonsaiSnapshotWorldStateStorage: Carries cache info, doesn't use it -
 * BonsaiCachedWorldStateLayerStorage: Only component that reads from cache
 *
 * <p>Version semantics: - Version 0: Initial state - Version 1+: Each commit increments version for
 * all modified values
 */
public class BonsaiWorldStateKeyValueStorage extends PathBasedWorldStateKeyValueStorage
    implements WorldStateKeyValueStorage {
  protected final BonsaiFlatDbStrategyProvider flatDbStrategyProvider;
  protected final VersionedCacheManager cacheManager;
  private long cacheVersion;

  public BonsaiWorldStateKeyValueStorage(
      final StorageProvider provider,
      final MetricsSystem metricsSystem,
      final DataStorageConfiguration dataStorageConfiguration) {
    this(
        provider,
        metricsSystem,
        dataStorageConfiguration,
        new VersionedCacheManager(
            100_000, // accountCacheSize
            1_000_000, // storageCacheSize
            metricsSystem));
  }

  public BonsaiWorldStateKeyValueStorage(
      final StorageProvider provider,
      final MetricsSystem metricsSystem,
      final DataStorageConfiguration dataStorageConfiguration,
      final VersionedCacheManager cacheManager) {
    super(
        provider.getStorageBySegmentIdentifiers(
            List.of(
                ACCOUNT_INFO_STATE, CODE_STORAGE, ACCOUNT_STORAGE_STORAGE, TRIE_BRANCH_STORAGE)),
        provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_LOG_STORAGE));
    this.flatDbStrategyProvider =
        new BonsaiFlatDbStrategyProvider(metricsSystem, dataStorageConfiguration);
    flatDbStrategyProvider.loadFlatDbStrategy(composedWorldStateStorage);

    this.cacheManager = cacheManager;
    this.cacheVersion = cacheManager.globalVersion.get();
  }

  protected BonsaiWorldStateKeyValueStorage(
      final BonsaiFlatDbStrategyProvider flatDbStrategyProvider,
      final SegmentedKeyValueStorage composedWorldStateStorage,
      final KeyValueStorage trieLogStorage,
      final VersionedCacheManager cacheManager) {
    super(composedWorldStateStorage, trieLogStorage);
    this.flatDbStrategyProvider = flatDbStrategyProvider;
    this.cacheManager = cacheManager;
    this.cacheVersion = cacheManager.globalVersion.get();
  }

  @Override
  public DataStorageFormat getDataStorageFormat() {
    return DataStorageFormat.BONSAI;
  }

  @Override
  public FlatDbMode getFlatDbMode() {
    return flatDbStrategyProvider.getFlatDbMode();
  }

  public long getCurrentVersion() {
    return cacheVersion;
  }

  public Optional<Bytes> getAccount(final Hash accountHash) {
    final byte[] key = accountHash.getBytes().toArrayUnsafe();
    return cacheManager.getFromCacheOrStorage(
        ACCOUNT_INFO_STATE,
        key,
        getCurrentVersion(),
        () ->
            getFlatDbStrategy()
                .getFlatAccount(
                    this::getWorldStateRootHash,
                    this::getAccountStateTrieNode,
                    accountHash,
                    composedWorldStateStorage));
  }

  public Optional<Bytes> getStorageValueByStorageSlotKey(
      final Hash accountHash, final StorageSlotKey storageSlotKey) {
    return getStorageValueByStorageSlotKey(
        () ->
            getAccount(accountHash)
                .map(
                    b ->
                        PmtStateTrieAccountValue.readFrom(
                                org.hyperledger.besu.ethereum.rlp.RLP.input(b))
                            .getStorageRoot()),
        accountHash,
        storageSlotKey);
  }

  public Optional<Bytes> getStorageValueByStorageSlotKey(
      final Supplier<Optional<Hash>> storageRootSupplier,
      final Hash accountHash,
      final StorageSlotKey storageSlotKey) {
    final byte[] key =
        Bytes.concatenate(accountHash.getBytes(), storageSlotKey.getSlotHash().getBytes())
            .toArrayUnsafe();
    return cacheManager.getFromCacheOrStorage(
        ACCOUNT_STORAGE_STORAGE,
        key,
        getCurrentVersion(),
        () ->
            getFlatDbStrategy()
                .getFlatStorageValueByStorageSlotKey(
                    this::getWorldStateRootHash,
                    storageRootSupplier,
                    (location, hash) -> getAccountStorageTrieNode(accountHash, location, hash),
                    accountHash,
                    storageSlotKey,
                    composedWorldStateStorage));
  }

  public List<Optional<byte[]>> getMultipleKeys(
      final SegmentIdentifier segmentIdentifier, final List<byte[]> keys) {
    return cacheManager.getMultipleFromCacheOrStorage(
        segmentIdentifier,
        keys,
        getCurrentVersion(),
        keysToFetch -> composedWorldStateStorage.multiget(segmentIdentifier, keysToFetch));
  }

  public Optional<Bytes> getCode(final Hash codeHash, final Hash accountHash) {
    if (codeHash.equals(Hash.EMPTY)) {
      return Optional.of(Bytes.EMPTY);
    }
    return getFlatDbStrategy().getFlatCode(codeHash, accountHash, composedWorldStateStorage);
  }

  public Optional<Bytes> getAccountStateTrieNode(final Bytes location, final Bytes32 nodeHash) {
    if (nodeHash.equals(MerkleTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerkleTrie.EMPTY_TRIE_NODE);
    }
    return composedWorldStateStorage
        .get(TRIE_BRANCH_STORAGE, location.toArrayUnsafe())
        .map(Bytes::wrap)
        .filter(b -> Hash.hash(b).getBytes().equals(nodeHash));
  }

  public Optional<Bytes> getAccountStorageTrieNode(
      final Hash accountHash, final Bytes location, final Bytes32 nodeHash) {
    if (nodeHash.equals(MerkleTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerkleTrie.EMPTY_TRIE_NODE);
    }
    return composedWorldStateStorage
        .get(
            TRIE_BRANCH_STORAGE,
            Bytes.concatenate(accountHash.getBytes(), location).toArrayUnsafe())
        .map(Bytes::wrap)
        .filter(b -> Hash.hash(b).getBytes().equals(nodeHash));
  }

  public Optional<Bytes> getTrieNodeUnsafe(final Bytes key) {
    return composedWorldStateStorage.get(TRIE_BRANCH_STORAGE, key.toArrayUnsafe()).map(Bytes::wrap);
  }

  public NavigableMap<Bytes32, AccountStorageEntry> storageEntriesFrom(
      final Hash addressHash, final Bytes32 startKeyHash, final int limit) {
    throw new RuntimeException("Bonsai Tries does not currently support enumerating storage");
  }

  public void upgradeToFullFlatDbMode() {
    flatDbStrategyProvider.upgradeToFullFlatDbMode(composedWorldStateStorage);
  }

  public void downgradeToPartialFlatDbMode() {
    flatDbStrategyProvider.downgradeToPartialFlatDbMode(composedWorldStateStorage);
  }

  @Override
  public void clear() {
    super.clear();
    flatDbStrategyProvider.loadFlatDbStrategy(composedWorldStateStorage);
  }

  @Override
  public BonsaiFlatDbStrategy getFlatDbStrategy() {
    return (BonsaiFlatDbStrategy)
        flatDbStrategyProvider.getFlatDbStrategy(composedWorldStateStorage);
  }

  @Override
  public Updater updater() {
    if (cacheManager != null) {
      return new CachedUpdater(
          composedWorldStateStorage.startTransaction(),
          trieLogStorage.startTransaction(),
          getFlatDbStrategy(),
          composedWorldStateStorage);
    }

    return new Updater(
        composedWorldStateStorage.startTransaction(),
        trieLogStorage.startTransaction(),
        getFlatDbStrategy(),
        composedWorldStateStorage);
  }

  public long getCacheSize(final SegmentIdentifier segment) {
    return cacheManager != null ? cacheManager.getCacheSize(segment) : 0;
  }

  public boolean isCached(final SegmentIdentifier segment, final byte[] key) {
    return cacheManager != null && cacheManager.isCached(segment, key);
  }

  public Optional<VersionedValue> getCachedValue(
      final SegmentIdentifier segment, final byte[] key) {
    return cacheManager != null ? cacheManager.getCachedValue(segment, key) : Optional.empty();
  }

  public VersionedCacheManager getCacheManager() {
    return cacheManager;
  }

  public long getCacheVersion() {
    return cacheVersion;
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
   * Manages versioned caching for world state data. Only used by layers for reading - base storage
   * only writes to it.
   */
  public static class VersionedCacheManager {
    private final AtomicLong globalVersion = new AtomicLong(0);
    private final Map<SegmentIdentifier, Cache<ByteArrayWrapper, VersionedValue>> caches;

    private final LabelledMetric<Counter> cacheRequestCounter;
    private final LabelledMetric<Counter> cacheHitCounter;
    private final LabelledMetric<Counter> cacheMissCounter;
    private final LabelledMetric<Counter> cacheInsertCounter;
    private final LabelledMetric<Counter> cacheRemovalCounter;

    public VersionedCacheManager(
        final long accountCacheSize,
        final long storageCacheSize,
        final MetricsSystem metricsSystem) {

      this.caches = new HashMap<>();
      caches.put(ACCOUNT_INFO_STATE, createCache(accountCacheSize));
      caches.put(ACCOUNT_STORAGE_STORAGE, createCache(storageCacheSize));

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
          .initialCapacity((int) (maxSize * 0.1))
          .maximumSize(maxSize)
          .build();
    }

    public long getCurrentVersion() {
      return globalVersion.get();
    }

    public long incrementAndGetVersion() {
      return globalVersion.incrementAndGet();
    }

    /**
     * Get from cache or storage - used only by layers. Updates cache if version matches current
     * version (read-through caching).
     */
    public Optional<Bytes> getFromCacheOrStorage(
        final SegmentIdentifier segment,
        final byte[] key,
        final long version,
        final Supplier<Optional<Bytes>> storageGetter) {

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

      // Update cache if version matches current version (only for current version reads)
      if (version == globalVersion.get()) {
        cacheInsertCounter.labels(segmentName).inc();
        if (result.isPresent()) {
          cache.put(wrapper, new VersionedValue(result.get().toArrayUnsafe(), version, false));
        } else {
          // Cache negative results (key doesn't exist)
          cache.put(wrapper, new VersionedValue(null, version, true));
        }
      }

      return result;
    }

    /**
     * Get multiple keys from cache or storage - used only by layers. Updates cache for missing keys
     * if version matches current version.
     */
    public List<Optional<byte[]>> getMultipleFromCacheOrStorage(
        final SegmentIdentifier segment,
        final List<byte[]> keys,
        final long version,
        final Function<List<byte[]>, List<Optional<byte[]>>> batchFetcher) {

      final String segmentName = segment.getName();
      final Cache<ByteArrayWrapper, VersionedValue> cache = caches.get(segment);

      if (cache == null) {
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

      // Second pass: fetch missing keys and optionally update cache
      if (!keysToFetch.isEmpty()) {
        final List<Optional<byte[]>> fetchedValues = batchFetcher.apply(keysToFetch);
        final boolean shouldUpdateCache = (version == globalVersion.get());

        for (int i = 0; i < fetchedValues.size(); i++) {
          final Optional<byte[]> fetchedValue = fetchedValues.get(i);
          final int resultIndex = indicesToFetch.get(i);
          final byte[] key = keysToFetch.get(i);

          results.set(resultIndex, fetchedValue);

          // Update cache if version matches current version
          if (shouldUpdateCache) {
            cacheInsertCounter.labels(segmentName).inc();
            final ByteArrayWrapper wrapper = new ByteArrayWrapper(key);
            if (fetchedValue.isPresent()) {
              cache.put(wrapper, new VersionedValue(fetchedValue.get(), version, false));
            } else {
              // Cache negative results
              cache.put(wrapper, new VersionedValue(null, version, true));
            }
          }
        }
      }

      return results;
    }

    /** Write to cache - called only by base storage updater on commit. */
    public void putInCache(
        final SegmentIdentifier segment, final byte[] key, final byte[] value, final long version) {
      final Cache<ByteArrayWrapper, VersionedValue> cache = caches.get(segment);
      if (cache != null) {
        cache.put(new ByteArrayWrapper(key), new VersionedValue(value, version, false));
        cacheInsertCounter.labels(segment.getName()).inc();
      }
    }

    /** Mark removal in cache - called only by base storage updater on commit. */
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

  public static class Updater implements PathBasedWorldStateKeyValueStorage.Updater {

    protected final SegmentedKeyValueStorageTransaction composedWorldStateTransaction;
    protected final KeyValueStorageTransaction trieLogStorageTransaction;
    protected final FlatDbStrategy flatDbStrategy;
    protected final SegmentedKeyValueStorage worldStorage;

    public Updater(
        final SegmentedKeyValueStorageTransaction composedWorldStateTransaction,
        final KeyValueStorageTransaction trieLogStorageTransaction,
        final FlatDbStrategy flatDbStrategy,
        final SegmentedKeyValueStorage worldStorage) {

      this.composedWorldStateTransaction = composedWorldStateTransaction;
      this.trieLogStorageTransaction = trieLogStorageTransaction;
      this.flatDbStrategy = flatDbStrategy;
      this.worldStorage = worldStorage;
    }

    public Updater removeCode(final Hash accountHash, final Hash codeHash) {
      flatDbStrategy.removeFlatCode(
          worldStorage, composedWorldStateTransaction, accountHash, codeHash);
      return this;
    }

    public Updater putCode(final Hash accountHash, final Bytes code) {
      final Hash codeHash = code.size() == 0 ? Hash.EMPTY : Hash.hash(code);
      return putCode(accountHash, codeHash, code);
    }

    public Updater putCode(final Hash accountHash, final Hash codeHash, final Bytes code) {
      if (code.isEmpty()) {
        return this;
      }
      flatDbStrategy.putFlatCode(
          worldStorage, composedWorldStateTransaction, accountHash, codeHash, code);
      return this;
    }

    public Updater removeAccountInfoState(final Hash accountHash) {
      flatDbStrategy.removeFlatAccount(worldStorage, composedWorldStateTransaction, accountHash);
      return this;
    }

    public Updater putAccountInfoState(final Hash accountHash, final Bytes accountValue) {
      if (accountValue.isEmpty()) {
        return this;
      }
      flatDbStrategy.putFlatAccount(
          worldStorage, composedWorldStateTransaction, accountHash, accountValue);
      return this;
    }

    @Override
    public Updater saveWorldState(final Bytes blockHash, final Bytes32 nodeHash, final Bytes node) {
      composedWorldStateTransaction.put(
          TRIE_BRANCH_STORAGE, Bytes.EMPTY.toArrayUnsafe(), node.toArrayUnsafe());
      composedWorldStateTransaction.put(
          TRIE_BRANCH_STORAGE, WORLD_ROOT_HASH_KEY, nodeHash.toArrayUnsafe());
      composedWorldStateTransaction.put(
          TRIE_BRANCH_STORAGE, WORLD_BLOCK_HASH_KEY, blockHash.toArrayUnsafe());
      return this;
    }

    public Updater putAccountStateTrieNode(
        final Bytes location, final Bytes32 nodeHash, final Bytes node) {
      if (nodeHash.equals(MerkleTrie.EMPTY_TRIE_NODE_HASH)) {
        return this;
      }
      composedWorldStateTransaction.put(
          TRIE_BRANCH_STORAGE, location.toArrayUnsafe(), node.toArrayUnsafe());
      return this;
    }

    public Updater removeAccountStateTrieNode(final Bytes location) {
      composedWorldStateTransaction.remove(TRIE_BRANCH_STORAGE, location.toArrayUnsafe());
      return this;
    }

    public synchronized Updater putAccountStorageTrieNode(
        final Hash accountHash, final Bytes location, final Bytes32 nodeHash, final Bytes node) {
      if (nodeHash.equals(MerkleTrie.EMPTY_TRIE_NODE_HASH)) {
        return this;
      }
      composedWorldStateTransaction.put(
          TRIE_BRANCH_STORAGE,
          Bytes.concatenate(accountHash.getBytes(), location).toArrayUnsafe(),
          node.toArrayUnsafe());
      return this;
    }

    public synchronized Updater putStorageValueBySlotHash(
        final Hash accountHash, final Hash slotHash, final Bytes storageValue) {
      flatDbStrategy.putFlatAccountStorageValueByStorageSlotHash(
          worldStorage, composedWorldStateTransaction, accountHash, slotHash, storageValue);
      return this;
    }

    public synchronized void removeStorageValueBySlotHash(
        final Hash accountHash, final Hash slotHash) {
      flatDbStrategy.removeFlatAccountStorageValueByStorageSlotHash(
          worldStorage, composedWorldStateTransaction, accountHash, slotHash);
    }

    @Override
    public SegmentedKeyValueStorageTransaction getWorldStateTransaction() {
      return composedWorldStateTransaction;
    }

    @Override
    public KeyValueStorageTransaction getTrieLogStorageTransaction() {
      return trieLogStorageTransaction;
    }

    @Override
    public void commit() {
      trieLogStorageTransaction.commit();
      composedWorldStateTransaction.commit();
    }

    @Override
    public void commitTrieLogOnly() {
      trieLogStorageTransaction.commit();
      composedWorldStateTransaction.close();
    }

    @Override
    public void commitComposedOnly() {
      composedWorldStateTransaction.commit();
      trieLogStorageTransaction.close();
    }

    @Override
    public void rollback() {
      composedWorldStateTransaction.rollback();
      trieLogStorageTransaction.rollback();
    }
  }

  /**
   * Cached updater that stages changes and updates cache on commit. Used only by base storage (not
   * snapshots or layers).
   */
  public class CachedUpdater extends Updater {

    private final Map<SegmentIdentifier, Map<ByteArrayWrapper, byte[]>> pending = new HashMap<>();
    private final Map<SegmentIdentifier, Map<ByteArrayWrapper, Boolean>> pendingRemovals =
        new HashMap<>();

    public CachedUpdater(
        final SegmentedKeyValueStorageTransaction composedWorldStateTransaction,
        final KeyValueStorageTransaction trieLogStorageTransaction,
        final FlatDbStrategy flatDbStrategy,
        final SegmentedKeyValueStorage worldStorage) {
      super(composedWorldStateTransaction, trieLogStorageTransaction, flatDbStrategy, worldStorage);
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

    protected void updateCache() {
      cacheVersion = cacheManager.incrementAndGetVersion();
      // Apply all pending updates to cache
      pending.forEach(
          (segment, updates) ->
              updates.forEach(
                  (wrapper, value) ->
                      cacheManager.putInCache(segment, wrapper.getData(), value, cacheVersion)));

      // Apply all pending removals to cache
      pendingRemovals.forEach(
          (segment, removals) ->
              removals
                  .keySet()
                  .forEach(
                      wrapper ->
                          cacheManager.removeFromCache(segment, wrapper.getData(), cacheVersion)));

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
