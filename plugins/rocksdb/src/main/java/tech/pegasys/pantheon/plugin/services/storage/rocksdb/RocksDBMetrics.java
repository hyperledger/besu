/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.plugin.services.storage.rocksdb;

import tech.pegasys.pantheon.metrics.PantheonMetricCategory;
import tech.pegasys.pantheon.metrics.prometheus.PrometheusMetricsSystem;
import tech.pegasys.pantheon.metrics.rocksdb.RocksDBStats;
import tech.pegasys.pantheon.plugin.services.MetricsSystem;
import tech.pegasys.pantheon.plugin.services.metrics.Counter;
import tech.pegasys.pantheon.plugin.services.metrics.OperationTimer;
import tech.pegasys.pantheon.plugin.services.storage.rocksdb.configuration.RocksDBConfiguration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rocksdb.RocksDBException;
import org.rocksdb.Statistics;
import org.rocksdb.TransactionDB;

public class RocksDBMetrics {

  private static final Logger LOG = LogManager.getLogger();

  private final OperationTimer readLatency;
  private final OperationTimer removeLatency;
  private final OperationTimer writeLatency;
  private final OperationTimer commitLatency;
  private final Counter rollbackCount;

  private RocksDBMetrics(
      final OperationTimer readLatency,
      final OperationTimer removeLatency,
      final OperationTimer writeLatency,
      final OperationTimer commitLatency,
      final Counter rollbackCount) {
    this.readLatency = readLatency;
    this.removeLatency = removeLatency;
    this.writeLatency = writeLatency;
    this.commitLatency = commitLatency;
    this.rollbackCount = rollbackCount;
  }

  public static RocksDBMetrics of(
      final MetricsSystem metricsSystem,
      final RocksDBConfiguration rocksDbConfiguration,
      final TransactionDB db,
      final Statistics stats) {
    final OperationTimer readLatency =
        metricsSystem
            .createLabelledTimer(
                PantheonMetricCategory.KVSTORE_ROCKSDB,
                "read_latency_seconds",
                "Latency for read from RocksDB.",
                "database")
            .labels(rocksDbConfiguration.getLabel());
    final OperationTimer removeLatency =
        metricsSystem
            .createLabelledTimer(
                PantheonMetricCategory.KVSTORE_ROCKSDB,
                "remove_latency_seconds",
                "Latency of remove requests from RocksDB.",
                "database")
            .labels(rocksDbConfiguration.getLabel());
    final OperationTimer writeLatency =
        metricsSystem
            .createLabelledTimer(
                PantheonMetricCategory.KVSTORE_ROCKSDB,
                "write_latency_seconds",
                "Latency for write to RocksDB.",
                "database")
            .labels(rocksDbConfiguration.getLabel());
    final OperationTimer commitLatency =
        metricsSystem
            .createLabelledTimer(
                PantheonMetricCategory.KVSTORE_ROCKSDB,
                "commit_latency_seconds",
                "Latency for commits to RocksDB.",
                "database")
            .labels(rocksDbConfiguration.getLabel());

    if (metricsSystem instanceof PrometheusMetricsSystem) {
      RocksDBStats.registerRocksDBMetrics(stats, (PrometheusMetricsSystem) metricsSystem);
    }

    metricsSystem.createLongGauge(
        PantheonMetricCategory.KVSTORE_ROCKSDB,
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

    metricsSystem.createLongGauge(
        PantheonMetricCategory.KVSTORE_ROCKSDB,
        "rocks_db_files_size_bytes",
        "Estimated database size in bytes",
        () -> {
          try {
            return db.getLongProperty("rocksdb.live-sst-files-size");
          } catch (final RocksDBException e) {
            LOG.debug("Failed to get RocksDB metric", e);
            return 0L;
          }
        });

    final Counter rollbackCount =
        metricsSystem
            .createLabelledCounter(
                PantheonMetricCategory.KVSTORE_ROCKSDB,
                "rollback_count",
                "Number of RocksDB transactions rolled back.",
                "database")
            .labels(rocksDbConfiguration.getLabel());

    return new RocksDBMetrics(
        readLatency, removeLatency, writeLatency, commitLatency, rollbackCount);
  }

  public OperationTimer getReadLatency() {
    return readLatency;
  }

  public OperationTimer getRemoveLatency() {
    return removeLatency;
  }

  public OperationTimer getWriteLatency() {
    return writeLatency;
  }

  public OperationTimer getCommitLatency() {
    return commitLatency;
  }

  public Counter getRollbackCount() {
    return rollbackCount;
  }
}
