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
import static org.mockito.Mockito.when;

import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.DatabaseMetadata;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBFactoryConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RocksDBKeyValuePrivacyStorageFactoryTest {
  private static final int DEFAULT_VERSION = 1;
  private static final int DEFAULT_PRIVACY_VERSION = 1;

  @Mock private RocksDBFactoryConfiguration rocksDbConfiguration;
  @Mock private BesuConfiguration commonConfiguration;
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private final ObservableMetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final List<SegmentIdentifier> segments = List.of();
  @Mock private SegmentIdentifier segment;

  @Test
  public void shouldDetectVersion0DatabaseIfNoMetadataFileFound() throws Exception {
    final Path tempDataDir = temporaryFolder.newFolder().toPath().resolve("data");
    final Path tempDatabaseDir = temporaryFolder.newFolder().toPath().resolve("db");
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

    assertThat(DatabaseMetadata.lookUpFrom(commonConfiguration.getDataPath()).maybePrivacyVersion())
        .isNotEmpty();

    assertThat(DatabaseMetadata.lookUpFrom(commonConfiguration.getDataPath()).getVersion())
        .isEqualTo(0);

    assertThat(
            DatabaseMetadata.lookUpFrom(commonConfiguration.getDataPath())
                .maybePrivacyVersion()
                .get())
        .isEqualTo(0);
  }

  @Test
  public void shouldCreateCorrectMetadataFileForLatestVersion() throws Exception {
    final Path tempDataDir = temporaryFolder.newFolder().toPath().resolve("data");
    final Path tempDatabaseDir = temporaryFolder.newFolder().toPath().resolve("db");
    when(commonConfiguration.getStoragePath()).thenReturn(tempDatabaseDir);
    when(commonConfiguration.getDataPath()).thenReturn(tempDataDir);
    when(commonConfiguration.getDatabaseVersion()).thenReturn(DEFAULT_VERSION);

    final RocksDBKeyValuePrivacyStorageFactory storageFactory =
        new RocksDBKeyValuePrivacyStorageFactory(
            new RocksDBKeyValueStorageFactory(
                () -> rocksDbConfiguration,
                segments,
                RocksDBMetricsFactory.PRIVATE_ROCKS_DB_METRICS));

    // Side effect is creation of the Metadata version file
    storageFactory.create(segment, commonConfiguration, metricsSystem);

    assertThat(DatabaseMetadata.lookUpFrom(commonConfiguration.getDataPath()).maybePrivacyVersion())
        .isNotEmpty();

    assertThat(DatabaseMetadata.lookUpFrom(commonConfiguration.getDataPath()).getVersion())
        .isEqualTo(DEFAULT_VERSION);

    assertThat(
            DatabaseMetadata.lookUpFrom(commonConfiguration.getDataPath())
                .maybePrivacyVersion()
                .get())
        .isEqualTo(DEFAULT_PRIVACY_VERSION);
  }

  @Test
  public void shouldUpdateCorrectMetadataFileForLatestVersion() throws Exception {
    final Path tempDataDir = temporaryFolder.newFolder().toPath().resolve("data");
    final Path tempDatabaseDir = temporaryFolder.newFolder().toPath().resolve("db");
    when(commonConfiguration.getStoragePath()).thenReturn(tempDatabaseDir);
    when(commonConfiguration.getDataPath()).thenReturn(tempDataDir);
    when(commonConfiguration.getDatabaseVersion()).thenReturn(DEFAULT_VERSION);

    final RocksDBKeyValueStorageFactory storageFactory =
        new RocksDBKeyValueStorageFactory(
            () -> rocksDbConfiguration, segments, RocksDBMetricsFactory.PRIVATE_ROCKS_DB_METRICS);

    storageFactory.create(segment, commonConfiguration, metricsSystem);

    assertThat(DatabaseMetadata.lookUpFrom(commonConfiguration.getDataPath()).maybePrivacyVersion())
        .isEmpty();

    assertThat(DatabaseMetadata.lookUpFrom(commonConfiguration.getDataPath()).getVersion())
        .isEqualTo(DEFAULT_VERSION);

    final RocksDBKeyValuePrivacyStorageFactory privacyStorageFactory =
        new RocksDBKeyValuePrivacyStorageFactory(storageFactory);

    privacyStorageFactory.create(segment, commonConfiguration, metricsSystem);

    assertThat(DatabaseMetadata.lookUpFrom(commonConfiguration.getDataPath()).maybePrivacyVersion())
        .isNotEmpty();

    assertThat(
            DatabaseMetadata.lookUpFrom(commonConfiguration.getDataPath())
                .maybePrivacyVersion()
                .get())
        .isEqualTo(DEFAULT_PRIVACY_VERSION);
  }
}
