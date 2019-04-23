/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.services.kvstore;

import tech.pegasys.pantheon.metrics.Counter;
import tech.pegasys.pantheon.metrics.MetricCategory;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.OperationTimer;
import tech.pegasys.pantheon.metrics.prometheus.PrometheusMetricsSystem;
import tech.pegasys.pantheon.metrics.rocksdb.RocksDBStats;
import tech.pegasys.pantheon.services.util.RocksDbUtil;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.Closeable;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rocksdb.Options;
import org.rocksdb.RocksDBException;
import org.rocksdb.Statistics;
import org.rocksdb.TransactionDB;
import org.rocksdb.TransactionDBOptions;
import org.rocksdb.WriteOptions;

public class RocksDbKeyValueStorage implements KeyValueStorage, Closeable {

  private static final Logger LOG = LogManager.getLogger();

  private final Options options;
  private final TransactionDBOptions txOptions;
  private final TransactionDB db;
  private final AtomicBoolean closed = new AtomicBoolean(false);

  private final OperationTimer readLatency;
  private final OperationTimer removeLatency;
  private final OperationTimer writeLatency;
  private final OperationTimer commitLatency;
  private final Counter rollbackCount;
  private final Statistics stats;

  public static KeyValueStorage create(
      final RocksDbConfiguration rocksDbConfiguration, final MetricsSystem metricsSystem)
      throws StorageException {
    return new RocksDbKeyValueStorage(rocksDbConfiguration, metricsSystem);
  }

  private RocksDbKeyValueStorage(
      final RocksDbConfiguration rocksDbConfiguration, final MetricsSystem metricsSystem) {
    RocksDbUtil.loadNativeLibrary();
    try {
      stats = new Statistics();
      options =
          new Options()
              .setCreateIfMissing(true)
              .setMaxOpenFiles(rocksDbConfiguration.getMaxOpenFiles())
              .setTableFormatConfig(rocksDbConfiguration.getBlockBasedTableConfig())
              .setMaxBackgroundCompactions(rocksDbConfiguration.getMaxBackgroundCompactions())
              .setStatistics(stats);
      options.getEnv().setBackgroundThreads(rocksDbConfiguration.getBackgroundThreadCount());

      txOptions = new TransactionDBOptions();
      db = TransactionDB.open(options, txOptions, rocksDbConfiguration.getDatabaseDir().toString());

      readLatency =
          metricsSystem
              .createLabelledTimer(
                  MetricCategory.KVSTORE_ROCKSDB,
                  "read_latency_seconds",
                  "Latency for read from RocksDB.",
                  "database")
              .labels(rocksDbConfiguration.getLabel());
      removeLatency =
          metricsSystem
              .createLabelledTimer(
                  MetricCategory.KVSTORE_ROCKSDB,
                  "remove_latency_seconds",
                  "Latency of remove requests from RocksDB.",
                  "database")
              .labels(rocksDbConfiguration.getLabel());
      writeLatency =
          metricsSystem
              .createLabelledTimer(
                  MetricCategory.KVSTORE_ROCKSDB,
                  "write_latency_seconds",
                  "Latency for write to RocksDB.",
                  "database")
              .labels(rocksDbConfiguration.getLabel());
      commitLatency =
          metricsSystem
              .createLabelledTimer(
                  MetricCategory.KVSTORE_ROCKSDB,
                  "commit_latency_seconds",
                  "Latency for commits to RocksDB.",
                  "database")
              .labels(rocksDbConfiguration.getLabel());

      if (metricsSystem instanceof PrometheusMetricsSystem) {
        RocksDBStats.registerRocksDBMetrics(stats, (PrometheusMetricsSystem) metricsSystem);
      }

      metricsSystem.createLongGauge(
          MetricCategory.KVSTORE_ROCKSDB,
          "rocks_db_table_readers_memory_bytes",
          "Estimated memory used for RocksDB index and filter blocks in bytes",
          () -> {
            try {
              return db.getLongProperty("rocksdb.estimate-table-readers-mem");
            } catch (final RocksDBException e) {
              LOG.debug("Failed to get RocksDB metric", e);
              return 0L;
            }
          });

      rollbackCount =
          metricsSystem
              .createLabelledCounter(
                  MetricCategory.KVSTORE_ROCKSDB,
                  "rollback_count",
                  "Number of RocksDB transactions rolled back.",
                  "database")
              .labels(rocksDbConfiguration.getLabel());
    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public Optional<BytesValue> get(final BytesValue key) throws StorageException {
    throwIfClosed();

    try (final OperationTimer.TimingContext ignored = readLatency.startTimer()) {
      return Optional.ofNullable(db.get(key.getArrayUnsafe())).map(BytesValue::wrap);
    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public Transaction startTransaction() throws StorageException {
    throwIfClosed();
    final WriteOptions options = new WriteOptions();
    return new RocksDbTransaction(db.beginTransaction(options), options);
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      txOptions.close();
      options.close();
      db.close();
    }
  }

  private void throwIfClosed() {
    if (closed.get()) {
      LOG.error("Attempting to use a closed RocksDbKeyValueStorage");
      throw new IllegalStateException("Storage has been closed");
    }
  }

  private class RocksDbTransaction extends AbstractTransaction {
    private final org.rocksdb.Transaction innerTx;
    private final WriteOptions options;

    RocksDbTransaction(final org.rocksdb.Transaction innerTx, final WriteOptions options) {
      this.innerTx = innerTx;
      this.options = options;
    }

    @Override
    protected void doPut(final BytesValue key, final BytesValue value) {
      try (final OperationTimer.TimingContext ignored = writeLatency.startTimer()) {
        innerTx.put(key.getArrayUnsafe(), value.getArrayUnsafe());
      } catch (final RocksDBException e) {
        throw new StorageException(e);
      }
    }

    @Override
    protected void doRemove(final BytesValue key) {
      try (final OperationTimer.TimingContext ignored = removeLatency.startTimer()) {
        innerTx.delete(key.getArrayUnsafe());
      } catch (final RocksDBException e) {
        throw new StorageException(e);
      }
    }

    @Override
    protected void doCommit() throws StorageException {
      try (final OperationTimer.TimingContext ignored = commitLatency.startTimer()) {
        innerTx.commit();
      } catch (final RocksDBException e) {
        throw new StorageException(e);
      } finally {
        close();
      }
    }

    @Override
    protected void doRollback() {
      try {
        innerTx.rollback();
        rollbackCount.inc();
      } catch (final RocksDBException e) {
        throw new StorageException(e);
      } finally {
        close();
      }
    }

    private void close() {
      innerTx.close();
      options.close();
    }
  }
}
