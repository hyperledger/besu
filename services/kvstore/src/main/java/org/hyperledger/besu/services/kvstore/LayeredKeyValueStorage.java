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
package org.hyperledger.besu.services.kvstore;

import static java.util.Spliterator.DISTINCT;
import static java.util.Spliterator.ORDERED;
import static java.util.Spliterator.SORTED;

import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.SnappedKeyValueStorage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Streams;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Key value storage which stores in memory all updates to a parent worldstate storage. */
public class LayeredKeyValueStorage extends SegmentedInMemoryKeyValueStorage
    implements SnappedKeyValueStorage {

  private static final Logger LOG = LoggerFactory.getLogger(LayeredKeyValueStorage.class);

  private final SegmentedKeyValueStorage parent;

  /**
   * Cached result of parent.isClosed(). The closed state transitions from false to true exactly
   * once per process lifetime and never back, so caching the first true result is safe and converts
   * the O(N) parent-chain walk into an O(1) check.
   */
  private volatile boolean closedCache = false;

  /**
   * Instantiates a new Layered key value storage.
   *
   * @param parent the parent key value storage for this layered storage.
   */
  public LayeredKeyValueStorage(final SegmentedKeyValueStorage parent) {
    this(new ConcurrentHashMap<>(), parent);
  }

  /**
   * Constructor which takes an explicit backing map for the layered key value storage.
   *
   * @param map the backing map
   * @param parent the parent key value storage for this layered storage.
   */
  public LayeredKeyValueStorage(
      final ConcurrentMap<SegmentIdentifier, NavigableMap<Bytes, Optional<byte[]>>> map,
      final SegmentedKeyValueStorage parent) {
    super(map);
    this.parent = parent;
  }

  @Override
  public boolean containsKey(final SegmentIdentifier segmentId, final byte[] key)
      throws StorageException {
    return get(segmentId, key).isPresent();
  }

  @Override
  public Optional<byte[]> get(final SegmentIdentifier segmentId, final byte[] key)
      throws StorageException {
    throwIfClosed();
    final Bytes wrapKey = Bytes.wrap(key);
    LayeredKeyValueStorage cur = this;
    Optional<byte[]> found = cur.getLocal(segmentId, wrapKey);
    while (found == null && cur.parent instanceof LayeredKeyValueStorage next) {
      cur = next;
      found = cur.getLocal(segmentId, wrapKey);
    }
    if (found != null) return found;
    return cur.parent.get(segmentId, key);
  }

  /**
   * Bytes-keyed variant for callers that want to inject a function evaluated at the bottom of the
   * layer chain when no layer has a value for the key. Used by Bonsai to consult the cross-block
   * cache without going through the persistent storage twice.
   *
   * @param segmentId the segment identifier
   * @param key the key
   * @param cacheGetFunction function evaluated on the persistent parent when no layer holds the key
   * @return optional value as {@link Bytes}
   */
  public Optional<Bytes> get(
      final SegmentIdentifier segmentId,
      final Bytes key,
      final Function<SegmentedKeyValueStorage, Optional<Bytes>> cacheGetFunction) {
    throwIfClosed();
    LayeredKeyValueStorage cur = this;
    Optional<byte[]> found = cur.getLocal(segmentId, key);
    while (found == null && cur.parent instanceof LayeredKeyValueStorage next) {
      cur = next;
      found = cur.getLocal(segmentId, key);
    }
    if (found != null) return found.map(Bytes::wrap);
    return cacheGetFunction != null
        ? cacheGetFunction.apply(cur.parent)
        : cur.parent.get(segmentId, key.toArrayUnsafe()).map(Bytes::wrap);
  }

  private Optional<byte[]> getLocal(final SegmentIdentifier segmentId, final Bytes key) {
    final Lock lock = rwLock.readLock();
    lock.lock();
    try {
      final NavigableMap<Bytes, Optional<byte[]>> segment = hashValueStore.get(segmentId);
      return segment == null ? null : segment.get(key);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public Optional<NearestKeyValue> getNearestBefore(
      final SegmentIdentifier segmentIdentifier, final Bytes key) throws StorageException {
    return getNearest(
        key,
        k -> super.getNearestBefore(segmentIdentifier, k),
        k -> parent.getNearestBefore(segmentIdentifier, k),
        false);
  }

  @Override
  public Optional<NearestKeyValue> getNearestAfter(
      final SegmentIdentifier segmentIdentifier, final Bytes key) throws StorageException {
    return getNearest(
        key,
        k -> super.getNearestAfter(segmentIdentifier, k),
        k -> parent.getNearestAfter(segmentIdentifier, k),
        true);
  }

  private Optional<NearestKeyValue> getNearest(
      final Bytes key,
      final Function<Bytes, Optional<NearestKeyValue>> ourNearestFunction,
      final Function<Bytes, Optional<NearestKeyValue>> parentNearestFunction,
      final boolean isAfter)
      throws StorageException {

    final Optional<NearestKeyValue> ourNearest = ourNearestFunction.apply(key);
    final Optional<NearestKeyValue> parentNearest = parentNearestFunction.apply(key);

    if (ourNearest.isPresent() && parentNearest.isPresent()) {
      return compareNearest(ourNearest, parentNearest, key, isAfter);
    } else if (ourNearest.isPresent()) {
      return ourNearest;
    } else {
      return parentNearest;
    }
  }

  private Optional<NearestKeyValue> compareNearest(
      final Optional<NearestKeyValue> ourNearest,
      final Optional<NearestKeyValue> parentNearest,
      final Bytes key,
      final boolean isAfter) {

    final int ourDistance = ourNearest.get().key().compareTo(key);
    final int parentDistance = parentNearest.get().key().compareTo(key);
    if (ourDistance == 0) {
      return ourNearest;
    } else if (parentDistance == 0) {
      return parentNearest;
    } else {
      final int ourCommonPrefixLength = ourNearest.get().key().commonPrefixLength(key);
      final int parentCommonPrefixLength = parentNearest.get().key().commonPrefixLength(key);
      if (ourCommonPrefixLength != parentCommonPrefixLength) {
        return ourCommonPrefixLength > parentCommonPrefixLength ? ourNearest : parentNearest;
      } else {
        // When searching for a key, if isAfter is true, we choose the next smallest key after our
        // target because both found keys are after it.
        // If isAfter is false, meaning we're doing a seekForPrev, we select the largest key that
        // comes before our target, as it's the nearest one.
        // For example : if the searched key is 0x0101 and we found 0x0001 and 0x0100 when isAfter
        // == false we will take 0x0100
        if (ourNearest.get().key().compareTo(parentNearest.get().key()) > 0) {
          return isAfter ? parentNearest : ourNearest;
        } else {
          return isAfter ? ourNearest : parentNearest;
        }
      }
    }
  }

  @Override
  public Stream<Pair<byte[], byte[]>> stream(final SegmentIdentifier segmentId) {
    throwIfClosed();
    // Merge all layers (top first) and the persistent parent in one pass rather than recursively.
    final List<Iterator<Map.Entry<Bytes, Optional<byte[]>>>> sources = new ArrayList<>();
    SegmentedKeyValueStorage cur = this;
    while (cur instanceof LayeredKeyValueStorage layered) {
      final NavigableMap<Bytes, Optional<byte[]>> segment = layered.hashValueStore.get(segmentId);
      if (segment != null) {
        sources.add(segment.entrySet().iterator());
      }
      cur = layered.parent;
    }
    sources.add(
        cur.stream(segmentId)
            .map(pair -> Map.entry(Bytes.wrap(pair.getKey()), Optional.ofNullable(pair.getValue())))
            .iterator());

    return StreamSupport.stream(
        Spliterators.spliteratorUnknownSize(
            new LayeredMergeIterator(sources), ORDERED | SORTED | DISTINCT),
        false);
  }

  /** Merges sorted sources; higher layers win on duplicate keys and empty values are deletions. */
  private static class LayeredMergeIterator extends AbstractIterator<Pair<byte[], byte[]>> {

    private static final class Source {
      private final int rank;
      private final Iterator<Map.Entry<Bytes, Optional<byte[]>>> iterator;
      private Map.Entry<Bytes, Optional<byte[]>> head;

      Source(final int rank, final Iterator<Map.Entry<Bytes, Optional<byte[]>>> iterator) {
        this.rank = rank;
        this.iterator = iterator;
      }
    }

    private final PriorityQueue<Source> queue =
        new PriorityQueue<>(
            Comparator.comparing((Source s) -> s.head.getKey()).thenComparingInt(s -> s.rank));

    LayeredMergeIterator(final List<Iterator<Map.Entry<Bytes, Optional<byte[]>>>> sources) {
      for (int i = 0; i < sources.size(); i++) {
        advance(new Source(i, sources.get(i)));
      }
    }

    private void advance(final Source source) {
      if (source.iterator.hasNext()) {
        source.head = source.iterator.next();
        queue.add(source);
      }
    }

    @Override
    protected Pair<byte[], byte[]> computeNext() {
      while (!queue.isEmpty()) {
        final Source top = queue.poll();
        final Map.Entry<Bytes, Optional<byte[]>> winner = top.head;
        advance(top);
        // drop the same key from lower-precedence sources
        while (!queue.isEmpty() && queue.peek().head.getKey().compareTo(winner.getKey()) == 0) {
          advance(queue.poll());
        }
        if (winner.getValue().isPresent()) {
          return Pair.of(winner.getKey().toArrayUnsafe(), winner.getValue().get());
        }
      }
      return endOfData();
    }
  }

  @Override
  public Stream<Pair<byte[], byte[]>> streamFromKey(
      final SegmentIdentifier segmentId, final byte[] startKey) {
    final Bytes startKeyBytes = Bytes.wrap(startKey);
    return stream(segmentId).filter(e -> startKeyBytes.compareTo(Bytes.wrap(e.getKey())) <= 0);
  }

  @Override
  public Stream<Pair<byte[], byte[]>> streamFromKey(
      final SegmentIdentifier segmentId, final byte[] startKey, final byte[] endKey) {
    final Bytes startKeyBytes = Bytes.wrap(startKey);
    final Bytes endKeyBytes = Bytes.wrap(endKey);
    return stream(segmentId)
        .filter(e -> startKeyBytes.compareTo(Bytes.wrap(e.getKey())) <= 0)
        .filter(e -> endKeyBytes.compareTo(Bytes.wrap(e.getKey())) >= 0);
  }

  @Override
  public Stream<byte[]> streamKeys(final SegmentIdentifier segmentId) {
    throwIfClosed();
    // Collapse all layers into one key -> isPresent map; the highest layer with a key decides.
    final Map<Bytes, Boolean> layeredKeys = new HashMap<>();
    SegmentedKeyValueStorage cur = this;
    while (cur instanceof LayeredKeyValueStorage layered) {
      final Lock lock = layered.rwLock.readLock();
      lock.lock();
      try {
        final NavigableMap<Bytes, Optional<byte[]>> segment = layered.hashValueStore.get(segmentId);
        if (segment != null) {
          segment.forEach((key, value) -> layeredKeys.putIfAbsent(key, value.isPresent()));
        }
      } finally {
        lock.unlock();
      }
      cur = layered.parent;
    }

    return Streams.concat(
        layeredKeys.entrySet().stream()
            .filter(Map.Entry::getValue)
            .map(entry -> entry.getKey().toArrayUnsafe()),
        cur.streamKeys(segmentId).filter(key -> !layeredKeys.containsKey(Bytes.wrap(key))));
  }

  @Override
  public boolean tryDelete(final SegmentIdentifier segmentId, final byte[] key) {
    hashValueStore
        .computeIfAbsent(segmentId, __ -> newSegmentMap())
        .put(Bytes.wrap(key), Optional.empty());
    return true;
  }

  @Override
  public SegmentedKeyValueStorageTransaction startTransaction() {
    throwIfClosed();

    return new SegmentedKeyValueStorageTransactionValidatorDecorator(
        new SegmentedInMemoryTransaction() {
          @Override
          public void commit() throws StorageException {
            final Lock lock = rwLock.writeLock();
            lock.lock();
            try {
              updatedValues.entrySet().stream()
                  .forEach(
                      entry ->
                          hashValueStore
                              .computeIfAbsent(entry.getKey(), __ -> newSegmentMap())
                              .putAll(entry.getValue()));

              // put empty rather than remove in order to not ask parent in case of deletion
              removedKeys.entrySet().stream()
                  .forEach(
                      segmentEntry ->
                          hashValueStore
                              .computeIfAbsent(segmentEntry.getKey(), __ -> newSegmentMap())
                              .putAll(
                                  segmentEntry.getValue().stream()
                                      .collect(
                                          Collectors.toMap(key -> key, __ -> Optional.empty()))));

              updatedValues.clear();
              removedKeys.clear();
            } finally {
              lock.unlock();
            }
          }
        },
        this::isClosed);
  }

  @Override
  public boolean isClosed() {
    if (closedCache) return true;
    SegmentedKeyValueStorage cur = parent;
    while (cur instanceof LayeredKeyValueStorage layered) {
      if (layered.closedCache) return closedCache = true;
      cur = layered.parent;
    }
    if (cur.isClosed()) return closedCache = true;
    return false;
  }

  @Override
  public SnappedKeyValueStorage clone() {
    return new LayeredKeyValueStorage(hashValueStore, parent);
  }

  private void throwIfClosed() {
    if (isClosed()) {
      LOG.error("Attempting to use a closed RocksDBKeyValueStorage");
      throw new StorageException("Storage has been closed");
    }
  }
}
