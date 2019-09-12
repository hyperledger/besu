/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.services.kvstore;

import tech.pegasys.pantheon.plugin.services.exception.StorageException;
import tech.pegasys.pantheon.plugin.services.storage.KeyValueStorage;
import tech.pegasys.pantheon.plugin.services.storage.KeyValueStorageTransaction;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;

public class InMemoryKeyValueStorage implements KeyValueStorage {

  private final Map<BytesValue, byte[]> hashValueStore;
  private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

  public InMemoryKeyValueStorage() {
    this(new HashMap<>());
  }

  protected InMemoryKeyValueStorage(final Map<BytesValue, byte[]> hashValueStore) {
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
    final Lock lock = rwLock.readLock();
    lock.lock();
    try {
      return hashValueStore.containsKey(BytesValue.wrap(key));
    } finally {
      lock.unlock();
    }
  }

  @Override
  public Optional<byte[]> get(final byte[] key) throws StorageException {
    final Lock lock = rwLock.readLock();
    lock.lock();
    try {
      return Optional.ofNullable(hashValueStore.get(BytesValue.wrap(key)));
    } finally {
      lock.unlock();
    }
  }

  @Override
  public long removeAllKeysUnless(final Predicate<byte[]> retainCondition) throws StorageException {
    long initialSize = hashValueStore.keySet().size();
    hashValueStore.keySet().removeIf(key -> !retainCondition.test(key.getArrayUnsafe()));
    return initialSize - hashValueStore.keySet().size();
  }

  @Override
  public void close() {}

  @Override
  public KeyValueStorageTransaction startTransaction() {
    return new KeyValueStorageTransactionTransitionValidatorDecorator(new InMemoryTransaction());
  }

  public Set<BytesValue> keySet() {
    return Set.copyOf(hashValueStore.keySet());
  }

  private class InMemoryTransaction implements KeyValueStorageTransaction {

    private Map<BytesValue, byte[]> updatedValues = new HashMap<>();
    private Set<BytesValue> removedKeys = new HashSet<>();

    @Override
    public void put(final byte[] key, final byte[] value) {
      updatedValues.put(BytesValue.wrap(key), value);
      removedKeys.remove(BytesValue.wrap(key));
    }

    @Override
    public void remove(final byte[] key) {
      removedKeys.add(BytesValue.wrap(key));
      updatedValues.remove(BytesValue.wrap(key));
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
      updatedValues = null;
      removedKeys = null;
    }
  }
}
