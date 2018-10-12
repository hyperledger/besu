package net.consensys.pantheon.services.kvstore;

import net.consensys.pantheon.util.bytes.BytesValue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InMemoryKeyValueStorage implements KeyValueStorage {

  private final Map<BytesValue, BytesValue> hashValueStore = new HashMap<>();
  private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

  @Override
  public Optional<BytesValue> get(final BytesValue key) {
    final Lock lock = rwLock.readLock();
    try {
      lock.lock();
      return Optional.ofNullable(hashValueStore.get(key));
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void put(final BytesValue key, final BytesValue value) {
    final Lock lock = rwLock.writeLock();
    try {
      lock.lock();
      hashValueStore.put(key, value);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void remove(final BytesValue key) throws StorageException {
    final Lock lock = rwLock.writeLock();
    try {
      lock.lock();
      hashValueStore.remove(key);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public Transaction getStartTransaction() {
    return new InMemoryTransaction();
  }

  @Override
  public Stream<Entry> entries() {
    final Lock lock = rwLock.readLock();
    try {
      lock.lock();
      // Ensure we have collected all entries before releasing the lock and returning
      return hashValueStore
          .entrySet()
          .stream()
          .map(e -> Entry.create(e.getKey(), e.getValue()))
          .collect(Collectors.toSet())
          .stream();
    } finally {
      lock.unlock();
    }
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
      try {
        lock.lock();
        hashValueStore.putAll(updatedValues);
        removedKeys.forEach(k -> hashValueStore.remove(k));
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
