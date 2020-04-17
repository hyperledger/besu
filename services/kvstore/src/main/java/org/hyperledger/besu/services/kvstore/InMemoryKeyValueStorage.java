/*
 * Copyright ConsenSys AG.
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

import static java.util.stream.Collectors.toUnmodifiableSet;

import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;

import org.apache.tuweni.bytes.Bytes;

public class InMemoryKeyValueStorage implements KeyValueStorage {

  private final Map<Bytes, byte[]> hashValueStore;
  private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

  public InMemoryKeyValueStorage() {
    this(new HashMap<>());
  }

  protected InMemoryKeyValueStorage(final Map<Bytes, byte[]> hashValueStore) {
    this.hashValueStore = hashValueStore;
  }

  @Override
  public void clear() {
    final Lock lock = rwLock.writeLock();
    lock.lock();
    try {
      hashValueStore.clear();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public boolean containsKey(final byte[] key) throws StorageException {
    return get(key).isPresent();
  }

  @Override
  public Optional<byte[]> get(final byte[] key) throws StorageException {
    final Lock lock = rwLock.readLock();
    lock.lock();
    try {
      return Optional.ofNullable(hashValueStore.get(Bytes.wrap(key)));
    } finally {
      lock.unlock();
    }
  }

  @Override
  public long removeAllKeysUnless(final Predicate<byte[]> retainCondition) throws StorageException {
    final Lock lock = rwLock.writeLock();
    lock.lock();
    try {
      long initialSize = hashValueStore.keySet().size();
      hashValueStore.keySet().removeIf(key -> !retainCondition.test(key.toArrayUnsafe()));
      return initialSize - hashValueStore.keySet().size();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public Set<byte[]> getAllKeysThat(final Predicate<byte[]> returnCondition) {
    final Lock lock = rwLock.readLock();
    lock.lock();
    try {
      return hashValueStore.keySet().stream()
          .map(Bytes::toArrayUnsafe)
          .filter(returnCondition)
          .collect(toUnmodifiableSet());
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void close() {}

  @Override
  public KeyValueStorageTransaction startTransaction() {
    return new KeyValueStorageTransactionTransitionValidatorDecorator(new InMemoryTransaction());
  }

  public Set<Bytes> keySet() {
    return Set.copyOf(hashValueStore.keySet());
  }

  private class InMemoryTransaction implements KeyValueStorageTransaction {

    private Map<Bytes, byte[]> updatedValues = new HashMap<>();
    private Set<Bytes> removedKeys = new HashSet<>();

    @Override
    public void put(final byte[] key, final byte[] value) {
      updatedValues.put(Bytes.wrap(key), value);
      removedKeys.remove(Bytes.wrap(key));
    }

    @Override
    public void remove(final byte[] key) {
      removedKeys.add(Bytes.wrap(key));
      updatedValues.remove(Bytes.wrap(key));
    }

    @Override
    public void commit() throws StorageException {
      final Lock lock = rwLock.writeLock();
      lock.lock();
      try {
        hashValueStore.putAll(updatedValues);
        removedKeys.forEach(hashValueStore::remove);
        updatedValues = null;
        removedKeys = null;
      } finally {
        lock.unlock();
      }
    }

    @Override
    public void rollback() {
      updatedValues.clear();
      removedKeys.clear();
    }
  }
}
