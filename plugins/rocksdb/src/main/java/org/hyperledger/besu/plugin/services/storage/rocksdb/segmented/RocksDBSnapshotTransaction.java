/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.plugin.services.storage.rocksdb.segmented;

import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetrics;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDbIterator;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.OptimisticTransactionDB;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Transaction;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Rocks db snapshot transaction. */
public class RocksDBSnapshotTransaction
    implements SegmentedKeyValueStorageTransaction, AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(RocksDBSnapshotTransaction.class);
  private static final String NO_SPACE_LEFT_ON_DEVICE = "No space left on device";
  private final RocksDBMetrics metrics;
  private final OptimisticTransactionDB db;
  private final Function<SegmentIdentifier, ColumnFamilyHandle> columnFamilyMapper;
  private final Transaction snapTx;
  private final RocksDBSnapshot snapshot;
  private final WriteOptions writeOptions;
  private final ReadOptions readOptions;
  private final AtomicBoolean isClosed = new AtomicBoolean(false);

  /**
   * Instantiates a new RocksDb snapshot transaction.
   *
   * @param db the db
   * @param columnFamilyMapper mapper from segment identifier to column family handle
   * @param metrics the metrics
   */
  RocksDBSnapshotTransaction(
      final OptimisticTransactionDB db,
      final Function<SegmentIdentifier, ColumnFamilyHandle> columnFamilyMapper,
      final RocksDBMetrics metrics) {
    this.metrics = metrics;
    this.db = db;
    this.columnFamilyMapper = columnFamilyMapper;
    this.snapshot = new RocksDBSnapshot(db);
    this.writeOptions = new WriteOptions();
    this.snapTx = db.beginTransaction(writeOptions);
    this.readOptions =
        new ReadOptions().setVerifyChecksums(false).setSnapshot(snapshot.markAndUseSnapshot());
  }

  private RocksDBSnapshotTransaction(
      final OptimisticTransactionDB db,
      final Function<SegmentIdentifier, ColumnFamilyHandle> columnFamilyMapper,
      final RocksDBMetrics metrics,
      final RocksDBSnapshot snapshot,
      final Transaction snapTx,
      final ReadOptions readOptions) {
    this.metrics = metrics;
    this.db = db;
    this.columnFamilyMapper = columnFamilyMapper;
    this.snapshot = snapshot;
    this.writeOptions = new WriteOptions();
    this.readOptions = readOptions;
    this.snapTx = snapTx;
  }

  /**
   * Get data against given key.
   *
   * @param segmentId the segment id
   * @param key the key
   * @return the optional data
   */
  public Optional<byte[]> get(final SegmentIdentifier segmentId, final byte[] key) {
    throwIfClosed();

    try (final OperationTimer.TimingContext ignored = metrics.getReadLatency().startTimer()) {
      return Optional.ofNullable(snapTx.get(columnFamilyMapper.apply(segmentId), readOptions, key));
    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void put(final SegmentIdentifier segmentId, final byte[] key, final byte[] value) {
    throwIfClosed();

    try (final OperationTimer.TimingContext ignored = metrics.getWriteLatency().startTimer()) {
      snapTx.put(columnFamilyMapper.apply(segmentId), key, value);
    } catch (final RocksDBException e) {
      if (e.getMessage().contains(NO_SPACE_LEFT_ON_DEVICE)) {
        LOG.error(e.getMessage());
        System.exit(0);
      }
      throw new StorageException(e);
    }
  }

  @Override
  public void remove(final SegmentIdentifier segmentId, final byte[] key) {
    throwIfClosed();

    try (final OperationTimer.TimingContext ignored = metrics.getRemoveLatency().startTimer()) {
      snapTx.delete(columnFamilyMapper.apply(segmentId), key);
    } catch (final RocksDBException e) {
      if (e.getMessage().contains(NO_SPACE_LEFT_ON_DEVICE)) {
        LOG.error(e.getMessage());
        System.exit(0);
      }
      throw new StorageException(e);
    }
  }

  /**
   * get a RocksIterator that reads through the transaction to represent the current state.
   *
   * <p>be sure to close this iterator, like in a try-with-resources block, otherwise a native
   * memory leak might occur.
   *
   * @param segmentId id for the segment to iterate over.
   * @return RocksIterator
   */
  public RocksIterator getIterator(final SegmentIdentifier segmentId) {
    return snapTx.getIterator(readOptions, columnFamilyMapper.apply(segmentId));
  }

  /**
   * Stream.
   *
   * @param segmentId the segment id
   * @return the stream
   */
  public Stream<Pair<byte[], byte[]>> stream(final SegmentIdentifier segmentId) {
    throwIfClosed();

    final RocksIterator rocksIterator =
        db.newIterator(columnFamilyMapper.apply(segmentId), readOptions);
    rocksIterator.seekToFirst();
    return RocksDbIterator.create(rocksIterator).toStream();
  }

  /**
   * Stream keys.
   *
   * @param segmentId the segment id
   * @return the stream
   */
  public Stream<byte[]> streamKeys(final SegmentIdentifier segmentId) {
    throwIfClosed();

    final RocksIterator rocksIterator =
        db.newIterator(columnFamilyMapper.apply(segmentId), readOptions);
    rocksIterator.seekToFirst();
    return RocksDbIterator.create(rocksIterator).toStreamKeys();
  }

  /**
   * Returns a stream of key-value pairs starting from the specified key. This method is used to
   * retrieve a stream of data reading through the transaction, starting from the given key. If no
   * data is available from the specified key onwards, an empty stream is returned.
   *
   * @param segment The segment identifier whose keys we want to stream.
   * @param startKey The key from which the stream should start.
   * @return A stream of key-value pairs starting from the specified key.
   */
  public Stream<Pair<byte[], byte[]>> streamFromKey(
      final SegmentIdentifier segment, final byte[] startKey) {
    throwIfClosed();

    final RocksIterator rocksIterator =
        db.newIterator(columnFamilyMapper.apply(segment), readOptions);
    rocksIterator.seek(startKey);
    return RocksDbIterator.create(rocksIterator).toStream();
  }

  /**
   * Returns a stream of key-value pairs starting from the specified key, ending at the specified
   * key. This method is used to retrieve a stream of data reading through the transaction, starting
   * from the given key. If no data is available from the specified key onwards, an empty stream is
   * returned.
   *
   * @param segment The segment identifier whose keys we want to stream.
   * @param startKey The key from which the stream should start.
   * @param endKey The key at which the stream should stop.
   * @return A stream of key-value pairs starting from the specified key.
   */
  public Stream<Pair<byte[], byte[]>> streamFromKey(
      final SegmentIdentifier segment, final byte[] startKey, final byte[] endKey) {
    throwIfClosed();
    final Bytes endKeyBytes = Bytes.wrap(endKey);

    final RocksIterator rocksIterator =
        db.newIterator(columnFamilyMapper.apply(segment), readOptions);
    rocksIterator.seek(startKey);
    return RocksDbIterator.create(rocksIterator)
        .toStream()
        .takeWhile(e -> endKeyBytes.compareTo(Bytes.wrap(e.getKey())) >= 0);
  }

  @Override
  public void commit() throws StorageException {
    // no-op
  }

  @Override
  public void rollback() {
    throwIfClosed();

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

  /**
   * Copy.
   *
   * @return the rocks db snapshot transaction
   */
  public RocksDBSnapshotTransaction copy() {
    throwIfClosed();
    try {
      var copyReadOptions = new ReadOptions().setSnapshot(snapshot.markAndUseSnapshot());
      var copySnapTx = db.beginTransaction(writeOptions);
      copySnapTx.rebuildFromWriteBatch(snapTx.getWriteBatch().getWriteBatch());
      return new RocksDBSnapshotTransaction(
          db, columnFamilyMapper, metrics, snapshot, copySnapTx, copyReadOptions);
    } catch (Exception ex) {
      LOG.error("Failed to copy snapshot transaction", ex);
      snapshot.unMarkSnapshot();
      throw new StorageException(ex);
    }
  }

  @Override
  public void close() {
    snapTx.close();
    writeOptions.close();
    readOptions.close();
    snapshot.unMarkSnapshot();
    isClosed.set(true);
  }

  private void throwIfClosed() {
    if (isClosed.get()) {
      LOG.error("Attempting to use a closed RocksDBSnapshotTransaction");
      throw new StorageException("Storage has already been closed");
    }
  }
}
