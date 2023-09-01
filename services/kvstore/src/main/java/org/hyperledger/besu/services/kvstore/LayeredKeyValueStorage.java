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

import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.SnappedKeyValueStorage;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
      final Map<SegmentIdentifier, Map<Bytes, Optional<byte[]>>> map,
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
          hashValueStore.computeIfAbsent(segmentId, __ -> new HashMap<>()).get(wrapKey);
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
  public Stream<Pair<byte[], byte[]>> stream(final SegmentIdentifier segmentId) {
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
              .map(
                  bytesEntry ->
                      Pair.of(bytesEntry.getKey().toArrayUnsafe(), bytesEntry.getValue().get()))
          // since we are layered, concat a parent stream filtered by our map entries:
          ,
          parent.stream(segmentId).filter(e -> !ourLayerState.containsKey(Bytes.of(e.getLeft()))));
    } finally {
      lock.unlock();
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
        .computeIfAbsent(segmentId, __ -> new HashMap<>())
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
                              .computeIfAbsent(entry.getKey(), __ -> new HashMap<>())
                              .putAll(entry.getValue()));

              // put empty rather than remove in order to not ask parent in case of deletion
              removedKeys.entrySet().stream()
                  .forEach(
                      segmentEntry ->
                          hashValueStore
                              .computeIfAbsent(segmentEntry.getKey(), __ -> new HashMap<>())
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
}
