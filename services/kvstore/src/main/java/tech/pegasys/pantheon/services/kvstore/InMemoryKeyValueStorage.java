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

  private final Map<BytesValue, BytesValue> hashValueStore;
  private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

  public InMemoryKeyValueStorage() {
    this(new HashMap<>());
  }

  protected InMemoryKeyValueStorage(final Map<BytesValue, BytesValue> hashValueStore) {
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
  public void close() {}

  @Override
  public boolean containsKey(final BytesValue key) throws StorageException {
    final Lock lock = rwLock.readLock();
    lock.lock();
    try {
      return hashValueStore.containsKey(key);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public Optional<BytesValue> get(final BytesValue key) {
    final Lock lock = rwLock.readLock();
    lock.lock();
    try {
      return Optional.ofNullable(hashValueStore.get(key));
    } finally {
      lock.unlock();
    }
  }

  @Override
  public long removeUnless(final Predicate<BytesValue> inUseCheck) {
    final Lock lock = rwLock.writeLock();
    lock.lock();
    try {
      long initialSize = hashValueStore.keySet().size();
      hashValueStore.keySet().removeIf(key -> !inUseCheck.test(key));
      return initialSize - hashValueStore.keySet().size();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public Transaction startTransaction() {
    return new InMemoryTransaction();
  }

  public Set<BytesValue> keySet() {
    return Set.copyOf(hashValueStore.keySet());
  }

  private class InMemoryTransaction extends AbstractTransaction {

    private Map<BytesValue, BytesValue> updatedValues = new HashMap<>();
    private Set<BytesValue> removedKeys = new HashSet<>();

    @Override
    protected void doPut(final BytesValue key, final BytesValue value) {
      updatedValues.put(key, value);
      removedKeys.remove(key);
    }

    @Override
    protected void doRemove(final BytesValue key) {
      removedKeys.add(key);
      updatedValues.remove(key);
    }

    @Override
    protected void doCommit() {
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
    protected void doRollback() {
      updatedValues = null;
      removedKeys = null;
    }
  }
}
