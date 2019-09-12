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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.metrics.ObservableMetricsSystem;
import tech.pegasys.pantheon.metrics.PantheonMetricCategory;
import tech.pegasys.pantheon.plugin.services.metrics.Counter;
import tech.pegasys.pantheon.plugin.services.metrics.LabelledMetric;
import tech.pegasys.pantheon.plugin.services.metrics.OperationTimer;
import tech.pegasys.pantheon.plugin.services.storage.rocksdb.configuration.RocksDBConfiguration;
import tech.pegasys.pantheon.plugin.services.storage.rocksdb.configuration.RocksDBConfigurationBuilder;

import java.util.function.LongSupplier;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.rocksdb.Statistics;
import org.rocksdb.TransactionDB;

@RunWith(MockitoJUnitRunner.class)
public class RocksDBMetricsTest {

  @Mock private ObservableMetricsSystem metricsSystemMock;
  @Mock private LabelledMetric<OperationTimer> labelledMetricOperationTimerMock;
  @Mock private LabelledMetric<Counter> labelledMetricCounterMock;
  @Mock private OperationTimer operationTimerMock;
  @Mock private TransactionDB db;
  @Mock private Statistics stats;

  @Rule public final TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void createStoreMustCreateMetrics() throws Exception {
    // Prepare mocks
    when(labelledMetricOperationTimerMock.labels(any())).thenReturn(operationTimerMock);
    when(metricsSystemMock.createLabelledTimer(
            eq(PantheonMetricCategory.KVSTORE_ROCKSDB), anyString(), anyString(), any()))
        .thenReturn(labelledMetricOperationTimerMock);
    when(metricsSystemMock.createLabelledCounter(
            eq(PantheonMetricCategory.KVSTORE_ROCKSDB), anyString(), anyString(), any()))
        .thenReturn(labelledMetricCounterMock);
    // Prepare argument captors
    final ArgumentCaptor<String> labelledTimersMetricsNameArgs =
        ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<String> labelledTimersHelpArgs = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<String> labelledCountersMetricsNameArgs =
        ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<String> labelledCountersHelpArgs = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<String> longGaugesMetricsNameArgs = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<String> longGaugesHelpArgs = ArgumentCaptor.forClass(String.class);

    RocksDBMetrics.of(metricsSystemMock, config(), db, stats);

    verify(metricsSystemMock, times(4))
        .createLabelledTimer(
            eq(PantheonMetricCategory.KVSTORE_ROCKSDB),
            labelledTimersMetricsNameArgs.capture(),
            labelledTimersHelpArgs.capture(),
            any());
    assertThat(labelledTimersMetricsNameArgs.getAllValues())
        .containsExactly(
            "read_latency_seconds",
            "remove_latency_seconds",
            "write_latency_seconds",
            "commit_latency_seconds");
    assertThat(labelledTimersHelpArgs.getAllValues())
        .containsExactly(
            "Latency for read from RocksDB.",
            "Latency of remove requests from RocksDB.",
            "Latency for write to RocksDB.",
            "Latency for commits to RocksDB.");

    verify(metricsSystemMock, times(2))
        .createLongGauge(
            eq(PantheonMetricCategory.KVSTORE_ROCKSDB),
            longGaugesMetricsNameArgs.capture(),
            longGaugesHelpArgs.capture(),
            any(LongSupplier.class));
    assertThat(longGaugesMetricsNameArgs.getAllValues())
        .containsExactly("rocks_db_table_readers_memory_bytes", "rocks_db_files_size_bytes");
    assertThat(longGaugesHelpArgs.getAllValues())
        .containsExactly(
            "Estimated memory used for RocksDB index and filter blocks in bytes",
            "Estimated database size in bytes");

    verify(metricsSystemMock)
        .createLabelledCounter(
            eq(PantheonMetricCategory.KVSTORE_ROCKSDB),
            labelledCountersMetricsNameArgs.capture(),
            labelledCountersHelpArgs.capture(),
            any());
    assertThat(labelledCountersMetricsNameArgs.getValue()).isEqualTo("rollback_count");
    assertThat(labelledCountersHelpArgs.getValue())
        .isEqualTo("Number of RocksDB transactions rolled back.");
  }

  private RocksDBConfiguration config() throws Exception {
    return new RocksDBConfigurationBuilder().databaseDir(folder.newFolder().toPath()).build();
  }
}
