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

import static java.util.stream.Collectors.toUnmodifiableSet;

import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.SnappedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetrics;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.rocksdb.AbstractRocksIterator;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.OptimisticTransactionDB;
import org.rocksdb.RocksIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The RocksDb columnar key value snapshot. */
public class RocksDBColumnarKeyValueSnapshot
    implements SegmentedKeyValueStorage, SnappedKeyValueStorage {

  private static final Logger LOG = LoggerFactory.getLogger(RocksDBColumnarKeyValueSnapshot.class);

  /** The Db. */
  final OptimisticTransactionDB db;

  /** The Snap tx. */
  final RocksDBSnapshotTransaction snapTx;

  private final AtomicBoolean closed = new AtomicBoolean(false);

  /**
   * Instantiates a new RocksDb columnar key value snapshot.
   *
   * @param db the db
   * @param metrics the metrics
   */
  RocksDBColumnarKeyValueSnapshot(
      final OptimisticTransactionDB db,
      final Function<SegmentIdentifier, ColumnFamilyHandle> columnFamilyMapper,
      final RocksDBMetrics metrics) {
    this.db = db;
    this.snapTx = new RocksDBSnapshotTransaction(db, columnFamilyMapper, metrics);
  }

  @Override
  public Optional<byte[]> get(final SegmentIdentifier segment, final byte[] key)
      throws StorageException {
    throwIfClosed();
    return snapTx.get(segment, key);
  }

  @Override
  public Optional<NearestKeyValue> getNearestBefore(
      final SegmentIdentifier segmentIdentifier, final Bytes key) throws StorageException {

    try (final RocksIterator rocksIterator = snapTx.getIterator(segmentIdentifier)) {
      rocksIterator.seekForPrev(key.toArrayUnsafe());
      return Optional.of(rocksIterator)
          .filter(AbstractRocksIterator::isValid)
          .map(it -> new NearestKeyValue(Bytes.of(it.key()), Optional.of(it.value())));
    }
  }

  @Override
  public Optional<NearestKeyValue> getNearestAfter(
      final SegmentIdentifier segmentIdentifier, final Bytes key) throws StorageException {
    try (final RocksIterator rocksIterator = snapTx.getIterator(segmentIdentifier)) {
      rocksIterator.seek(key.toArrayUnsafe());
      return Optional.of(rocksIterator)
          .filter(AbstractRocksIterator::isValid)
          .map(it -> new NearestKeyValue(Bytes.of(it.key()), Optional.of(it.value())));
    }
  }

  @Override
  public Stream<Pair<byte[], byte[]>> stream(final SegmentIdentifier segment) {
    throwIfClosed();
    return snapTx.stream(segment);
  }

  @Override
  public Stream<Pair<byte[], byte[]>> streamFromKey(
      final SegmentIdentifier segment, final byte[] startKey) {
    return snapTx.streamFromKey(segment, startKey);
  }

  @Override
  public Stream<Pair<byte[], byte[]>> streamFromKey(
      final SegmentIdentifier segment, final byte[] startKey, final byte[] endKey) {
    return snapTx.streamFromKey(segment, startKey, endKey);
  }

  @Override
  public Stream<byte[]> streamKeys(final SegmentIdentifier segment) {
    throwIfClosed();
    return snapTx.streamKeys(segment);
  }

  @Override
  public boolean tryDelete(final SegmentIdentifier segment, final byte[] key)
      throws StorageException {
    throwIfClosed();
    snapTx.remove(segment, key);
    return true;
  }

  @Override
  public Set<byte[]> getAllKeysThat(
      final SegmentIdentifier segment, final Predicate<byte[]> returnCondition) {
    return streamKeys(segment).filter(returnCondition).collect(toUnmodifiableSet());
  }

  @Override
  public Set<byte[]> getAllValuesFromKeysThat(
      final SegmentIdentifier segment, final Predicate<byte[]> returnCondition) {
    return stream(segment)
        .filter(pair -> returnCondition.test(pair.getKey()))
        .map(Pair::getValue)
        .collect(toUnmodifiableSet());
  }

  @Override
  public SegmentedKeyValueStorageTransaction startTransaction() throws StorageException {
    // The use of a transaction on a transaction based key value store is dubious
    // at best.  return our snapshot transaction instead.
    return snapTx;
  }

  @Override
  public boolean isClosed() {
    return closed.get();
  }

  @Override
  public void clear(final SegmentIdentifier segment) {
    throw new UnsupportedOperationException(
        "RocksDBColumnarKeyValueSnapshot does not support clear");
  }

  @Override
  public boolean containsKey(final SegmentIdentifier segment, final byte[] key)
      throws StorageException {
    throwIfClosed();
    return snapTx.get(segment, key).isPresent();
  }

  @Override
  public void close() throws IOException {
    if (closed.compareAndSet(false, true)) {
      snapTx.close();
    }
  }

  private void throwIfClosed() {
    if (closed.get()) {
      LOG.error("Attempting to use a closed RocksDBKeyValueStorage");
      throw new IllegalStateException("Storage has been closed");
    }
  }

  @Override
  public SegmentedKeyValueStorageTransaction getSnapshotTransaction() {
    return snapTx;
  }
}
