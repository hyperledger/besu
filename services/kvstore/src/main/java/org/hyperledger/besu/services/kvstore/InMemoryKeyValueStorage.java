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
import org.hyperledger.besu.plugin.services.storage.SnappableKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SnappedKeyValueStorage;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;

/** The In memory key value storage. */
public class InMemoryKeyValueStorage
    implements SnappedKeyValueStorage, SnappableKeyValueStorage, KeyValueStorage {

  /** protected access for the backing hash map. */
  protected final Map<Bytes, Optional<byte[]>> hashValueStore;

  /** protected access to the rw lock. */
  protected final ReadWriteLock rwLock = new ReentrantReadWriteLock();

  /** Instantiates a new In memory key value storage. */
  public InMemoryKeyValueStorage() {
    this(new HashMap<>());
  }

  /**
   * Instantiates a new In memory key value storage.
   *
   * @param hashValueStore the hash value store
   */
  protected InMemoryKeyValueStorage(final Map<Bytes, Optional<byte[]>> hashValueStore) {
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
      return hashValueStore.getOrDefault(Bytes.wrap(key), Optional.empty());
    } finally {
      lock.unlock();
    }
  }

  @Override
  public Set<byte[]> getAllKeysThat(final Predicate<byte[]> returnCondition) {
    return stream()
        .filter(pair -> returnCondition.test(pair.getKey()))
        .map(Pair::getKey)
        .collect(toUnmodifiableSet());
  }

  @Override
  public Set<byte[]> getAllValuesFromKeysThat(final Predicate<byte[]> returnCondition) {
    return stream()
        .filter(pair -> returnCondition.test(pair.getKey()))
        .map(Pair::getValue)
        .collect(toUnmodifiableSet());
  }

  @Override
  public Stream<Pair<byte[], byte[]>> stream() {
    final Lock lock = rwLock.readLock();
    lock.lock();
    try {
      return ImmutableSet.copyOf(hashValueStore.entrySet()).stream()
          .filter(bytesEntry -> bytesEntry.getValue().isPresent())
          .map(
              bytesEntry ->
                  Pair.of(bytesEntry.getKey().toArrayUnsafe(), bytesEntry.getValue().get()));
    } finally {
      lock.unlock();
    }
  }

  @Override
  public Stream<Pair<byte[], byte[]>> streamFromKey(final byte[] startKey) {
    return stream().filter(e -> Bytes.wrap(startKey).compareTo(Bytes.wrap(e.getKey())) <= 0);
  }

  @Override
  public Stream<byte[]> streamKeys() {
    final Lock lock = rwLock.readLock();
    lock.lock();
    try {
      return ImmutableSet.copyOf(hashValueStore.entrySet()).stream()
          .map(bytesEntry -> bytesEntry.getKey().toArrayUnsafe());
    } finally {
      lock.unlock();
    }
  }

  @Override
  public boolean tryDelete(final byte[] key) {
    final Lock lock = rwLock.writeLock();
    if (lock.tryLock()) {
      try {
        hashValueStore.remove(Bytes.wrap(key));
      } finally {
        lock.unlock();
      }
      return true;
    }
    return false;
  }

  @Override
  public void close() {}

  @Override
  public KeyValueStorageTransaction startTransaction() {
    return new KeyValueStorageTransactionTransitionValidatorDecorator(new InMemoryTransaction());
  }

  @Override
  public boolean isClosed() {
    return false;
  }

  /**
   * Key set.
   *
   * @return the set of keys
   */
  public Set<Bytes> keySet() {
    return Set.copyOf(hashValueStore.keySet());
  }

  @Override
  public SnappedKeyValueStorage takeSnapshot() {
    return new InMemoryKeyValueStorage(new HashMap<>(hashValueStore));
  }

  @Override
  public KeyValueStorageTransaction getSnapshotTransaction() {
    return startTransaction();
  }

  /** In memory transaction. */
  public class InMemoryTransaction implements KeyValueStorageTransaction {

    /** protected access to updatedValues map for the transaction. */
    protected Map<Bytes, Optional<byte[]>> updatedValues = new HashMap<>();
    /** protected access to deletedValues set for the transaction. */
    protected Set<Bytes> removedKeys = new HashSet<>();

    @Override
    public void put(final byte[] key, final byte[] value) {
      updatedValues.put(Bytes.wrap(key), Optional.of(value));
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

  /**
   * Dump.
   *
   * @param ps the PrintStream where to report the dump
   */
  public void dump(final PrintStream ps) {
    final Lock lock = rwLock.readLock();
    lock.lock();
    try {
      ImmutableSet.copyOf(hashValueStore.entrySet()).stream()
          .filter(bytesEntry -> bytesEntry.getValue().isPresent())
          .forEach(
              entry ->
                  ps.printf(
                      "  %s : %s%n",
                      entry.getKey().toHexString(),
                      Bytes.wrap(entry.getValue().get()).toHexString()));
    } finally {
      lock.unlock();
    }
  }
}
