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
package org.hyperledger.besu.plugin.services.storage.rocksdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.plugin.services.storage.DataStorageFormat.BONSAI;
import static org.hyperledger.besu.plugin.services.storage.DataStorageFormat.FOREST;
import static org.hyperledger.besu.plugin.services.storage.rocksdb.segmented.RocksDBColumnarKeyValueStorageTest.TestSegment;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.storage.DataStorageConfiguration;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.BaseVersionedStorageFormat;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.DatabaseMetadata;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.PrivacyVersionedStorageFormat;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBFactoryConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RocksDBKeyValuePrivacyStorageFactoryTest {
  @Mock private RocksDBFactoryConfiguration rocksDbConfiguration;
  @Mock private BesuConfiguration commonConfiguration;
  @Mock private DataStorageConfiguration dataStorageConfiguration;
  @TempDir private Path temporaryFolder;
  private final ObservableMetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final SegmentIdentifier segment = TestSegment.BAR;
  private final List<SegmentIdentifier> segments = List.of(TestSegment.DEFAULT, segment);

  @Test
  public void shouldDetectVersion1MetadataIfPresent() throws Exception {
    final Path tempDataDir = temporaryFolder.resolve("data");
    final Path tempDatabaseDir = temporaryFolder.resolve("db");
    final Path tempPrivateDatabaseDir = tempDatabaseDir.resolve("private");
    Files.createDirectories(tempPrivateDatabaseDir);
    Files.createDirectories(tempDataDir);
    mockCommonConfiguration(tempDataDir, tempDatabaseDir);

    Utils.createDatabaseMetadataV1Privacy(
        tempDataDir, PrivacyVersionedStorageFormat.FOREST_ORIGINAL);

    final RocksDBKeyValuePrivacyStorageFactory storageFactory =
        new RocksDBKeyValuePrivacyStorageFactory(
            new RocksDBKeyValueStorageFactory(
                () -> rocksDbConfiguration,
                segments,
                RocksDBMetricsFactory.PRIVATE_ROCKS_DB_METRICS));

    // Side effect is creation of the Metadata version file
    try (final var storage = storageFactory.create(segment, commonConfiguration, metricsSystem)) {

      assertThat(DatabaseMetadata.lookUpFrom(tempDataDir).getVersionedStorageFormat())
          .isEqualTo(PrivacyVersionedStorageFormat.FOREST_WITH_RECEIPT_COMPACTION);
    }
  }

  @Test
  public void shouldCreateCorrectMetadataFileForLatestVersion() throws Exception {
    final Path tempDataDir = temporaryFolder.resolve("data");
    final Path tempDatabaseDir = temporaryFolder.resolve("db");
    mockCommonConfiguration(tempDataDir, tempDatabaseDir);

    final RocksDBKeyValuePrivacyStorageFactory storageFactory =
        new RocksDBKeyValuePrivacyStorageFactory(
            new RocksDBKeyValueStorageFactory(
                () -> rocksDbConfiguration,
                segments,
                RocksDBMetricsFactory.PRIVATE_ROCKS_DB_METRICS));

    // Side effect is creation of the Metadata version file
    try (final var storage = storageFactory.create(segment, commonConfiguration, metricsSystem)) {
      assertThat(DatabaseMetadata.lookUpFrom(tempDataDir).getVersionedStorageFormat())
          .isEqualTo(PrivacyVersionedStorageFormat.FOREST_WITH_RECEIPT_COMPACTION);
    }
  }

  @ParameterizedTest
  @EnumSource(DataStorageFormat.class)
  public void shouldUpdateCorrectMetadataFileForLatestVersion(
      final DataStorageFormat dataStorageFormat) throws Exception {
    final Path tempDataDir = temporaryFolder.resolve("data");
    final Path tempDatabaseDir = temporaryFolder.resolve("db");
    mockCommonConfiguration(tempDataDir, tempDatabaseDir, dataStorageFormat);

    final RocksDBKeyValueStorageFactory storageFactory =
        new RocksDBKeyValueStorageFactory(
            () -> rocksDbConfiguration, segments, RocksDBMetricsFactory.PRIVATE_ROCKS_DB_METRICS);

    try (final var storage = storageFactory.create(segment, commonConfiguration, metricsSystem)) {
      final BaseVersionedStorageFormat expectedBaseVersion =
          dataStorageFormat == BONSAI
              ? BaseVersionedStorageFormat.BONSAI_WITH_RECEIPT_COMPACTION
              : BaseVersionedStorageFormat.FOREST_WITH_RECEIPT_COMPACTION;
      assertThat(DatabaseMetadata.lookUpFrom(tempDataDir).getVersionedStorageFormat())
          .isEqualTo(expectedBaseVersion);
    }
    storageFactory.close();

    final RocksDBKeyValuePrivacyStorageFactory privacyStorageFactory =
        new RocksDBKeyValuePrivacyStorageFactory(storageFactory);

    try (final var storage =
        privacyStorageFactory.create(segment, commonConfiguration, metricsSystem)) {
      final PrivacyVersionedStorageFormat expectedPrivacyVersion =
          dataStorageFormat == BONSAI
              ? PrivacyVersionedStorageFormat.BONSAI_WITH_RECEIPT_COMPACTION
              : PrivacyVersionedStorageFormat.FOREST_WITH_RECEIPT_COMPACTION;
      assertThat(DatabaseMetadata.lookUpFrom(tempDataDir).getVersionedStorageFormat())
          .isEqualTo(expectedPrivacyVersion);
    }
    privacyStorageFactory.close();
  }

  @ParameterizedTest
  @EnumSource(DataStorageFormat.class)
  public void shouldUpdateCorrectMetadataFileForLatestVersionWithReceiptCompaction(
      final DataStorageFormat dataStorageFormat) throws Exception {
    final Path tempDataDir = temporaryFolder.resolve("data");
    final Path tempDatabaseDir = temporaryFolder.resolve("db");
    mockCommonConfiguration(tempDataDir, tempDatabaseDir, dataStorageFormat);

    final RocksDBKeyValueStorageFactory storageFactory =
        new RocksDBKeyValueStorageFactory(
            () -> rocksDbConfiguration, segments, RocksDBMetricsFactory.PRIVATE_ROCKS_DB_METRICS);

    try (final var storage = storageFactory.create(segment, commonConfiguration, metricsSystem)) {
      final BaseVersionedStorageFormat expectedBaseVersion =
          dataStorageFormat == BONSAI
              ? BaseVersionedStorageFormat.BONSAI_WITH_RECEIPT_COMPACTION
              : BaseVersionedStorageFormat.FOREST_WITH_RECEIPT_COMPACTION;
      assertThat(DatabaseMetadata.lookUpFrom(tempDataDir).getVersionedStorageFormat())
          .isEqualTo(expectedBaseVersion);
    }
    storageFactory.close();

    final RocksDBKeyValuePrivacyStorageFactory privacyStorageFactory =
        new RocksDBKeyValuePrivacyStorageFactory(storageFactory);

    try (final var storage =
        privacyStorageFactory.create(segment, commonConfiguration, metricsSystem)) {
      final PrivacyVersionedStorageFormat expectedPrivacyVersion =
          dataStorageFormat == BONSAI
              ? PrivacyVersionedStorageFormat.BONSAI_WITH_RECEIPT_COMPACTION
              : PrivacyVersionedStorageFormat.FOREST_WITH_RECEIPT_COMPACTION;
      assertThat(DatabaseMetadata.lookUpFrom(tempDataDir).getVersionedStorageFormat())
          .isEqualTo(expectedPrivacyVersion);
    }
    privacyStorageFactory.close();
  }

  private void mockCommonConfiguration(final Path tempDataDir, final Path tempDatabaseDir) {
    mockCommonConfiguration(tempDataDir, tempDatabaseDir, FOREST);
  }

  private void mockCommonConfiguration(
      final Path tempDataDir,
      final Path tempDatabaseDir,
      final DataStorageFormat dataStorageFormat) {
    when(commonConfiguration.getStoragePath()).thenReturn(tempDatabaseDir);
    when(commonConfiguration.getDataPath()).thenReturn(tempDataDir);
    when(dataStorageConfiguration.getDatabaseFormat()).thenReturn(dataStorageFormat);
    lenient()
        .when(commonConfiguration.getDataStorageConfiguration())
        .thenReturn(dataStorageConfiguration);
  }
}
