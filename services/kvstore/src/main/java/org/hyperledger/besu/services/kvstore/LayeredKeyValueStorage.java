/*
 * Copyright Hyperledger Besu Contributors.
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
 *
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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

    final Lock lock = rwLock.readLock();
    lock.lock();
    try {
      Bytes wrapKey = Bytes.wrap(key);
      final Optional<byte[]> foundKey =
          hashValueStore.computeIfAbsent(segmentId, __ -> newSegmentMap()).get(wrapKey);
      if (foundKey == null) {
        return parent.get(segmentId, key);
      } else {
        return foundKey;
      }
    } finally {
      lock.unlock();
    }
  }

  @Override
  public Optional<NearestKeyValue> getNearestTo(
      final SegmentIdentifier segmentIdentifier, final Bytes key) throws StorageException {
    Optional<NearestKeyValue> ourNearest = super.getNearestTo(segmentIdentifier, key);
    Optional<NearestKeyValue> parentNearest = parent.getNearestTo(segmentIdentifier, key);

    if (ourNearest.isPresent() && parentNearest.isPresent()) {
      // Both are present, return the one closer to the key
      int ourDistance = ourNearest.get().key().commonPrefixLength(key);
      int parentDistance = parentNearest.get().key().commonPrefixLength(key);
      return (ourDistance <= parentDistance) ? ourNearest : parentNearest;
    } else if (ourNearest.isPresent()) {
      // Only ourNearest is present
      return ourNearest;
    } else {
      // return parentNearest, which may be an empty Optional
      return parentNearest;
    }
  }

  @Override
  public Stream<Pair<byte[], byte[]>> stream(final SegmentIdentifier segmentId) {
    throwIfClosed();
    var ourLayerState = hashValueStore.computeIfAbsent(segmentId, s -> newSegmentMap());

    if (ourLayerState == null) {
      return parent.stream(segmentId);
    }

    // otherwise, interleave the sorted streams:
    final PeekingIterator<Map.Entry<Bytes, Optional<byte[]>>> ourIterator =
        new PeekingIterator<>(
            ourLayerState.entrySet().stream()
                .filter(entry -> entry.getValue().isPresent())
                .iterator());

    final PeekingIterator<Pair<byte[], byte[]>> parentIterator =
        new PeekingIterator<>(parent.stream(segmentId).iterator());

    return StreamSupport.stream(
        Spliterators.spliteratorUnknownSize(
            new Iterator<>() {
              @Override
              public boolean hasNext() {
                return ourIterator.hasNext() || parentIterator.hasNext();
              }

              private Pair<byte[], byte[]> mapEntryToPair(
                  final Map.Entry<Bytes, Optional<byte[]>> entry) {
                return Optional.of(entry)
                    .map(
                        e ->
                            Pair.of(
                                e.getKey().toArrayUnsafe(),
                                e.getValue().orElseGet(() -> new byte[0])))
                    .get();
              }

              @Override
              public Pair<byte[], byte[]> next() {
                var ourPeek = ourIterator.peek();
                var parentPeek = parentIterator.peek();

                if (ourPeek == null || parentPeek == null) {
                  return ourPeek == null
                      ? parentIterator.next()
                      : mapEntryToPair(ourIterator.next());
                }

                // otherwise compare:
                int comparison = ourPeek.getKey().compareTo(Bytes.wrap(parentPeek.getKey()));
                if (comparison < 0) {
                  return mapEntryToPair(ourIterator.next());
                } else if (comparison == 0) {
                  // skip dupe key from parent, return ours:
                  parentIterator.next();
                  return mapEntryToPair(ourIterator.next());
                } else {
                  return parentIterator.next();
                }
              }
            },
            ORDERED | SORTED | DISTINCT),
        false);
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

    final Lock lock = rwLock.readLock();
    lock.lock();
    try {
      // copy of our in memory store to use for streaming and filtering:
      var ourLayerState =
          Optional.ofNullable(hashValueStore.get(segmentId))
              .map(HashMap::new)
              .orElse(new HashMap<>());

      return Streams.concat(
          ourLayerState.entrySet().stream()
              .filter(entry -> entry.getValue().isPresent())
              .map(bytesEntry -> bytesEntry.getKey().toArrayUnsafe())
          // since we are layered, concat a parent stream filtered by our map entries:
          ,
          parent.streamKeys(segmentId).filter(e -> !ourLayerState.containsKey(Bytes.of(e))));

    } finally {
      lock.unlock();
    }
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
    return parent.isClosed();
  }

  @Override
  public SnappedKeyValueStorage clone() {
    return new LayeredKeyValueStorage(hashValueStore, parent);
  }

  private void throwIfClosed() {
    if (parent.isClosed()) {
      LOG.error("Attempting to use a closed RocksDBKeyValueStorage");
      throw new StorageException("Storage has been closed");
    }
  }

  private static class PeekingIterator<E> implements Iterator<E> {
    private final Iterator<E> iterator;
    private E next;

    public PeekingIterator(final Iterator<E> iterator) {
      this.iterator = iterator;
      this.next = iterator.hasNext() ? iterator.next() : null;
    }

    public E peek() {
      return next;
    }

    @Override
    public boolean hasNext() {
      return next != null;
    }

    @Override
    public E next() {
      E oldNext = next;
      next = iterator.hasNext() ? iterator.next() : null;
      return oldNext;
    }
  }
}
