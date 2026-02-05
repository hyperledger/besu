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
package org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.cache;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE;

import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.tuweni.bytes.Bytes;

/** Versioned cache implementation using Caffeine. */
public class VersionedCacheManager implements CacheManager {
  private final AtomicLong globalVersion = new AtomicLong(0);
  private final Map<SegmentIdentifier, Cache<ByteArrayWrapper, VersionedValue>> caches;

  private final LabelledMetric<Counter> cacheRequestCounter;
  private final LabelledMetric<Counter> cacheHitCounter;
  private final LabelledMetric<Counter> cacheMissCounter;
  private final LabelledMetric<Counter> cacheInsertCounter;
  private final LabelledMetric<Counter> cacheRemovalCounter;

  public VersionedCacheManager(
      final long accountCacheSize, final long storageCacheSize, final MetricsSystem metricsSystem) {

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

  @Override
  public long getCurrentVersion() {
    return globalVersion.get();
  }

  @Override
  public long incrementAndGetVersion() {
    return globalVersion.incrementAndGet();
  }

  @Override
  public void clear(final SegmentIdentifier segment) {
    final Cache<ByteArrayWrapper, VersionedValue> cache = caches.get(segment);
    if (cache != null) {
      cache.invalidateAll();
    }
  }

  @Override
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

    if (versionedValue != null && versionedValue.version <= version) {
      cacheHitCounter.labels(segmentName).inc();
      return versionedValue.isRemoval
          ? Optional.empty()
          : Optional.of(Bytes.wrap(versionedValue.value));
    }

    cacheMissCounter.labels(segmentName).inc();
    final Optional<Bytes> result = storageGetter.get();

    if (version == globalVersion.get()) {
      cacheInsertCounter.labels(segmentName).inc();
      final byte[] valueToCache = result.map(Bytes::toArrayUnsafe).orElse(null);
      final boolean isRemoval = result.isEmpty();

      cache
          .asMap()
          .compute(
              wrapper,
              (k, existingValue) -> {
                if (existingValue == null || existingValue.version < version) {
                  return new VersionedValue(valueToCache, version, isRemoval);
                }
                return existingValue;
              });
    }

    return result;
  }

  @Override
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

    for (int i = 0; i < keys.size(); i++) {
      final byte[] key = keys.get(i);
      cacheRequestCounter.labels(segmentName).inc();

      final ByteArrayWrapper wrapper = new ByteArrayWrapper(key);
      final VersionedValue versionedValue = cache.getIfPresent(wrapper);

      if (versionedValue != null && versionedValue.version <= version) {
        cacheHitCounter.labels(segmentName).inc();
        results.add(
            versionedValue.isRemoval ? Optional.empty() : Optional.of(versionedValue.value));
      } else {
        cacheMissCounter.labels(segmentName).inc();
        results.add(null);
        keysToFetch.add(key);
        indicesToFetch.add(i);
      }
    }

    if (!keysToFetch.isEmpty()) {
      final List<Optional<byte[]>> fetchedValues = batchFetcher.apply(keysToFetch);
      final boolean shouldUpdateCache = version == globalVersion.get();

      for (int i = 0; i < fetchedValues.size(); i++) {
        final Optional<byte[]> fetchedValue = fetchedValues.get(i);
        final int resultIndex = indicesToFetch.get(i);
        final byte[] key = keysToFetch.get(i);

        results.set(resultIndex, fetchedValue);

        if (shouldUpdateCache) {
          cacheInsertCounter.labels(segmentName).inc();
          final ByteArrayWrapper wrapper = new ByteArrayWrapper(key);
          final byte[] valueToCache = fetchedValue.orElse(null);
          final boolean isRemoval = fetchedValue.isEmpty();

          cache
              .asMap()
              .compute(
                  wrapper,
                  (k, existingValue) -> {
                    if (existingValue == null || existingValue.version < version) {
                      return new VersionedValue(valueToCache, version, isRemoval);
                    }
                    return existingValue;
                  });
        }
      }
    }

    return results;
  }

  @Override
  public void putInCache(
      final SegmentIdentifier segment, final byte[] key, final byte[] value, final long version) {
    final Cache<ByteArrayWrapper, VersionedValue> cache = caches.get(segment);
    if (cache != null) {
      final ByteArrayWrapper wrapper = new ByteArrayWrapper(key);
      cache
          .asMap()
          .compute(
              wrapper,
              (k, existingValue) -> {
                if (existingValue == null || existingValue.version < version) {
                  cacheInsertCounter.labels(segment.getName()).inc();
                  return new VersionedValue(value, version, false);
                }
                return existingValue;
              });
    }
  }

  @Override
  public void removeFromCache(
      final SegmentIdentifier segment, final byte[] key, final long version) {
    final Cache<ByteArrayWrapper, VersionedValue> cache = caches.get(segment);
    if (cache != null) {
      final ByteArrayWrapper wrapper = new ByteArrayWrapper(key);
      cache
          .asMap()
          .compute(
              wrapper,
              (k, existingValue) -> {
                if (existingValue == null || existingValue.version < version) {
                  cacheRemovalCounter.labels(segment.getName()).inc();
                  return new VersionedValue(null, version, true);
                }
                return existingValue;
              });
    }
  }

  @Override
  public long getCacheSize(final SegmentIdentifier segment) {
    final Cache<ByteArrayWrapper, VersionedValue> cache = caches.get(segment);
    return cache != null ? cache.estimatedSize() : 0;
  }

  @Override
  public boolean isCached(final SegmentIdentifier segment, final byte[] key) {
    final Cache<ByteArrayWrapper, VersionedValue> cache = caches.get(segment);
    return cache != null && cache.getIfPresent(new ByteArrayWrapper(key)) != null;
  }

  @Override
  public Optional<VersionedValue> getCachedValue(
      final SegmentIdentifier segment, final byte[] key) {
    final Cache<ByteArrayWrapper, VersionedValue> cache = caches.get(segment);
    return cache != null
        ? Optional.ofNullable(cache.getIfPresent(new ByteArrayWrapper(key)))
        : Optional.empty();
  }
}
