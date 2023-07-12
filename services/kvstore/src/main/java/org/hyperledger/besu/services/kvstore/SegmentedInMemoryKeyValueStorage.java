package org.hyperledger.besu.services.kvstore;

import static java.util.stream.Collectors.toUnmodifiableSet;

import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SnappableSegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SnappedSegmentedKeyValueStorage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;

public class SegmentedInMemoryKeyValueStorage<S extends SegmentIdentifier> implements SnappedSegmentedKeyValueStorage<S>, SnappableSegmentedKeyValueStorage<S>, SegmentedKeyValueStorage<S> {
  /** protected access for the backing hash map. */
  final Map<S, Map<Bytes, Optional<byte[]>>> hashValueStore;

  /** protected access to the rw lock. */
  protected final ReadWriteLock rwLock = new ReentrantReadWriteLock();

  /** Instantiates a new In memory key value storage. */
  public SegmentedInMemoryKeyValueStorage() {
    this(new HashMap<>());
  }

  /**
   * Instantiates a new In memory key value storage.
   *
   * @param hashValueStore the hash value store
   */
  SegmentedInMemoryKeyValueStorage(final Map<S, Map<Bytes, Optional<byte[]>>> hashValueStore) {
    this.hashValueStore = hashValueStore;
  }

  @Override
  public void clear(final S segmentIdentifier) {
    final Lock lock = rwLock.writeLock();
    lock.lock();
    try {
      hashValueStore.computeIfAbsent(segmentIdentifier, s -> new HashMap<>()).clear();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public boolean containsKey(final S segmentIdentifier, final byte[] key) throws StorageException {
    return get(segmentIdentifier, key).isPresent();
  }

  @SuppressWarnings("unchecked")
  @Override
  public S getSegmentIdentifierByName(final SegmentIdentifier segment) {
    // this is tautological since we are using SegmentIdentifier as our Map key
    return (S) segment;
  }

  @Override
  public SegmentedKeyValueStorage getComposedSegmentStorage(final List<SegmentIdentifier> segments) {
    return null;
  }

  @Override
  public Optional<byte[]> get(final S segmentIdentifier, final byte[] key) throws StorageException {
    final Lock lock = rwLock.readLock();
    lock.lock();
    try {
      return hashValueStore.computeIfAbsent(segmentIdentifier, s -> new HashMap<>())
          .getOrDefault(Bytes.wrap(key), Optional.empty());
    } finally {
      lock.unlock();
    }
  }

  @Override
  public Set<byte[]> getAllKeysThat(final S segmentIdentifier, final Predicate<byte[]> returnCondition) {
    return stream(segmentIdentifier)
        .filter(pair -> returnCondition.test(pair.getKey()))
        .map(Pair::getKey)
        .collect(toUnmodifiableSet());
  }

  @Override
  public Set<byte[]> getAllValuesFromKeysThat(final S segmentIdentifier, final Predicate<byte[]> returnCondition) {
    return stream(segmentIdentifier)
        .filter(pair -> returnCondition.test(pair.getKey()))
        .map(Pair::getValue)
        .collect(toUnmodifiableSet());
  }

  @Override
  public Stream<Pair<byte[], byte[]>> stream(final S segmentIdentifier) {
    final Lock lock = rwLock.readLock();
    lock.lock();
    try {
      return ImmutableSet.copyOf(
          hashValueStore.computeIfAbsent(segmentIdentifier, s -> new HashMap<>())
              .entrySet())
          .stream()
          .filter(bytesEntry -> bytesEntry.getValue().isPresent())
          .map(bytesEntry -> Pair.of(bytesEntry.getKey().toArrayUnsafe(), bytesEntry.getValue().get()));
    } finally {
      lock.unlock();
    }
  }

  @Override
  public Stream<Pair<byte[], byte[]>> streamFromKey(final S segmentIdentifier, final byte[] startKey) {
    return stream(segmentIdentifier).filter(e -> Bytes.wrap(startKey).compareTo(Bytes.wrap(e.getKey())) <= 0);
  }

  @Override
  public Stream<byte[]> streamKeys(final S segmentIdentifier) {
    final Lock lock = rwLock.readLock();
    lock.lock();
    try {
      return ImmutableMap.copyOf(hashValueStore.computeIfAbsent(segmentIdentifier, s -> new HashMap<>()))
          .entrySet()
          .stream()
          .filter(bytesEntry -> bytesEntry.getValue().isPresent())
          .map(bytesEntry -> bytesEntry.getKey().toArrayUnsafe());
    } finally {
      lock.unlock();
    }
  }

  @Override
  public boolean tryDelete(final S segmentIdentifier, final byte[] key) {
    final Lock lock = rwLock.writeLock();
    if (lock.tryLock()) {
      try {
        Optional.ofNullable(hashValueStore.get(segmentIdentifier))
            .ifPresent(store -> store.remove(Bytes.wrap(key)));
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
    return new KeyValueStorageTransactionValidatorDecorator(new InMemoryKeyValueStorage.InMemoryTransaction());
  }

  @Override
  public boolean isClosed() {
    return false;
  }

  @Override
  public SnappedSegmentedKeyValueStorage takeSnapshot() {
    return new SegmentedInMemoryKeyValueStorage(new HashMap<>(hashValueStore));
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
        //TODO fix/adapt me to know about segments:
//        hashValueStore.putAll(updatedValues);
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
