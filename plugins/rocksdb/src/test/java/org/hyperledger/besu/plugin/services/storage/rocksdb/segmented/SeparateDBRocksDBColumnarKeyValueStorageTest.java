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

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetricsFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBConfigurationBuilder;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SeparateDBRocksDBColumnarKeyValueStorageTest {

  @TempDir Path tempDir;

  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final List<SegmentIdentifier> segments =
      List.of(KeyValueSegmentIdentifier.BLOCKCHAIN, KeyValueSegmentIdentifier.WORLD_STATE);
  private final RocksDBMetricsFactory rocksDBMetricsFactory =
      new RocksDBMetricsFactory(
          BesuMetricCategory.KVSTORE_ROCKSDB, BesuMetricCategory.KVSTORE_ROCKSDB_STATS);

  @Test
  public void shouldCreateSeparateDatabasePerColumn() throws Exception {
    var config =
        new RocksDBConfigurationBuilder()
            .databaseDir(tempDir)
            .useSeparateDatabasePerColumn(true)
            .build();

    try (var storage =
        new SeparateDBRocksDBColumnarKeyValueStorage(
            config, segments, List.of(), metricsSystem, rocksDBMetricsFactory)) {

      // Verify separate databases directories were created
      assertThat(tempDir.resolve("BLOCKCHAIN").toFile()).exists();
      assertThat(tempDir.resolve("WORLD_STATE").toFile()).exists();

      // Verify we can write to different segments
      SegmentedKeyValueStorageTransaction tx = storage.startTransaction();
      tx.put(KeyValueSegmentIdentifier.BLOCKCHAIN, new byte[] {1}, new byte[] {10});
      tx.put(KeyValueSegmentIdentifier.WORLD_STATE, new byte[] {2}, new byte[] {20});
      tx.commit();

      // Verify we can read from different segments
      Optional<byte[]> value1 = storage.get(KeyValueSegmentIdentifier.BLOCKCHAIN, new byte[] {1});
      Optional<byte[]> value2 = storage.get(KeyValueSegmentIdentifier.WORLD_STATE, new byte[] {2});

      assertThat(value1).isPresent();
      assertThat(value1.get()).isEqualTo(new byte[] {10});

      assertThat(value2).isPresent();
      assertThat(value2.get()).isEqualTo(new byte[] {20});
    }
  }

  @Test
  public void shouldIsolateDataBetweenSegments() throws Exception {
    var config =
        new RocksDBConfigurationBuilder()
            .databaseDir(tempDir)
            .useSeparateDatabasePerColumn(true)
            .build();

    try (var storage =
        new SeparateDBRocksDBColumnarKeyValueStorage(
            config, segments, List.of(), metricsSystem, rocksDBMetricsFactory)) {

      // Write same key to different segments
      SegmentedKeyValueStorageTransaction tx = storage.startTransaction();
      tx.put(KeyValueSegmentIdentifier.BLOCKCHAIN, new byte[] {1}, new byte[] {100});
      tx.put(KeyValueSegmentIdentifier.WORLD_STATE, new byte[] {1}, new byte[] {(byte) 200});
      tx.commit();

      // Verify values are isolated
      Optional<byte[]> blockchainValue =
          storage.get(KeyValueSegmentIdentifier.BLOCKCHAIN, new byte[] {1});
      Optional<byte[]> worldStateValue =
          storage.get(KeyValueSegmentIdentifier.WORLD_STATE, new byte[] {1});

      assertThat(blockchainValue).isPresent();
      assertThat(blockchainValue.get()).isEqualTo(new byte[] {100});

      assertThat(worldStateValue).isPresent();
      assertThat(worldStateValue.get()).isEqualTo(new byte[] {(byte) 200});
    }
  }

  @Test
  public void shouldHandleTransactionRollback() throws Exception {
    var config =
        new RocksDBConfigurationBuilder()
            .databaseDir(tempDir)
            .useSeparateDatabasePerColumn(true)
            .build();

    try (var storage =
        new SeparateDBRocksDBColumnarKeyValueStorage(
            config, segments, List.of(), metricsSystem, rocksDBMetricsFactory)) {

      // Create and rollback a transaction
      SegmentedKeyValueStorageTransaction tx = storage.startTransaction();
      tx.put(KeyValueSegmentIdentifier.BLOCKCHAIN, new byte[] {1}, new byte[] {10});
      tx.rollback();

      // Verify data was not persisted
      Optional<byte[]> value = storage.get(KeyValueSegmentIdentifier.BLOCKCHAIN, new byte[] {1});
      assertThat(value).isEmpty();
    }
  }

  @Test
  public void shouldClearSegment() throws Exception {
    var config =
        new RocksDBConfigurationBuilder()
            .databaseDir(tempDir)
            .useSeparateDatabasePerColumn(true)
            .build();

    try (var storage =
        new SeparateDBRocksDBColumnarKeyValueStorage(
            config, segments, List.of(), metricsSystem, rocksDBMetricsFactory)) {

      // Write data
      SegmentedKeyValueStorageTransaction tx = storage.startTransaction();
      tx.put(KeyValueSegmentIdentifier.BLOCKCHAIN, new byte[] {1}, new byte[] {10});
      tx.put(KeyValueSegmentIdentifier.BLOCKCHAIN, new byte[] {2}, new byte[] {20});
      tx.commit();

      // Clear segment
      storage.clear(KeyValueSegmentIdentifier.BLOCKCHAIN);

      // Verify data was cleared
      Optional<byte[]> value1 = storage.get(KeyValueSegmentIdentifier.BLOCKCHAIN, new byte[] {1});
      Optional<byte[]> value2 = storage.get(KeyValueSegmentIdentifier.BLOCKCHAIN, new byte[] {2});

      assertThat(value1).isEmpty();
      assertThat(value2).isEmpty();
    }
  }

  @Test
  public void shouldStreamFromSegment() throws Exception {
    var config =
        new RocksDBConfigurationBuilder()
            .databaseDir(tempDir)
            .useSeparateDatabasePerColumn(true)
            .build();

    try (var storage =
        new SeparateDBRocksDBColumnarKeyValueStorage(
            config, segments, List.of(), metricsSystem, rocksDBMetricsFactory)) {

      // Write multiple entries
      SegmentedKeyValueStorageTransaction tx = storage.startTransaction();
      tx.put(KeyValueSegmentIdentifier.BLOCKCHAIN, new byte[] {1}, new byte[] {10});
      tx.put(KeyValueSegmentIdentifier.BLOCKCHAIN, new byte[] {2}, new byte[] {20});
      tx.put(KeyValueSegmentIdentifier.BLOCKCHAIN, new byte[] {3}, new byte[] {30});
      tx.commit();

      // Stream and verify
      long count = storage.stream(KeyValueSegmentIdentifier.BLOCKCHAIN).count();
      assertThat(count).isEqualTo(3);
    }
  }

  @Test
  public void shouldDeleteFromSegment() throws Exception {
    var config =
        new RocksDBConfigurationBuilder()
            .databaseDir(tempDir)
            .useSeparateDatabasePerColumn(true)
            .build();

    try (var storage =
        new SeparateDBRocksDBColumnarKeyValueStorage(
            config, segments, List.of(), metricsSystem, rocksDBMetricsFactory)) {

      // Write data
      SegmentedKeyValueStorageTransaction tx = storage.startTransaction();
      tx.put(KeyValueSegmentIdentifier.BLOCKCHAIN, new byte[] {1}, new byte[] {10});
      tx.commit();

      // Delete data
      SegmentedKeyValueStorageTransaction tx2 = storage.startTransaction();
      tx2.remove(KeyValueSegmentIdentifier.BLOCKCHAIN, new byte[] {1});
      tx2.commit();

      // Verify deletion
      Optional<byte[]> value = storage.get(KeyValueSegmentIdentifier.BLOCKCHAIN, new byte[] {1});
      assertThat(value).isEmpty();
    }
  }
}
