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
package org.hyperledger.besu.plugin.services.storage.rocksdb.unsegmented;

import static java.util.stream.Collectors.toUnmodifiableSet;

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetrics;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetricsFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDbIterator;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDbUtil;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBConfiguration;
import org.hyperledger.besu.services.kvstore.KeyValueStorageTransactionTransitionValidatorDecorator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.LRUCache;
import org.rocksdb.OptimisticTransactionDB;
import org.rocksdb.Options;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Statistics;
import org.rocksdb.Status;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RocksDBKeyValueStorage implements KeyValueStorage {

  static {
    RocksDbUtil.loadNativeLibrary();
  }

  private static final Logger LOG = LoggerFactory.getLogger(RocksDBKeyValueStorage.class);

  private final Options options;
  private final OptimisticTransactionDB db;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final RocksDBMetrics rocksDBMetrics;
  private final WriteOptions tryDeleteOptions =
      new WriteOptions().setNoSlowdown(true).setIgnoreMissingColumnFamilies(true);

  public RocksDBKeyValueStorage(
      final RocksDBConfiguration configuration,
      final MetricsSystem metricsSystem,
      final RocksDBMetricsFactory rocksDBMetricsFactory) {
    try {
      final Statistics stats = new Statistics();
      options =
          new Options()
              .setCreateIfMissing(true)
              .setMaxOpenFiles(configuration.getMaxOpenFiles())
              .setTableFormatConfig(createBlockBasedTableConfig(configuration))
              .setMaxBackgroundCompactions(configuration.getMaxBackgroundCompactions())
              .setStatistics(stats);
      options.getEnv().setBackgroundThreads(configuration.getBackgroundThreadCount());

      db = OptimisticTransactionDB.open(options, configuration.getDatabaseDir().toString());
      rocksDBMetrics = rocksDBMetricsFactory.create(metricsSystem, configuration, db, stats);
    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void clear() throws StorageException {
    try (final RocksIterator rocksIterator = db.newIterator()) {
      rocksIterator.seekToFirst();
      if (rocksIterator.isValid()) {
        final byte[] firstKey = rocksIterator.key();
        rocksIterator.seekToLast();
        if (rocksIterator.isValid()) {
          final byte[] lastKey = rocksIterator.key();
          db.deleteRange(firstKey, lastKey);
          db.delete(lastKey);
        }
      }
    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public boolean containsKey(final byte[] key) throws StorageException {
    return get(key).isPresent();
  }

  @Override
  public Optional<byte[]> get(final byte[] key) throws StorageException {
    throwIfClosed();

    try (final OperationTimer.TimingContext ignored =
        rocksDBMetrics.getReadLatency().startTimer()) {
      return Optional.ofNullable(db.get(key));
    } catch (final RocksDBException e) {
      throw new StorageException(e);
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
  public Stream<Pair<byte[], byte[]>> stream() {
    final RocksIterator rocksIterator = db.newIterator();
    rocksIterator.seekToFirst();
    return RocksDbIterator.create(rocksIterator).toStream();
  }

  @Override
  public Stream<byte[]> streamKeys() {
    final RocksIterator rocksIterator = db.newIterator();
    rocksIterator.seekToFirst();
    return RocksDbIterator.create(rocksIterator).toStreamKeys();
  }

  @Override
  public Set<byte[]> getAllValuesFromKeysThat(final Predicate<byte[]> returnCondition) {
    return stream()
        .filter(pair -> returnCondition.test(pair.getKey()))
        .map(Pair::getValue)
        .collect(toUnmodifiableSet());
  }

  @Override
  public TreeMap<Bytes, Bytes> getInRange(final Bytes startKeyHash, final Bytes endKeyHash) {
    final RocksIterator rocksIterator = db.newIterator();
    rocksIterator.seek(startKeyHash.toArrayUnsafe());
    final RocksDbIterator rocksDbKeyIterator = RocksDbIterator.create(rocksIterator);
    try {
      final TreeMap<Bytes, Bytes> res = new TreeMap<>();
      while (rocksDbKeyIterator.hasNext()) {
        final Pair<byte[], byte[]> next = rocksDbKeyIterator.next();
        final Bytes key = Bytes.wrap(next.getKey());
        if (key.compareTo(startKeyHash) >= 0) {
          if (key.compareTo(endKeyHash) <= 0) {
            res.put(key, Bytes.of(next.getValue()));
          } else {
            return res;
          }
        }
      }
      return res;
    } finally {
      rocksDbKeyIterator.close();
      rocksIterator.close();
    }
  }

  @Override
  public List<Bytes> getByPrefix(final Bytes prefix) {
    final RocksIterator rocksIterator = db.newIterator();
    rocksIterator.seek(prefix.toArrayUnsafe());
    final RocksDbIterator rocksDbKeyIterator = RocksDbIterator.create(rocksIterator);
    try {
      final List<Bytes> res = new ArrayList<>();
      while (rocksDbKeyIterator.hasNext()) {
        final Bytes key = Bytes.wrap(rocksDbKeyIterator.nextKey());
        if (key.commonPrefixLength(prefix) == prefix.size()) {
          res.add(key);
        } else {
          return res;
        }
      }
      return res;
    } finally {
      rocksDbKeyIterator.close();
      rocksIterator.close();
    }
  }

  @Override
  public boolean isEmpty() {
    final RocksIterator rocksIterator = db.newIterator();
    try (rocksIterator) {
      rocksIterator.seekToFirst();
      return rocksIterator.isValid();
    }
  }

  @Override
  public boolean tryDelete(final byte[] key) {
    try {
      db.delete(tryDeleteOptions, key);
      return true;
    } catch (RocksDBException e) {
      if (e.getStatus().getCode() == Status.Code.Incomplete) {
        return false;
      } else {
        throw new StorageException(e);
      }
    }
  }

  @Override
  public KeyValueStorageTransaction startTransaction() throws StorageException {
    throwIfClosed();
    final WriteOptions options = new WriteOptions();
    options.setIgnoreMissingColumnFamilies(true);
    return new KeyValueStorageTransactionTransitionValidatorDecorator(
        new RocksDBTransaction(db.beginTransaction(options), options, rocksDBMetrics));
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      tryDeleteOptions.close();
      options.close();
      db.close();
    }
  }

  private BlockBasedTableConfig createBlockBasedTableConfig(final RocksDBConfiguration config) {
    final LRUCache cache = new LRUCache(config.getCacheCapacity());
    return new BlockBasedTableConfig().setBlockCache(cache);
  }

  private void throwIfClosed() {
    if (closed.get()) {
      LOG.error("Attempting to use a closed RocksDBKeyValueStorage");
      throw new IllegalStateException("Storage has been closed");
    }
  }
}
