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
import static org.hyperledger.besu.plugin.services.storage.rocksdb.segmented.RocksDBColumnarKeyValueStorageTest.TestSegment;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.DatabaseMetadata;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.PrivateDatabaseMetadata;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.PrivateVersionedStorageFormat;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBFactoryConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RocksDBKeyValuePrivacyStorageFactoryTest {
  @Mock private RocksDBFactoryConfiguration rocksDbConfiguration;
  @Mock private BesuConfiguration commonConfiguration;
  @TempDir private Path temporaryFolder;
  private final ObservableMetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final SegmentIdentifier segment = TestSegment.BAR;
  private final List<SegmentIdentifier> segments = List.of(TestSegment.DEFAULT, segment);

  @Test
  public void shouldDetectVersion1DatabaseIfNoMetadataFileFound() throws Exception {
    final Path tempDataDir = temporaryFolder.resolve("data");
    final Path tempDatabaseDir = temporaryFolder.resolve("db");
    final Path tempPrivateDatabaseDir = tempDatabaseDir.resolve("private");
    Files.createDirectories(tempPrivateDatabaseDir);
    Files.createDirectories(tempDataDir);
    when(commonConfiguration.getStoragePath()).thenReturn(tempDatabaseDir);
    when(commonConfiguration.getDataPath()).thenReturn(tempDataDir);

    final RocksDBKeyValuePrivacyStorageFactory storageFactory =
        new RocksDBKeyValuePrivacyStorageFactory(
            new RocksDBKeyValueStorageFactory(
                () -> rocksDbConfiguration,
                segments,
                RocksDBMetricsFactory.PRIVATE_ROCKS_DB_METRICS));

    // Side effect is creation of the Metadata version file
    storageFactory.create(segment, commonConfiguration, metricsSystem);

    assertThat(PrivateDatabaseMetadata.lookUpFrom(tempDataDir).getPrivateVersionedStorageFormat())
        .isEqualTo(PrivateVersionedStorageFormat.ORIGINAL);
  }

  @Test
  public void shouldCreateCorrectMetadataFileForLatestVersion() throws Exception {
    final Path tempDataDir = temporaryFolder.resolve("data");
    final Path tempDatabaseDir = temporaryFolder.resolve("db");
    when(commonConfiguration.getStoragePath()).thenReturn(tempDatabaseDir);
    when(commonConfiguration.getDataPath()).thenReturn(tempDataDir);
    when(commonConfiguration.getDatabaseFormat()).thenReturn(DataStorageFormat.FOREST);

    final RocksDBKeyValuePrivacyStorageFactory storageFactory =
        new RocksDBKeyValuePrivacyStorageFactory(
            new RocksDBKeyValueStorageFactory(
                () -> rocksDbConfiguration,
                segments,
                RocksDBMetricsFactory.PRIVATE_ROCKS_DB_METRICS));

    // Side effect is creation of the Metadata version file
    storageFactory.create(segment, commonConfiguration, metricsSystem);

    assertThat(PrivateDatabaseMetadata.lookUpFrom(tempDataDir).getPrivateVersionedStorageFormat())
        .isEqualTo(PrivateVersionedStorageFormat.ORIGINAL);
  }

  @Test
  public void shouldUpdateCorrectMetadataFileForLatestVersion() throws Exception {
    final Path tempDataDir = temporaryFolder.resolve("data");
    final Path tempDatabaseDir = temporaryFolder.resolve("db");
    when(commonConfiguration.getStoragePath()).thenReturn(tempDatabaseDir);
    when(commonConfiguration.getDataPath()).thenReturn(tempDataDir);
    when(commonConfiguration.getDatabaseFormat()).thenReturn(DataStorageFormat.FOREST);

    final RocksDBKeyValueStorageFactory storageFactory =
        new RocksDBKeyValueStorageFactory(
            () -> rocksDbConfiguration, segments, RocksDBMetricsFactory.PRIVATE_ROCKS_DB_METRICS);

    storageFactory.create(segment, commonConfiguration, metricsSystem);

    assertThat(DatabaseMetadata.lookUpFrom(tempDataDir).getVersionedStorageFormat())
        .isEqualTo(PrivateVersionedStorageFormat.ORIGINAL);

    final RocksDBKeyValuePrivacyStorageFactory privacyStorageFactory =
        new RocksDBKeyValuePrivacyStorageFactory(storageFactory);

    privacyStorageFactory.create(segment, commonConfiguration, metricsSystem);

    assertThat(PrivateDatabaseMetadata.lookUpFrom(tempDataDir).getPrivateVersionedStorageFormat())
        .isEqualTo(PrivateVersionedStorageFormat.ORIGINAL);
  }
}
