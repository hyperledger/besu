/*
 * Copyright Hyperledger Besu Contributors.
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
 *
 */
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

  private RocksDBColumnarKeyValueSnapshot(
      final OptimisticTransactionDB db, final RocksDBSnapshotTransaction snapTx) {
    this.db = db;
    this.snapTx = snapTx;
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
    // The use of a transaction on a transaction based key value store is dubious
    // at best.  return our snapshot transaction instead.
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

  @Override
  public SnappedKeyValueStorage cloneFromSnapshot() {
    return new RocksDBColumnarKeyValueSnapshot(db, snapTx.copy());
  }
}
