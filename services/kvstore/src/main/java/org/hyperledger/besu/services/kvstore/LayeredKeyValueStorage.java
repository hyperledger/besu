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
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.SnappedKeyValueStorage;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;

/** Key value storage which stores in memory all updates to a parent worldstate storage. */
public class LayeredKeyValueStorage extends InMemoryKeyValueStorage
    implements SnappedKeyValueStorage {

  private final KeyValueStorage parent;

  /**
   * Instantiates a new Layered key value storage.
   *
   * @param parent the parent key value storage for this layered storage.
   */
  public LayeredKeyValueStorage(final KeyValueStorage parent) {
    this(new ConcurrentHashMap<>(), parent);
  }

  /**
   * Constructor which takes an explicit backing map for the layered key value storage.
   *
   * @param map the backing map
   * @param parent the parent key value storage for this layered storage.
   */
  public LayeredKeyValueStorage(final Map<Bytes, byte[]> map, final KeyValueStorage parent) {
    super(map);
    this.parent = parent;
  }

  @Override
  public boolean containsKey(final byte[] key) throws StorageException {
    return get(key).isPresent();
  }

  @Override
  public Optional<byte[]> get(final byte[] key) throws StorageException {
    Bytes wrapKey = Bytes.wrap(key);
    return Optional.of(wrapKey)
        .flatMap(
            keyWrapped ->
                Optional.ofNullable(hashValueStore.get(keyWrapped))
                    .or(
                        () ->
                            parent
                                .get(key)
                                .map(
                                    value -> {
                                      cacheFromParent(wrapKey, value);
                                      return value;
                                    })))
        .filter(bytes -> bytes.length > 0);
  }

  private void cacheFromParent(final Bytes key, final byte[] value) {
    if (!(parent instanceof LayeredKeyValueStorage)) {
      hashValueStore.put(key, value);
    }
  }

  @Override
  public Stream<Pair<byte[], byte[]>> stream() {
    final Lock lock = rwLock.readLock();
    lock.lock();
    try {
      // immutable copy of our in memory store to use for streaming and filtering:
      Map<Bytes, byte[]> ourLayerState = ImmutableMap.copyOf(hashValueStore);

      return Streams.concat(
          ourLayerState.entrySet().stream()
              .filter(entry -> entry.getValue().length > 0)
              .map(
                  bytesEntry -> Pair.of(bytesEntry.getKey().toArrayUnsafe(), bytesEntry.getValue()))
          // since we are layered, concat a parent stream filtered by our map entries:
          ,
          parent.stream().filter(e -> !ourLayerState.containsKey(Bytes.of(e.getLeft()))));
    } finally {
      lock.unlock();
    }
  }

  @Override
  public Stream<byte[]> streamKeys() {
    final Lock lock = rwLock.readLock();
    lock.lock();
    try {
      // immutable copy of our in memory store to use for streaming and filtering:
      Map<Bytes, byte[]> ourLayerState = ImmutableMap.copyOf(hashValueStore);

      return Streams.concat(
          ourLayerState.entrySet().stream()
              .filter(entry -> entry.getValue().length > 0)
              .map(bytesEntry -> bytesEntry.getKey().toArrayUnsafe())
          // since we are layered, concat a parent stream filtered by our map entries:
          ,
          parent.streamKeys().filter(e -> !ourLayerState.containsKey(Bytes.of(e))));

    } finally {
      lock.unlock();
    }
  }

  @Override
  public boolean tryDelete(final byte[] key) {
    // TODO: can we rely on zero byte array to indicate deletion?
    hashValueStore.put(Bytes.wrap(key), new byte[0]);
    return true;
  }

  @Override
  public KeyValueStorageTransaction startTransaction() {
    return new KeyValueStorageTransactionTransitionValidatorDecorator(
        new InMemoryTransaction() {
          @Override
          public void commit() throws StorageException {

            final Lock lock = rwLock.writeLock();
            lock.lock();
            try {
              hashValueStore.putAll(updatedValues);
              removedKeys.forEach(key -> hashValueStore.put(key, new byte[] {}));
              // put empty and not removed to not ask parent in case of deletion
              updatedValues = null;
              removedKeys = null;
            } finally {
              lock.unlock();
            }
          }
        });
  }

  @Override
  public SnappedKeyValueStorage clone() {
    return new LayeredKeyValueStorage(hashValueStore, parent);
  }
}
