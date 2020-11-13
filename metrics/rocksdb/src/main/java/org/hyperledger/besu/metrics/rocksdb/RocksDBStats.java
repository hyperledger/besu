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
package org.hyperledger.besu.metrics.rocksdb;

import static org.hyperledger.besu.metrics.BesuMetricCategory.KVSTORE_ROCKSDB_STATS;

import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.prometheus.client.Collector;
import org.rocksdb.HistogramData;
import org.rocksdb.HistogramType;
import org.rocksdb.Statistics;
import org.rocksdb.TickerType;

public class RocksDBStats {

  static final List<String> LABELS = Collections.singletonList("quantile");
  static final List<String> LABEL_50 = Collections.singletonList("0.5");
  static final List<String> LABEL_95 = Collections.singletonList("0.95");
  static final List<String> LABEL_99 = Collections.singletonList("0.99");

  // Tickers - RocksDB equivalent of counters
  static final TickerType[] TICKERS = {
    TickerType.BLOCK_CACHE_ADD,
    TickerType.BLOCK_CACHE_HIT,
    TickerType.BLOCK_CACHE_ADD_FAILURES,
    TickerType.BLOCK_CACHE_INDEX_MISS,
    TickerType.BLOCK_CACHE_INDEX_HIT,
    TickerType.BLOCK_CACHE_INDEX_ADD,
    TickerType.BLOCK_CACHE_INDEX_BYTES_INSERT,
    TickerType.BLOCK_CACHE_INDEX_BYTES_EVICT,
    TickerType.BLOCK_CACHE_FILTER_MISS,
    TickerType.BLOCK_CACHE_FILTER_HIT,
    TickerType.BLOCK_CACHE_FILTER_ADD,
    TickerType.BLOCK_CACHE_FILTER_BYTES_INSERT,
    TickerType.BLOCK_CACHE_FILTER_BYTES_EVICT,
    TickerType.BLOCK_CACHE_DATA_MISS,
    TickerType.BLOCK_CACHE_DATA_HIT,
    TickerType.BLOCK_CACHE_DATA_ADD,
    TickerType.BLOCK_CACHE_DATA_BYTES_INSERT,
    TickerType.BLOCK_CACHE_BYTES_READ,
    TickerType.BLOCK_CACHE_BYTES_WRITE,
    TickerType.BLOOM_FILTER_USEFUL,
    TickerType.PERSISTENT_CACHE_HIT,
    TickerType.PERSISTENT_CACHE_MISS,
    TickerType.SIM_BLOCK_CACHE_HIT,
    TickerType.SIM_BLOCK_CACHE_MISS,
    TickerType.MEMTABLE_HIT,
    TickerType.MEMTABLE_MISS,
    TickerType.GET_HIT_L0,
    TickerType.GET_HIT_L1,
    TickerType.GET_HIT_L2_AND_UP,
    TickerType.COMPACTION_KEY_DROP_NEWER_ENTRY,
    TickerType.COMPACTION_KEY_DROP_OBSOLETE,
    TickerType.COMPACTION_KEY_DROP_RANGE_DEL,
    TickerType.COMPACTION_KEY_DROP_USER,
    TickerType.COMPACTION_RANGE_DEL_DROP_OBSOLETE,
    TickerType.NUMBER_KEYS_WRITTEN,
    TickerType.NUMBER_KEYS_READ,
    TickerType.NUMBER_KEYS_UPDATED,
    TickerType.BYTES_WRITTEN,
    TickerType.BYTES_READ,
    TickerType.NUMBER_DB_SEEK,
    TickerType.NUMBER_DB_NEXT,
    TickerType.NUMBER_DB_PREV,
    TickerType.NUMBER_DB_SEEK_FOUND,
    TickerType.NUMBER_DB_NEXT_FOUND,
    TickerType.NUMBER_DB_PREV_FOUND,
    TickerType.ITER_BYTES_READ,
    TickerType.NO_FILE_CLOSES,
    TickerType.NO_FILE_OPENS,
    TickerType.NO_FILE_ERRORS,
    // TickerType.STALL_L0_SLOWDOWN_MICROS,
    // TickerType.STALL_MEMTABLE_COMPACTION_MICROS,
    // TickerType.STALL_L0_NUM_FILES_MICROS,
    TickerType.STALL_MICROS,
    TickerType.DB_MUTEX_WAIT_MICROS,
    TickerType.RATE_LIMIT_DELAY_MILLIS,
    TickerType.NO_ITERATORS,
    TickerType.NUMBER_MULTIGET_BYTES_READ,
    TickerType.NUMBER_MULTIGET_KEYS_READ,
    TickerType.NUMBER_MULTIGET_CALLS,
    TickerType.NUMBER_FILTERED_DELETES,
    TickerType.NUMBER_MERGE_FAILURES,
    TickerType.BLOOM_FILTER_PREFIX_CHECKED,
    TickerType.BLOOM_FILTER_PREFIX_USEFUL,
    TickerType.NUMBER_OF_RESEEKS_IN_ITERATION,
    TickerType.GET_UPDATES_SINCE_CALLS,
    TickerType.BLOCK_CACHE_COMPRESSED_MISS,
    TickerType.BLOCK_CACHE_COMPRESSED_HIT,
    TickerType.BLOCK_CACHE_COMPRESSED_ADD,
    TickerType.BLOCK_CACHE_COMPRESSED_ADD_FAILURES,
    TickerType.WAL_FILE_SYNCED,
    TickerType.WAL_FILE_BYTES,
    TickerType.WRITE_DONE_BY_SELF,
    TickerType.WRITE_DONE_BY_OTHER,
    TickerType.WRITE_TIMEDOUT,
    TickerType.WRITE_WITH_WAL,
    TickerType.COMPACT_READ_BYTES,
    TickerType.COMPACT_WRITE_BYTES,
    TickerType.FLUSH_WRITE_BYTES,
    TickerType.NUMBER_DIRECT_LOAD_TABLE_PROPERTIES,
    TickerType.NUMBER_SUPERVERSION_ACQUIRES,
    TickerType.NUMBER_SUPERVERSION_RELEASES,
    TickerType.NUMBER_SUPERVERSION_CLEANUPS,
    TickerType.NUMBER_BLOCK_COMPRESSED,
    TickerType.NUMBER_BLOCK_DECOMPRESSED,
    TickerType.NUMBER_BLOCK_NOT_COMPRESSED,
    TickerType.MERGE_OPERATION_TOTAL_TIME,
    TickerType.FILTER_OPERATION_TOTAL_TIME,
    TickerType.ROW_CACHE_HIT,
    TickerType.ROW_CACHE_MISS,
    TickerType.READ_AMP_ESTIMATE_USEFUL_BYTES,
    TickerType.READ_AMP_TOTAL_READ_BYTES,
    TickerType.NUMBER_RATE_LIMITER_DRAINS,
    TickerType.NUMBER_ITER_SKIP,
    TickerType.NUMBER_MULTIGET_KEYS_FOUND,
  };

  // Histograms - treated as prometheus summaries
  static final HistogramType[] HISTOGRAMS = {
    HistogramType.DB_GET,
    HistogramType.DB_WRITE,
    HistogramType.COMPACTION_TIME,
    HistogramType.SUBCOMPACTION_SETUP_TIME,
    HistogramType.TABLE_SYNC_MICROS,
    HistogramType.COMPACTION_OUTFILE_SYNC_MICROS,
    HistogramType.WAL_FILE_SYNC_MICROS,
    HistogramType.MANIFEST_FILE_SYNC_MICROS,
    HistogramType.TABLE_OPEN_IO_MICROS,
    HistogramType.DB_MULTIGET,
    HistogramType.READ_BLOCK_COMPACTION_MICROS,
    HistogramType.READ_BLOCK_GET_MICROS,
    HistogramType.WRITE_RAW_BLOCK_MICROS,
    HistogramType.STALL_L0_SLOWDOWN_COUNT,
    HistogramType.STALL_MEMTABLE_COMPACTION_COUNT,
    HistogramType.STALL_L0_NUM_FILES_COUNT,
    HistogramType.HARD_RATE_LIMIT_DELAY_COUNT,
    HistogramType.SOFT_RATE_LIMIT_DELAY_COUNT,
    HistogramType.NUM_FILES_IN_SINGLE_COMPACTION,
    HistogramType.DB_SEEK,
    HistogramType.WRITE_STALL,
    HistogramType.SST_READ_MICROS,
    HistogramType.NUM_SUBCOMPACTIONS_SCHEDULED,
    HistogramType.BYTES_PER_READ,
    HistogramType.BYTES_PER_WRITE,
    HistogramType.BYTES_PER_MULTIGET,
    HistogramType.BYTES_COMPRESSED,
    HistogramType.BYTES_DECOMPRESSED,
    HistogramType.COMPRESSION_TIMES_NANOS,
    HistogramType.DECOMPRESSION_TIMES_NANOS,
    HistogramType.READ_NUM_MERGE_OPERANDS,
  };

  public static void registerRocksDBMetrics(
      final Statistics stats,
      final PrometheusMetricsSystem metricsSystem,
      final MetricCategory category) {
    if (!metricsSystem.isCategoryEnabled(category)) {
      return;
    }
    for (final TickerType ticker : TICKERS) {
      final String promCounterName = ticker.name().toLowerCase();
      metricsSystem.createLongGauge(
          category,
          promCounterName,
          "RocksDB reported statistics for " + ticker.name(),
          () -> stats.getTickerCount(ticker));
    }

    for (final HistogramType histogram : HISTOGRAMS) {
      metricsSystem.addCollector(category, () -> histogramToCollector(stats, histogram));
    }
  }

  private static Collector histogramToCollector(
      final Statistics stats, final HistogramType histogram) {
    return new Collector() {
      final String metricName =
          KVSTORE_ROCKSDB_STATS.getName() + "_" + histogram.name().toLowerCase();

      @Override
      public List<MetricFamilySamples> collect() {
        final HistogramData data = stats.getHistogramData(histogram);
        return Collections.singletonList(
            new MetricFamilySamples(
                metricName,
                Type.SUMMARY,
                "RocksDB histogram for " + metricName,
                Arrays.asList(
                    new MetricFamilySamples.Sample(metricName, LABELS, LABEL_50, data.getMedian()),
                    new MetricFamilySamples.Sample(
                        metricName, LABELS, LABEL_95, data.getPercentile95()),
                    new MetricFamilySamples.Sample(
                        metricName, LABELS, LABEL_99, data.getPercentile99()))));
      }
    };
  }
}
