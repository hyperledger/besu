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
 */
package org.hyperledger.besu.plugin.services.storage.rocksdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.DatabaseMetadata;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBFactoryConfiguration;
import org.hyperledger.besu.plugin.services.storage.rocksdb.segmented.RocksDBColumnarKeyValueStorageTest.TestSegment;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RocksDBKeyValueStorageFactoryTest {

  private static final String METADATA_FILENAME = "DATABASE_METADATA.json";
  private static final int DEFAULT_VERSION = 1;

  @Mock private RocksDBFactoryConfiguration rocksDbConfiguration;
  @Mock private BesuConfiguration commonConfiguration;
  @TempDir public Path temporaryFolder;
  private final ObservableMetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final SegmentIdentifier segment = TestSegment.FOO;
  private final List<SegmentIdentifier> segments = List.of(TestSegment.DEFAULT, segment);

  @Test
  public void shouldCreateCorrectMetadataFileForLatestVersion() throws Exception {
    final Path tempDataDir = temporaryFolder.resolve("data");
    final Path tempDatabaseDir = temporaryFolder.resolve("db");
    when(commonConfiguration.getStoragePath()).thenReturn(tempDatabaseDir);
    when(commonConfiguration.getDataPath()).thenReturn(tempDataDir);
    when(commonConfiguration.getDatabaseFormat()).thenReturn(DataStorageFormat.FOREST);

    final RocksDBKeyValueStorageFactory storageFactory =
        new RocksDBKeyValueStorageFactory(
            () -> rocksDbConfiguration, segments, RocksDBMetricsFactory.PUBLIC_ROCKS_DB_METRICS);

    // Side effect is creation of the Metadata version file
    storageFactory.create(segment, commonConfiguration, metricsSystem);

    assertThat(DatabaseMetadata.lookUpFrom(tempDataDir, commonConfiguration.getDatabaseFormat()).getVersion()).isEqualTo(DEFAULT_VERSION);
  }

  @Test
  public void shouldDetectVersion1DatabaseIfNoMetadataFileFound() throws Exception {
    final Path tempDataDir = temporaryFolder.resolve("data");
    final Path tempDatabaseDir = temporaryFolder.resolve("db");
    Files.createDirectories(tempDatabaseDir);
    Files.createDirectories(tempDataDir);
    when(commonConfiguration.getStoragePath()).thenReturn(tempDatabaseDir);
    when(commonConfiguration.getDataPath()).thenReturn(tempDataDir);

    final RocksDBKeyValueStorageFactory storageFactory =
        new RocksDBKeyValueStorageFactory(
            () -> rocksDbConfiguration, segments, RocksDBMetricsFactory.PUBLIC_ROCKS_DB_METRICS);

    storageFactory.create(segment, commonConfiguration, metricsSystem);

    assertThat(DatabaseMetadata.lookUpFrom(tempDataDir, commonConfiguration.getDatabaseFormat()).getVersion()).isEqualTo(DEFAULT_VERSION);
  }

  @Test
  public void shouldDetectCorrectVersionIfMetadataFileExists() throws Exception {
    final Path tempDataDir = temporaryFolder.resolve("data");
    final Path tempDatabaseDir = temporaryFolder.resolve("db");
    Files.createDirectories(tempDataDir);
    when(commonConfiguration.getStoragePath()).thenReturn(tempDatabaseDir);
    when(commonConfiguration.getDataPath()).thenReturn(tempDataDir);
    when(commonConfiguration.getDatabaseFormat()).thenReturn(DataStorageFormat.FOREST);

    final RocksDBKeyValueStorageFactory storageFactory =
        new RocksDBKeyValueStorageFactory(
            () -> rocksDbConfiguration, segments, RocksDBMetricsFactory.PUBLIC_ROCKS_DB_METRICS);

    storageFactory.create(segment, commonConfiguration, metricsSystem);

    assertThat(DatabaseMetadata.lookUpFrom(tempDataDir, commonConfiguration.getDatabaseFormat()).getVersion()).isEqualTo(DEFAULT_VERSION);
    assertThat(storageFactory.isSegmentIsolationSupported()).isTrue();
  }

  @Test
  public void shouldDetectCorrectVersionInCaseOfRollback() throws Exception {
    final Path tempDataDir = temporaryFolder.resolve("data");
    final Path tempDatabaseDir = temporaryFolder.resolve("db");
    Files.createDirectories(tempDatabaseDir);
    Files.createDirectories(tempDataDir);
    when(commonConfiguration.getStoragePath()).thenReturn(tempDatabaseDir);
    when(commonConfiguration.getDataPath()).thenReturn(tempDataDir);

    final RocksDBKeyValueStorageFactory storageFactory =
        new RocksDBKeyValueStorageFactory(
            () -> rocksDbConfiguration,
            segments,
            DataStorageFormat.BONSAI,
            RocksDBMetricsFactory.PUBLIC_ROCKS_DB_METRICS);

    storageFactory.create(segment, commonConfiguration, metricsSystem);
    storageFactory.close();

    final RocksDBKeyValueStorageFactory rolledbackStorageFactory =
        new RocksDBKeyValueStorageFactory(
            () -> rocksDbConfiguration,
            segments,
            DataStorageFormat.FOREST,
            RocksDBMetricsFactory.PUBLIC_ROCKS_DB_METRICS);
    rolledbackStorageFactory.create(segment, commonConfiguration, metricsSystem);
  }

  @Test
  public void shouldThrowExceptionWhenVersionNumberIsInvalid() throws Exception {
    final Path tempDataDir = temporaryFolder.resolve("data");
    final Path tempDatabaseDir = temporaryFolder.resolve("db");
    Files.createDirectories(tempDatabaseDir);
    Files.createDirectories(tempDataDir);
    when(commonConfiguration.getStoragePath()).thenReturn(tempDatabaseDir);
    when(commonConfiguration.getDataPath()).thenReturn(tempDataDir);
    new DatabaseMetadata(DataStorageFormat.FOREST, 99).writeToDirectory(tempDataDir);
    assertThatThrownBy(
            () ->
                new RocksDBKeyValueStorageFactory(
                        () -> rocksDbConfiguration,
                        segments,
                        RocksDBMetricsFactory.PUBLIC_ROCKS_DB_METRICS)
                    .create(segment, commonConfiguration, metricsSystem))
        .isInstanceOf(StorageException.class);
  }

  @Test
  public void shouldSetSegmentationFieldDuringCreation() throws Exception {
    final Path tempDataDir = temporaryFolder.resolve("data");
    final Path tempDatabaseDir = temporaryFolder.resolve("db");
    Files.createDirectories(tempDatabaseDir);
    Files.createDirectories(tempDataDir);
    when(commonConfiguration.getStoragePath()).thenReturn(tempDatabaseDir);
    when(commonConfiguration.getDataPath()).thenReturn(tempDataDir);

    final RocksDBKeyValueStorageFactory storageFactory =
        new RocksDBKeyValueStorageFactory(
            () -> rocksDbConfiguration, segments, RocksDBMetricsFactory.PUBLIC_ROCKS_DB_METRICS);
    storageFactory.create(segment, commonConfiguration, metricsSystem);
    assertThatCode(storageFactory::isSegmentIsolationSupported).doesNotThrowAnyException();
  }

  @Test
  public void shouldThrowExceptionWhenMetaDataFileIsCorrupted() throws Exception {
    final Path tempDataDir = temporaryFolder.resolve("data");
    final Path tempDatabaseDir = temporaryFolder.resolve("db");
    Files.createDirectories(tempDatabaseDir);
    Files.createDirectories(tempDataDir);
    when(commonConfiguration.getStoragePath()).thenReturn(tempDatabaseDir);
    when(commonConfiguration.getDataPath()).thenReturn(tempDataDir);

    final String badVersion = "{\"🦄\":1}";
    Files.write(
        tempDataDir.resolve(METADATA_FILENAME), badVersion.getBytes(Charset.defaultCharset()));

    assertThatThrownBy(
            () ->
                new RocksDBKeyValueStorageFactory(
                        () -> rocksDbConfiguration,
                        segments,
                        RocksDBMetricsFactory.PUBLIC_ROCKS_DB_METRICS)
                    .create(segment, commonConfiguration, metricsSystem))
        .isInstanceOf(IllegalStateException.class);

    final String badValue = "{\"version\":\"iomedae\"}";
    Files.write(
        tempDatabaseDir.resolve(METADATA_FILENAME), badValue.getBytes(Charset.defaultCharset()));

    assertThatThrownBy(
            () ->
                new RocksDBKeyValueStorageFactory(
                        () -> rocksDbConfiguration,
                        segments,
                        RocksDBMetricsFactory.PUBLIC_ROCKS_DB_METRICS)
                    .create(segment, commonConfiguration, metricsSystem))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldCreateDBCorrectlyIfSymlink() throws Exception {
    final Path tempRealDataDir = Files.createDirectories(temporaryFolder.resolve("real-data-dir"));
    final Path tempSymLinkDataDir =
        Files.createSymbolicLink(temporaryFolder.resolve("symlink-data-dir"), tempRealDataDir);
    final Path tempDatabaseDir = temporaryFolder.resolve("db");
    when(commonConfiguration.getStoragePath()).thenReturn(tempDatabaseDir);
    when(commonConfiguration.getDataPath()).thenReturn(tempSymLinkDataDir);
    when(commonConfiguration.getDatabaseFormat()).thenReturn(DataStorageFormat.FOREST);

    final RocksDBKeyValueStorageFactory storageFactory =
        new RocksDBKeyValueStorageFactory(
            () -> rocksDbConfiguration, segments, RocksDBMetricsFactory.PUBLIC_ROCKS_DB_METRICS);

    // Ensure that having created everything via a symlink data dir the DB meta-data has been
    // created correctly
    storageFactory.create(segment, commonConfiguration, metricsSystem);
    assertThat(DatabaseMetadata.lookUpFrom(tempRealDataDir, commonConfiguration.getDatabaseFormat()).getVersion())
        .isEqualTo(DEFAULT_VERSION);
  }
}
