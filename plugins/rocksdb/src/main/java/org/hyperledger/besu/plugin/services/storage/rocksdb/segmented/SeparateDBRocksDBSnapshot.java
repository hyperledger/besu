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
import org.hyperledger.besu.plugin.services.storage.SnappedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetrics;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDBException;
import org.rocksdb.Snapshot;
import org.rocksdb.TransactionDB;

/**
 * Snapshot implementation for separate database architecture.
 *
 * <p>Creates and manages snapshots across multiple independent RocksDB instances.
 */
public class SeparateDBRocksDBSnapshot implements SnappedKeyValueStorage {

  private final Map<SegmentIdentifier, TransactionDB> databases;
  private final Map<SegmentIdentifier, ColumnFamilyHandle> columnHandles;
  private final Map<SegmentIdentifier, Snapshot> snapshots;
  private final Map<SegmentIdentifier, RocksDBMetrics> metrics;
  private final ReadOptions readOptions;
  private volatile boolean closed = false;

  /**
   * Creates a new snapshot across all segment databases.
   *
   * @param databases the databases per segment
   * @param columnHandles the column handles per segment
   * @param metrics the metrics per segment
   * @param enableReadCache whether to enable read cache for snapshots
   */
  public SeparateDBRocksDBSnapshot(
      final Map<SegmentIdentifier, TransactionDB> databases,
      final Map<SegmentIdentifier, ColumnFamilyHandle> columnHandles,
      final Map<SegmentIdentifier, RocksDBMetrics> metrics,
      final boolean enableReadCache) {

    this.databases = databases;
    this.columnHandles = columnHandles;
    this.metrics = metrics;
    this.snapshots = new HashMap<>();

    // Create snapshots for each database
    for (Map.Entry<SegmentIdentifier, TransactionDB> entry : databases.entrySet()) {
      Snapshot snapshot = entry.getValue().getSnapshot();
      snapshots.put(entry.getKey(), snapshot);
    }

    // Configure read options for snapshots
    this.readOptions = new ReadOptions().setVerifyChecksums(false).setFillCache(enableReadCache);

    // Set snapshots in read options (will be set per-read)
  }

  @Override
  public Optional<byte[]> get(final SegmentIdentifier segment, final byte[] key)
      throws StorageException {
    throwIfClosed();

    try (final OperationTimer.TimingContext ignored =
        metrics.get(segment).getReadLatency().startTimer()) {

      TransactionDB db = databases.get(segment);
      ColumnFamilyHandle handle = columnHandles.get(segment);
      Snapshot snapshot = snapshots.get(segment);

      if (db == null || handle == null || snapshot == null) {
        throw new StorageException("Segment not found: " + segment.getName());
      }

      // Use the snapshot for this read
      ReadOptions snapshotReadOptions = new ReadOptions(readOptions).setSnapshot(snapshot);
      try {
        return Optional.ofNullable(db.get(handle, snapshotReadOptions, key));
      } finally {
        snapshotReadOptions.close();
      }
    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public Optional<NearestKeyValue> getNearestBefore(
      final SegmentIdentifier segment, final Bytes key) throws StorageException {
    throw new UnsupportedOperationException("getNearestBefore is not supported on snapshots");
  }

  @Override
  public Optional<NearestKeyValue> getNearestAfter(final SegmentIdentifier segment, final Bytes key)
      throws StorageException {
    throw new UnsupportedOperationException("getNearestAfter is not supported on snapshots");
  }

  @Override
  public Stream<Pair<byte[], byte[]>> stream(final SegmentIdentifier segment) {
    throwIfClosed();
    throw new UnsupportedOperationException("stream is not supported on snapshots");
  }

  @Override
  public Stream<Pair<byte[], byte[]>> streamFromKey(
      final SegmentIdentifier segment, final byte[] startKey) {
    throwIfClosed();
    throw new UnsupportedOperationException("streamFromKey is not supported on snapshots");
  }

  @Override
  public Stream<Pair<byte[], byte[]>> streamFromKey(
      final SegmentIdentifier segment, final byte[] startKey, final byte[] endKey) {
    throwIfClosed();
    throw new UnsupportedOperationException("streamFromKey is not supported on snapshots");
  }

  @Override
  public Stream<byte[]> streamKeys(final SegmentIdentifier segment) {
    throwIfClosed();
    throw new UnsupportedOperationException("streamKeys is not supported on snapshots");
  }

  @Override
  public boolean tryDelete(final SegmentIdentifier segment, final byte[] key) {
    throw new UnsupportedOperationException("tryDelete is not supported on snapshots");
  }

  @Override
  public Set<byte[]> getAllKeysThat(
      final SegmentIdentifier segment, final Predicate<byte[]> returnCondition) {
    throwIfClosed();
    throw new UnsupportedOperationException("getAllKeysThat is not supported on snapshots");
  }

  @Override
  public Set<byte[]> getAllValuesFromKeysThat(
      final SegmentIdentifier segment, final Predicate<byte[]> returnCondition) {
    throwIfClosed();
    throw new UnsupportedOperationException(
        "getAllValuesFromKeysThat is not supported on snapshots");
  }

  @Override
  public SegmentedKeyValueStorageTransaction startTransaction() throws StorageException {
    throw new UnsupportedOperationException("startTransaction is not supported on snapshots");
  }

  @Override
  public SegmentedKeyValueStorageTransaction getSnapshotTransaction() {
    throw new UnsupportedOperationException(
        "getSnapshotTransaction is not supported on separate database snapshots");
  }

  @Override
  public void clear(final SegmentIdentifier segment) {
    throw new UnsupportedOperationException("clear is not supported on snapshots");
  }

  @Override
  public void close() {
    if (!closed) {
      closed = true;

      // Release all snapshots
      for (Map.Entry<SegmentIdentifier, Snapshot> entry : snapshots.entrySet()) {
        TransactionDB db = databases.get(entry.getKey());
        if (db != null) {
          db.releaseSnapshot(entry.getValue());
        }
      }

      snapshots.clear();
      readOptions.close();
    }
  }

  @Override
  public boolean isClosed() {
    return closed;
  }

  private void throwIfClosed() {
    if (closed) {
      throw new IllegalStateException("Snapshot has been closed");
    }
  }
}
