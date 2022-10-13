package org.hyperledger.besu.plugin.services.storage.rocksdb.segmented;

import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetrics;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDbKeyIterator;

import java.util.Optional;
import java.util.stream.Stream;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.OptimisticTransactionDB;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Snapshot;
import org.rocksdb.Transaction;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RocksDBSnapshotTransaction implements KeyValueStorageTransaction {
  private static final Logger LOG = LoggerFactory.getLogger(RocksDBSnapshotTransaction.class);
  private static final String NO_SPACE_LEFT_ON_DEVICE = "No space left on device";

  private final RocksDBMetrics metrics;
  private final OptimisticTransactionDB db;
  private final ColumnFamilyHandle columnFamilyHandle;
  private final Transaction snapTx;
  private final Snapshot snapshot;
  private final WriteOptions writeOptions;
  private final ReadOptions readOptions;

  RocksDBSnapshotTransaction(
      final OptimisticTransactionDB db,
      final ColumnFamilyHandle columnFamilyHandle,
      final RocksDBMetrics metrics) {
    this.metrics = metrics;
    this.db = db;
    this.columnFamilyHandle = columnFamilyHandle;
    this.snapshot = db.getSnapshot();
    this.writeOptions = new WriteOptions();
    this.snapTx = db.beginTransaction(writeOptions);
    this.readOptions = new ReadOptions().setSnapshot(snapshot);
  }

  public Optional<byte[]> get(final byte[] key) {
    try (final OperationTimer.TimingContext ignored = metrics.getReadLatency().startTimer()) {
      return Optional.ofNullable(snapTx.get(columnFamilyHandle, readOptions, key));
    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void put(final byte[] key, final byte[] value) {
    try (final OperationTimer.TimingContext ignored = metrics.getWriteLatency().startTimer()) {
      snapTx.put(columnFamilyHandle, key, value);
    } catch (final RocksDBException e) {
      if (e.getMessage().contains(NO_SPACE_LEFT_ON_DEVICE)) {
        LOG.error(e.getMessage());
        System.exit(0);
      }
      throw new StorageException(e);
    }
  }

  @Override
  public void remove(final byte[] key) {
    try (final OperationTimer.TimingContext ignored = metrics.getRemoveLatency().startTimer()) {
      snapTx.delete(columnFamilyHandle, key);
    } catch (final RocksDBException e) {
      if (e.getMessage().contains(NO_SPACE_LEFT_ON_DEVICE)) {
        LOG.error(e.getMessage());
        System.exit(0);
      }
      throw new StorageException(e);
    }
  }

  public Stream<byte[]> streamKeys() {
    final RocksIterator rocksIterator = db.newIterator(columnFamilyHandle, readOptions);
    rocksIterator.seekToFirst();
    return RocksDbKeyIterator.create(rocksIterator).toStream();
  }

  @Override
  public void commit() throws StorageException {
    // no-op or throw?
    throw new UnsupportedOperationException("RocksDBSnapshotTransaction does not support commit");
  }

  @Override
  public void rollback() {
    try {
      snapTx.rollback();
      metrics.getRollbackCount().inc();
    } catch (final RocksDBException e) {
      if (e.getMessage().contains(NO_SPACE_LEFT_ON_DEVICE)) {
        LOG.error(e.getMessage());
        System.exit(0);
      }
      throw new StorageException(e);
    } finally {
      close();
    }
  }

  void close() {
    snapshot.close();
    snapTx.close();
    writeOptions.close();
    readOptions.close();
  }
}
