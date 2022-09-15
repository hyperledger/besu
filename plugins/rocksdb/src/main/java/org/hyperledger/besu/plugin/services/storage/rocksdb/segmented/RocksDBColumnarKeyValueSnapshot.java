package org.hyperledger.besu.plugin.services.storage.rocksdb.segmented;

import static java.util.stream.Collectors.toUnmodifiableSet;

import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.SnappedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetrics;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDbSegmentIdentifier;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.rocksdb.OptimisticTransactionDB;

public class RocksDBColumnarKeyValueSnapshot implements SnappedKeyValueStorage {
  final OptimisticTransactionDB db;
  final RocksDBSnapshotTransaction snapTx;

  RocksDBColumnarKeyValueSnapshot(
      final OptimisticTransactionDB db,
      final RocksDbSegmentIdentifier segment,
      final RocksDBMetrics metrics) {
    this.db = db;
    this.snapTx = new RocksDBSnapshotTransaction(db, segment.get(), metrics);
  }

  @Override
  public Optional<byte[]> get(final byte[] key) throws StorageException {
    return snapTx.get(key);
  }

  @Override
  public Stream<byte[]> streamKeys() {
    return snapTx.streamKeys();
  }

  @Override
  public boolean tryDelete(final byte[] key) throws StorageException {
    snapTx.remove(key);
    return true;
  }

  @Override
  public Set<byte[]> getAllKeysThat(final Predicate<byte[]> returnCondition) {
    return streamKeys().filter(returnCondition).collect(toUnmodifiableSet());
  }

  @Override
  public KeyValueStorageTransaction startTransaction() throws StorageException {
    // TODO: we should probably return a wrapped transaction
    return snapTx;
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException(
        "RocksDBColumnarKeyValueSnapshot does not support clear");
  }

  @Override
  public boolean containsKey(final byte[] key) throws StorageException {
    return snapTx.get(key).isPresent();
  }

  @Override
  public void close() throws IOException {
    snapTx.close();
  }

  @Override
  public KeyValueStorageTransaction getSnapshotTransaction() {
    return snapTx;
  }
}
