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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.metrics.ObservableMetricsSystem;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.plugin.services.PantheonConfiguration;
import tech.pegasys.pantheon.plugin.services.exception.StorageException;
import tech.pegasys.pantheon.plugin.services.storage.SegmentIdentifier;
import tech.pegasys.pantheon.plugin.services.storage.rocksdb.configuration.DatabaseMetadata;
import tech.pegasys.pantheon.plugin.services.storage.rocksdb.configuration.RocksDBFactoryConfiguration;

import java.nio.charset.Charset;
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
public class RocksDBKeyValueStorageFactoryTest {

  private static final String METADATA_FILENAME = "DATABASE_METADATA.json";
  private static final int DEFAULT_VERSION = 1;

  @Mock private RocksDBFactoryConfiguration rocksDbConfiguration;
  @Mock private PantheonConfiguration commonConfiguration;
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private final ObservableMetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final List<SegmentIdentifier> segments = List.of();
  @Mock private SegmentIdentifier segment;

  @Test
  public void shouldCreateCorrectMetadataFileForLatestVersion() throws Exception {
    final Path tempDatabaseDir = temporaryFolder.newFolder().toPath().resolve("db");
    when(commonConfiguration.getStoragePath()).thenReturn(tempDatabaseDir);

    final RocksDBKeyValueStorageFactory storageFactory =
        new RocksDBKeyValueStorageFactory(() -> rocksDbConfiguration, segments);

    // Side effect is creation of the Metadata version file
    storageFactory.create(() -> "block-chain", commonConfiguration, metricsSystem);

    assertEquals(
        DEFAULT_VERSION,
        DatabaseMetadata.fromDirectory(commonConfiguration.getStoragePath()).getVersion());
  }

  @Test
  public void shouldDetectVersion0DatabaseIfNoMetadataFileFound() throws Exception {
    final Path tempDatabaseDir = temporaryFolder.newFolder().toPath().resolve("db");
    Files.createDirectories(tempDatabaseDir);
    tempDatabaseDir.resolve("IDENTITY").toFile().createNewFile();
    when(commonConfiguration.getStoragePath()).thenReturn(tempDatabaseDir);

    final RocksDBKeyValueStorageFactory storageFactory =
        new RocksDBKeyValueStorageFactory(() -> rocksDbConfiguration, segments);

    storageFactory.create(segment, commonConfiguration, metricsSystem);

    assertEquals(0, DatabaseMetadata.fromDirectory(tempDatabaseDir).getVersion());
  }

  @Test
  public void shouldDetectCorrectVersionIfMetadataFileExists() throws Exception {
    final Path tempDatabaseDir = temporaryFolder.newFolder().toPath().resolve("db");
    Files.createDirectories(tempDatabaseDir);
    tempDatabaseDir.resolve("IDENTITY").toFile().createNewFile();
    new DatabaseMetadata(DEFAULT_VERSION).writeToDirectory(tempDatabaseDir);
    when(commonConfiguration.getStoragePath()).thenReturn(tempDatabaseDir);
    final RocksDBKeyValueStorageFactory storageFactory =
        new RocksDBKeyValueStorageFactory(() -> rocksDbConfiguration, segments);

    storageFactory.create(() -> "block-chain", commonConfiguration, metricsSystem);

    assertEquals(DEFAULT_VERSION, DatabaseMetadata.fromDirectory(tempDatabaseDir).getVersion());
    assertTrue(storageFactory.isSegmentIsolationSupported());
  }

  @Test
  public void shouldThrowExceptionWhenVersionNumberIsInvalid() throws Exception {
    final Path tempDatabaseDir = temporaryFolder.newFolder().toPath().resolve("db");
    Files.createDirectories(tempDatabaseDir);
    tempDatabaseDir.resolve("IDENTITY").toFile().createNewFile();
    new DatabaseMetadata(-1).writeToDirectory(tempDatabaseDir);
    when(commonConfiguration.getStoragePath()).thenReturn(tempDatabaseDir);

    assertThatThrownBy(
            () ->
                new RocksDBKeyValueStorageFactory(() -> rocksDbConfiguration, segments)
                    .create(() -> "segment-does-not-matter", commonConfiguration, metricsSystem))
        .isInstanceOf(StorageException.class);
  }

  @Test
  public void shouldThrowExceptionWhenMetaDataFileIsCorrupted() throws Exception {
    final Path tempDatabaseDir = temporaryFolder.newFolder().toPath().resolve("db");
    Files.createDirectories(tempDatabaseDir);
    when(commonConfiguration.getStoragePath()).thenReturn(tempDatabaseDir);
    tempDatabaseDir.resolve("IDENTITY").toFile().createNewFile();
    final String badVersion = "{\"🦄\":1}";
    Files.write(
        tempDatabaseDir.resolve(METADATA_FILENAME), badVersion.getBytes(Charset.defaultCharset()));

    assertThatThrownBy(
            () ->
                new RocksDBKeyValueStorageFactory(() -> rocksDbConfiguration, segments)
                    .create(() -> "bad-version", commonConfiguration, metricsSystem))
        .isInstanceOf(IllegalStateException.class);

    final String badValue = "{\"version\":\"iomedae\"}";
    Files.write(
        tempDatabaseDir.resolve(METADATA_FILENAME), badValue.getBytes(Charset.defaultCharset()));

    assertThatThrownBy(
            () ->
                new RocksDBKeyValueStorageFactory(() -> rocksDbConfiguration, segments)
                    .create(() -> "bad-value", commonConfiguration, metricsSystem))
        .isInstanceOf(IllegalStateException.class);
  }
}
